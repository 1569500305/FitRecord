package com.fitrecord.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.fitrecord.app.BuildConfig
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.ui.components.AppCard
import com.fitrecord.app.ui.components.SegmentedSwitch
import com.fitrecord.app.ui.components.stableOutlinedTextFieldColors
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek

// ================================================================ P6 身体参数

@Composable
fun BodyProfileScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val vm: BodyProfileViewModel = viewModel(factory = BodyProfileViewModel.factory(container))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var savedHint by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            ScreenHeaderPublic("身体参数") { nav.popBackStack() }

            Text("性别", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedSwitch(
                options = listOf("男", "女", "不愿填"),
                selectedIndex = when (ui.gender) { "M" -> 0; "F" -> 1; "X" -> 2; else -> -1 },
                onSelect = { vm.onGender(listOf("M", "F", "X")[it]) },
            )
            OutlinedTextField(
                value = ui.ageText, onValueChange = { vm.onAge(it) },
                label = { Text("年龄（可留空）") }, singleLine = true,
                isError = ui.errors.any { it.contains("年龄") },
                colors = stableOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.heightText, onValueChange = { vm.onHeight(it) },
                label = { Text("身高 cm（可留空）") }, singleLine = true,
                isError = ui.errors.any { it.contains("身高") },
                colors = stableOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.weightText, onValueChange = { vm.onWeight(it) },
                label = { Text("体重 kg（必填）") }, singleLine = true,
                isError = ui.errors.any { it.contains("体重") },
                supportingText = {
                    val err = ui.errors.firstOrNull { it.contains("体重") }
                    if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
                },
                colors = stableOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            ui.errors.filterNot { it.contains("体重") }.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { scope.launch { if (vm.save()) savedHint = true } },
                enabled = ui.errors.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            if (savedHint) {
                Text(
                    "已更新，之后的记录将按新体重计算",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ================================================================ P10 外观设置

@Composable
fun AppearanceScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val vm: AppearanceViewModel = viewModel(factory = AppearanceViewModel.factory(container))
    val ui by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            ScreenHeaderPublic("外观与主题") { nav.popBackStack() }
            AppCard {
                Text("主题模式", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Dimens.space8))
                SegmentedSwitch(
                    options = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = when (ui.themeMode) {
                        ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2
                    },
                    onSelect = { vm.setTheme(it) },
                )
            }
            AppCard {
                Text("周起始日", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Dimens.space8))
                SegmentedSwitch(
                    options = listOf("周一", "周日"),
                    selectedIndex = if (ui.firstDay == DayOfWeek.MONDAY) 0 else 1,
                    onSelect = { vm.setFirstDay(it) },
                )
                Text(
                    "影响日历首页与周统计的分界",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ================================================================ P3 我的

@Composable
fun ProfileScreen(nav: NavHostController) {
    val container = LocalContainer.current
    // 数据库还没吐出第一帧时，null 和「确实没填」分不开，不能先画出空态按钮。
    val profileFlow = remember(container) { container.profileDao.observeProfile() }
    val typesFlow = remember(container) { container.typeDao.observeAll() }
    val partsFlow = remember(container) { container.bodyPartDao.observeAll() }
    val profile = collectReady(profileFlow, null)
    val types = collectReady(typesFlow, emptyList())
    val parts = collectReady(partsFlow, emptyList())
    var showAbout by remember { mutableStateOf(false) }

    // 累计总账（规格 P3 页脚）
    val lifetime = remember { mutableStateOf(Triple(0, 0, 0)) }
    LaunchedEffect(profile.value, types.value) {
        runCatching {
            Triple(
                container.statsDao.lifetimeDays(),
                container.statsDao.lifetimeSegments(),
                container.statsDao.lifetimeMinutes(),
            )
        }.onSuccess { lifetime.value = it }
    }

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            item { Text("我的", style = MaterialTheme.typography.headlineSmall) }
            item { BodySummaryCard(profile) { nav.navigate(Routes.BODY) } }
            item {
                ProfileRow("运动类型", countSubtitle(types) { list ->
                    "${list.size} 项（自定义 ${list.count { !it.isBuiltin }}）"
                }) { nav.navigate(Routes.TYPE_LIST) }
            }
            item {
                ProfileRow("锻炼部位", countSubtitle(parts) { "${it.size} 项（V1 固定）" }, onClick = null)
            }
            item { ProfileRow("数据备份与恢复", "导出 / 导入 JSON") { nav.navigate(Routes.BACKUP) } }
            item { ProfileRow("外观与主题", "深色模式 · 周起始日") { nav.navigate(Routes.APPEARANCE) } }
            item { ProfileRow("关于", "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") { showAbout = true } }
            item {
                Text(
                    "累计 ${lifetime.value.first} 天 · ${lifetime.value.second} 段 · ${lifetime.value.third} 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("轻体记") },
            text = { Text("无网络请求 · 无广告 · 无账号\n数据只存在这台手机里\n\n版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("关闭") } },
        )
    }
}

/** 数据库尚未返回时的占位。isReady 为 false 表示还不知道真实值。 */
private data class ReadyValue<T>(val value: T, val isReady: Boolean = false)

/**
 * 收集一条数据库 Flow。第一帧保持未就绪，避免把「还在查询」画成空数据。
 */
@Composable
private fun <T> collectReady(flow: Flow<T>, empty: T): ReadyValue<T> {
    val readyFlow = remember(flow) { flow.map { ReadyValue(it, true) } }
    val snapshot by readyFlow.collectAsStateWithLifecycle(initialValue = ReadyValue(empty))
    return snapshot
}

/** 列表还没读到时副标题留空，不先显示 0 项。 */
private fun <T> countSubtitle(snapshot: ReadyValue<List<T>>, format: (List<T>) -> String): String =
    if (snapshot.isReady) format(snapshot.value) else " "

/**
 * 身体参数卡。查询完成前只占一行高度，不显示「完善身体参数」。
 */
@Composable
private fun BodySummaryCard(profile: ReadyValue<UserProfileEntity?>, onOpen: () -> Unit) {
    AppCard(onClick = if (profile.isReady) onOpen else null) {
        if (!profile.isReady) {
            Text(" ", style = MaterialTheme.typography.titleLarge)
            return@AppCard
        }
        val saved = profile.value
        if (saved == null) {
            Text("还没有身体参数", style = MaterialTheme.typography.titleLarge)
            Text(
                "体重用于计算卡路里消耗",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.space8))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("完善身体参数")
            }
            return@AppCard
        }
        Text(
            listOfNotNull(
                "${saved.weightKg} kg",
                saved.heightCm?.let { "${it} cm" },
                saved.age?.let { "$it 岁" },
                when (saved.gender) { "M" -> "男"; "F" -> "女"; else -> null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: (() -> Unit)?) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 二级页共用的返回式标题（DayAndRecord 里的私版提升为公用） */
@Composable
fun ScreenHeaderPublic(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}
