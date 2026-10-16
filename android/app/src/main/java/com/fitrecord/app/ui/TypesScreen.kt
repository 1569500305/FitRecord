package com.fitrecord.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.fitrecord.app.AppContainer
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.db.seed.MetReference
import com.fitrecord.app.ui.components.AppCard
import com.fitrecord.app.ui.components.ConfirmDialog
import com.fitrecord.app.ui.components.TagSelector
import com.fitrecord.app.ui.components.stableOutlinedTextFieldColors
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.ExercisePalette
import com.fitrecord.app.ui.theme.toColor
import com.fitrecord.app.ui.theme.toRgbLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 编辑器状态。editingId == null 表示编辑器关闭，-1 表示新增。 */
data class TypeDraft(
    val editingId: Long? = null,
    val name: String = "",
    val met: String = "",
    val colorIndex: Int = 0,
    val parts: Set<Long> = emptySet(),
    val showMetSheet: Boolean = false,
)

data class TypeRow(
    val entity: ExerciseTypeEntity,
    val paletteIndex: Int,
)

data class TypesUi(
    val rows: List<TypeRow>,
    val draft: TypeDraft,
    val partOptions: List<Pair<Long, String>>,
    val errors: List<String>,
    /** 数据库尚未返回前为 false，避免空列表把添加按钮顶到标题下面。 */
    val isReady: Boolean = false,
)

/**
 * P7 列表 + P8 编辑 + P8b MET 参考表。
 *
 * 删除策略（规格 6.4）：被记录引用的类型**不得删除**，只能归档。
 * 用量在点击删除时即时查一次，不在列表里做 N+1。
 */
class TypesViewModel(private val container: AppContainer) : ViewModel() {

    private val draft = MutableStateFlow(TypeDraft())

    val uiState: StateFlow<TypesUi> = combine(
        draft, container.typeDao.observeAll(), container.bodyPartDao.observeAll(),
    ) { d, types, parts ->
        TypesUi(
            rows = types.map { TypeRow(it, paletteIndexOf(it.color)) },
            draft = d,
            partOptions = parts.map { it.id to it.name },
            errors = validate(d, types),
            isReady = true,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        TypesUi(emptyList(), TypeDraft(), emptyList(), emptyList()),
    )

    private fun paletteIndexOf(colorLong: Long): Int {
        val index = ExercisePalette.colors.indexOfFirst { it.toRgbLong() == colorLong }
        return if (index >= 0) index else 0
    }

    private fun validate(d: TypeDraft, all: List<ExerciseTypeEntity>): List<String> = buildList {
        if (d.editingId == null) return@buildList
        val trimmed = d.name.trim()
        val original = all.find { it.id == d.editingId }
        if (trimmed.isEmpty()) add("名称不能为空")
        else if (trimmed.length > 10) add("名称最多 10 个字")
        else if (trimmed != original?.name?.trim() && all.any { it.name.trim() == trimmed }) add("该名称已存在")
        val met = d.met.toDoubleOrNull()
        if (met == null) add("MET 必填，可点下方查参考表")
        else if (met !in MIN_MET..MAX_MET) add("MET 需在 $MIN_MET–$MAX_MET")
    }

    fun openNew() = viewModelScope.launch {
        draft.value = TypeDraft(editingId = NEW, colorIndex = ExercisePalette.colors.size - 1)
    }

    fun openEdit(entity: ExerciseTypeEntity) = viewModelScope.launch {
        draft.value = TypeDraft(
            editingId = entity.id,
            name = entity.name,
            met = trimZero(entity.met),
            colorIndex = paletteIndexOf(entity.color),
            parts = BodyParts.parseIds(entity.defaultBodyPartIds).toSet(),
        )
    }

    fun close() = viewModelScope.launch { draft.value = TypeDraft() }

    fun onName(value: String) = viewModelScope.launch { draft.update { it.copy(name = value.take(12)) } }

    fun onMet(value: String) = viewModelScope.launch {
        draft.update { it.copy(met = value.onlyDecimal().take(5)) }
    }

    fun onColor(index: Int) = viewModelScope.launch { draft.update { it.copy(colorIndex = index) } }

    fun onTogglePart(id: Long) = viewModelScope.launch {
        draft.update { it.copy(parts = if (id in it.parts) it.parts - id else it.parts + id) }
    }

    fun showMetSheet() = viewModelScope.launch { draft.update { it.copy(showMetSheet = true) } }
    fun hideMetSheet() = viewModelScope.launch { draft.update { it.copy(showMetSheet = false) } }

    /** 点参考表某一行：回填 MET，名称为空时顺带填上（用户常在改内置项时只想换个 MET） */
    fun pickMet(met: Double, refName: String) = viewModelScope.launch {
        draft.update {
            it.copy(
                met = trimZero(met),
                name = if (it.name.isBlank()) refName.substringBefore(' ').take(10) else it.name,
                showMetSheet = false,
            )
        }
    }

    fun save() = viewModelScope.launch {
        val current = uiState.value
        val d = current.draft
        if (d.editingId == null || current.errors.isNotEmpty()) return@launch
        val met = d.met.toDoubleOrNull() ?: return@launch
        val color = ExercisePalette.colors[d.colorIndex % ExercisePalette.colors.size].toRgbLong()
        val partsCsv = d.parts.sorted().joinToString(",")

        if (d.editingId == NEW) {
            val newId = container.typeDao.insert(
                ExerciseTypeEntity(
                    name = d.name.trim(), met = met, color = color, isBuiltin = false,
                    sortOrder = 0, defaultBodyPartIds = partsCsv,
                )
            )
            val activeIds = current.rows.filter { !it.entity.archived }.map { it.entity.id } + newId
            val archivedIds = current.rows.filter { it.entity.archived }.map { it.entity.id }
            container.typeDao.reorder(activeIds + archivedIds)
        } else {
            val existing = container.typeDao.byId(d.editingId) ?: return@launch
            container.typeDao.update(
                existing.copy(
                    name = d.name.trim(),
                    met = met,
                    color = color,
                    // 内置类型被改过 MET 要打标，P7 才显示"内置（已改）"并可恢复默认
                    metOverridden = existing.isBuiltin && met != existing.met,
                    defaultBodyPartIds = partsCsv,
                )
            )
        }
        close()
    }

    /** 恢复内置默认 MET */
    fun resetMet(entity: ExerciseTypeEntity) = viewModelScope.launch {
        val default = com.fitrecord.app.data.db.seed.BuiltinExercises.defaultMet(entity.name) ?: return@launch
        container.typeDao.update(entity.copy(met = default, metOverridden = false))
    }

    fun archive(entity: ExerciseTypeEntity) = viewModelScope.launch {
        val rows = uiState.value.rows
        val activeIds = rows.filter { !it.entity.archived && it.entity.id != entity.id }.map { it.entity.id }
        val archivedIds = rows.filter { it.entity.archived }.map { it.entity.id } + entity.id
        container.typeDao.archive(entity.id)
        container.typeDao.reorder(activeIds + archivedIds)
    }

    /** 还原后排到使用中的末尾，不插回归档前的位置。 */
    fun unarchive(entity: ExerciseTypeEntity) = viewModelScope.launch {
        val rows = uiState.value.rows
        val activeIds = rows.filter { !it.entity.archived }.map { it.entity.id } + entity.id
        val archivedIds = rows.filter { it.entity.archived && it.entity.id != entity.id }.map { it.entity.id }
        container.typeDao.unarchive(entity.id)
        container.typeDao.reorder(activeIds + archivedIds)
    }

    /**
     * 拖动只发生在「使用中」这一段。
     * 写回时使用中在前、已归档在后，选择训练的顺序与这段一致。
     */
    fun commitOrder(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        val rows = uiState.value.rows
        val activeIds = rows.filter { !it.entity.archived }.map { it.entity.id }.toMutableList()
        if (fromIndex == toIndex) return@launch
        if (fromIndex !in activeIds.indices || toIndex !in activeIds.indices) return@launch
        val moved = activeIds.removeAt(fromIndex)
        activeIds.add(toIndex, moved)
        val archivedIds = rows.filter { it.entity.archived }.map { it.entity.id }
        container.typeDao.reorder(activeIds + archivedIds)
    }

    /** 返回 false 表示仍被记录引用，调用方应提示改走归档 */
    suspend fun delete(entity: ExerciseTypeEntity): Boolean {
        if (entity.isBuiltin) return false
        if (container.typeDao.usageCount(entity.id) > 0) return false
        container.typeDao.delete(entity)
        return true
    }

    private fun String.onlyDecimal(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        return if (dot < 0) filtered else filtered.take(dot + 1) + filtered.substring(dot + 1).replace(".", "")
    }

    private fun trimZero(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object {
        private const val NEW = -1L
        private const val MIN_MET = 0.5
        private const val MAX_MET = 20.0

        fun factory(c: AppContainer) = factoryOf { TypesViewModel(c) }
    }
}

@Composable
fun TypeListScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val vm: TypesViewModel = viewModel(factory = TypesViewModel.factory(container))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<ExerciseTypeEntity?>(null) }
    var deleteBlocked by remember { mutableStateOf<String?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOrigin by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableMapOf<Long, Float>() }
    val listGap = Dimens.cardGap / 2
    val gapPx = with(LocalDensity.current) { listGap.toPx() }
    val activeRows = ui.rows.filter { !it.entity.archived }
    val archivedRows = ui.rows.filter { it.entity.archived }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(listGap),
        ) {
            item { ScreenHeaderPublic("运动类型") { nav.popBackStack() } }
            item {
                Text(
                    "使用中的类型可拖动排序；已归档的在最下面，不出现在记录选择里。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.isReady) {
                item { ExerciseTypeOrderPreview(activeRows.map { it.entity }) }
                item { TypeSectionTitle("使用中") }
                itemsIndexed(activeRows, key = { _, row -> row.entity.id }) { index, row ->
                    val slot = (rowHeights[draggingId] ?: 1f) + gapPx
                    val lastIndex = activeRows.lastIndex
                    val target = if (draggingId == null || dragOrigin < 0 || slot <= 1f) {
                        dragOrigin
                    } else {
                        (dragOrigin + (dragOffset / slot).roundToInt()).coerceIn(0, lastIndex)
                    }
                    val shift = neighborShift(index, dragOrigin, target, draggingId, slot, dragOffset)
                    TypeSortRow(
                        row = row,
                        shiftY = shift,
                        lifted = draggingId == row.entity.id,
                        canDrag = true,
                        onMeasured = { rowHeights[row.entity.id] = it },
                        onDragStart = {
                            draggingId = row.entity.id
                            dragOrigin = index
                            dragOffset = 0f
                        },
                        onDrag = { delta -> dragOffset += delta },
                        onDragEnd = {
                            val from = dragOrigin
                            val height = (rowHeights[row.entity.id] ?: 1f) + gapPx
                            val to = if (from < 0 || height <= 1f) from
                            else (from + (dragOffset / height).roundToInt()).coerceIn(0, lastIndex)
                            draggingId = null
                            dragOffset = 0f
                            dragOrigin = -1
                            if (from >= 0 && to >= 0) vm.commitOrder(from, to)
                        },
                        onDragCancel = {
                            draggingId = null
                            dragOffset = 0f
                            dragOrigin = -1
                        },
                        onEdit = { vm.openEdit(row.entity) },
                        onArchive = { vm.archive(row.entity) },
                        onUnarchive = { vm.unarchive(row.entity) },
                        onResetMet = { vm.resetMet(row.entity) },
                        onDelete = { pendingDelete = row.entity },
                    )
                }
                if (archivedRows.isNotEmpty()) {
                    item { TypeSectionTitle("已归档") }
                    items(archivedRows, key = { it.entity.id }) { row ->
                        TypeSortRow(
                            row = row,
                            shiftY = 0f,
                            lifted = false,
                            canDrag = false,
                            onMeasured = {},
                            onDragStart = {},
                            onDrag = {},
                            onDragEnd = {},
                            onDragCancel = {},
                            onEdit = { vm.openEdit(row.entity) },
                            onArchive = { vm.archive(row.entity) },
                            onUnarchive = { vm.unarchive(row.entity) },
                            onResetMet = { vm.resetMet(row.entity) },
                            onDelete = { pendingDelete = row.entity },
                        )
                    }
                }
                item {
                    Button(onClick = { vm.openNew() }, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ 添加自定义类型")
                    }
                }
            }
        }
    }

    if (ui.draft.editingId != null) {
        TypeEditorDialog(vm = vm, ui = ui)
    }

    // 参考表放在屏一级而不是嵌在编辑器弹窗里：嵌套 Dialog 在部分机型上抢不到焦点
    if (ui.draft.showMetSheet) {
        MetReferenceSheet(
            onDismiss = { vm.hideMetSheet() },
            onPick = { met, name -> vm.pickMet(met, name) },
        )
    }

    pendingDelete?.let { entity ->
        ConfirmDialog(
            title = "删除「${entity.name}」？",
            message = "只有没有任何记录引用的类型才能删除。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                scope.launch {
                    if (!vm.delete(entity)) deleteBlocked = "该类型已有训练记录，请改用「归档」"
                    pendingDelete = null
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    deleteBlocked?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteBlocked = null },
            title = { Text("无法删除") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deleteBlocked = null }) { Text("知道了") } },
        )
    }
}

/** 拖动中，被挤开的那几行要让出一格；被拖的那行跟着手指走。 */
private fun neighborShift(
    index: Int,
    origin: Int,
    target: Int,
    draggingId: Long?,
    slot: Float,
    dragOffset: Float,
): Float {
    if (draggingId == null || origin < 0) return 0f
    if (index == origin) return dragOffset
    if (origin < target && index in (origin + 1)..target) return -slot
    if (origin > target && index in target until origin) return slot
    return 0f
}

/** 类型列表行相对原来的高度比例。 */
private const val TYPE_ROW_SCALE = 0.75f

/** 已归档的名称和颜色圆点透明度。 */
private const val ARCHIVED_MARK_ALPHA = 0.45f

/** 把字号和行高按列表行比例缩小。 */
private fun TextStyle.atRowScale(): TextStyle = copy(
    fontSize = fontSize * TYPE_ROW_SCALE,
    lineHeight = lineHeight * TYPE_ROW_SCALE,
)

/** 使用中 / 已归档 分段标题。 */
@Composable
private fun TypeSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 类型列表的一行。使用中的可拖动；已归档的去掉手柄并降低名称和圆点。 */
@Composable
private fun TypeSortRow(
    row: TypeRow,
    shiftY: Float,
    lifted: Boolean,
    canDrag: Boolean,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onResetMet: () -> Unit,
    onDelete: () -> Unit,
) {
    val entity = row.entity
    val dragStartNow = rememberUpdatedState(onDragStart)
    val dragNow = rememberUpdatedState(onDrag)
    val dragEndNow = rememberUpdatedState(onDragEnd)
    val dragCancelNow = rememberUpdatedState(onDragCancel)
    val titleStyle = MaterialTheme.typography.titleLarge.atRowScale()
    val captionStyle = MaterialTheme.typography.labelSmall.atRowScale()
    val markAlpha = if (entity.archived) ARCHIVED_MARK_ALPHA else 1f
    Row(
        modifier = Modifier
            .zIndex(if (lifted) 1f else 0f)
            .onGloballyPositioned { onMeasured(it.size.height.toFloat()) }
            .graphicsLayer { translationY = shiftY },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canDrag) {
            Text(
                text = "≡",
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .pointerInput(entity.id) {
                        detectDragGestures(
                            onDragStart = { dragStartNow.value() },
                            onDragEnd = { dragEndNow.value() },
                            onDragCancel = { dragCancelNow.value() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragNow.value(amount.y)
                            },
                        )
                    }
                    .padding(end = Dimens.space8),
            )
        }
        AppCard(
            onClick = onEdit,
            modifier = Modifier.weight(1f),
            contentPadding = Dimens.cardPadding * TYPE_ROW_SCALE,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = entity.color.toColor().copy(alpha = markAlpha), style = titleStyle)
                Spacer(Modifier.padding(horizontal = Dimens.space4))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entity.name,
                            style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = markAlpha),
                        )
                        if (entity.archived) {
                            Spacer(Modifier.padding(horizontal = Dimens.space4))
                            ArchivedBadge()
                        }
                    }
                    Text(
                        typeKindLabel(entity),
                        style = captionStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = markAlpha),
                    )
                }
                TypeRowActions(entity, onArchive, onUnarchive, onResetMet, onDelete)
            }
        }
    }
}

/** 已归档标签，和 MET 说明分开，避免只靠同一行灰字区分。 */
@Composable
private fun ArchivedBadge() {
    Text(
        "已归档",
        style = MaterialTheme.typography.labelSmall.atRowScale(),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Dimens.chipCorner))
            .padding(horizontal = Dimens.space8, vertical = Dimens.space2),
    )
}

/** MET 与内置/自定义说明。归档状态不写在这句里。 */
private fun typeKindLabel(entity: ExerciseTypeEntity): String {
    val kind = when {
        entity.isBuiltin && entity.metOverridden -> "内置（已改）"
        entity.isBuiltin -> "内置"
        else -> "自定义"
    }
    return "MET ${entity.met} · $kind"
}

/** 行内操作。高度按 3/4 缩放，不用系统最小点击高度把整行撑回去。 */
@Composable
private fun TypeRowActions(
    entity: ExerciseTypeEntity,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onResetMet: () -> Unit,
    onDelete: () -> Unit,
) {
    if (entity.archived) {
        CompactAction("还原", MaterialTheme.colorScheme.primary, onUnarchive)
    } else {
        CompactAction("归档", MaterialTheme.colorScheme.primary, onArchive)
    }
    if (entity.isBuiltin && entity.metOverridden) {
        CompactAction("恢复默认", MaterialTheme.colorScheme.primary, onResetMet)
    }
    if (!entity.isBuiltin) {
        CompactAction("删除", MaterialTheme.colorScheme.error, onDelete)
    }
}

/** 类型行里的文字按钮，高度为标准点击高度的 3/4。 */
@Composable
private fun CompactAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge.atRowScale(),
        modifier = Modifier
            .height(Dimens.minTouchTarget * TYPE_ROW_SCALE)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space8)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun TypeEditorDialog(vm: TypesViewModel, ui: TypesUi) {
    AlertDialog(
        onDismissRequest = { vm.close() },
        title = { Text(if (ui.draft.editingId == -1L) "新增运动类型" else "编辑运动类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space8)) {
                OutlinedTextField(
                    value = ui.draft.name, onValueChange = { vm.onName(it) },
                    label = { Text("名称（≤10 字）") }, singleLine = true,
                    colors = stableOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = ui.draft.met, onValueChange = { vm.onMet(it) },
                    label = { Text("MET 值（0.5–20）") }, singleLine = true,
                    isError = ui.errors.any { it.contains("MET") },
                    colors = stableOutlinedTextFieldColors(),
                )
                TextButton(onClick = { vm.showMetSheet() }) { Text("📖 查常见运动 MET 参考表") }
                Text("颜色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space4)) {
                    ExercisePalette.colors.forEachIndexed { index, color ->
                        Text(
                            "●",
                            color = color,
                            modifier = Modifier
                                .padding(Dimens.space4)
                                .border(
                                    width = if (index == ui.draft.colorIndex) 2.dp else 0.dp,
                                    color = if (index == ui.draft.colorIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .clickable { vm.onColor(index) }
                                .padding(Dimens.space4),
                        )
                    }
                }
                Text("默认部位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TagSelector(options = ui.partOptions, selected = ui.draft.parts, onToggle = { vm.onTogglePart(it) })
                ui.errors.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.save() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { vm.close() }) { Text("取消") } },
    )
}

/** MET 参考表（P8b）：按强度分组，点一行回填 */
@Composable
fun MetReferenceSheet(onDismiss: () -> Unit, onPick: (Double, String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("常见运动 MET 参考") },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                MetReference.groupByIntensity().forEach { (intensity, refs) ->
                    item {
                        Text(
                            intensity,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(refs) { ref ->
                        TextButton(onClick = { onPick(ref.met, ref.name) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${ref.name}    MET ${ref.met}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
