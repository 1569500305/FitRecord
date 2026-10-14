package com.fitrecord.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.fitrecord.app.ui.components.stableOutlinedTextFieldColors
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.domain.EffortFormat
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.ExerciseIcons
import com.fitrecord.app.ui.theme.toColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 顶栏返回。用箭头图标替代文字尖括号，点击区域保持 48dp。 */
@Composable
private fun backArrow(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}

/** 表单分区标题：左侧图标 + 标题，让每一块一眼能对上用途。 */
@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.space16),
        )
        Spacer(Modifier.width(Dimens.space8))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
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
                    colors = stableOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingDelete?.let { id ->
        val record = ui.records.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "删除这段训练？",
            message = record?.let { "${it.type.name} · ${it.effortText()}" } ?: "",
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
            contentPadding = PaddingValues(horizontal = Dimens.pagePadding, vertical = Dimens.space8),
            verticalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            item {
                EditorTopBar(
                    title = if (recordId < 0) "新增训练" else "编辑训练",
                    canSave = ui.canSave,
                    onBack = ::leave,
                    onSave = {
                        scope.launch {
                            if (vm.save()) {
                                savedOnce = true
                                nav.popBackStack()
                            }
                        }
                    },
                )
                if (ui.readOnly) {
                    Text(
                        "当前为只读模式，无法保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { DateStepCard(date = ui.date, onDate = vm::onDate) }
            item {
                ExerciseTypeCard(
                    types = ui.types,
                    selectedTypeId = ui.selectedType,
                    // 类型列表还没到时先不亮错误，避免一进页面先闪出“请选择运动方式”再消失
                    error = if (ui.types.isEmpty()) {
                        null
                    } else {
                        ui.errors.firstOrNull { it.contains("运动方式") }
                    },
                    onSelect = vm::onType,
                )
            }
            item {
                AppCard {
                    if (ui.recordBySets) {
                        SectionTitle(Icons.Default.PlayArrow, "组数")
                        Spacer(Modifier.height(Dimens.space4))
                        SetRepEditor(
                            setCount = ui.setCount,
                            repsPerSet = ui.repsPerSet,
                            onSetCount = vm::onSetCount,
                            onRepsPerSet = vm::onRepsPerSet,
                        )
                        ui.errors.firstOrNull { it.contains("组") }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        SectionTitle(Icons.Default.PlayArrow, "时长")
                        Spacer(Modifier.height(Dimens.space4))
                        MinuteField(
                            value = ui.minutes,
                            onValueChange = { vm.onMinutes(it) },
                            error = ui.errors.firstOrNull { it.contains("时长") },
                        )
                    }
                }
            }
            item {
                AppCard {
                    SectionTitle(Icons.Default.Person, "锻炼部位（可多选）")
                    Spacer(Modifier.height(Dimens.space4))
                    TagSelector(options = ui.parts, selected = ui.selectedParts, onToggle = { vm.onTogglePart(it) })
                }
            }
            item {
                Text(
                    text = ui.previewKcal?.let { "预计消耗  $it kcal" } ?: "完善体重后可计算消耗",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                ui.formula?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { nav.navigate(Routes.BODY) }) {
                    Text(if (ui.weightMissing) "去填写体重" else "修改身体参数")
                }
            }
            item {
                OutlinedTextField(
                    value = ui.note,
                    onValueChange = { vm.onNote(it) },
                    label = { Text("备注") },
                    placeholder = { Text("重量、感受…") },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    isError = ui.errors.any { it.contains("备注") },
                    supportingText = ui.errors.firstOrNull { it.contains("备注") }?.let { { Text(it) } },
                    colors = stableOutlinedTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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

/** 顶栏：返回、标题、保存同一行，避免保存按钮沉到页面底部。 */
@Composable
private fun EditorTopBar(
    title: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        backArrow(onBack)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Button(onClick = onSave, enabled = canSave) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(Dimens.space4))
            Text("保存")
        }
    }
}

/** 日期一行排完：前一天、日期、今天、后一天。 */
@Composable
private fun DateStepCard(date: LocalDate, onDate: (LocalDate) -> Unit) {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        IconButton(onClick = { onDate(date.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Text(
            "${date.monthValue}月${date.dayOfMonth}日 $weekday",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onDate(LocalDate.now()) }) { Text("今天") }
        IconButton(onClick = { onDate(date.plusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
        }
    }
}

/** 力量类：组数和每组个数并排，默认 5 组、每组 20 个。 */
@Composable
private fun SetRepEditor(
    setCount: Int,
    repsPerSet: Int,
    onSetCount: (Int) -> Unit,
    onRepsPerSet: (Int) -> Unit,
) {
    Text(
        "例如 5 组 × 20 个",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CountStepper("组数", setCount, onSetCount, EffortFormat.MAX_SET_COUNT)
        CountStepper("每组个数", repsPerSet, onRepsPerSet, EffortFormat.MAX_REPS_PER_SET)
    }
}

/** 整数步进。减到 1 为止，加到上限为止。 */
@Composable
private fun RowScope.CountStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    max: Int,
) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }) { Text("−") }
            Text("$value", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("＋") }
        }
    }
}

/** 运动方式横滑选择。选中项用类型色描边和浅底，并带上对应运动图标。 */
@Composable
private fun ExerciseTypeCard(
    types: List<ExerciseTypeEntity>,
    selectedTypeId: Long?,
    error: String?,
    onSelect: (Long) -> Unit,
) {
    AppCard {
        SectionTitle(Icons.Default.Star, "运动方式")
        Spacer(Modifier.height(Dimens.space4))
        LazyRow(
            modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            items(types, key = { it.id }) { type ->
                ExerciseTypeChip(
                    name = type.name,
                    color = type.color.toColor(),
                    selected = type.id == selectedTypeId,
                    onClick = { onSelect(type.id) },
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(Dimens.space4))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** 单个运动方式。未收录的自定义类型用奖牌占位，避免空白块。 */
@Composable
private fun ExerciseTypeChip(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.cardCorner)
    val emoji = ExerciseIcons.emoji(name) ?: "🏅"
    Column(
        modifier = Modifier
            .width(Dimens.typeChipWidth)
            .clip(shape)
            .border(
                width = if (selected) Dimens.space2 else Dimens.strokeWidth,
                color = if (selected) color else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .background(if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.space4, horizontal = Dimens.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Dimens.space2))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

