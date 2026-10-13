package com.fitrecord.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fitrecord.app.data.prefs.AppSettings
import com.fitrecord.app.ui.DbUnrecoverableScreen
import com.fitrecord.app.ui.FitRecordRoot
import com.fitrecord.app.ui.KEY_OPEN_BACKUP
import com.fitrecord.app.ui.PREF_RECOVERY
import com.fitrecord.app.ui.Routes
import com.fitrecord.app.ui.theme.FitRecordTheme
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.onStart
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 在 Android 15 上强制无边框；显式启用， insets 由各屏 Scaffold 消费（台账 P16）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as FitRecordApp).container

        // 一次性消费“重建后从备份导入”标记：命中则本次冷启动直接落在备份页，随后清除
        val recoveryPrefs = getSharedPreferences(PREF_RECOVERY, MODE_PRIVATE)
        val startAtBackup = recoveryPrefs.getBoolean(KEY_OPEN_BACKUP, false)
        if (startAtBackup) recoveryPrefs.edit().remove(KEY_OPEN_BACKUP).apply()

        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                // 只订阅 snapshot()：主题与周起始日必须原子更新，否则会出现
                // "主题已切、周视图首日还没切"的中间帧（复审 I-1 附带结论）
                val fallback = AppSettings(ThemeMode.SYSTEM, DayOfWeek.MONDAY, null)
                val settings by container.settings.snapshot()
                    .onStart { emit(fallback) }
                    .collectAsState(fallback)

                FitRecordTheme(themeMode = settings.themeMode) {
                    // 数据库彻底损坏（规格 6.10）：NavHost 一查即崩，整屏顶替为恢复页
                    val fatal by container.fatal.collectAsState()
                    val reason = (fatal as? AppContainer.FatalState.Corrupted)?.reason
                    if (reason != null) {
                        DbUnrecoverableScreen(reason)
                    } else {
                        FitRecordRoot(
                            startDestination = if (startAtBackup) Routes.BACKUP else Routes.CALENDAR,
                        )
                    }
                }
            }
        }
    }
}
