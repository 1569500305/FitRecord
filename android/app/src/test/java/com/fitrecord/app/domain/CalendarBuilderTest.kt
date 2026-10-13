package com.fitrecord.app.domain

import com.fitrecord.app.data.db.RecordWithType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 日历格状态的判定规则（规格 4.1）—— 首页显示对错的唯一真相来源 */
class CalendarBuilderTest {

    private val today = LocalDate.of(2026, 10, 12)
    private val oct = YearMonth.of(2026, 10)

    private fun rec(day: LocalDate, typeId: Long, color: Long) = RecordWithType(
        id = typeId * 100_000 + day.toEpochDay(),
        date = day.toEpochDay().toInt(),
        exerciseTypeId = typeId,
        durationMin = 30,
        calories = 100.0,
        note = "",
        typeName = "t$typeId",
        typeColor = color,
        met = 5.0,
    )

    private fun grid(
        byDay: Map<Int, List<RecordWithType>> = emptyMap(),
        selected: LocalDate? = null,
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
    ) = CalendarBuilder.monthGrid(oct, firstDay, byDay, today, selected)

    @Test fun `month grid drops the trailing week with no day in this month`() {
        val cells = grid()
        // 2026-10：6 行去掉纯下月的最后一行（11/2–11/8）= 5 行 35 格
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 9, 28), cells.first().date)
        assertEquals(LocalDate.of(2026, 11, 1), cells.last().date)
        assertTrue(cells.none { it.date == LocalDate.of(2026, 11, 2) })
        assertEquals(0, cells.size % 7)   // 仍是整行，列对齐不破
    }

    @Test fun `exactly the days of the month are flagged inMonth`() {
        val cells = grid()
        assertEquals(31, cells.count { it.inMonth })
        assertEquals(4, cells.count { !it.inMonth })   // 前置 9/28-30 + 尾行仅 11/1
        assertTrue(cells.first { it.date == LocalDate.of(2026, 10, 1) }.inMonth)
        assertFalse(cells.first { it.date == LocalDate.of(2026, 9, 30) }.inMonth)
        assertFalse(cells.first { it.date == LocalDate.of(2026, 11, 1) }.inMonth)
    }

    @Test fun `day with records reports segment count and distinct colors`() {
        val d = LocalDate.of(2026, 10, 5)
        val byDay = mapOf(
            d.toEpochDay().toInt() to listOf(
                rec(d, 1, 0xFF111111L), rec(d, 1, 0xFF111111L), rec(d, 2, 0xFF222222L),
            )
        )
        val cell = grid(byDay).first { it.date == d }
        assertEquals(3, cell.segmentCount)              // 段数按记录条数
        assertEquals(2, cell.typeColors.size)           // 颜色按去重类型
        assertFalse(cell.showPlusN)
        assertTrue(cell.trained)
    }

    @Test fun `more than three distinct types caps dots and shows plusN`() {
        val d = LocalDate.of(2026, 10, 6)
        val byDay = mapOf(
            d.toEpochDay().toInt() to listOf(
                rec(d, 1, 0xFF111111L), rec(d, 2, 0xFF222222L), rec(d, 3, 0xFF333333L), rec(d, 4, 0xFF444444L),
            )
        )
        val cell = grid(byDay).first { it.date == d }
        assertEquals(4, cell.segmentCount)
        assertEquals(3, cell.typeColors.size)
        assertTrue(cell.showPlusN)
    }

    @Test fun `day without records is not trained and has no colors`() {
        val cell = grid().first { it.date == LocalDate.of(2026, 10, 20) }
        assertEquals(0, cell.segmentCount)
        assertTrue(cell.typeColors.isEmpty())
        assertFalse(cell.trained)
        assertFalse(cell.showPlusN)
    }

    @Test fun `primary type is the one with the largest total duration`() {
        val d = LocalDate.of(2026, 10, 7)
        val long = rec(d, 1, 0xFF111111L).copy(durationMin = 50)   // 类型1 共 50 分钟
        val short1 = rec(d, 2, 0xFF222222L).copy(durationMin = 20) // 类型2 共 40
        val short2 = rec(d, 2, 0xFF222222L).copy(durationMin = 20)
        val byDay = mapOf(d.toEpochDay().toInt() to listOf(short1, long, short2))
        val cell = grid(byDay).first { it.date == d }
        // 类型2 段数更多（2），但类型1 总时长更大 → 主类型应为 t1
        assertEquals("t1", cell.primaryTypeName)
    }

    @Test fun `empty day has null primary type`() {
        assertNull(grid().first { it.date == LocalDate.of(2026, 10, 20) }.primaryTypeName)
    }

    @Test fun `today and selected are independent flags`() {
        val byDay = mapOf(today.toEpochDay().toInt() to listOf(rec(today, 1, 0xFFAA0000L)))
        val selected = LocalDate.of(2026, 10, 20)
        val cells = grid(byDay, selected)
        val todayCell = cells.first { it.date == today }
        assertTrue(todayCell.isToday)
        assertFalse(todayCell.isSelected)
        assertTrue(todayCell.trained)
        val selectedCell = cells.first { it.date == selected }
        assertFalse(selectedCell.isToday)
        assertTrue(selectedCell.isSelected)
        assertEquals(0, selectedCell.segmentCount)
    }

    @Test fun `future day may hold records and is not disabled`() {
        val future = LocalDate.of(2026, 10, 30)
        val byDay = mapOf(future.toEpochDay().toInt() to listOf(rec(future, 1, 0xFF111111L)))
        val cell = grid(byDay).first { it.date == future }
        // 规格 4.1：未来日期允许有记录（计划/补录），不得被当作禁用格
        assertTrue(cell.trained)
        assertEquals(1, cell.segmentCount)
        assertTrue(cell.inMonth)
    }

    @Test fun `week row returns the 7 days of the selected week`() {
        val row = CalendarBuilder.weekRow(
            date = LocalDate.of(2026, 10, 14),
            firstDay = DayOfWeek.MONDAY,
            recordsByDay = emptyMap(),
            today = today,
            selected = LocalDate.of(2026, 10, 14),
        )
        assertEquals(7, row.size)
        assertEquals(LocalDate.of(2026, 10, 12), row.first().date)
        assertEquals(LocalDate.of(2026, 10, 18), row.last().date)
        assertTrue(row.all { it.inMonth })   // 周行不跨月裁剪，整周都算本月可见
    }

    @Test fun `week row respects sunday first day`() {
        val row = CalendarBuilder.weekRow(
            date = LocalDate.of(2026, 10, 14),
            firstDay = DayOfWeek.SUNDAY,
            recordsByDay = emptyMap(),
            today = today,
            selected = null,
        )
        assertEquals(LocalDate.of(2026, 10, 11), row.first().date)
        assertEquals(LocalDate.of(2026, 10, 17), row.last().date)
    }

    @Test fun `epoch day accessor matches the date`() {
        val cell = grid().first { it.date == today }
        assertEquals(today.toEpochDay().toInt(), cell.epochDay)
    }
}
