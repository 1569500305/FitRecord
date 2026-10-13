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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.ui.components.AppCard
import com.fitrecord.app.ui.components.ConfirmDialog
import com.fitrecord.app.ui.components.MetricCard
import com.fitrecord.app.ui.components.MinuteField
import com.fitrecord.app.ui.components.RecordCard
import com.fitrecord.app.ui.components.TagSelector
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.toColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun backArrow(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        backArrow(onBack)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

// ================================================================ P4 当日记录

@Composable
fun DayRecordScreen(nav: NavHostController, date: LocalDate) {
    val container = LocalContainer.current
    val vm: DayRecordViewModel = viewModel(factory = DayRecordViewModel.factory(container, date))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // 删除后 5 秒内可撤销（规格 5.4）
    LaunchedEffect(ui.pendingUndo) {
        if (ui.pendingUndo != null) {
            val result = snackbar.showSnackbar("已删除 1 段训练", actionLabel = "撤销")
            if (result == SnackbarResult.Dismissed) vm.dismissUndo()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            item {
                val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
                val suffix = if (date != LocalDate.now()) "（补录）" else ""
                ScreenHeader("${date.monthValue}月${date.dayOfMonth}日 $weekday$suffix", { nav.popBackStack() })
            }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今日已健身", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = ui.trained,
                            enabled = !ui.readOnly,
                            onCheckedChange = { on ->
                                if (!on && ui.trained) confirmClear = true else vm.toggleTrained(on)
                            },
                        )
                    }
                }
            }
            item {
                MetricCard(
                    title = "当日汇总",
                    value = "${ui.segments}",
                    unit = "段 · ${ui.minutes} 分钟",
                    caption = ui.calories?.let { "≈ $it kcal   ${ui.foodText}" } ?: "完善体重后显示消耗",
                    accent = true,
                )
            }
            items(ui.records, key = { it.id }) { record ->
                RecordCard(
                    record = record,
                    bodyPartNames = record.bodyPartIds.mapNotNull { ui.partNames[it] }.joinToString("·"),
                    onClick = { nav.navigate(Routes.record(date, record.id)) },
                    onDelete = { pendingDelete = record.id },
                )
            }
            item {
                OutlinedButton(
                    onClick = { nav.navigate(Routes.record(date)) },
                    enabled = !ui.readOnly,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("＋ 添加一段训练") }
            }
            item {
                OutlinedTextField(
                    value = ui.dayNote,
                    onValueChange = { vm.saveNote(it) },
                    label = { Text("当日备注") },
                    placeholder = { Text("重量、组数、感受…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingDelete?.let { id ->
        val record = ui.records.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "删除这段训练？",
            message = record?.let { "${it.type.name} · ${it.durationMin} 分钟" } ?: "",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = { vm.delete(id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "清空当日训练？",
            message = "将删除今天全部 ${ui.segments} 段记录",
            confirmLabel = "清空",
            destructive = true,
            onConfirm = { vm.toggleTrained(false); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

// ================================================================ P5 记录编辑

@Composable
fun RecordEditorScreen(nav: NavHostController, date: LocalDate, recordId: Long) {
    val container = LocalContainer.current
    val vm: RecordEditorViewModel = viewModel(factory = RecordEditorViewModel.factory(container, date, recordId))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var savedOnce by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    fun leave() {
        if (ui.dirty && !savedOnce) showDiscard = true else nav.popBackStack()
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            item {
                ScreenHeader(if (recordId < 0) "新增训练" else "编辑训练", ::leave)
            }
            item {
                AppCard {
                    Text("日期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.space4))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.onDate(ui.date.minusDays(1)) }) { Text("−1") }
                        Text(
                            ui.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { vm.onDate(ui.date.plusDays(1)) }) { Text("+1") }
                    }
                    TextButton(onClick = { vm.onDate(LocalDate.now()) }) { Text("设为今天") }
                }
            }
            item {
                AppCard {
                    Text("运动方式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.space8))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space8)) {
                        items(ui.types) { type ->
                            val selected = type.id == ui.selectedType
                            AppCard(
                                modifier = Modifier.width(104.dp),
                                onClick = { vm.onType(type.id) },
                            ) {
                                Text(
                                    type.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) type.color.toColor() else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    ui.errors.firstOrNull { it.contains("运动方式") }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                AppCard {
                    MinuteField(
                        value = ui.minutes,
                        onValueChange = { vm.onMinutes(it) },
                        error = ui.errors.firstOrNull { it.contains("时长") },
                    )
                }
            }
            item {
                AppCard {
                    Text("锻炼部位（可多选）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.space8))
                    TagSelector(options = ui.parts, selected = ui.selectedParts, onToggle = { vm.onTogglePart(it) })
                }
            }
            item {
                AppCard {
                    Text(
                        ui.previewKcal?.let { "预计消耗  $it kcal" } ?: "完善体重后可计算消耗",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    ui.formula?.let {
                        Spacer(Modifier.height(Dimens.space4))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { nav.navigate(Routes.BODY) }) {
                        Text(if (ui.weightMissing) "去填写体重" else "修改身体参数")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = ui.note,
                    onValueChange = { vm.onNote(it) },
                    label = { Text("备注") },
                    placeholder = { Text("重量、组数、感受…") },
                    isError = ui.errors.any { it.contains("备注") },
                    supportingText = ui.errors.firstOrNull { it.contains("备注") }?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            if (vm.save()) {
                                savedOnce = true
                                nav.popBackStack()
                            }
                        }
                    },
                    enabled = ui.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存") }
                if (ui.readOnly) {
                    Text(
                        "当前为只读模式，无法保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "放弃未保存的修改？",
            message = "刚才填写的内容还没有保存",
            confirmLabel = "放弃",
            destructive = true,
            onConfirm = { showDiscard = false; nav.popBackStack() },
            onDismiss = { showDiscard = false },
        )
    }
}
