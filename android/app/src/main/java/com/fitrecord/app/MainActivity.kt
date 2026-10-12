package com.fitrecord.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fitrecord.app.ui.theme.FitRecordTheme
import com.fitrecord.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 在 Android 15 上强制无边框；显式启用并由各屏 Scaffold 消费 insets
        // （台账 P16）。主题模式在 Task 24 由 SettingsStore 驱动，此处先用 SYSTEM。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FitRecordTheme(themeMode = ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("FitRecord skeleton", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
