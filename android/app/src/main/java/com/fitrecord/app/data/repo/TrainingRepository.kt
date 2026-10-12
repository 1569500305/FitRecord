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
import com.fitrecord.app.data.db.entity.RecordBodyPartEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.domain.CalorieCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * P4/P5 读写训练记录的唯一入口（规格模块1-2）。
 * "健身次数"没有字段，就是当日记录条数（规格 4.2）。
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

    fun observeDay(date: LocalDate): Flow<List<DayRecord>> =
        trainingDao.observeRange(date.epochDay(), date.epochDay())
            .map { rows -> rows.map { toDayRecord(it) } }

    /** 供日历页按任意区间观察（Task 14 的 GridKey 驱动） */
    fun observeRecords(range: IntRange): Flow<List<RecordWithType>> =
        trainingDao.observeRange(range.first, range.last)

    fun observeFoods(): Flow<List<FoodItemEntity>> = foodDao.observeAll()

    /** null = 未填体重，UI 显示引导 Banner（规格 6.1） */
    fun observeWeightKg(): Flow<Double?> = profileDao.observeProfile().map { it?.weightKg }

    suspend fun dayRecords(date: LocalDate): List<DayRecord> =
        trainingDao.rangeOnce(date.epochDay(), date.epochDay()).map { toDayRecord(it) }

    suspend fun add(
        date: LocalDate,
        exerciseTypeId: Long,
        durationMin: Int,
        bodyPartIds: List<Long>,
        note: String,
    ): Long {
        val now = System.currentTimeMillis()
        val id = trainingDao.insert(
            TrainingRecordEntity(
                date = date.epochDay(),
                exerciseTypeId = exerciseTypeId,
                durationMin = durationMin,
                calories = calc(exerciseTypeId, durationMin),
                note = note,
                createdAt = now,
                updatedAt = now,
            )
        )
        replaceBodyParts(id, bodyPartIds)
        return id
    }

    suspend fun update(
        id: Long,
        date: LocalDate,
        exerciseTypeId: Long,
        durationMin: Int,
        bodyPartIds: List<Long>,
        note: String,
    ) {
        val old = trainingDao.byId(id) ?: return
        trainingDao.update(
            old.copy(
                date = date.epochDay(),
                exerciseTypeId = exerciseTypeId,
                durationMin = durationMin,
                calories = calc(exerciseTypeId, durationMin),
                note = note,
                updatedAt = System.currentTimeMillis(),
            )
        )
        replaceBodyParts(id, bodyPartIds)
    }

    /** 返回快照供 Snackbar 撤销（规格 5.4）；null 表示记录已不存在 */
    suspend fun delete(id: Long): DayRecord? {
        val entity = trainingDao.byId(id) ?: return null
        val snapshot = dayRecords(LocalDate.ofEpochDay(entity.date.toLong())).first { it.id == id }
        trainingDao.deleteById(id)
        return snapshot
    }

    suspend fun restore(record: DayRecord): Long =
        add(record.date, record.type.id, record.durationMin, record.bodyPartIds, record.note)

    /**
     * 规格 P4 的"今日已健身"开关。
     * 开：当日无记录时补一条默认 60 分钟（类型取启用列表首个，即内置划船机）；
     *     用户"最近使用类型"由 SettingsStore 记录，只用于 P5 编辑器默认选中（台账 P1）。
     * 关：清空当日全部记录，返回被清掉的条数。
     */
    suspend fun setTrainedToday(date: LocalDate, trained: Boolean): Int {
        val existing = dayRecords(date)
        if (trained) {
            if (existing.isEmpty()) {
                val typeId = defaultTypeId()
                add(date, typeId, durationMin = 60, bodyPartIds = defaultBodyParts(typeId), note = "")
            }
            return existing.size
        }
        existing.forEach { trainingDao.deleteById(it.id) }
        return existing.size
    }

    suspend fun dayNote(date: LocalDate): String = noteDao.note(date.epochDay())?.text.orEmpty()

    suspend fun saveDayNote(date: LocalDate, text: String) {
        val day = date.epochDay()
        if (text.isBlank()) noteDao.delete(day)
        else noteDao.upsert(DailyNoteEntity(day, text, System.currentTimeMillis()))
    }

    private suspend fun replaceBodyParts(recordId: Long, bodyPartIds: List<Long>) {
        trainingDao.clearBodyParts(recordId)
        if (bodyPartIds.isNotEmpty()) {
            trainingDao.linkBodyParts(bodyPartIds.distinct().map { RecordBodyPartEntity(recordId, it) })
        }
    }

    private suspend fun defaultTypeId(): Long =
        typeDao.observeActive().first().firstOrNull()?.id
            ?: typeDao.all().first().id

    private suspend fun defaultBodyParts(typeId: Long): List<Long> =
        typeDao.byId(typeId)?.let { BodyParts.parseIds(it.defaultBodyPartIds) }.orEmpty()

    private suspend fun calc(typeId: Long, durationMin: Int): Double? =
        CalorieCalculator.kcal(
            met = typeDao.byId(typeId)?.met ?: 0.0,
            weightKg = profileDao.profile()?.weightKg,
            durationMin = durationMin,
        )

    private suspend fun toDayRecord(row: RecordWithType): DayRecord = DayRecord(
        id = row.id,
        date = LocalDate.ofEpochDay(row.date.toLong()),
        type = typeDao.byId(row.exerciseTypeId) ?: ExerciseTypeEntity(
            id = row.exerciseTypeId,
            name = row.typeName,
            met = row.met,
            color = row.typeColor,
            isBuiltin = true,
        ),
        durationMin = row.durationMin,
        calories = row.calories,
        note = row.note,
        bodyPartIds = trainingDao.bodyPartIds(row.id),
    )

    private fun LocalDate.epochDay(): Int = toEpochDay().toInt()
}
