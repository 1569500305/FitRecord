package com.fitrecord.app.data.repo

import com.fitrecord.app.data.db.RecordWithType
import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.NoteDao
import com.fitrecord.app.data.db.dao.ProfileDao
import com.fitrecord.app.data.db.dao.TrainingDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.db.entity.DailyNoteEntity
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.entity.FoodItemEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.domain.CalorieCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * P4/P5 读写训练记录的唯一入口（规格模块1-2）。
 *
 * 两条硬性约定：
 *  - "健身次数"没有字段，就是当日记录条数（规格 4.2）；
 *  - 读写路径零 N+1：类型信息全部来自 JOIN，部位关联按区间一次批量取回（评审 I-1）。
 */
class TrainingRepository(
    private val trainingDao: TrainingDao,
    private val typeDao: TypeDao,
    private val profileDao: ProfileDao,
    private val noteDao: NoteDao,
    private val foodDao: FoodDao,
) {

    /** 一条记录 + 它的类型与部位，UI 直接渲染这个结构 */
    data class DayRecord(
        val id: Long,
        val date: LocalDate,
        val type: ExerciseTypeEntity,
        val durationMin: Int,
        val calories: Double?,
        val note: String,
        val bodyPartIds: List<Long>,
    )

    /** setTrainedToday 的结果，四种语义互斥（复审 S-1） */
    enum class TodayToggle {
        /** 本次开关新增了一段默认 60 分钟记录 */
        Marked,

        /** 本次开关清空了当日记录 */
        Cleared,

        /** 打开时当日已有记录，未做变更 */
        AlreadyMarked,

        /** 关闭时当日本就没有记录，未做变更 */
        AlreadyUnmarked,

        /** 类型表为空（种子未就绪），UI 应提示重试而不是崩（评审 M-6） */
        NoTypesAvailable,
    }

    /** 当日记录（P4 主数据源）。记录与关联各一条查询，随写入自动 re-emit */
    fun observeDay(date: LocalDate): Flow<List<DayRecord>> {
        val day = date.epochDay()
        return combine(
            trainingDao.observeRange(day, day),
            trainingDao.observeLinksInRange(day, day),
        ) { rows, links -> assemble(date, rows, links) }
    }

    /** 供日历页按任意区间观察（Task 14 的 GridKey 驱动），返回原始扁平行 */
    fun observeRecords(range: IntRange): Flow<List<RecordWithType>> =
        trainingDao.observeRange(range.first, range.last)

    fun observeFoods(): Flow<List<FoodItemEntity>> = foodDao.observeAll()

    /** null = 未填体重，UI 显示引导 Banner（规格 6.1） */
    fun observeWeightKg(): Flow<Double?> = profileDao.observeProfile().map { it?.weightKg }

    fun observeDayNote(date: LocalDate): Flow<String> =
        noteDao.observeNote(date.epochDay()).map { it?.text.orEmpty() }

    suspend fun dayRecords(date: LocalDate): List<DayRecord> {
        val day = date.epochDay()
        return assemble(date, trainingDao.rangeOnce(day, day), trainingDao.linksInRange(day, day))
    }

    suspend fun add(
        date: LocalDate,
        exerciseTypeId: Long,
        durationMin: Int,
        bodyPartIds: List<Long>,
        note: String,
    ): Long =
        trainingDao.insertWithBodyParts(
            entity = TrainingRecordEntity(
                date = date.epochDay(),
                exerciseTypeId = exerciseTypeId,
                durationMin = durationMin,
                calories = calc(exerciseTypeId, durationMin),
                note = note,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
            bodyPartIds = bodyPartIds,
        )

    /** 返回 false 表示记录已不存在（并发删除），UI 据此刷新而不是崩 */
    suspend fun update(
        id: Long,
        date: LocalDate,
        exerciseTypeId: Long,
        durationMin: Int,
        bodyPartIds: List<Long>,
        note: String,
    ): Boolean {
        val old = trainingDao.byId(id) ?: return false
        trainingDao.updateWithBodyParts(
            entity = old.copy(
                date = date.epochDay(),
                exerciseTypeId = exerciseTypeId,
                durationMin = durationMin,
                calories = calc(exerciseTypeId, durationMin),
                note = note,
                updatedAt = System.currentTimeMillis(),
            ),
            bodyPartIds = bodyPartIds,
        )
        return true
    }

    /**
     * 返回快照供 Snackbar 撤销（规格 5.4）；null 表示记录已不存在。
     * 单事务内取回 + 删除（评审 I-2），不再为一条快照遍历整天记录。
     */
    suspend fun delete(id: Long): DayRecord? {
        val (row, parts) = trainingDao.deleteAndReturn(id)
        val record = row ?: return null
        return DayRecord(
            id = record.id,
            date = LocalDate.ofEpochDay(record.date.toLong()),
            type = typeOf(record),
            durationMin = record.durationMin,
            calories = record.calories,
            note = record.note,
            bodyPartIds = parts,
        )
    }

    suspend fun restore(record: DayRecord): Long =
        add(record.date, record.type.id, record.durationMin, record.bodyPartIds, record.note)

    /**
     * 规格 P4 的"今日已健身"开关。
     * 开：当日无记录时补一条默认 60 分钟（类型取启用列表首个，即内置划船机）；
     *     用户"最近使用类型"由 SettingsStore 记录，只用于 P5 编辑器默认选中（台账 P1）。
     * 关：单语句清空当日（评审 I-3）。
     *
     * 返回枚举而不是 Int（复审 S-1）：一个整数同时承载"删了几条"与"失败哨兵 -1"
     * 会让 P4 的提示文案失真，语义必须互斥。
     */
    suspend fun setTrainedToday(date: LocalDate, trained: Boolean): TodayToggle {
        if (!trained) {
            return if (trainingDao.deleteByDay(date.epochDay()) > 0) TodayToggle.Cleared else TodayToggle.AlreadyUnmarked
        }
        if (trainingDao.rangeOnce(date.epochDay(), date.epochDay()).isNotEmpty()) {
            return TodayToggle.AlreadyMarked
        }
        val typeId = typeDao.observeActive().first().firstOrNull()?.id ?: return TodayToggle.NoTypesAvailable
        add(date, typeId, durationMin = 60, bodyPartIds = defaultBodyParts(typeId), note = "")
        return TodayToggle.Marked
    }

    suspend fun dayNote(date: LocalDate): String = noteDao.note(date.epochDay())?.text.orEmpty()

    suspend fun saveDayNote(date: LocalDate, text: String) {
        val day = date.epochDay()
        if (text.isBlank()) noteDao.delete(day)
        else noteDao.upsert(DailyNoteEntity(day, text, System.currentTimeMillis()))
    }

    // ---------- 内部 ----------

    /**
     * 记录列表 + 一次性取回的关联列表 → 展示模型。全程两条查询。
     * 部位顺序由 SQL 的 `ORDER BY rb.bodyPartId` 决走；V1 中 sortOrder == id 所以等价，
     * V1.1 开放自定义部位后如需按 sortOrder 排，在这里加一次投影（复审 S-3）。
     */
    private fun assemble(
        date: LocalDate,
        rows: List<RecordWithType>,
        links: List<com.fitrecord.app.data.db.entity.RecordBodyPartEntity>,
    ): List<DayRecord> {
        val partsByRecord = links.groupBy { it.recordId }.mapValues { entry -> entry.value.map { it.bodyPartId } }
        return rows.map { row ->
            DayRecord(
                id = row.id,
                date = date,
                type = typeOf(row),
                durationMin = row.durationMin,
                calories = row.calories,
                note = row.note,
                bodyPartIds = partsByRecord[row.id].orEmpty(),
            )
        }
    }

    /** 直接用 JOIN 带回的字段构造类型，零额外查询（评审 I-1） */
    private fun typeOf(row: RecordWithType) = ExerciseTypeEntity(
        id = row.exerciseTypeId,
        name = row.typeName,
        met = row.met,
        color = row.typeColor,
        isBuiltin = row.typeIsBuiltin,
        archived = row.typeArchived,
        metOverridden = row.typeMetOverridden,
        sortOrder = row.typeSortOrder,
        defaultBodyPartIds = row.typeDefaultBodyPartIds,
    )

    private suspend fun defaultBodyParts(typeId: Long): List<Long> =
        typeDao.byId(typeId)?.let { BodyParts.parseIds(it.defaultBodyPartIds) }.orEmpty()

    private suspend fun calc(typeId: Long, durationMin: Int): Double? =
        CalorieCalculator.kcal(
            met = typeDao.byId(typeId)?.met ?: 0.0,
            weightKg = profileDao.profile()?.weightKg,
            durationMin = durationMin,
        )

    private fun LocalDate.epochDay(): Int = toEpochDay().toInt()
}
