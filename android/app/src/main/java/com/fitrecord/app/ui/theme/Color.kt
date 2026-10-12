package com.fitrecord.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ---------- 规格 2.1 色板（浅色 / 深色成对）----------

private val Green = Color(0xFF2E9E5B)
private val GreenDark = Color(0xFF4FBE7E)
private val OnGreen = Color(0xFFFFFFFF)
private val OnGreenDark = Color(0xFF07210F)
private val GreenContainer = Color(0xFFDCF2E4)
private val GreenContainerDark = Color(0xFF123A22)
private val Orange = Color(0xFFF5A623)
private val OrangeDark = Color(0xFFF7BE55)
private val OnOrange = Color(0xFF3A2400)
private val Background = Color(0xFFF5F6F7)
private val BackgroundDark = Color(0xFF101114)
private val Surface = Color(0xFFFFFFFF)
private val SurfaceDark = Color(0xFF1A1C20)
private val SurfaceVariant = Color(0xFFF1F2F4)
private val SurfaceVariantDark = Color(0xFF23262B)
private val OnSurface = Color(0xFF1B1C1E)
private val OnSurfaceDark = Color(0xFFE3E3E6)
private val OnSurfaceVariant = Color(0xFF6B7075)
private val OnSurfaceVariantDark = Color(0xFFA5AAAF)
private val Outline = Color(0xFFDDE0E3)
private val OutlineDark = Color(0xFF33373C)
private val ErrorLight = Color(0xFFC62828)
private val ErrorDark = Color(0xFFFF8A80)
private val Unread = Color(0xFFB6BABD)
private val UnreadDark = Color(0xFF5F6468)
private val Disabled = Color(0xFFBFC3C6)
private val DisabledDark = Color(0xFF5A5F63)

object AppColors {

    val light: ColorScheme = lightColorScheme(
        primary = Green,
        onPrimary = OnGreen,
        primaryContainer = GreenContainer,
        onPrimaryContainer = OnSurface,
        secondary = Orange,
        onSecondary = OnOrange,
        background = Background,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        error = ErrorLight,
        onError = Color(0xFFFFFFFF),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = GreenDark,
        onPrimary = OnGreenDark,
        primaryContainer = GreenContainerDark,
        onPrimaryContainer = OnSurfaceDark,
        secondary = OrangeDark,
        onSecondary = OnOrange,
        background = BackgroundDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        error = ErrorDark,
        onError = Color(0xFF2B0000),
    )

    /** 日历格"未健身"灰字：Material3 无对应槽位，单独暴露（规格 2.1） */
    val unreadLight: Color = Unread
    val unreadDark: Color = UnreadDark

    /** 禁用态：同上，单独暴露 */
    val disabledLight: Color = Disabled
    val disabledDark: Color = DisabledDark
}

/**
 * 运动类型配色（规格 2.1）。下标顺序即内置库顺序：
 * 划船机 椭圆机 动感单车 跑步机 自由力量 瑜伽 快走 游泳
 */
object ExercisePalette {

    val colors: List<Color> = listOf(
        Color(0xFF2E9E5B), // 划船机
        Color(0xFF3E8FD6), // 椭圆机
        Color(0xFFE5645C), // 动感单车
        Color(0xFFF5A623), // 跑步机
        Color(0xFF8E6BE8), // 自由力量
        Color(0xFF20B2AA), // 瑜伽
        Color(0xFFD9772E), // 快走
        Color(0xFF5C7CC9), // 游泳
    )

    /** 自定义类型按 id 轮询取色；floorMod 保证负 id（脏数据）也不越界 */
    fun colorForId(id: Long): Color = colors[java.lang.Math.floorMod(id.toInt(), colors.size)]
}

/** 日历热力图 5 档（规格 2.5：0 / 1 / 2 / 3 / ≥4 段），由浅到深 */
object HeatLevels {
    val levels: List<Color> = listOf(
        Color(0xFFE8F5EC),
        Color(0xFFBFE6CC),
        Color(0xFF7FCB98),
        Color(0xFF45B170),
        Color(0xFF1F8A4C),
    )

    fun colorFor(level: Int): Color = levels[level.coerceIn(0, levels.lastIndex)]
}

/** 环形图"其他"扇区固定灰（规格 4.6） */
val ColorOthers: Color = Color(0xFFB6BABD)

// ---------- 颜色 ↔ 存库 Long（Room 存 ARGB Int 位模式，规格 4.2 color INT）----------

fun Color.toRgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

fun Long.toColor(): Color = Color(this.toInt())
