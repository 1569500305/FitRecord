package com.fitrecord.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 周/月区间工具（规格 4.6）。所有区间都是**闭区间**的 epochDay，
 * 直接喂给 SQL 的 BETWEEN，不在 UI 层做日期字符串比较。
 */
object WeekUtil {

    /** date 所在周的第一天，起始日可配（规格 D9：默认周一） */
    fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.minusDays(((date.dayOfWeek.value - firstDay.value + 7) % 7).toLong())

    /** [start, start + days - 1] */
    fun epochRange(start: LocalDate, days: Long): IntRange =
        IntRange(start.toEpochDay().toInt(), start.plusDays(days - 1).toEpochDay().toInt())

    fun weekRange(date: LocalDate, firstDay: DayOfWeek): IntRange =
        epochRange(weekStart(date, firstDay), 7)

    fun monthRange(ym: YearMonth): IntRange =
        epochRange(ym.atDay(1), ym.lengthOfMonth().toLong())

    /** 月视图 6×7 网格的左上角那天（可能落在上月） */
    fun monthGridStart(ym: YearMonth, firstDay: DayOfWeek): LocalDate =
        weekStart(ym.atDay(1), firstDay)

    /** 有符号月差，用于分页器边界与"近 N 月"趋势 */
    fun monthsBetween(a: YearMonth, b: YearMonth): Int =
        ChronoUnit.MONTHS.between(a.atDay(1), b.atDay(1)).toInt()

    fun toEpochDay(date: LocalDate): Int = date.toEpochDay().toInt()

    fun fromEpochDay(day: Int): LocalDate = LocalDate.ofEpochDay(day.toLong())
}
