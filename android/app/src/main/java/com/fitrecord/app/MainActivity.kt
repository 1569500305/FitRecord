package com.fitrecord.app

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.fitrecord.app.data.prefs.AppSettings
import com.fitrecord.app.ui.DbUnrecoverableScreen
import com.fitrecord.app.ui.FitRecordRoot
import com.fitrecord.app.ui.KEY_OPEN_BACKUP
import com.fitrecord.app.ui.PREF_RECOVERY
import com.fitrecord.app.ui.Routes
import com.fitrecord.app.ui.theme.AppColors
import com.fitrecord.app.ui.theme.FitRecordTheme
import com.fitrecord.app.ui.theme.ThemeMode
import com.fitrecord.app.ui.theme.applyFitRecordWindowTheme
import com.fitrecord.app.ui.theme.resolveDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as FitRecordApp).container
        val initialSettings = readInitialSettings(container)
        val dark = initialSettings.themeMode.resolveDark(isSystemNight())
        // Decor 安装前就切窗口主题。装完再改 drawable，切页和滑动仍会清出浅色底
        applyFitRecordWindowTheme(
            activity = this,
            dark = dark,
            background = if (dark) AppColors.dark.background else AppColors.light.background,
        )
        super.onCreate(savedInstanceState)

        // 一次性消费“重建后从备份导入”标记：命中则本次冷启动直接落在备份页，随后清除
        val recoveryPrefs = getSharedPreferences(PREF_RECOVERY, MODE_PRIVATE)
        val startAtBackup = recoveryPrefs.getBoolean(KEY_OPEN_BACKUP, false)
        if (startAtBackup) recoveryPrefs.edit().remove(KEY_OPEN_BACKUP).apply()

        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                // 只订阅 snapshot()，并记住这条流。主题与周起始日必须原子更新。
                // 每次重组都新建流会重新收集，中间帧被写成浅色，切页时整页闪回浅色模式。
                val settingsFlow = remember { container.settings.snapshot() }
                val settings by settingsFlow.collectAsState(initialSettings)

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

    /** 当前配置是不是系统夜间模式。主题为「跟随系统」时用它决定窗口深浅。 */
    private fun isSystemNight(): Boolean {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }
}

private const val INITIAL_SETTINGS_TIMEOUT_MILLIS = 2_000L
private const val LOG_TAG = "MainActivity"

/**
 * 启动时同步读一次设置，用来在第一帧之前决定窗口主题。
 * 读失败或超时退回浅色：设置损坏不能挡住启动。
 */
private fun readInitialSettings(container: AppContainer): AppSettings {
    val fallback = AppSettings(ThemeMode.LIGHT, DayOfWeek.MONDAY, null)
    return try {
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(INITIAL_SETTINGS_TIMEOUT_MILLIS) {
                container.settings.snapshot().first()
            }
        } ?: fallback
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(LOG_TAG, "读取主题失败，窗口先用浅色", error)
        fallback
    }
}
