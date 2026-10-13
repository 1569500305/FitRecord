package com.fitrecord.app.domain

import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.RecordWithType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历格状态（规格 4.1）。纯函数，不依赖 Android，首页显示的一切由它决定。
 */
data class DayCell(
    val date: LocalDate,
    /** 月视图中属于本月的格子才显示深色文字；溢出格用于填满 6×7 网格 */
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** 当日运动段数 = 记录条数（规格模块1-2：次数由条数派生，无字段） */
    val segmentCount: Int,
    /** 去重后的类型配色，最多 3 个 */
    val typeColors: List<Long>,
    /** 超过 3 种类型时显示 "+n" */
    val showPlusN: Boolean,
    /**
     * 当天"主类型"名 = 各类型中总时长最大的那个（并列取先出现的），用于渲染类型图标。
     * 默认 null 保证旧调用与测试的构造器不破（无记录 / 仅旧数据时为 null）。
     */
    val primaryTypeName: String? = null,
) {
    val epochDay: Int get() = date.toEpochDay().toInt()
    val trained: Boolean get() = segmentCount > 0
}

object CalendarBuilder {

    /** 6 行 × 7 列，保证任何月份都填满且不因周数变化而跳动 */
    const val MONTH_CELLS = 42
    const val WEEK_CELLS = 7

    /** 色点上限，超出用 showPlusN 表达（规格 2.3 图标位） */
    private const val MAX_DOTS = 3

    fun monthGrid(
        month: YearMonth,
        firstDay: DayOfWeek,
        recordsByDay: Map<Int, List<RecordWithType>>,
        today: LocalDate,
        selected: LocalDate?,
    ): List<DayCell> = buildGrid(
        start = WeekUtil.monthGridStart(month, firstDay),
        month = month,
        recordsByDay = recordsByDay,
        today = today,
        selected = selected,
        count = MONTH_CELLS,
    )

    fun weekRow(
        date: LocalDate,
        firstDay: DayOfWeek,
        recordsByDay: Map<Int, List<RecordWithType>>,
        today: LocalDate,
        selected: LocalDate?,
    ): List<DayCell> = buildGrid(
        start = WeekUtil.weekStart(date, firstDay),
        month = null,
        recordsByDay = recordsByDay,
        today = today,
        selected = selected,
        count = WEEK_CELLS,
    )

    private fun buildGrid(
        start: LocalDate,
        month: YearMonth?,
        recordsByDay: Map<Int, List<RecordWithType>>,
        today: LocalDate,
        selected: LocalDate?,
        count: Int,
    ): List<DayCell> = (0 until count).map { offset ->
        val day = start.plusDays(offset.toLong())
        buildCell(day, month, recordsByDay, today, selected)
    }

    private fun buildCell(
        day: LocalDate,
        month: YearMonth?,
        recordsByDay: Map<Int, List<RecordWithType>>,
        today: LocalDate,
        selected: LocalDate?,
    ): DayCell {
        val records = recordsByDay[day.toEpochDay().toInt()].orEmpty()
        val distinctColors = records.map { it.typeColor }.distinct()
        // 主类型：按类型分组后总时长最大者；maxByOrNull 遇并列保留第一个，与记录顺序一致
        val primaryType = records.groupBy { it.typeName }
            .maxByOrNull { (_, rs) -> rs.sumOf { it.durationMin } }
            ?.key
        return DayCell(
            date = day,
            inMonth = month == null || YearMonth.from(day) == month,
            isToday = day == today,
            isSelected = selected != null && day == selected,
            segmentCount = records.size,
            typeColors = distinctColors.take(MAX_DOTS),
            showPlusN = distinctColors.size > MAX_DOTS,
            primaryTypeName = primaryType,
        )
    }
}
