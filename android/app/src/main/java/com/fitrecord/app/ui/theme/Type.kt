package com.fitrecord.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 规格 2.2 字阶映射到 Material3 Typography 槽位：
 *   display 22 → headlineSmall      title 17 → titleLarge
 *   body 14    → bodyMedium         label 13 → labelLarge
 *   caption 11 → labelSmall         numberLg 28 → MetricNumber
 */
val FitRecordTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 15.sp,
    ),
)

/** 统计数字与时长：28sp Medium + 等宽数字（规格 2.2 numberLg） */
val MetricNumber = TextStyle(
    fontSize = 28.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)
