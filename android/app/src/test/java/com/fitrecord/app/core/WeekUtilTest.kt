package com.fitrecord.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 周/月区间是统计口径的地基（规格 4.6），跨月跨年的边界必须钉死 */
class WeekUtilTest {

    private val monday = LocalDate.of(2026, 10, 12)   // 2026-10-12 是周一

    @Test fun `monday start keeps monday as the first day`() {
        assertEquals(monday, WeekUtil.weekStart(monday, DayOfWeek.MONDAY))
        assertEquals(monday, WeekUtil.weekStart(LocalDate.of(2026, 10, 18), DayOfWeek.MONDAY))
        assertEquals(monday, WeekUtil.weekStart(LocalDate.of(2026, 10, 13), DayOfWeek.MONDAY))
    }

    @Test fun `sunday start shifts back to the previous sunday`() {
        assertEquals(LocalDate.of(2026, 10, 11), WeekUtil.weekStart(monday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 10, 11), WeekUtil.weekStart(LocalDate.of(2026, 10, 17), DayOfWeek.SUNDAY))
    }

    @Test fun `saturday start works too`() {
        assertEquals(LocalDate.of(2026, 10, 10), WeekUtil.weekStart(monday, DayOfWeek.SATURDAY))
    }

    @Test fun `week range spans exactly 7 epoch days`() {
        val r = WeekUtil.weekRange(monday, DayOfWeek.MONDAY)
        assertEquals(7, r.last - r.first + 1)
        assertEquals(monday.toEpochDay().toInt(), r.first)
        assertEquals(LocalDate.of(2026, 10, 18).toEpochDay().toInt(), r.last)
    }

    @Test fun `month range for october 2026 covers the 1st to the 31st`() {
        val r = WeekUtil.monthRange(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay().toInt(), r.first)
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay().toInt(), r.last)
        assertEquals(31, r.last - r.first + 1)
    }

    @Test fun `february in a leap year has 29 days`() {
        val r = WeekUtil.monthRange(YearMonth.of(2028, 2))
        assertEquals(29, r.last - r.first + 1)
    }

    @Test fun `month grid starts on the first week day before the 1st`() {
        // 2026-10-01 是周四，周一起始时网格应从 09-28 开始
        assertEquals(LocalDate.of(2026, 9, 28), WeekUtil.monthGridStart(YearMonth.of(2026, 10), DayOfWeek.MONDAY))
        // 周日起始时，10-01（周四）所在的周从 09-27（周日）开始
        assertEquals(LocalDate.of(2026, 9, 27), WeekUtil.monthGridStart(YearMonth.of(2026, 10), DayOfWeek.SUNDAY))
    }

    @Test fun `month grid start equals the 1st when the month begins on the first day of week`() {
        // 2026-11-01 是周日
        assertEquals(LocalDate.of(2026, 11, 1), WeekUtil.monthGridStart(YearMonth.of(2026, 11), DayOfWeek.SUNDAY))
    }

    @Test fun `months between handles year boundary in both directions`() {
        assertEquals(0, WeekUtil.monthsBetween(YearMonth.of(2026, 10), YearMonth.of(2026, 10)))
        assertEquals(2, WeekUtil.monthsBetween(YearMonth.of(2026, 11), YearMonth.of(2027, 1)))
        assertEquals(-2, WeekUtil.monthsBetween(YearMonth.of(2027, 1), YearMonth.of(2026, 11)))
    }

    @Test fun `epoch range is inclusive on both ends`() {
        val start = LocalDate.of(2026, 10, 12)
        val r = WeekUtil.epochRange(start, 1)
        assertEquals(start.toEpochDay().toInt(), r.first)
        assertEquals(start.toEpochDay().toInt(), r.last)
    }
}
