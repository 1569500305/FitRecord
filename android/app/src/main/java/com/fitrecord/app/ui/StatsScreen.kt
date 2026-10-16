package com.fitrecord.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.ui.charts.BarChart
import com.fitrecord.app.ui.charts.CalendarHeatmap
import com.fitrecord.app.ui.charts.ChartPoint
import com.fitrecord.app.ui.charts.DonutChart
import com.fitrecord.app.ui.charts.LineChart
import com.fitrecord.app.ui.charts.RadarChart
import com.fitrecord.app.ui.components.ChartCard
import com.fitrecord.app.ui.components.EmptyState
import com.fitrecord.app.ui.components.LegendRow
import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.domain.StatsUiModel
import com.fitrecord.app.ui.components.ViewModeSwitch
import com.fitrecord.app.ui.theme.ColorOthers
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.DonutContrast
import com.fitrecord.app.ui.theme.HeatLevels
import com.fitrecord.app.ui.theme.toRgbLong
import java.time.LocalDate

/**
 * P2 统计页（规格 3-P2）。卡片顺序固定：概览 3 张 → 图表 4 张，
 * 便于用户形成肌肉记忆；空周期时图表卡整体换成 EmptyState 但保留卡片高度。
 */
@Composable
fun StatsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val vm: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val model = ui.model
    val isWeek = ui.period.type.name == "WEEK"

    Scaffold(
        // 外层底栏已经含系统导航栏。这里再吃一次 navigationBars，菜单上方会多出一条空白挡住图表。
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "数据统计",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    ViewModeSwitch(
                        options = listOf("周", "月"),
                        selectedIndex = if (isWeek) 0 else 1,
                        onSelect = { vm.onPeriodType(it) },
                    )
                }
                PeriodPager(
                    label = ui.period.label,
                    canShiftForward = ui.canShiftForward,
                    onShift = vm::onShift,
                )
            }

            if (model == null) {
                item { EmptyState("正在统计…") }
                return@LazyColumn
            }

            item {
                FrequencyCard(
                    model = model,
                    isWeek = isWeek,
                    onDayClick = { index -> nav.navigate(Routes.day(dayOf(model, index))) },
                )
            }

            item {
                ChartCard("时长趋势", if (isWeek) "最近 8 周" else "最近 6 个月") {
                    LineChart(
                        points = model.trend.map { ChartPoint(it.label, it.minutes.toFloat()) },
                        lineColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                ChartCard("运动方式占比", "按时长加权，最多 6 类 + 其他") {
                    if (model.byType.isEmpty()) EmptyState("暂无数据") else Column {
                        val sliceColors = model.byType.mapIndexed { index, slice ->
                            donutSliceColor(index, slice.name)
                        }
                        DonutChart(
                            slices = model.byType.mapIndexed { index, slice ->
                                slice.name to sliceColors[index]
                            },
                            values = model.byType.map { it.minutes.toFloat() },
                            centerLabel = "总时长",
                            centerValue = "${model.summary.minutes} 分",
                        )
                        Spacer(Modifier.height(Dimens.space8))
                        model.byType.forEachIndexed { index, slice ->
                            LegendRow(
                                color = sliceColors[index],
                                label = slice.name,
                                value = "${slice.minutes} 分 · ${slice.percent.toInt()}%",
                            )
                        }
                    }
                }
            }

            item {
                ChartCard("部位训练占比", "8 轴雷达，没练过的部位显示 0 而不是消失") {
                    if (model.summary.segments == 0) EmptyState("暂无数据") else {
                        val ordered = model.byPart.keys.sorted()
                        RadarChart(
                            axes = ordered.map { BodyParts.name(it) },
                            values = ordered.map { (model.byPart[it] ?: 0).toFloat() },
                            fillColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private const val OTHERS_SLICE_NAME = "其他"

/** 圆环和图例共用的扇区颜色。前几类按序号取高对比色，「其他」保持灰色。 */
private fun donutSliceColor(index: Int, name: String): Long {
    if (name == OTHERS_SLICE_NAME) return ColorOthers.toRgbLong()
    val palette = DonutContrast.colors
    return palette[index % palette.size].toRgbLong()
}

/**
 * 页首频率卡：三枚总览数字 + 周柱或月历热力。
 * 今天的柱用实色，其余减淡，方便一眼看到今天练了没。
 */
@Composable
private fun FrequencyCard(
    model: StatsUiModel,
    isWeek: Boolean,
    onDayClick: (Int) -> Unit,
) {
    val days = model.period.range.map { LocalDate.ofEpochDay(it.toLong()) }
    val todayIndex = days.indexOf(LocalDate.now())
    ChartCard(
        title = "运动频率",
        subtitle = if (isWeek) "柱高是当天训练段数，点某一天可打开记录" else "颜色越深，当天练得越多",
    ) {
        SummaryStrip(model)
        Spacer(Modifier.height(Dimens.space8))
        if (model.summary.segments == 0) {
            EmptyState("这个周期还没有训练记录")
        } else if (isWeek) {
            val weekday = listOf("一", "二", "三", "四", "五", "六", "日")
            BarChart(
                points = model.heat.mapIndexed { index, value ->
                    val label = days.getOrNull(index)?.let { weekday[it.dayOfWeek.value - 1] } ?: ""
                    ChartPoint(label, value.toFloat())
                },
                barColor = MaterialTheme.colorScheme.primary,
                emphasizeIndex = todayIndex,
                showValues = true,
                onBarClick = onDayClick,
            )
        } else {
            WeekdayHeader(model.period.firstDay)
            CalendarHeatmap(
                days = days,
                segments = model.heat,
                levelColors = HeatLevels.levels,
                leadingBlanks = WeekUtil.weekStart(days.first(), model.period.firstDay).let { start ->
                    (days.first().toEpochDay() - start.toEpochDay()).toInt()
                },
                onDayClick = onDayClick,
            )
            HeatLegend()
        }
    }
}

/** 段数、分钟、消耗并排，替代原来三张单独的指标卡。 */
@Composable
private fun SummaryStrip(model: StatsUiModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SummaryCell("段数", "${model.summary.segments}")
        SummaryCell("分钟", "${model.summary.minutes}")
        SummaryCell("千卡", model.summary.calories?.toString() ?: "—")
    }
    val caption = when {
        model.summary.uncalculated > 0 -> "打卡 ${model.summary.trainedDays} 天 · 含 ${model.summary.uncalculated} 条未计算消耗"
        model.foodText.isNotBlank() -> "打卡 ${model.summary.trainedDays} 天 · ${model.foodText}"
        else -> "打卡 ${model.summary.trainedDays} 天 · 日均 ${model.summary.avgMinPerDay} 分钟"
    }
    Text(
        caption,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.space4),
    )
}

/** 总览里的一个数字 */
@Composable
private fun SummaryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 月历上方的周起始日表头，顺序跟热力图列对齐 */
@Composable
private fun WeekdayHeader(firstDay: java.time.DayOfWeek) {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    val start = firstDay.value - 1
    Row(Modifier.fillMaxWidth().padding(bottom = Dimens.space4)) {
        repeat(7) { offset ->
            Text(
                names[(start + offset) % 7],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 热力色阶：浅到深，不让颜色成为唯一信息 */
@Composable
private fun HeatLegend() {
    Row(
        Modifier.fillMaxWidth().padding(top = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HeatLevels.levels.forEach { color ->
            Box(
                Modifier
                    .padding(horizontal = Dimens.space2)
                    .size(Dimens.space12)
                    .clip(RoundedCornerShape(Dimens.space2))
                    .background(color),
            )
        }
        Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 周期翻页条：左右箭头夹住当前区间文案。
 * 已在当前或未来周期时禁用向右，避免点了没反应。
 */
@Composable
private fun PeriodPager(
    label: String,
    canShiftForward: Boolean,
    onShift: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周期")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onShift(1) }, enabled = canShiftForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周期")
        }
    }
}

/** 图表下标 → 真实日期。区间是闭区间，首格即 range.first。 */
private fun dayOf(model: com.fitrecord.app.domain.StatsUiModel, index: Int): LocalDate =
    LocalDate.ofEpochDay(model.period.range.first.toLong() + index)
