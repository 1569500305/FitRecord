package com.fitrecord.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 规格 2.3 栅格与尺寸。屏幕里不允许出现字面量 dp（全局约束）。 */
object Dimens {
    val pagePadding: Dp = 16.dp
    val cardGap: Dp = 12.dp
    val cardPadding: Dp = 16.dp
    val cardCorner: Dp = 16.dp
    val chipCorner: Dp = 8.dp
    val sheetCorner: Dp = 24.dp

    val dayCellHeightWeek: Dp = 44.dp
    val dayCellHeightMonth: Dp = 64.dp
    val dayCellHeightMonthLarge: Dp = 80.dp   // 字号 1.3× 时（规格 5.3）

    val navBarHeight: Dp = 64.dp
    val fabSize: Dp = 56.dp
    val minTouchTarget: Dp = 48.dp

    val chartHeight: Dp = 180.dp
    val radarSize: Dp = 200.dp
    val donutSize: Dp = 160.dp
    val sparklineHeight: Dp = 48.dp

    val dotSize: Dp = 6.dp
    val selectedCircle: Dp = 40.dp
    val lunarPlaceholder: Dp = 10.dp           // 农历占位行（V1 不显示内容）

    val space2: Dp = 2.dp
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space24: Dp = 24.dp
}
