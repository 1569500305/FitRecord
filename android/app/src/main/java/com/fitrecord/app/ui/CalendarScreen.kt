package com.fitrecord.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.domain.DayCell
import com.fitrecord.app.ui.components.AppCard
import com.fitrecord.app.ui.components.DayCellView
import com.fitrecord.app.ui.components.InfoBanner
import com.fitrecord.app.ui.components.SegmentedSwitch
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.ExerciseIcons
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val TODAY_CHIP_MILLIS = 220
private val TODAY_CHIP_HEIGHT = 32.dp

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

    // 不用 MutableState：拖动过程中写状态会把整页重组一遍，看起来像闪烁
    val horizontalDrag = remember { FloatArray(1) }
    val swipeThreshold = with(LocalDensity.current) { Dimens.minTouchTarget.toPx() }

    Scaffold(
        // 外层底栏已经含系统导航栏。这里再吃一次 navigationBars，菜单上方会多出一条空白，把每日概况挡住。
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        val contentParent = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val summaryAnchorY = remember { mutableFloatStateOf(Float.NaN) }
        val lastDayCenterX = remember { mutableFloatStateOf(Float.NaN) }
        val lastDayCenterY = remember { mutableFloatStateOf(Float.NaN) }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { contentParent[0] = it },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.pagePadding)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag[0] = 0f },
                            onDragCancel = { horizontalDrag[0] = 0f },
                            onDragEnd = {
                                val total = horizontalDrag[0]
                                horizontalDrag[0] = 0f
                                when {
                                    total < -swipeThreshold -> vm.onMonthShift(1)
                                    total > swipeThreshold -> vm.onMonthShift(-1)
                                }
                            },
                            onHorizontalDrag = { _, dragAmount -> horizontalDrag[0] += dragAmount },
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
                        AnimatedVisibility(
                            visible = ui.showJumpToToday,
                            enter = fadeIn(tween(TODAY_CHIP_MILLIS)) + expandHorizontally(
                                animationSpec = tween(TODAY_CHIP_MILLIS),
                                expandFrom = Alignment.End,
                            ),
                            exit = fadeOut(tween(TODAY_CHIP_MILLIS)) + shrinkHorizontally(
                                animationSpec = tween(TODAY_CHIP_MILLIS),
                                shrinkTowards = Alignment.End,
                            ),
                        ) {
                            TodayJumpChip(
                                onClick = vm::onJumpToToday,
                                modifier = Modifier.padding(end = Dimens.space8),
                            )
                        }
                        SegmentedSwitch(
                            options = listOf("周", "月"),
                            selectedIndex = if (ui.isWeekMode) 0 else 1,
                            onSelect = vm::onViewMode,
                        )
                    }
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
                    MonthGrid(
                        month = ui.month,
                        weekMode = ui.isWeekMode,
                        cells = ui.cells,
                        onDaySelected = vm::onDaySelected,
                        onLastDayPositioned = if (ui.isWeekMode) {
                            null
                        } else {
                            { child ->
                                updateLastDayAnchor(contentParent[0], child, lastDayCenterX, lastDayCenterY)
                            }
                        },
                    )
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
                    SelectedDayCard(
                        summary = ui.daySummary,
                        onOpen = { nav.navigate(Routes.day(ui.daySummary.date)) },
                    )
                }
                item {
                    MonthSummaryCard(
                        summary = ui.summary,
                        reserveButtonSpace = ui.isWeekMode,
                        onPositioned = { child ->
                            updateSummaryAnchor(contentParent[0], child, summaryAnchorY)
                        },
                        onOpenStats = { nav.switchTab(Routes.STATS) },
                    )
                }
            }

            AddTrainingButton(
                coverLastDay = !ui.isWeekMode,
                lastDayCenterX = lastDayCenterX,
                lastDayCenterY = lastDayCenterY,
                summaryCenterY = summaryAnchorY,
                parentWidth = maxWidth,
                savedTopY = { vm.addButtonTopY },
                onPlace = { vm.addButtonTopY = it },
                maxHeight = maxHeight,
                readOnly = ui.readOnly,
                onClick = { nav.navigate(Routes.record(ui.selected)) },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/** 把月度总结的垂直中心记到内容区坐标里。周视图的添加按钮跟它对齐。 */
private fun updateSummaryAnchor(
    parent: LayoutCoordinates?,
    child: LayoutCoordinates,
    anchorCenterY: MutableFloatState,
) {
    if (parent == null || !parent.isAttached || !child.isAttached) return
    val centerY = parent.localPositionOf(child, Offset(0f, child.size.height / 2f)).y
    if (anchorCenterY.floatValue != centerY) anchorCenterY.floatValue = centerY
}

/** 把日历最后一格的中心记到内容区坐标里。月视图的添加按钮盖住这一格的数字。 */
private fun updateLastDayAnchor(
    parent: LayoutCoordinates?,
    child: LayoutCoordinates,
    anchorCenterX: MutableFloatState,
    anchorCenterY: MutableFloatState,
) {
    if (parent == null || !parent.isAttached || !child.isAttached) return
    val center = parent.localPositionOf(child, Offset(child.size.width / 2f, child.size.height / 2f))
    if (anchorCenterX.floatValue != center.x) anchorCenterX.floatValue = center.x
    if (anchorCenterY.floatValue != center.y) anchorCenterY.floatValue = center.y
}

/**
 * 月度总结。周视图时右侧留出按钮宽度，避免文字被挡住。
 */
@Composable
private fun MonthSummaryCard(
    summary: MonthSummary,
    reserveButtonSpace: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onOpenStats: () -> Unit,
) {
    Box(Modifier.onGloballyPositioned(onPositioned)) {
        AppCard(onClick = onOpenStats) {
            Column(if (reserveButtonSpace) Modifier.padding(end = Dimens.fabSize) else Modifier) {
                Text(
                    "本月 ${summary.days} 天 · ${summary.segments} 段 · ${summary.minutes} 分钟",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(Dimens.space4))
                Text(
                    summary.calories
                        ?.let { "≈ $it kcal   ${summary.foodText}" }
                        ?: "完善体重后显示消耗",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/**
 * 添加按钮，可上下拖动。
 * 没拖过时：月视图盖住日历最后一格的数字，周视图贴在月度总结右侧。
 * 拖过之后的垂直位置记在 ViewModel 里，离开页面再回来还在原地。
 * 只读模式仍可拖动，点击不进入编辑（规格 6.9）。
 * 不用阴影：按压时阴影动画会让浅色整页闪一下。
 */
@Composable
private fun AddTrainingButton(
    coverLastDay: Boolean,
    lastDayCenterX: MutableFloatState,
    lastDayCenterY: MutableFloatState,
    summaryCenterY: MutableFloatState,
    parentWidth: Dp,
    savedTopY: () -> Float?,
    onPlace: (Float) -> Unit,
    maxHeight: Dp,
    readOnly: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val buttonPx = with(density) { Dimens.fabSize.toPx() }
    val endPaddingPx = with(density) { Dimens.pagePadding.toPx() }
    val parentWidthPx = with(density) { parentWidth.toPx() }
    val minY = with(density) { Dimens.pagePadding.toPx() }
    val maxY = (with(density) { maxHeight.toPx() } - buttonPx - minY).coerceAtLeast(minY)
    val topY = remember { mutableFloatStateOf(savedTopY() ?: Float.NaN) }
    val saved = topY.floatValue
    val displayY = if (!saved.isNaN()) {
        saved.coerceIn(minY, maxY)
    } else {
        defaultButtonTop(coverLastDay, lastDayCenterY.floatValue, summaryCenterY.floatValue, buttonPx, minY, maxY)
    }
    val displayX = defaultButtonLeft(
        coverLastDay,
        lastDayCenterX.floatValue,
        buttonPx,
        parentWidthPx,
        endPaddingPx,
    )
    val coverLastDayNow = rememberUpdatedState(coverLastDay)
    val dragState = rememberDraggableState { delta ->
        val base = topY.floatValue.takeUnless { it.isNaN() }
            ?: defaultButtonTop(
                coverLastDayNow.value,
                lastDayCenterY.floatValue,
                summaryCenterY.floatValue,
                buttonPx,
                minY,
                maxY,
            )
        if (base.isNaN()) return@rememberDraggableState
        val next = (base + delta).coerceIn(minY, maxY)
        topY.floatValue = next
        onPlace(next)
    }
    if (displayX.isNaN() || displayY.isNaN()) return
    Surface(
        onClick = { if (!readOnly) onClick() },
        modifier = modifier
            .offset { IntOffset(displayX.roundToInt(), displayY.roundToInt()) }
            .draggable(state = dragState, orientation = Orientation.Vertical)
            .size(Dimens.fabSize),
        shape = RoundedCornerShape(Dimens.chipCorner),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 0.dp,
        border = BorderStroke(Dimens.strokeWidth, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Add,
                contentDescription = if (readOnly) "只读模式不可添加" else "添加训练",
                modifier = Modifier.size(Dimens.fabIconSize),
            )
        }
    }
}

/**
 * 未拖动时的按钮顶边。
 * 月视图中心对齐最后一格，盖住日期数字；周视图与月度总结垂直居中。锚点还没量到时返回 NaN。
 */
private fun defaultButtonTop(
    coverLastDay: Boolean,
    lastDayCenterY: Float,
    summaryCenterY: Float,
    buttonPx: Float,
    minY: Float,
    maxY: Float,
): Float {
    val anchor = if (coverLastDay) lastDayCenterY else summaryCenterY
    if (anchor.isNaN()) return Float.NaN
    return (anchor - buttonPx / 2f).coerceIn(minY, maxY)
}

/** 按钮左边。月视图对准最后一格中心，周视图贴在内容区右缘。锚点还没量到时返回 NaN。 */
private fun defaultButtonLeft(
    coverLastDay: Boolean,
    lastDayCenterX: Float,
    buttonPx: Float,
    parentWidthPx: Float,
    endPaddingPx: Float,
): Float {
    val raw = if (coverLastDay) {
        if (lastDayCenterX.isNaN()) return Float.NaN
        lastDayCenterX - buttonPx / 2f
    } else {
        parentWidthPx - endPaddingPx - buttonPx
    }
    val maxX = (parentWidthPx - buttonPx).coerceAtLeast(0f)
    return raw.coerceIn(0f, maxX)
}

/** 跳回今天。高度与周/月切换一致，出现在切换按钮左侧。 */
@Composable
private fun TodayJumpChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(TODAY_CHIP_HEIGHT)
            .clip(RoundedCornerShape(Dimens.chipCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "回到今天" }
            .padding(horizontal = Dimens.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 选中日的运动概况。整卡可点，进入当日页。
 * 类型名和图标放在最上方，段数、时长和消耗排在日期下面。
 */
@Composable
private fun SelectedDayCard(
    summary: SelectedDaySummary,
    onOpen: () -> Unit,
) {
    AppCard(onClick = onOpen) {
        if (summary.trained) {
            DayTypeLine(summary.typeEfforts)
            Spacer(Modifier.height(Dimens.space8))
        }
        Text(
            "${summary.date.monthValue}月${summary.date.dayOfMonth}日 · 运动概况",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(Dimens.space4))
        if (summary.trained) {
            DayEffortLine(summary)
        } else {
            Text(
                "这一天还没有记录，点 + 添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 当天练过的类型：图标、名称，后面紧跟这一类型的合计分钟。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayTypeLine(typeEfforts: List<DayTypeEffort>) {
    if (typeEfforts.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        typeEfforts.forEach { effort ->
            val emoji = ExerciseIcons.emoji(effort.name)
            val label = if (emoji != null) "$emoji ${effort.name}" else effort.name
            Text(
                text = "$label ${effort.minutes}分钟",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** 选中日的段数、时长、消耗，以及对应的食物换算。 */
@Composable
private fun DayEffortLine(summary: SelectedDaySummary) {
    Text(
        "${summary.segments} 段 · ${summary.minutes} 分钟" +
            (summary.calories?.let { " · ≈ $it kcal" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (summary.calories == null || summary.foodText.isBlank()) return
    Text(
        summary.foodText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

/**
 * 记住翻月前一帧的格子。
 * 进出两层如果都读最新 cells，会叠成同一个月，滑动时整页闪一下。
 */
private class GridFrames(
    private var key: Pair<YearMonth, Boolean>,
    private var cells: List<DayCell>,
) {
    var previousCells: List<DayCell> = cells
        private set

    /** 月份或周/月模式变化时冻结旧格子；同一页只更新当前格子上的选中态。 */
    fun sync(newKey: Pair<YearMonth, Boolean>, newCells: List<DayCell>) {
        if (newKey != key) {
            previousCells = cells
            key = newKey
        }
        cells = newCells
    }
}

/**
 * 月网格。只在月份变化时横向滑动；点选某一天不重启动画。
 * 尺寸动画关掉：高度弹簧会把下面的卡片顶一下，看起来像页面闪烁。
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    weekMode: Boolean,
    cells: List<DayCell>,
    onDaySelected: (LocalDate) -> Unit,
    onLastDayPositioned: ((LayoutCoordinates) -> Unit)?,
) {
    val frames = remember { GridFrames(month to weekMode, cells) }
    val currentKey = month to weekMode
    frames.sync(currentKey, cells)

    AnimatedContent(
        targetState = currentKey,
        transitionSpec = { monthSlide() },
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background),
        label = "monthGrid",
    ) { key ->
        val pageCells = if (key == currentKey) cells else frames.previousCells
        val reportLastDay = onLastDayPositioned != null && key == currentKey
        MonthCells(
            cells = pageCells,
            compact = key.second,
            onDaySelected = onDaySelected,
            onLastDayPositioned = if (reportLastDay) onLastDayPositioned else null,
        )
    }
}

/** 翻月用纯滑动，不淡入淡出、不改高度，避免底色透出或整页跳动。 */
private fun AnimatedContentTransitionScope<Pair<YearMonth, Boolean>>.monthSlide(): ContentTransform {
    val enter: EnterTransition
    val exit: ExitTransition
    if (targetState.first == initialState.first) {
        enter = EnterTransition.None
        exit = ExitTransition.None
    } else {
        val direction = if (targetState.first > initialState.first) 1 else -1
        enter = slideInHorizontally { width -> direction * width }
        exit = slideOutHorizontally { width -> -direction * width }
    }
    return ContentTransform(enter, exit, sizeTransform = null)
}

/** 把一周 7 格排成一行。最后一格上报位置，供月视图把添加按钮放在它上方。 */
@Composable
private fun MonthCells(
    cells: List<DayCell>,
    compact: Boolean,
    onDaySelected: (LocalDate) -> Unit,
    onLastDayPositioned: ((LayoutCoordinates) -> Unit)?,
) {
    val lastIndex = cells.lastIndex
    Column(Modifier.fillMaxWidth()) {
        cells.chunked(7).forEachIndexed { weekIndex, week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEachIndexed { dayIndex, cell ->
                    val cellIndex = weekIndex * 7 + dayIndex
                    Box(Modifier.weight(1f)) {
                        DayCellView(
                            cell = cell,
                            compact = compact,
                            onClick = { onDaySelected(cell.date) },
                            modifier = lastDayModifier(cellIndex, lastIndex, onLastDayPositioned),
                        )
                    }
                }
            }
        }
    }
}

/** 只有日历网格的最后一格需要测量顶边，其余格子不挂布局回调。 */
private fun lastDayModifier(
    cellIndex: Int,
    lastIndex: Int,
    onLastDayPositioned: ((LayoutCoordinates) -> Unit)?,
): Modifier {
    if (cellIndex != lastIndex || onLastDayPositioned == null) return Modifier
    return Modifier.onGloballyPositioned(onLastDayPositioned)
}

/** 尚未接线的界面占位（下一步逐个替换掉） */
@Composable
fun ComingSoonScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
