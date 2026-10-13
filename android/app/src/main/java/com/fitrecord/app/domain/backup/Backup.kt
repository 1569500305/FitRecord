package com.fitrecord.app.domain.backup

import com.fitrecord.app.data.db.seed.BuiltinExercises
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

// ============================================================ 数据模型

@Serializable
data class ProfileDto(
    val gender: String?,
    val age: Int?,
    val heightCm: Double?,
    val weightKg: Double,
)

@Serializable
data class ExerciseTypeDto(
    val name: String,
    val met: Double,
    val archived: Boolean = false,
)

/**
 * 一条训练记录。`date` 用 ISO 字符串而不是 epochDay：
 * 备份文件要能被用户用文本编辑器看懂并手改，数字天数没有可读性。
 */
@Serializable
data class RecordDto(
    val date: String,
    val exercise: String,
    val met: Double,
    val durationMin: Int,
    val calories: Double? = null,
    val bodyParts: List<String> = emptyList(),
    val note: String = "",
)

@Serializable
data class DailyNoteDto(val date: String, val text: String)

@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val profile: ProfileDto? = null,
    val exerciseTypes: List<ExerciseTypeDto> = emptyList(),
    val records: List<RecordDto> = emptyList(),
    val dailyNotes: List<DailyNoteDto> = emptyList(),
)

// ============================================================ JSON

/** JSON 完整备份的编解码 */
object BackupCodec {

    const val CURRENT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true   // 前向兼容：新版本加的字段不认识的忽略掉
        prettyPrint = true
    }

    fun toJson(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** 失败一律带可读中文原因（规格 6.5 / 6.6），UI 直接展示给用户 */
    fun fromJson(text: String): Result<BackupFile> = runCatching {
        val parsed = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (t: Throwable) {
            throw IllegalArgumentException("JSON 解析失败：这不是有效的备份文件", t)
        }
        require(parsed.schemaVersion <= CURRENT_VERSION) {
            "备份文件版本为 ${parsed.schemaVersion}，高于本应用支持的 $CURRENT_VERSION，请升级 App 后再导入"
        }
        require(parsed.records.isNotEmpty() || parsed.dailyNotes.isNotEmpty()) {
            "备份文件中没有可导入的训练记录"
        }
        parsed
    }
}

// ============================================================ CSV

/**
 * CSV 记录表。两条硬要求：
 *  - 写：含逗号/引号/换行的字段必须加引号，内部引号翻倍（RFC4180）；
 *  - 读：不能用 split(",")，否则带逗号的备注会被切成多列。
 * BOM 让 Excel 打开时中文不乱码。
 */
object CsvCodec {

    const val HEADER = "date,exercise,met,durationMin,calories,bodyParts,note"
    const val BOM = "\uFEFF"
    private const val COLUMN_COUNT = 7

    fun writeLine(record: RecordDto): String = listOf(
        record.date,
        escape(record.exercise),
        trimNumber(record.met),
        record.durationMin.toString(),
        record.calories?.let { trimNumber(it) }.orEmpty(),
        escape(record.bodyParts.joinToString(";")),
        escape(record.note),
    ).joinToString(",")

    fun writeAll(records: List<RecordDto>): String =
        BOM + HEADER + "\n" + records.joinToString("\n") { writeLine(it) } + "\n"

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    fun parse(text: String): Result<List<RecordDto>> = runCatching {
        val body = text.removePrefix(BOM).replace("\r\n", "\n").trimEnd('\n')
        val lines = body.split("\n")
        require(lines.isNotEmpty() && lines.first().trim() == HEADER) {
            "CSV 表头不符，应为首行：$HEADER"
        }
        lines.drop(1).filter { it.isNotBlank() }.mapIndexed { index, line ->
            val lineNumber = index + 2   // 表头是第 1 行
            val cells = parseCells(line, lineNumber)
            require(cells.size == COLUMN_COUNT) {
                "第 $lineNumber 行有 ${cells.size} 列，应为 $COLUMN_COUNT 列"
            }
            toRecord(cells, lineNumber)
        }
    }

    /** 逐字符扫描，正确处理被引号包裹且内含逗号/转义引号的字段 */
    private fun parseCells(line: String, lineNumber: Int): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }

                !inQuotes && ch == '"' -> inQuotes = true
                !inQuotes && ch == ',' -> { cells.add(current.toString()); current.setLength(0) }
                else -> current.append(ch)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }

    private fun toRecord(cells: List<String>, lineNumber: Int): RecordDto {
        // 故意不用解构：Kotlin 只为 List 提供 component1()..component5()，7 列会编译不过
        val date = cells[0]
        val exercise = cells[1]
        val met = cells[2]
        val duration = cells[3]
        val calories = cells[4]
        val parts = cells[5]
        val note = cells[6]
        try {
            LocalDate.parse(date.trim())
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("第 $lineNumber 行日期无法解析：'$date'（应为 2026-10-12 这种格式）", e)
        }
        val metValue = met.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("第 $lineNumber 行 MET 不是数字：'$met'")
        val durationValue = duration.trim().toIntOrNull()
            ?: throw IllegalArgumentException("第 $lineNumber 行时长不是整数：'$duration'")
        return RecordDto(
            date = date.trim(),
            exercise = exercise.trim(),
            met = metValue,
            durationMin = durationValue,
            calories = calories.trim().toDoubleOrNull(),
            bodyParts = parts.split(';').map { it.trim() }.filter { it.isNotEmpty() },
            note = note,
        )
    }
}

// ============================================================ 合并策略

enum class ConflictPolicy { SKIP, OVERWRITE }

data class MergePlan(
    val toInsert: List<RecordDto>,
    val toUpdate: List<RecordDto>,
    val skipped: Int,
    /** 无法解析的行；不阻断整批，但要在预览页列出来（规格 6.5） */
    val errors: List<String>,
    /** 需要自动创建的运动类型（规格 4.7） */
    val newTypes: List<ExerciseTypeDto>,
) {
    val total: Int get() = toInsert.size + toUpdate.size
}

/**
 * 导入合并（规格 4.7）。
 *
 * 幂等键 = date|exercise|durationMin|note —— 刻意不含 calories：
 * 它由 MET×体重×时长 派生，换到新手机后体重不同会算出不同值，
 * 若把它纳入键，同一条记录会被重复插入。
 */
object MergeResolver {

    fun keyOf(record: RecordDto): String =
        listOf(record.date.trim(), record.exercise.trim(), record.durationMin.toString(), record.note)
            .joinToString("|")

    fun resolve(
        incoming: List<RecordDto>,
        existing: List<RecordDto>,
        policy: ConflictPolicy,
    ): MergePlan {
        val existingKeys = existing.map { keyOf(it) }.toSet()
        val insert = mutableListOf<RecordDto>()
        val update = mutableListOf<RecordDto>()
        val errors = mutableListOf<String>()
        var skipped = 0

        incoming.forEachIndexed { index, record ->
            val problem = validate(record)
            if (problem != null) {
                errors.add("第 ${index + 1} 条：$problem")
                return@forEachIndexed
            }
            if (keyOf(record) in existingKeys) {
                if (policy == ConflictPolicy.OVERWRITE) update += record else skipped++
            } else {
                insert += record
            }
        }

        // 内置库里查不到的类型名，导入时需要顺带创建
        val newTypes = insert
            .filter { BuiltinExercises.defaultMet(it.exercise.trim()) == null }
            .map { ExerciseTypeDto(name = it.exercise.trim(), met = it.met) }
            .distinctBy { it.name }

        return MergePlan(
            toInsert = insert,
            toUpdate = update,
            skipped = skipped,
            errors = errors,
            newTypes = newTypes,
        )
    }

    private fun validate(record: RecordDto): String? = when {
        runCatching { LocalDate.parse(record.date.trim()) }.isFailure -> "日期无法解析：'${record.date}'"
        record.exercise.isBlank() -> "运动类型为空"
        record.durationMin < 1 -> "时长必须 ≥ 1 分钟：${record.durationMin}"
        record.durationMin > 1440 -> "时长超过一天：${record.durationMin} 分钟"
        record.met < 0.5 || record.met > 20.0 -> "MET 超出合理范围：${record.met}"
        else -> null
    }
}
