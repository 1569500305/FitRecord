package com.fitrecord.app.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.fitrecord.app.AppContainer
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.ui.theme.Dimens
import java.io.File
import kotlin.system.exitProcess

/** 重建/导入跳转的一次性标记（普通 SharedPreferences，与 DataStore 解耦，重启后由 MainActivity 消费并清除） */
const val PREF_RECOVERY = "fitrecord_recovery"
const val KEY_OPEN_BACKUP = "open_backup_after_recreate"

/**
 * 数据库彻底损坏（SQLITE_NOTADB / malformed，规格 6.10）时的整屏恢复页。
 *
 * 此时 NavHost 一查询就会崩，故不套路由，直接由 MainActivity 顶替整个内容。
 * 两条出路都先把坏库文件删掉再**真正冷启动进程**：
 *  - 重建空库：重启后走正常冷启动（空库 + 种子），落在日历页；
 *  - 从备份导入：额外置一次性标记，重启后直接落在备份页，用户立即导入 JSON/CSV。
 */
@Composable
fun DbUnrecoverableScreen(reason: String?) {
    val context = LocalContext.current
    val container = LocalContainer.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("数据库无法打开", style = MaterialTheme.typography.headlineSmall)
            Text(
                "本机训练数据文件已损坏，App 已停止读写以避免进一步破坏。" +
                    "你可以重建一个空库继续使用，或重建后从之前导出的备份文件恢复数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = !busy,
                onClick = { busy = true; context.recreateFromCrash(container, openBackup = false) { error = it; busy = false } },
            ) { Text("重建空库") }
            OutlinedButton(
                enabled = !busy,
                onClick = { busy = true; context.recreateFromCrash(container, openBackup = true) { error = it; busy = false } },
            ) { Text("重建后从备份导入") }
        }
    }
}

/**
 * 删库 + 置标记 + 冷重启。清理失败则回调错误、**不重启**（评审 Critical-1：
 * 不能带着坏库进入正常首页，否则用户既丢数据又回不到恢复页）。
 */
private fun Context.recreateFromCrash(
    container: AppContainer,
    openBackup: Boolean,
    onError: (String) -> Unit,
) {
    // 尽力关闭当前（可能损坏的）Room 句柄，释放文件描述符
    runCatching { container.database.close() }
    if (!purgeDatabase(this)) {
        onError("清理损坏文件失败，请重试，或手动卸载后重装")
        return
    }
    getSharedPreferences(PREF_RECOVERY, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_OPEN_BACKUP, openBackup).apply()
    val intent = packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    intent?.let { startActivity(it) }
    // finishAffinity 只结束 Activity，进程与挂在 Application 上的容器（含坏库句柄）会被复用 ——
    // 必须真正杀进程走冷启动，让新进程重新建库 + 跑种子；降级态复位交给新进程，不在此手动 reset。
    (this as? Activity)?.finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
    exitProcess(1)
}

/**
 * 删除主库文件及其 WAL / -shm / -journal 侧文件（Room 默认 WAL，只删主文件会残留旧 WAL，
 * 新建同名库时 SQLite 对"新库 + 旧 WAL"行为不确定 —— 正是"重建后仍打不开"的经典成因）。
 * 文件本就不存在视为已清理；任一存在却删不掉则返回 false。[FatalStateTest] 守护这条真实路径。
 */
internal fun purgeDatabase(context: Context): Boolean {
    val base = context.getDatabasePath(FitRecordDatabase.DB_NAME).path
    return listOf(base, "$base-wal", "$base-shm", "$base-journal")
        .map { File(it) }
        .all { !it.exists() || it.delete() }
}
