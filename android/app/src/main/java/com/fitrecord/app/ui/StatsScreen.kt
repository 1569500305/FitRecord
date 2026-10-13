package com.fitrecord.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fitrecord.app.ui.components.MetricCard
import com.fitrecord.app.ui.components.SegmentedSwitch
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.HeatLevels
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

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("数据统计", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.onShift(-1) }) { Text("‹") }
                    Spacer(Modifier.padding(horizontal = Dimens.space4))
                    OutlinedButton(onClick = { vm.onShift(1) }) { Text("›") }
                }
                Spacer(Modifier.height(Dimens.space8))
                SegmentedSwitch(
                    options = listOf("周", "月"),
                    selectedIndex = if (isWeek) 0 else 1,
                    onSelect = { vm.onPeriodType(it) },
                )
                Text(
                    ui.period.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (model == null) {
                item { EmptyState("正在统计…") }
                return@LazyColumn
            }

            item {
                MetricCard(
                    title = "运动段数",
                    value = "${model.summary.segments}",
                    unit = "段",
                    caption = "打卡 ${model.summary.trainedDays} 天",
                )
            }
            item {
                MetricCard(
                    title = "总时长",
                    value = "${model.summary.minutes}",
                    unit = "分钟",
                    caption = "平均 ${model.summary.avgMinPerDay} 分钟/天 · ${model.summary.avgMinPerSegment} 分钟/段",
                )
            }
            item {
                MetricCard(
                    title = "消耗",
                    value = model.summary.calories?.toString() ?: "—",
                    unit = "kcal",
                    caption = when {
                        model.summary.uncalculated > 0 -> "含 ${model.summary.uncalculated} 条未计算记录"
                        model.foodText.isNotBlank() -> model.foodText
                        else -> "完善体重后显示"
                    },
                    accent = true,
                )
            }

            item {
                ChartCard("运动频率分布", if (isWeek) "按天段数" else "颜色越深，当天练得越多") {
                    if (model.summary.segments == 0) EmptyState("这个周期还没有训练记录")
                    else if (isWeek) {
                        BarChart(
                            points = model.heat.mapIndexed { i, v -> ChartPoint((i + 1).toString(), v.toFloat()) },
                            barColor = MaterialTheme.colorScheme.primary,
                            onBarClick = { index -> nav.navigate(Routes.day(dayOf(model, index))) },
                        )
                    } else {
                        CalendarHeatmap(
                            days = model.period.range.map { LocalDate.ofEpochDay(it.toLong()) },
                            segments = model.heat,
                            levelColors = HeatLevels.levels,
                            onDayClick = { index -> nav.navigate(Routes.day(dayOf(model, index))) },
                        )
                    }
                }
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
                        DonutChart(
                            slices = model.byType.map { it.name to it.color },
                            values = model.byType.map { it.minutes.toFloat() },
                            centerLabel = "总时长",
                            centerValue = "${model.summary.minutes} 分",
                        )
                        Spacer(Modifier.height(Dimens.space8))
                        model.byType.forEach { slice ->
                            LegendRow(
                                color = slice.color,
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

/** 图表下标 → 真实日期。区间是闭区间，首格即 range.first。 */
private fun dayOf(model: com.fitrecord.app.domain.StatsUiModel, index: Int): LocalDate =
    LocalDate.ofEpochDay(model.period.range.first.toLong() + index)
