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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitrecord.app.ui.Routes as Nav

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(Nav.CALENDAR, "日历", Icons.Default.DateRange),
    TabSpec(Nav.STATS, "统计", Icons.Default.List),
    TabSpec(Nav.PROFILE, "我的", Icons.Default.Person),
)

/** 单 Activity + Navigation-Compose（规格 1.1 / 1.2） */
@Composable
fun FitRecordRoot() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
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
            startDestination = Nav.CALENDAR,
            modifier = Modifier.padding(padding),
        ) {
            composable(Nav.CALENDAR) { CalendarScreen(nav) }
            composable(Nav.STATS) { ComingSoonScreen("统计（接线中）") }
            composable(Nav.PROFILE) { ProfileScreen(nav) }
            composable(Nav.BODY) { BodyProfileScreen(nav) }
            composable(Nav.TYPE_LIST) { ComingSoonScreen("运动类型（接线中）") }
            composable(Nav.BACKUP) { ComingSoonScreen("数据备份（接线中）") }
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
