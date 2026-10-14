package com.fitrecord.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrecord.app.domain.DayCell
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.ExerciseIcons
import com.fitrecord.app.ui.theme.LocalUnreadColor
import com.fitrecord.app.ui.theme.toColor

/** 空态（规格 2.4 EmptyState） */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 几何占位图形，不用任何第三方素材（零版权风险）
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(Dimens.space12))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimens.space12))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 破坏性操作确认（规格 5.4：确认按钮用 error 色） */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 可关闭提示条（规格 2.4 InfoBanner） */
@Composable
fun InfoBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.padding(horizontal = Dimens.cardPadding, vertical = Dimens.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "关闭提示" }) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** 数据库只读模式顶栏（规格 6.9，台账 P16） */
@Composable
fun ReadOnlyBar(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error) {
        Row(
            Modifier.padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            Text(text, color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 日历单格（规格 2.4 DayCell / 4.1 状态判定）。
 * 状态不单靠颜色：已健身同时有"色点 + 加粗字重"（规格 5.3）。
 */
@Composable
fun DayCellView(
    cell: DayCell,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = LocalUnreadColor.current
    val desc = "${cell.date.monthValue}月${cell.date.dayOfMonth}日，" +
        if (cell.trained) "已健身 ${cell.segmentCount} 段" else "未健身"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) Dimens.dayCellHeightWeek else Dimens.dayCellHeightMonth)
            .clip(RoundedCornerShape(Dimens.chipCorner))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        DayHighlight(cell)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.let { body ->
                    body.copy(fontSize = (body.fontSize.value + 2f).sp)
                },
                fontWeight = if (cell.trained || cell.isToday) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    cell.isToday -> MaterialTheme.colorScheme.onPrimary
                    // 当月日期一律黑色（是否已练靠字重 + 下方图标/色点区分，不靠颜色）；非当月置灰
                    !cell.inMonth -> unread.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            // 农历位：V1 不做，留固定占位（规格 2.3）
            Spacer(Modifier.height(Dimens.lunarPlaceholder))
            val emoji = ExerciseIcons.emoji(cell.primaryTypeName)
            if (emoji != null) {
                // 主类型命中映射：用一个类型图标取代色点（跑步→🏃、划船机→🚣 …）
                Text(
                    emoji,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.height(Dimens.dotSize * 2 + 6.dp),
                )
            } else {
                // 自定义/未映射类型回退为原来的彩色圆点，不丢信息
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    cell.typeColors.forEach { color ->
                        Box(Modifier.size(Dimens.dotSize).clip(CircleShape).background(color.toColor()))
                    }
                    if (cell.showPlusN) {
                        Text("+", style = MaterialTheme.typography.labelSmall, color = unread)
                    }
                }
            }
        }
    }
}

/**
 * 选中或今日的高亮框。
 * 有运动时铺满整格，把日期和图标包在同一个圆角矩形里；
 * 没有运动时用正方形圆角，避免空格子被拉成细长矩形。
 */
@Composable
private fun BoxWithConstraintsScope.DayHighlight(cell: DayCell) {
    if (!cell.isToday && !cell.isSelected) return
    val shape = RoundedCornerShape(Dimens.chipCorner)
    val bounds = if (cell.trained) {
        Modifier.matchParentSize().padding(Dimens.space2)
    } else {
        // 与列宽取齐的正方形；requiredSize 才能在固定行高里不被拉回长方形
        val side = minOf(maxWidth, maxHeight) - Dimens.space4
        Modifier.align(Alignment.Center).requiredSize(side)
    }
    val painted = if (cell.isToday) {
        bounds.background(MaterialTheme.colorScheme.primary, shape)
    } else {
        bounds
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
    }
    Box(painted)
}
