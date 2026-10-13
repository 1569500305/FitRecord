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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.ui.theme.Dimens

/** 重建/导入跳转的一次性标记（普通 SharedPreferences，与 DataStore 解耦，重启后由 MainActivity 消费并清除） */
const val PREF_RECOVERY = "fitrecord_recovery"
const val KEY_OPEN_BACKUP = "open_backup_after_recreate"

/**
 * 数据库彻底损坏（SQLITE_NOTADB，规格 6.10）时的整屏恢复页。
 *
 * 此时 NavHost 一查询就会崩，故不套路由，直接由 MainActivity 顶替整个内容。
 * 两条出路都先把坏库文件删掉再重启进程：
 *  - 重建空库：重启后走正常冷启动（空库 + 种子），落在日历页；
 *  - 从备份导入：额外置一次性标记，重启后直接落在备份页，用户立即导入 JSON/CSV。
 * 删除 + 重启的落盘行为由 FatalStateTest 守护。
 */
@Composable
fun DbUnrecoverableScreen(reason: String?) {
    val context = LocalContext.current
    val container = LocalContainer.current

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
            Button(onClick = { context.recreateDatabase(openBackup = false, container) }) {
                Text("重建空库")
            }
            OutlinedButton(onClick = { context.recreateDatabase(openBackup = true, container) }) {
                Text("重建后从备份导入")
            }
        }
    }
}

private fun Context.recreateDatabase(openBackup: Boolean, container: com.fitrecord.app.AppContainer) {
    // 关掉当前库句柄无公开 API，删文件 + 杀进程重启即等价于全新建库（V1 不引 ProcessPhoenix）
    getDatabasePath(FitRecordDatabase.DB_NAME).delete()
    getSharedPreferences(PREF_RECOVERY, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_OPEN_BACKUP, openBackup).apply()
    // 复位降级状态，避免重启前的瞬时 UI 仍显示只读横幅
    container.resetFatalForRecreate()
    val intent = packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    intent?.let { startActivity(it) }
    // finishAffinity 是 Activity 方法；LocalContext 在 Composable 里就是宿主 Activity，安安全转型
    (this as? Activity)?.finishAffinity()
}
