package com.fitrecord.app.ui

import android.content.ContentResolver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.navigation.NavHostController
import com.fitrecord.app.AppContainer
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.data.repo.BackupRepository
import com.fitrecord.app.domain.backup.ConflictPolicy
import com.fitrecord.app.ui.components.AppCard
import com.fitrecord.app.ui.components.ConfirmDialog
import com.fitrecord.app.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** P9 备份页的 UI 状态机。每一步都显式建模，避免"点了没反应"的静默失败（规格 6.5）。 */
sealed interface BackupUi {
    data object Idle : BackupUi
    data class Working(val step: String) : BackupUi
    data class Previewing(val preview: BackupRepository.MergePreview) : BackupUi
    data class Done(val message: String) : BackupUi
    data class Failed(val reason: String) : BackupUi
}

/**
 * 备份页 ViewModel。
 *
 * 刻意**不持 Uri / ContentResolver**：SAF 启动器与内容解析留在 Composable 层，
 * 本类只接收已打开的流，于是 Robolectric 单测能直接喂内存字节（ByteArray）而不触真 SAF。
 */
class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val uiState: StateFlow<BackupUi> = _ui.asStateFlow()

    private val _policy = MutableStateFlow(ConflictPolicy.SKIP)
    val policy: StateFlow<ConflictPolicy> = _policy.asStateFlow()

    /** 预览时把整份文件读进内存缓存，确认导入时复用 —— InputStream 不可重读 */
    private var pendingBytes: ByteArray? = null

    fun exportJson(resolver: ContentResolver, uri: Uri) =
        runExport("JSON 完整备份", { stream -> container.backupRepo.exportJson(stream) }, resolver, uri)

    fun exportCsv(resolver: ContentResolver, uri: Uri) =
        runExport("CSV 记录表", { stream -> container.backupRepo.exportCsv(stream) }, resolver, uri)

    private fun runExport(
        label: String,
        block: suspend (java.io.OutputStream) -> Int,
        resolver: ContentResolver,
        uri: Uri,
    ) = viewModelScope.launch {
        _ui.value = BackupUi.Working("正在导出 $label…")
        val result = runCatching {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { block(it) } ?: error("无法写入所选位置（可能未授予写权限）")
            }
        }
        result.onSuccess { _ui.value = BackupUi.Done("已导出 $it 条训练记录到 $label") }
            .onFailure { _ui.value = BackupUi.Failed(it.readableMessage()) }
    }

    fun preview(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _ui.value = BackupUi.Working("正在读取备份…")
        val bytes = runCatching {
            withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取所选文件")
            }
        }
        bytes.onFailure { pendingBytes = null; _ui.value = BackupUi.Failed(it.readableMessage()); return@launch }
        pendingBytes = bytes.getOrThrow()
        runPreview()
    }

    private suspend fun runPreview() {
        val data = pendingBytes ?: return
        val result = runCatching {
            withContext(Dispatchers.IO) {
                container.backupRepo.preview(ByteArrayInputStream(data), _policy.value)
            }
        }
        result.onSuccess { _ui.value = BackupUi.Previewing(it) }
            .onFailure { _ui.value = BackupUi.Failed(it.readableMessage()) }
    }

    fun setPolicy(skip: Boolean) = viewModelScope.launch {
        _policy.value = if (skip) ConflictPolicy.SKIP else ConflictPolicy.OVERWRITE
        if (pendingBytes != null && _ui.value is BackupUi.Previewing) runPreview()
    }

    fun apply() = viewModelScope.launch {
        val data = pendingBytes ?: return@launch
        _ui.value = BackupUi.Working("正在导入…")
        val result = runCatching {
            withContext(Dispatchers.IO) {
                container.backupRepo.apply(ByteArrayInputStream(data), _policy.value)
            }
        }
        result.onSuccess {
            pendingBytes = null
            _ui.value = BackupUi.Done("导入完成：新增 ${it.inserted} · 更新 ${it.updated} · 跳过 ${it.skipped}")
        }.onFailure {
            pendingBytes = null
            _ui.value = BackupUi.Failed(it.readableMessage())
        }
    }

    fun reset() = viewModelScope.launch { pendingBytes = null; _ui.value = BackupUi.Idle }

    companion object {
        fun suggestedName(ext: String): String = BackupRepository.suggestedName(ext)
        fun factory(c: AppContainer) = factoryOf { BackupViewModel(c) }
    }
}

private fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "未知错误（${this::class.java.simpleName}）"

// ================================================================ P9 界面

@Composable
fun BackupScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val resolver = context.contentResolver
    val vm: BackupViewModel = viewModel(factory = BackupViewModel.factory(container))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    var confirmOverwrite by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.exportJson(resolver, it) } }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { vm.exportCsv(resolver, it) } }

    // 用户取消选择时 uri == null → 什么都不做，不弹错误（规格 6.7）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.preview(resolver, it) } }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            ScreenHeaderPublic("数据备份与恢复") { nav.popBackStack() }
            Text(
                "所有文件都在本机生成与读取，不上传、不联网。导出的 JSON 可用文本编辑器查看。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { exportJsonLauncher.launch(BackupViewModel.suggestedName("json")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出 JSON 完整备份") }
            Button(
                onClick = { exportCsvLauncher.launch(BackupViewModel.suggestedName("csv")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出 CSV 记录表") }
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/comma-separated-values", "text/csv", "application/octet-stream"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("选择文件导入") }

            when (ui) {
                is BackupUi.Working -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text((ui as BackupUi.Working).step, style = MaterialTheme.typography.labelSmall)
                }

                is BackupUi.Failed -> AppCard {
                    Text(
                        (ui as BackupUi.Failed).reason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is BackupUi.Done -> AppCard {
                    Text((ui as BackupUi.Done).message, style = MaterialTheme.typography.bodyMedium)
                }

                is BackupUi.Previewing -> {
                    val preview = (ui as BackupUi.Previewing).preview
                    AppCard {
                        Text("导入预览", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(Dimens.space8))
                        Text("可导入 ${preview.total} 条 · 跳过重复 ${preview.skipped} 条")
                        Text("其中新增 ${preview.toInsert} 条 · 覆盖 ${preview.toUpdate} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        preview.errors.take(5).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (preview.errors.size > 5) {
                            Text("…另有 ${preview.errors.size - 5} 条异常",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(Dimens.space8))
                        Text("冲突处理方式", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = policy == ConflictPolicy.SKIP, onClick = { vm.setPolicy(true) })
                            Text("跳过已存在的记录")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = policy == ConflictPolicy.OVERWRITE, onClick = { vm.setPolicy(false) })
                            Text("覆盖同名记录")
                        }
                        Spacer(Modifier.height(Dimens.space8))
                        Button(
                            onClick = { if (policy == ConflictPolicy.OVERWRITE) confirmOverwrite = true else vm.apply() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("确认导入") }
                        OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                    }
                }

                BackupUi.Idle -> Unit
            }
        }
    }

    if (confirmOverwrite) {
        ConfirmDialog(
            title = "覆盖已有记录？",
            message = "将用备份中的记录覆盖本机同键记录，此操作不可撤销。",
            confirmLabel = "确认覆盖",
            destructive = true,
            onConfirm = { confirmOverwrite = false; vm.apply() },
            onDismiss = { confirmOverwrite = false },
        )
    }
}
