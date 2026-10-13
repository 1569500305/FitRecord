package com.fitrecord.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.ui.components.AppCard
import com.fitrecord.app.ui.components.DayCellView
import com.fitrecord.app.ui.components.InfoBanner
import com.fitrecord.app.ui.components.SegmentedSwitch
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.ExerciseIcons
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * P1 日历首页（规格 1.1 / 3-P1）。
 *
 * 左右滑切月用手写横向拖拽检测而不是 HorizontalPager：Pager 在 foundation 1.7 仍是
 * 实验性 API 且 rememberPagerState 签名跨版本变动过，V1 优先保证不炸（台账偏离记录）。
 */
@Composable
fun CalendarScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val vm: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(container))
    val ui by vm.uiState.collectAsStateWithLifecycle()

    var dragTotal by remember { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 60.dp.toPx() }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.pagePadding)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragTotal < -swipeThreshold -> vm.onMonthShift(1)
                                    dragTotal > swipeThreshold -> vm.onMonthShift(-1)
                                }
                                dragTotal = 0f
                            },
                            onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                        )
                    },
                contentPadding = PaddingValues(vertical = Dimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ui.month.year}年${ui.month.monthValue}月",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.onMonthShift(-1) }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一月")
                        }
                        IconButton(onClick = { vm.onMonthShift(1) }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一月")
                        }
                    }
                }
                item {
                    SegmentedSwitch(
                        options = listOf("月", "周"),
                        selectedIndex = if (ui.isWeekMode) 1 else 0,
                        onSelect = { vm.onToggleMode() },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        WEEKDAYS.forEach { day ->
                            Text(
                                day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth()) {
                        ui.cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { cell ->
                                    Box(Modifier.weight(1f)) {
                                        DayCellView(
                                            cell = cell,
                                            compact = ui.isWeekMode,
                                            onClick = { vm.onDaySelected(cell.date) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (ui.showBanner) {
                    item {
                        InfoBanner(
                            text = "填写体重后才能计算卡路里",
                            actionLabel = "去设置",
                            onAction = { nav.navigate(Routes.BODY) },
                            onDismiss = { vm.dismissBanner() },
                        )
                    }
                }
                item {
                    AppCard(onClick = { nav.navigate(Routes.STATS) }) {
                        Text(
                            "本月 ${ui.summary.days} 天 · ${ui.summary.segments} 段 · ${ui.summary.minutes} 分钟",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(Dimens.space4))
                        Text(
                            ui.summary.calories
                                ?.let { "≈ $it kcal   ${ui.summary.foodText}" }
                                ?: "完善体重后显示消耗",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                item {
                    // 选中日的运动概况（需求 3）；整卡仍可点 → 进当日页（规格 P1 交互 2）
                    val ds = ui.daySummary
                    AppCard(onClick = { nav.navigate(Routes.day(ds.date)) }) {
                        Text(
                            "${ds.date.monthValue}月${ds.date.dayOfMonth}日 · 运动概况",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(Dimens.space4))
                        if (ds.trained) {
                            Text(
                                "${ds.segments} 段 · ${ds.minutes} 分钟" +
                                    (ds.calories?.let { " · ≈ $it kcal" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (ds.calories != null && ds.foodText.isNotBlank()) {
                                Text(
                                    ds.foodText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            // 类型名 + 对应图标并列（图标与格子里同一套映射）
                            Text(
                                ds.typeNames.joinToString("、") {
                                    ExerciseIcons.emoji(it)?.let { e -> "$it $e" } ?: it
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "这一天还没有记录，点右下角 + 添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                // 只读模式（规格 6.9）：不关入口而是让点击成为空操作（本 Material3 版本 FAB 无 enabled 参）
                onClick = { if (!ui.readOnly) nav.navigate(Routes.record(ui.selected)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.pagePadding),
            ) {
                Icon(Icons.Default.Add, contentDescription = if (ui.readOnly) "只读模式不可添加" else "添加训练")
            }
        }
    }
}

/** 尚未接线的界面占位（下一步逐个替换掉） */
@Composable
fun ComingSoonScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
