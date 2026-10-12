package com.fitrecord.app.data.repo

import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.PerDayRow
import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.StatsDao
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.domain.FoodEquivalent
import com.fitrecord.app.domain.PeriodFactory
import com.fitrecord.app.domain.PeriodType
import com.fitrecord.app.domain.SliceStats
import com.fitrecord.app.domain.StatsPeriod
import com.fitrecord.app.domain.StatsSummary
import com.fitrecord.app.domain.StatsUiModel
import com.fitrecord.app.domain.TrendPoint
import com.fitrecord.app.ui.theme.ColorOthers
import com.fitrecord.app.ui.theme.toRgbLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 统计仓库（规格 4.6）。
 *
 * 两条硬约定：
 *  1. 聚合一律在 SQL 层完成（GROUP BY / SUM / COUNT DISTINCT），不在内存遍历全表；
 *  2. 周/月口径只由 StatsPeriod 决定，UI 不参与算数 —— 否则切维度时数字会漂。
 */
class StatsRepository(
    private val statsDao: StatsDao,
    private val foodDao: FoodDao,
) {

    fun periodOf(type: PeriodType, anchor: LocalDate, firstDay: DayOfWeek): StatsPeriod =
        PeriodFactory.of(type, anchor, firstDay)

    /**
     * 观察一个周期的全部统计数据。
     * 触发源是"区间内的按天聚合"与"食物库"两条 Flow；
     * combine 的 transform 是 suspend 的，所以里面可以直接调其余聚合查询。
     */
    fun observeStats(period: StatsPeriod): Flow<StatsUiModel> = combine(
        statsDao.observeRangeRaw(period.range.first, period.range.last),
        foodDao.observeAll(),
    ) { perDayRows, foods -> buildUi(period, perDayRows, foods) }

    private suspend fun buildUi(
        period: StatsPeriod,
        perDay: List<PerDayRow>,
        foods: List<com.fitrecord.app.data.db.entity.FoodItemEntity>,
    ): StatsUiModel {
        val from = period.range.first
        val to = period.range.last

        /*
         * 总量直接由已聚合的 perDay 派生（评审 I-2①）：
         * perDay 本身就是 SQL `GROUP BY date` 的结果，再发一次 SUM 既是浪费，
         * 更会因不在同一快照里而出现"柱状图与总时长对不上"的中间态。
         * 现在 heat / 柱状 / 段数 / 总时长 / 打卡天数天然同一快照。
         */
        val minutes = perDay.sumOf { it.minutes }
        val segments = perDay.sumOf { it.segments }
        val calories = statsDao.calories(from, to)?.let { Math.round(it).toInt() }

        val summary = StatsSummary(
            segments = segments,
            minutes = minutes,
            trainedDays = perDay.size,
            calories = calories,
            uncalculated = statsDao.uncalculated(from, to),
            // 分母是周期天数（含没练的天），不是打卡天数 —— 规格 4.6
            avgMinPerDay = if (period.dayCount == 0) 0 else Math.round(minutes.toDouble() / period.dayCount).toInt(),
            avgMinPerSegment = if (segments == 0) 0 else Math.round(minutes.toDouble() / segments).toInt(),
        )

        // 热力/柱状：即使某天为 0 也要占一格，否则格子会错位到别的日期上
        val segmentsByDay = perDay.associate { it.date to it.segments }
        val heat = (from..to).map { segmentsByDay[it] ?: 0 }

        val typeRows = statsDao.perType(from, to)
        val totalMinutes = typeRows.sumOf { it.minutes }
        // 有记录但总时长为 0（导入路径可送）时不得发一张每片 0% 的空饼（评审 M-2）
        val byType = if (totalMinutes <= 0) {
            emptyList()
        } else {
            val top = typeRows.take(TOP_TYPES).map {
                SliceStats(it.name, it.color, it.minutes, it.segments, it.minutes * 100f / totalMinutes)
            }
            val tail = typeRows.drop(TOP_TYPES)
            if (tail.isEmpty()) top else top + SliceStats(
                // 第 7 名以后并入"其他"，环形图扇区数才有上限（规格 2.5）
                name = OTHERS,
                color = ColorOthers.toRgbLong(),
                minutes = tail.sumOf { it.minutes },
                segments = tail.sumOf { it.segments },
                percent = tail.sumOf { it.minutes } * 100f / totalMinutes,
            )
        }

        // 雷达图 8 轴恒定存在：没练过的部位显示 0，而不是从图上消失
        val partRows = statsDao.perPart(from, to)
        val byPart = BodyParts.axisIds.associateWith { id ->
            partRows.firstOrNull { it.partId == id }?.segments ?: 0
        }

        return StatsUiModel(
            period = period,
            summary = summary,
            perDay = perDay,
            heat = heat,
            trend = TrendBuckets.of(period, statsDao),
            byType = byType,
            byPart = byPart,
            foodText = FoodEquivalent.text(calories, foods),
        )
    }

    private companion object {
        const val TOP_TYPES = 6
        const val OTHERS = "其他"
    }
}

/**
 * 时长趋势分桶（规格 4.6）：周维度看最近 8 个周，月维度看最近 6 个自然月。
 * 分桶边界一律用 period.firstDay，保证与周视图首日一致。
 */
object TrendBuckets {

    private const val WEEK_BUCKETS = 8
    private const val MONTH_BUCKETS = 6
    private val weekLabelFmt = DateTimeFormatter.ofPattern("MM-dd")
    private val monthLabelFmt = DateTimeFormatter.ofPattern("yy-MM")

    suspend fun of(period: StatsPeriod, statsDao: StatsDao): List<TrendPoint> = when (period.type) {
        PeriodType.WEEK -> {
            val lastStart = WeekUtil.weekStart(period.anchor, period.firstDay)
            (WEEK_BUCKETS - 1 downTo 0).map { back ->
                val start = lastStart.minusWeeks(back.toLong())
                val range = WeekUtil.epochRange(start, 7)
                TrendPoint(start.format(weekLabelFmt), statsDao.minutes(range.first, range.last), start)
            }
        }

        PeriodType.MONTH -> {
            val lastMonth = YearMonth.from(period.anchor)
            (MONTH_BUCKETS - 1 downTo 0).map { back ->
                val ym = lastMonth.minusMonths(back.toLong())
                val range = WeekUtil.monthRange(ym)
                TrendPoint(ym.format(monthLabelFmt), statsDao.minutes(range.first, range.last), ym.atDay(1))
            }
        }
    }
}
