package com.fitrecord.app.data.repo

import androidx.room.withTransaction
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.MetaKeys
import com.fitrecord.app.data.db.dao.BodyPartDao
import com.fitrecord.app.data.db.dao.MetaDao
import com.fitrecord.app.data.db.dao.NoteDao
import com.fitrecord.app.data.db.dao.ProfileDao
import com.fitrecord.app.data.db.dao.TrainingDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.db.entity.DailyNoteEntity
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.entity.MetaEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.domain.CalorieCalculator
import com.fitrecord.app.domain.backup.BackupCodec
import com.fitrecord.app.domain.backup.BackupFile
import com.fitrecord.app.domain.backup.ConflictPolicy
import com.fitrecord.app.domain.backup.CsvCodec
import com.fitrecord.app.domain.backup.DailyNoteDto
import com.fitrecord.app.domain.backup.ExerciseTypeDto
import com.fitrecord.app.domain.backup.MergeResolver
import com.fitrecord.app.domain.backup.ProfileDto
import com.fitrecord.app.domain.backup.RecordDto
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 备份导出 / 导入（规格模块4-3 / 4.7 / 6.5）。
 *
 * 依赖方向（评审前瞻）：本类在 data 层，**不反向 import ui**。新建自定义类型所需的
 * 取色逻辑由组合根（AppContainer）以 [colorForNewType] 注入。
 *
 * 导入全程包在 [FitRecordDatabase.withTransaction] 里：类型创建、记录写入、身体参数与
 * 备注恢复都在**同一事务**，任一抛错整批回滚，不留"半截备份"（规格 6.5）。
 */
class BackupRepository(
    private val db: FitRecordDatabase,
    private val trainingDao: TrainingDao,
    private val typeDao: TypeDao,
    private val profileDao: ProfileDao,
    private val noteDao: NoteDao,
    private val bodyPartDao: BodyPartDao,
    private val metaDao: MetaDao,
    private val colorForNewType: (Long) -> Long,
) {

    /** 预览：只统计不落库（规格 6.5，用户先看清再确认） */
    data class MergePreview(
        val total: Int,
        val toInsert: Int,
        val toUpdate: Int,
        val skipped: Int,
        val errors: List<String>,
    )

    /** 实际写入结果 */
    data class MergeOutcome(
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val errors: Int,
    )

    // ---------------------------------------------------------------- 导出

    suspend fun exportJson(stream: OutputStream): Int {
        val file = buildBackupFile()
        stream.writer(Charsets.UTF_8).use { it.write(BackupCodec.toJson(file)) }
        return file.records.size
    }

    suspend fun exportCsv(stream: OutputStream): Int {
        val records = currentRecordDtos()
        stream.writer(Charsets.UTF_8).use { it.write(CsvCodec.writeAll(records)) }
        return records.size
    }

    private suspend fun buildBackupFile(): BackupFile {
        val partsById = bodyPartDao.all().associate { it.id to it.name }
        val linksByRecord = trainingDao.allLinks().groupBy { it.recordId }
        val records = trainingDao.rangeOnce(0, Int.MAX_VALUE).map { row ->
            RecordDto(
                date = LocalDate.ofEpochDay(row.date.toLong()).toString(),
                exercise = row.typeName,
                met = row.met,
                durationMin = row.durationMin,
                calories = row.calories,
                bodyParts = linksByRecord[row.id].orEmpty().mapNotNull { partsById[it.bodyPartId] },
                note = row.note,
            )
        }
        return BackupFile(
            schemaVersion = BackupCodec.CURRENT_VERSION,
            exportedAt = LocalDateTime.now().toString(),
            profile = profileDao.profile()?.let {
                ProfileDto(gender = it.gender, age = it.age, heightCm = it.heightCm, weightKg = it.weightKg)
            },
            exerciseTypes = typeDao.all().map { ExerciseTypeDto(name = it.name, met = it.met, archived = it.archived) },
            records = records,
            dailyNotes = noteDao.all().map {
                DailyNoteDto(LocalDate.ofEpochDay(it.date.toLong()).toString(), it.text)
            },
        )
    }

    // ---------------------------------------------------------------- 导入

    /** 预览一段备份将产生什么效果，不写库 */
    suspend fun preview(stream: InputStream, policy: ConflictPolicy): MergePreview {
        val plan = MergeResolver.resolve(parsed(stream).records, existingDtos(), policy)
        return MergePreview(
            total = plan.total,
            toInsert = plan.toInsert.size,
            toUpdate = plan.toUpdate.size,
            skipped = plan.skipped,
            errors = plan.errors,
        )
    }

    /** UI 默认入口：遇脏行不阻断，跳过并计数（规格 6.5 前 5 条列出即可） */
    suspend fun apply(stream: InputStream, policy: ConflictPolicy): MergeOutcome =
        applyInternal(stream, policy, strict = false)

    /** 解析/校验阶段遇脏行即抛、整批不进入写入；仅测试用于验证不留半截 */
    suspend fun applyStrict(stream: InputStream, policy: ConflictPolicy): MergeOutcome =
        applyInternal(stream, policy, strict = true)

    private suspend fun applyInternal(
        stream: InputStream,
        policy: ConflictPolicy,
        strict: Boolean,
    ): MergeOutcome {
        val parsed = parsed(stream)
        val existing = existingRows()
        val plan = MergeResolver.resolve(parsed.records, existing.map { it.first }, policy)
        if (strict && plan.errors.isNotEmpty()) {
            // 在开事务之前就拒绝：整批不进入写入，杜绝"半截备份"（规格 6.5）
            throw IllegalStateException("导入存在无法解析的行：${plan.errors.first()}")
        }

        var inserted = 0
        var updated = 0
        db.withTransaction {
            // 1) 先补建运动类型：文件里显式声明的（含 archived）+ 记录引用的未知类型；
            //    记录的外键 RESTRICT 要求父类型先存在。
            for (t in parsed.exerciseTypes) {
                if (typeDao.byName(t.name) == null) createType(t.name, t.met, t.archived)
            }
            for (t in plan.newTypes) {
                if (typeDao.byName(t.name) == null) createType(t.name, t.met)
            }
            // 2) 覆盖更新：训练记录表无唯一约束，同键可能有多行；用"键 → 待消费 id 队列"
            //    逐条配对，避免把多条更新全打在同一个 id 上、其余永不刷新（评审 I-5）。
            val idsByKey = existing
                .groupBy({ MergeResolver.keyOf(it.first) }, { it.second })
                .mapValues { it.value.toMutableList() }
            if (policy == ConflictPolicy.OVERWRITE) {
                for (dto in plan.toUpdate) {
                    val id = idsByKey[MergeResolver.keyOf(dto)]?.removeFirstOrNull()
                    if (id != null && updateRecord(id, dto)) updated++
                }
            }
            // 3) 新增
            for (dto in plan.toInsert) {
                insertRecord(dto)
                inserted++
            }
            // 4) 恢复身体参数：仅当本机还没有时写入，不覆盖用户当前体重
            //    （评审 I-4：JSON 不只是记录表，换手机恢复必须带身体参数）。
            parsed.profile?.let { p ->
                if (profileDao.profile() == null) {
                    profileDao.upsert(
                        UserProfileEntity(
                            id = 0, gender = p.gender, age = p.age, heightCm = p.heightCm,
                            weightKg = p.weightKg, weeklyGoal = 0, updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            // 5) 恢复每日备注：SKIP 保留本机已有非空备注，OVERWRITE 覆盖
            for (n in parsed.dailyNotes) {
                val day = LocalDate.parse(n.date.trim()).toEpochDay().toInt()
                if (policy == ConflictPolicy.SKIP && noteDao.note(day)?.text?.isNotBlank() == true) continue
                noteDao.upsert(DailyNoteEntity(day, n.text, System.currentTimeMillis()))
            }
            // 6) 导入日志（统一用 MetaKeys.LAST_IMPORT，值含四类计数）
            metaDao.put(
                MetaEntity(
                    MetaKeys.LAST_IMPORT,
                    "${System.currentTimeMillis()}|$inserted|$updated|${plan.skipped}|${plan.errors.size}",
                )
            )
        }
        return MergeOutcome(inserted, updated, plan.skipped, plan.errors.size)
    }

    // ---------------------------------------------------------------- 映射

    private suspend fun insertRecord(dto: RecordDto) {
        val day = LocalDate.parse(dto.date.trim()).toEpochDay().toInt()
        val typeId = resolveTypeId(dto.exercise.trim(), dto.met)
        val partIds = resolvePartIds(dto.bodyParts)
        val now = System.currentTimeMillis()
        val kcal = dto.calories ?: CalorieCalculator.kcal(dto.met, profileDao.profile()?.weightKg, dto.durationMin)
        trainingDao.insertWithBodyParts(
            entity = TrainingRecordEntity(
                date = day,
                exerciseTypeId = typeId,
                durationMin = dto.durationMin,
                calories = kcal,
                note = dto.note,
                createdAt = now,
                updatedAt = now,
            ),
            bodyPartIds = partIds,
        )
    }

    private suspend fun updateRecord(id: Long, dto: RecordDto): Boolean {
        val entity = trainingDao.byId(id) ?: return false
        val day = LocalDate.parse(dto.date.trim()).toEpochDay().toInt()
        val typeId = resolveTypeId(dto.exercise.trim(), dto.met)
        val kcal = dto.calories ?: CalorieCalculator.kcal(dto.met, profileDao.profile()?.weightKg, dto.durationMin)
        trainingDao.updateWithBodyParts(
            entity = entity.copy(
                date = day,
                exerciseTypeId = typeId,
                durationMin = dto.durationMin,
                calories = kcal,
                note = dto.note,
                updatedAt = System.currentTimeMillis(),
            ),
            bodyPartIds = resolvePartIds(dto.bodyParts),
        )
        return true
    }

    /** 类型名 → id；不存在则新建（自定义、非内置、按注入的调色板取色，规格 4.7） */
    private suspend fun resolveTypeId(name: String, met: Double): Long {
        typeDao.byName(name)?.let { return it.id }
        return createType(name, met)
    }

    private suspend fun createType(name: String, met: Double, archived: Boolean = false): Long {
        val sortOrder = typeDao.all().size + SORT_ORDER_BASE
        val entity = ExerciseTypeEntity(
            name = name, met = met, color = 0L, isBuiltin = false, archived = archived, sortOrder = sortOrder,
        )
        val newId = typeDao.insert(entity)
        // 颜色按真实 id 取，需一次 update；插入先占位避免非空列缺失
        typeDao.update(entity.copy(id = newId, color = colorForNewType(newId)))
        return newId
    }

    private suspend fun resolvePartIds(names: List<String>): List<Long> {
        if (names.isEmpty()) return emptyList()
        val byName = bodyPartDao.all().associate { it.name to it.id }
        return names.mapNotNull { byName[it.trim()] }.distinct()
    }

    // ---------------------------------------------------------------- 读取来数据

    /** 一段备份解析后的完整内容（CSV 只有 records，其余节为空/默认） */
    private data class ParsedBackup(
        val records: List<RecordDto>,
        val profile: ProfileDto? = null,
        val exerciseTypes: List<ExerciseTypeDto> = emptyList(),
        val dailyNotes: List<DailyNoteDto> = emptyList(),
    )

    /** 依据首字符区分 JSON / CSV；先剥 BOM 再嗅探（Windows 记事本另存默认带 BOM） */
    private fun parsed(stream: InputStream): ParsedBackup {
        val text = stream.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        return if (text.startsWith("{")) {
            val file = BackupCodec.fromJson(text).getOrThrow()
            ParsedBackup(file.records, file.profile, file.exerciseTypes, file.dailyNotes)
        } else {
            ParsedBackup(CsvCodec.parse(text).getOrThrow())
        }
    }

    /** 现有记录投影为参与幂等键比较的 RecordDto 列表 */
    private suspend fun existingDtos(): List<RecordDto> = existingRows().map { it.first }

    private suspend fun existingRows(): List<Pair<RecordDto, Long>> =
        trainingDao.rangeOnce(0, Int.MAX_VALUE).map { row ->
            // 键 = date|exercise|durationMin|note，不含 bodyParts/calories，故这两列留空即可
            val dto = RecordDto(
                date = LocalDate.ofEpochDay(row.date.toLong()).toString(),
                exercise = row.typeName,
                met = row.met,
                durationMin = row.durationMin,
                calories = row.calories,
                bodyParts = emptyList(),
                note = row.note,
            )
            dto to row.id
        }

    /** 当前全部记录（含部位名），供 CSV 导出 */
    private suspend fun currentRecordDtos(): List<RecordDto> {
        val partsById = bodyPartDao.all().associate { it.id to it.name }
        val linksByRecord = trainingDao.allLinks().groupBy { it.recordId }
        return trainingDao.rangeOnce(0, Int.MAX_VALUE).map { row ->
            RecordDto(
                date = LocalDate.ofEpochDay(row.date.toLong()).toString(),
                exercise = row.typeName,
                met = row.met,
                durationMin = row.durationMin,
                calories = row.calories,
                bodyParts = linksByRecord[row.id].orEmpty().mapNotNull { partsById[it.bodyPartId] },
                note = row.note,
            )
        }
    }

    companion object {
        private const val SORT_ORDER_BASE = 100
        private val NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)

        /** 默认文件名 fitrecord-YYYYMMDD-HHmm.ext（规格 6.7） */
        fun suggestedName(ext: String, now: LocalDate = LocalDate.now()): String =
            "fitrecord-${NAME_TIME.format(now.atStartOfDay())}.$ext"
    }
}
