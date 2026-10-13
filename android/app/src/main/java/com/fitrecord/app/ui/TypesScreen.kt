package com.fitrecord.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            container.typeDao.insert(
                ExerciseTypeEntity(
                    name = d.name.trim(), met = met, color = color, isBuiltin = false,
                    sortOrder = 100 + current.rows.size, defaultBodyPartIds = partsCsv,
                )
            )
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
        container.typeDao.archive(entity.id)
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

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            item { ScreenHeaderPublic("运动类型") { nav.popBackStack() } }
            item {
                Text(
                    "内置类型不可删除；已有记录引用的类型只能归档，历史不会丢。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ui.rows, key = { it.entity.id }) { row ->
                val entity = row.entity
                AppCard(onClick = { vm.openEdit(entity) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = entity.color.toColor(), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.padding(horizontal = Dimens.space4))
                        Column(Modifier.weight(1f)) {
                            Text(entity.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                buildString {
                                    append("MET ${entity.met}")
                                    append(" · ")
                                    append(
                                        when {
                                            entity.isBuiltin && entity.metOverridden -> "内置（已改）"
                                            entity.isBuiltin -> "内置"
                                            entity.archived -> "已归档"
                                            else -> "自定义"
                                        }
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!entity.archived) {
                            TextButton(onClick = { vm.archive(entity) }) { Text("归档") }
                        }
                        if (entity.isBuiltin && entity.metOverridden) {
                            TextButton(onClick = { vm.resetMet(entity) }) { Text("还原") }
                        }
                        if (!entity.isBuiltin) {
                            TextButton(onClick = { pendingDelete = entity }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { vm.openNew() }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ 添加自定义类型")
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
                )
                OutlinedTextField(
                    value = ui.draft.met, onValueChange = { vm.onMet(it) },
                    label = { Text("MET 值（0.5–20）") }, singleLine = true,
                    isError = ui.errors.any { it.contains("MET") },
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
                                    color = if (index == ui.draft.colorIndex) Color.Black else Color.Transparent,
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
