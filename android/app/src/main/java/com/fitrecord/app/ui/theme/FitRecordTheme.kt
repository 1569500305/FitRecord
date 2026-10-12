package com.fitrecord.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 规格 D8 / P10：三态主题 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 日历格"未健身"灰字。Material3 ColorScheme 没有对应槽位，
 * 用 CompositionLocal 传递，避免屏幕里写死字面量颜色（全局约束）。
 */
val LocalUnreadColor = staticCompositionLocalOf { Color(0xFFB6BABD) }

/** 禁用态颜色，同上 */
val LocalDisabledColor = staticCompositionLocalOf { Color(0xFFBFC3C6) }

/**
 * 应用主题。刻意关闭动态取色（Material You）：
 * 品牌绿/橙是日历状态与图表配色的依据，随机取色会让状态语义失效（规格 2.1）。
 */
@Composable
fun FitRecordTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) AppColors.dark else AppColors.light

    CompositionLocalProvider(
        LocalUnreadColor provides if (dark) AppColors.unreadDark else AppColors.unreadLight,
        LocalDisabledColor provides if (dark) AppColors.disabledDark else AppColors.disabledLight,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FitRecordTypography,
            shapes = FitRecordShapes,
            content = content,
        )
    }
}
