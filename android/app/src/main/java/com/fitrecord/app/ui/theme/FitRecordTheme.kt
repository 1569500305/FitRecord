package com.fitrecord.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/** 规格 D8 / P10：三态主题。未设置时默认浅色。 */
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
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) AppColors.dark else AppColors.light
    ApplyWindowChrome(dark = dark, background = colorScheme.background)

    CompositionLocalProvider(
        LocalUnreadColor provides if (dark) AppColors.unreadDark else AppColors.unreadLight,
        LocalDisabledColor provides if (dark) AppColors.disabledDark else AppColors.disabledLight,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FitRecordTypography,
            shapes = FitRecordShapes,
        ) {
            // 铺一层不透明底，避免输入法顶起窗口时透出系统默认黑/白底
            Box(Modifier.fillMaxSize().background(colorScheme.background)) { content() }
        }
    }
}

/**
 * 让系统栏和窗口底色跟应用主题走，而不是跟系统深浅色走。
 * 默认的 auto 样式会在浅色模式下强制导航栏对比度蒙层；键盘弹出时这层蒙层反复开关，整页就会闪。
 */
@Composable
private fun ApplyWindowChrome(dark: Boolean, background: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val backgroundArgb = background.toArgb()
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val transparent = android.graphics.Color.TRANSPARENT
        val barStyle = if (dark) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        // light/dark 样式不会打开导航栏对比度蒙层；auto 才会，那正是浅色输入时闪屏的来源
        activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        activity.window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
    }
}

/** 从 ContextThemeWrapper 里找出 Activity；预览或测试环境可能没有。 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
