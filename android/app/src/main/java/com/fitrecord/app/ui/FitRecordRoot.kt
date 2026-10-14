package com.fitrecord.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.graphics.Color
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
        // 默认 700ms 交叉淡变会让两页同时半透明，点击或返回时整页闪一下
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                // 切页时新旧页面之间会露出主题灰底，看起来像整页闪一下
                .background(MaterialTheme.colorScheme.surface),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
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
