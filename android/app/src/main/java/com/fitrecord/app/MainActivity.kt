package com.fitrecord.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fitrecord.app.data.prefs.AppSettings
import com.fitrecord.app.ui.FitRecordRoot
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

        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                // 只订阅 snapshot()：主题与周起始日必须原子更新，否则会出现
                // "主题已切、周视图首日还没切"的中间帧（复审 I-1 附带结论）
                val fallback = AppSettings(ThemeMode.SYSTEM, DayOfWeek.MONDAY, null)
                val settings by container.settings.snapshot()
                    .onStart { emit(fallback) }
                    .collectAsState(fallback)

                FitRecordTheme(themeMode = settings.themeMode) {
                    FitRecordRoot()
                }
            }
        }
    }
}
