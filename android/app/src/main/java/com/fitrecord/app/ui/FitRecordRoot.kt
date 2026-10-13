package com.fitrecord.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.ui.components.ReadOnlyBar
import com.fitrecord.app.ui.Routes as Nav

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(Nav.CALENDAR, "日历", Icons.Default.DateRange),
    TabSpec(Nav.STATS, "统计", Icons.Default.List),
    TabSpec(Nav.PROFILE, "我的", Icons.Default.Person),
)

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
        topBar = {
            if (readOnly) ReadOnlyBar("数据升级失败，已切换为只读；请先导出数据再联系恢复")
        },
        bottomBar = {
            if (currentRoute in Nav.tabs) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
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
