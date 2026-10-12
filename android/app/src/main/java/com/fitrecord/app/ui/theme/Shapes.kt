package com.fitrecord.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 规格 2.3：卡片 16dp、chip/按钮 8dp、弹层顶部 24dp */
val FitRecordShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(Dimens.chipCorner),
    medium = RoundedCornerShape(Dimens.cardCorner),
    large = RoundedCornerShape(Dimens.cardCorner),
    extraLarge = RoundedCornerShape(Dimens.sheetCorner),
)
