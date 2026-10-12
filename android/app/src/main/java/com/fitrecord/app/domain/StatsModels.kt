package com.fitrecord.app.domain

import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.PerDayRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class PeriodType { WEEK, MONTH }

/**
 * 一个统计周期。`firstDay` 随对象携带而不是每次传参：
 * ‹ › 翻页、周视图首日、趋势分桶都必须用同一个周起始日，
 * 漏传一次就会出现"周视图从周一开始、趋势图按周日分桶"的错位。
 */
data class StatsPeriod(
    val type: PeriodType,
    val anchor: LocalDate,
    val range: IntRange,
    val label: String,
    val firstDay: DayOfWeek,
) {
    /** ‹ › 翻页：WEEK 移一周，MONTH 移一月 */
    fun shifted(delta: Int): StatsPeriod =
        PeriodFactory.of(type, shiftedDate(delta), firstDay)

    private fun shiftedDate(delta: Int): LocalDate = when (type) {
        PeriodType.WEEK -> anchor.plusWeeks(delta.toLong())
        PeriodType.MONTH -> YearMonth.from(anchor).plusMonths(delta.toLong()).atDay(1)
    }

    /** 周期天数（不是打卡天数）—— 平均时长的分母，规格 4.6 */
    val dayCount: Int get() = range.last - range.first + 1

    /** 用于禁用"翻到未来"（规格 P2：可回看，不可前进） */
    val isCurrentOrFuture: Boolean get() = range.last >= LocalDate.now().toEpochDay().toInt()
}

/** 环形图/饼图的一片 */
data class SliceStats(
    val name: String,
    val color: Long,
    val minutes: Int,
    val segments: Int,
    val percent: Float,
)

/** 趋势折线的一个点 */
data class TrendPoint(val label: String, val minutes: Int, val bucketStart: LocalDate)

data class StatsSummary(
    val segments: Int,
    val minutes: Int,
    val trainedDays: Int,
    /** null = 该周期内没有任何一条算得出卡路里的记录（规格 6.1） */
    val calories: Int?,
    /** 未计算卡路里的记录条数，统计卡副标题要注明 */
    val uncalculated: Int,
    /** 分母是周期天数（含 0 的天），不是打卡天数 —— 规格 4.6 */
    val avgMinPerDay: Int,
    val avgMinPerSegment: Int,
)

/** 统计页一次渲染需要的全部数据；P2 的五张图各自取一个字段 */
data class StatsUiModel(
    val period: StatsPeriod,
    val summary: StatsSummary,
    val perDay: List<PerDayRow>,
    /** 热力/柱状强度：周期内每天的运动段数，长度恒等于 period.dayCount */
    val heat: List<Int>,
    /** 周维度 8 桶 / 月维度 6 桶 */
    val trend: List<TrendPoint>,
    /** 时长加权 Top 6 + "其他" */
    val byType: List<SliceStats>,
    /** 雷达图 8 轴，无数据的轴补 0（键为 body_part.id） */
    val byPart: Map<Long, Int>,
    /** 食物等价一行文案，无卡路里时为空串 */
    val foodText: String,
)

/** 周期的唯一构造口：区间、标签、周起始日三者必须同时算出，避免各处手拼 */
object PeriodFactory {
    private val weekStartFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val weekEndFmt = DateTimeFormatter.ofPattern("MM-dd")

    fun of(type: PeriodType, anchor: LocalDate, firstDay: DayOfWeek): StatsPeriod = when (type) {
        PeriodType.WEEK -> {
            val start = WeekUtil.weekStart(anchor, firstDay)
            StatsPeriod(
                type = type,
                anchor = anchor,
                range = WeekUtil.epochRange(start, 7),
                label = "${start.format(weekStartFmt)} ~ ${start.plusDays(6).format(weekEndFmt)}",
                firstDay = firstDay,
            )
        }

        PeriodType.MONTH -> {
            val ym = YearMonth.from(anchor)
            StatsPeriod(
                type = type,
                anchor = anchor,
                range = WeekUtil.monthRange(ym),
                label = "${ym.year}年${ym.monthValue}月",
                firstDay = firstDay,
            )
        }
    }
}
