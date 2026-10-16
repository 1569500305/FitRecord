package com.fitrecord.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.ui.components.ReadOnlyBar
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.Routes as Nav

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(Nav.CALENDAR, "日历", Icons.Default.DateRange),
    TabSpec(Nav.STATS, "统计", Icons.Default.List),
    TabSpec(Nav.PROFILE, "我的", Icons.Default.Person),
)

/**
 * 切换底部三个一级页面。
 *
 * 回到日历时用 popBackStack，而不是再 navigate 一次。
 * 日历上的统计概览若直接压入统计页，后一种写法会在弹回日历的同时
 * 把统计页恢复出来，底栏点「日历」就像没有反应。
 */
fun NavHostController.switchTab(route: String) {
    val start = graph.findStartDestination()
    if (route == start.route) {
        if (currentDestination?.route != route) {
            popBackStack(start.id, inclusive = false, saveState = true)
        }
        return
    }
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 单 Activity + Navigation-Compose（规格 1.1 / 1.2） */
@Composable
fun FitRecordRoot(startDestination: String = Nav.CALENDAR) {
    val container = LocalContainer.current
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 只读模式（数据迁移失败，规格 6.9）：顶部插红色横幅，写入口由各自屏按 ui.readOnly 关闭
    val readOnly by container.readOnly.collectAsStateWithLifecycle()

    Scaffold(
        // 外层不再重复消费系统栏 inset：各屏自带 Scaffold 已加状态栏顶部，
        // 这里再叠一次就是顶部那块“空白 head”的根因（需求 4）。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (readOnly) ReadOnlyBar("数据升级失败，已切换为只读；请先导出数据再联系恢复")
        },
        bottomBar = {
            if (currentRoute in Nav.tabs) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route -> nav.switchTab(route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // 切页间隙必须盖住整块内容区，而且要和页面底色相同。
                // 用 surface 会在深色里闪出一块更浅的灰。
                .background(MaterialTheme.colorScheme.background)
                .clipSlideBounds(),
            enterTransition = {
                enterFor(initialState.destination.route, targetState.destination.route)
            },
            exitTransition = {
                exitFor(initialState.destination.route, targetState.destination.route)
            },
            popEnterTransition = {
                popEnterFor(initialState.destination.route, targetState.destination.route)
            },
            popExitTransition = {
                popExitFor(initialState.destination.route, targetState.destination.route)
            },
        ) {
            composable(Nav.CALENDAR) { CalendarScreen(nav) }
            composable(Nav.STATS) { StatsScreen(nav) }
            composable(Nav.PROFILE) { ProfileScreen(nav) }
            composable(Nav.BODY) { BodyProfileScreen(nav) }
            composable(Nav.TYPE_LIST) { TypeListScreen(nav) }
            composable(Nav.BACKUP) { BackupScreen(nav) }
            composable(Nav.APPEARANCE) { AppearanceScreen(nav) }
            composable(Nav.DAY) { entry ->
                DayRecordScreen(nav, Nav.epochDayOf(entry.arguments?.getString(Nav.ARG_DATE)))
            }
            composable(Nav.RECORD) { entry ->
                val args = entry.arguments
                RecordEditorScreen(
                    nav = nav,
                    date = Nav.epochDayOf(args?.getString(Nav.ARG_DATE)),
                    recordId = args?.getString(Nav.ARG_ID)?.toLongOrNull() ?: -1L,
                )
            }
            composable(Nav.TYPE_EDIT) { ComingSoonScreen("类型编辑（接线中）") }
        }
    }
}

/** 三个一级页互相切换的滑动时长。 */
private const val TAB_SLIDE_MILLIS = 200

/** 从一级页推进二级页的滑动时长。 */
private const val DETAIL_SLIDE_MILLIS = 250

/**
 * 页面进入方向。
 * 三个 Tab 按日历、统计、我的从左往右排；二级页一律从右侧推入。
 * 只用位移、不用透明度：淡入淡出会在深色主题下透出一帧浅色。
 */
private fun enterFor(fromRoute: String?, toRoute: String?): EnterTransition {
    if (isTabChange(fromRoute, toRoute)) {
        val direction = tabDirection(fromRoute, toRoute)
        return slideInHorizontally(slideSpec(TAB_SLIDE_MILLIS)) { width -> direction * width }
    }
    return slideInHorizontally(slideSpec(DETAIL_SLIDE_MILLIS)) { width -> width }
}

/** 与 [enterFor] 相反的离场方向。二级页离场时只让出约三分之一，避免整页抽空。 */
private fun exitFor(fromRoute: String?, toRoute: String?): ExitTransition {
    if (isTabChange(fromRoute, toRoute)) {
        val direction = tabDirection(fromRoute, toRoute)
        return slideOutHorizontally(slideSpec(TAB_SLIDE_MILLIS)) { width -> -direction * width }
    }
    return slideOutHorizontally(slideSpec(DETAIL_SLIDE_MILLIS)) { width -> -width / 3 }
}

/** 从二级页返回时，下层页面从左侧回到原位。Tab 之间仍按左右顺序滑。 */
private fun popEnterFor(fromRoute: String?, toRoute: String?): EnterTransition {
    if (isTabChange(fromRoute, toRoute)) return enterFor(fromRoute, toRoute)
    return slideInHorizontally(slideSpec(DETAIL_SLIDE_MILLIS)) { width -> -width / 3 }
}

/** 从二级页返回时，当前页向右滑出。 */
private fun popExitFor(fromRoute: String?, toRoute: String?): ExitTransition {
    if (isTabChange(fromRoute, toRoute)) return exitFor(fromRoute, toRoute)
    return slideOutHorizontally(slideSpec(DETAIL_SLIDE_MILLIS)) { width -> width }
}

/** 起点和终点都是底部三个 Tab 时，按 Tab 动画而不是二级页推入。 */
private fun isTabChange(fromRoute: String?, toRoute: String?): Boolean =
    fromRoute in Nav.tabs && toRoute in Nav.tabs

/** 目标在起点右侧时为 1，否则为 -1。 */
private fun tabDirection(fromRoute: String?, toRoute: String?): Int {
    val order = listOf(Nav.CALENDAR, Nav.STATS, Nav.PROFILE)
    val from = order.indexOf(fromRoute).coerceAtLeast(0)
    val to = order.indexOf(toRoute).coerceAtLeast(0)
    return if (to >= from) 1 else -1
}

/** 横向滑动的时长和减速曲线。 */
private fun slideSpec(millis: Int) = tween<IntOffset>(durationMillis = millis, easing = FastOutSlowInEasing)

/**
 * 裁掉滑出内容区的页面。
 * 不用 clipToBounds：它会建硬件层，深色下这一层第一帧按浅色窗口清屏。
 */
private fun Modifier.clipSlideBounds(): Modifier = drawWithContent {
    clipRect { this@drawWithContent.drawContent() }
}

/**
 * 底部三个 Tab。
 * 高亮放在系统导航栏上方的内容区里，圆角框同时包住图标和文字，避免被 64dp 高度裁掉。
 */
@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = Dimens.navBarHeight)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                BottomTab(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onSelect(tab.route) },
                )
            }
        }
    }
}

/** 单个 Tab。选中时圆角底衬同时包住图标和文字。 */
@Composable
private fun RowScope.BottomTab(
    tab: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val highlight = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val tint = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = Dimens.minTouchTarget)
            // 水波纹画在整列宽度上，会比中间那块圆角高亮更宽；按产品要求去掉这层动画
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.cardCorner))
                .background(highlight)
                .padding(horizontal = Dimens.space16, vertical = Dimens.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Icon(tab.icon, contentDescription = null, tint = tint)
            Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
