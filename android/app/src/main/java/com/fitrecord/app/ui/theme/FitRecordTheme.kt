package com.fitrecord.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.view.View
import java.util.WeakHashMap
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import com.fitrecord.app.R

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
    val dark = themeMode.resolveDark(isSystemInDarkTheme())
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
 * 三态主题折成实际深浅色。跟随系统时用调用方传入的系统夜间标记。
 */
fun ThemeMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 让系统栏、窗口底色和 Compose 视图底色跟应用主题走。
 * 硬件层在页面切换和日历滑动时会按窗口主题清屏；Manifest 若一直是浅色，
 * 清出来的就是浅色模式。这里在深浅色变化时才重设，避免每一帧都动系统栏。
 */
@Composable
private fun ApplyWindowChrome(dark: Boolean, background: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val backgroundArgb = background.toArgb()
    val applied = remember { WindowChromeStamp() }
    SideEffect {
        if (applied.dark == dark && applied.backgroundArgb == backgroundArgb) return@SideEffect
        val activity = view.context.findActivity() ?: return@SideEffect
        applied.dark = dark
        applied.backgroundArgb = backgroundArgb
        applyFitRecordWindowTheme(activity, dark, background, view)
    }
}

/**
 * 把 Activity 窗口切到与 [dark] 一致的主题，并铺上不透明底色。
 *
 * 必须在 DecorView 安装前调用一次（MainActivity.onCreate 里、super.onCreate 之前），
 * 否则窗口按浅色主题创建，之后只改 drawable 盖不住转场清屏。
 *
 * @param composeView 组合根视图；转场出现透明帧时，它的底色就是用户看见的那一层。
 */
fun applyFitRecordWindowTheme(
    activity: ComponentActivity,
    dark: Boolean,
    background: Color,
    composeView: View? = null,
) {
    // 主题没变就不要再 setTheme。安装 Decor 之后重复 setTheme 会把窗口打回主题默认底，切页时闪一下。
    if (windowDarkByActivity[activity] != dark) {
        activity.setTheme(if (dark) R.style.Theme_FitRecord_Dark else R.style.Theme_FitRecord)
        val transparent = android.graphics.Color.TRANSPARENT
        val barStyle = if (dark) {
            SystemBarStyle.dark(transparent)
        } else {
            // light 样式不会打开导航栏对比度蒙层；auto 才会，浅色输入时整页会闪
            SystemBarStyle.light(transparent, transparent)
        }
        activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        windowDarkByActivity[activity] = dark
    }
    val backgroundArgb = background.toArgb()
    activity.window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
    activity.window.decorView.setBackgroundColor(backgroundArgb)
    composeView?.setBackgroundColor(backgroundArgb)
}

/** Activity 实例上已经套过的深浅色。用弱引用，避免窗口销毁后还被这条缓存留住。 */
private val windowDarkByActivity = WeakHashMap<ComponentActivity, Boolean>()

/** 已经套到窗口上的深浅色，避免 SideEffect 每次重组都重设系统栏。 */
private class WindowChromeStamp {
    var dark: Boolean? = null
    var backgroundArgb: Int? = null
}

/** 从 ContextThemeWrapper 里找出 Activity；预览或测试环境可能没有。 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
