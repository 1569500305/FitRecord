package com.fitrecord.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitrecord.app.data.repo.TrainingRepository
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.MetricNumber
import com.fitrecord.app.ui.theme.toColor

/** 卡片外壳：白底、16dp 圆角、16dp 内边距（规格 2.3） */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = Modifier.fillMaxWidth().padding(vertical = Dimens.space4)
    Card(
        modifier = if (onClick == null) base else base.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // 阴影在浅色底上会随输入重组重绘，整张卡片闪一下；边框已经够区分层次
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Dimens.cardPadding)) { content() }
    }
}

/** 指标卡（规格 2.4 MetricCard）：标题 + 大数字 + 单位 + 副说明 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MetricNumber,
                color = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(Dimens.space4))
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra?.invoke()
    }
}

/** 一条训练记录（规格 2.4 RecordCard）。删除用显式图标按钮而非左滑：
 *  SwipeToDismissBox 在 M3 1.3 仍是实验性 API，V1 优先保证不崩（台账偏离记录）。 */
@Composable
fun RecordCard(
    record: TrainingRepository.DayRecord,
    bodyPartNames: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effort = record.effortText()
    val desc = "${record.type.name}，$effort，" +
        (record.calories?.let { "${it.toInt()} 千卡" } ?: "待完善体重")
    AppCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(Dimens.dotSize * 2)
                    .clip(CircleShape)
                    .background(record.type.color.toColor())
            )
            Spacer(Modifier.width(Dimens.space8))
            Text(
                text = record.type.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = effort,
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "删除这段训练" }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimens.minTouchTarget / 2 + 4.dp),
                )
            }
        }
        Spacer(Modifier.size(Dimens.space4))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space8)) {
            Text(
                text = bodyPartNames.ifBlank { "未选部位" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = record.calories?.let { "≈ ${it.toInt()} kcal" } ?: "待完善体重",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (record.note.isNotBlank()) {
            Text(
                text = record.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.space4).semantics { contentDescription = desc },
            )
        }
    }
}

/** 图表卡外壳（规格 2.4 ChartCard） */
@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(Dimens.space12))
        content()
    }
}

/** 图例的一行：色点 + 名称 + 数值（规格 5.3：颜色不作为唯一信息载体） */
@Composable
fun LegendRow(color: Long, label: String, value: String, selected: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                else Modifier
            )
            .padding(horizontal = Dimens.space4, vertical = Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimens.dotSize).clip(CircleShape).background(color.toColor()))
        Spacer(Modifier.width(Dimens.space8))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
