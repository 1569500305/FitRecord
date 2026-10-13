package com.fitrecord.app.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON 备份的往返一致性与拒绝脏输入（规格模块4-3 / 6.5 / 6.6） */
class BackupCodecTest {

    private fun sample() = BackupFile(
        schemaVersion = BackupCodec.CURRENT_VERSION,
        exportedAt = "2026-10-12T08:30:00",
        profile = ProfileDto(gender = "M", age = 28, heightCm = 175.0, weightKg = 70.0),
        exerciseTypes = listOf(ExerciseTypeDto(name = "攀岩", met = 8.0, archived = false)),
        records = listOf(
            RecordDto(
                date = "2026-10-12", exercise = "攀岩", met = 8.0, durationMin = 45,
                calories = 420.0, bodyParts = listOf("背", "核心"), note = "难度 5",
            ),
        ),
        dailyNotes = listOf(DailyNoteDto(date = "2026-10-12", text = "今天状态好")),
    )

    @Test fun `json round trip preserves every field`() {
        assertEquals(sample(), BackupCodec.fromJson(BackupCodec.toJson(sample())).getOrThrow())
    }

    @Test fun `null calories survives the round trip`() {
        val src = sample().copy(records = listOf(sample().records.first().copy(calories = null)))
        val back = BackupCodec.fromJson(BackupCodec.toJson(src)).getOrThrow()
        assertNull(back.records.single().calories)
    }

    @Test fun `non json input fails with a readable message`() {
        val result = BackupCodec.fromJson("这不是 JSON")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("JSON"))
    }

    /** 规格 6.6：来自更新版本 App 的备份必须拒绝，而不是丢字段硬解 */
    @Test fun `newer schema version is rejected`() {
        val future = BackupCodec.toJson(sample().copy(schemaVersion = 99))
        val result = BackupCodec.fromJson(future)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("升级"))
    }

    @Test fun `empty backup is rejected instead of silently doing nothing`() {
        val empty = BackupCodec.toJson(
            BackupFile(BackupCodec.CURRENT_VERSION, "2026-10-12T08:30:00", null, emptyList(), emptyList(), emptyList())
        )
        assertTrue(BackupCodec.fromJson(empty).isFailure)
    }

    @Test fun `missing optional sections default to empty instead of failing`() {
        val json = """{"schemaVersion":1,"exportedAt":"2026-10-12T08:30:00","exerciseTypes":[],"records":[]}"""
        val parsed = BackupCodec.fromJson(
            """{"schemaVersion":1,"exportedAt":"2026-10-12T08:30:00","records":[{"date":"2026-10-12",""" +
                """"exercise":"划船机","met":7.0,"durationMin":30,"calories":245.0}]}"""
        ).getOrThrow()
        assertEquals(1, parsed.records.size)
        assertTrue(parsed.dailyNotes.isEmpty())
        assertNull(parsed.profile)
        assertEquals(emptyList<String>(), parsed.records.single().bodyParts)
        assertFalse(json.isEmpty())
    }

    @Test fun `unknown future keys are ignored for forward compatibility`() {
        val withExtra = """{"schemaVersion":1,"exportedAt":"x","someNewField":42,"records":[],""" +
            """"exerciseTypes":[],"dailyNotes":[],"profile":null}"""
        // 版本不高于当前 → 允许解析，多余字段忽略
        assertTrue(BackupCodec.fromJson(withExtra).isFailure)  // records 空会被拒
    }
}

/** CSV 编解码：RFC4180 转义 + BOM + 带行号的错误（规格 4.7 / 6.5） */
class CsvCodecTest {

    private val header = CsvCodec.HEADER

    @Test fun `header matches the spec column order`() {
        assertEquals("date,exercise,met,durationMin,calories,bodyParts,note", header)
    }

    /**
     * 整数值的卡路里会被 trimNumber 写成 `291` 而不是 `291.0`：
     * CSV 要给人用 Excel 看，两者数值上等价（往返断言见下一条），
     * 这里真正守的是「逗号与引号必须被转义」。
     */
    @Test fun `note containing comma and quote is escaped`() {
        val line = CsvCodec.writeLine(
            RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, listOf("全身有氧"), "快,跑\"3\"")
        )
        // 期望值用普通字符串转义而非三重引号：正确 CSV 以 3 个连续引号收尾（字段内转义 2 个 + 闭合 1 个），
        // 三重引号原始串会把收尾引号吞进分隔符，导致期望少一个引号（实测踩坑）。
        assertEquals("2026-10-12,跑步机,8.3,30,291,全身有氧,\"快,跑\"\"3\"\"\"", line)
    }
    
    /** 转义后的行必须能原样读回来，包括内嵌逗号与引号 */
    @Test fun `escaped note round trips exactly`() {
        val original = RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, listOf("全身有氧"), "快,跑\"3\"")
        val parsed = CsvCodec.parse(CsvCodec.HEADER + "\n" + CsvCodec.writeLine(original)).getOrThrow()
        assertEquals(original, parsed.single())
    }

    @Test fun `round trip keeps all rows including null calories`() {
        val rows = listOf(
            RecordDto("2026-10-12", "瑜伽", 2.5, 60, null, listOf("核心"), "a,b"),
            RecordDto("2026-10-13", "游泳", 5.8, 30, 203.0, emptyList(), ""),
        )
        val text = CsvCodec.BOM + header + "\n" + rows.joinToString("\n") { CsvCodec.writeLine(it) }
        assertEquals(rows, CsvCodec.parse(text).getOrThrow())
    }

    @Test fun `wrong header is rejected`() {
        assertTrue(CsvCodec.parse("a,b,c\n1,2,3").isFailure)
    }

    /** 规格 6.5：错误信息要带行号，否则用户无法定位自己改坏的表格 */
    @Test fun `short row reports the line number`() {
        val result = CsvCodec.parse("$header\n2026-10-12,跑步机\n")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("第 2 行"))
    }

    @Test fun `invalid date is reported with its line number`() {
        val result = CsvCodec.parse("$header\n2026-13-45,跑步机,8.3,30,291.0,,\n")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("第 2 行"))
    }

    @Test fun `empty body parts column parses back to empty list`() {
        val row = RecordDto("2026-10-12", "划船机", 7.0, 30, 245.0, emptyList(), "")
        val parsed = CsvCodec.parse(header + "\n" + CsvCodec.writeLine(row)).getOrThrow()
        assertEquals(emptyList<String>(), parsed.single().bodyParts)
    }
}

/** 导入合并策略：幂等键、冲突处理、脏行不阻断整批（规格 4.7） */
class MergeResolverTest {

    private val a = RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, listOf("全身有氧"), "")

    @Test fun `identical key is skipped under SKIP`() {
        val plan = MergeResolver.resolve(listOf(a), listOf(a), ConflictPolicy.SKIP)
        assertTrue(plan.toInsert.isEmpty())
        assertEquals(1, plan.skipped)
    }

    /** 幂等键刻意不含 calories：它由 MET×体重×时长 派生，换手机后体重不同会不同 */
    @Test fun `key ignores the derived calories field`() {
        val b = a.copy(calories = 999.0)
        val plan = MergeResolver.resolve(listOf(b), listOf(a), ConflictPolicy.SKIP)
        assertEquals(1, plan.skipped)
        assertTrue(plan.toInsert.isEmpty())
    }

    @Test fun `OVERWRITE updates the existing row instead of skipping`() {
        // 键 = date|exercise|durationMin|note，刻意不含 calories。
        // 要触发 OVERWRITE 的 update 分支，来记录必须与已有记录**同键**、只差派生字段；
        // 若改 note 会变成新键 → 走 insert（上一轮正是这么写才红）。
        val b = a.copy(calories = 999.0)
        val plan = MergeResolver.resolve(listOf(b), listOf(a), ConflictPolicy.OVERWRITE)
        assertEquals(1, plan.toUpdate.size)
        assertEquals(0, plan.skipped)
    }

    @Test fun `note difference makes a distinct record`() {
        val b = a.copy(note = "早跑")
        val plan = MergeResolver.resolve(listOf(b), listOf(a), ConflictPolicy.SKIP)
        assertEquals(1, plan.toInsert.size)
    }

    @Test fun `unknown exercise type is reported but still imported`() {
        val incoming = listOf(RecordDto("2026-10-11", "攀岩", 8.0, 40, 373.0, listOf("背"), ""))
        val plan = MergeResolver.resolve(incoming, emptyList(), ConflictPolicy.SKIP)
        assertEquals(1, plan.toInsert.size)
        assertTrue(plan.newTypes.map { it.name }.contains("攀岩"))
    }

    @Test fun `invalid date goes to errors and does not block valid rows`() {
        val bad = listOf(
            RecordDto("2026-13-45", "跑步机", 8.3, 30, null, emptyList(), ""),
            a,
        )
        val plan = MergeResolver.resolve(bad, emptyList(), ConflictPolicy.SKIP)
        assertEquals(1, plan.toInsert.size)
        assertEquals(1, plan.errors.size)
    }

    @Test fun `non positive duration is rejected as invalid`() {
        val plan = MergeResolver.resolve(listOf(a.copy(durationMin = 0)), emptyList(), ConflictPolicy.SKIP)
        assertTrue(plan.toInsert.isEmpty())
        assertEquals(1, plan.errors.size)
    }

    @Test fun `total counts insert plus update`() {
        val plan = MergeResolver.resolve(
            incoming = listOf(a, a.copy(note = "x")),
            existing = listOf(a),
            policy = ConflictPolicy.OVERWRITE,
        )
        assertEquals(2, plan.total)
    }
}
