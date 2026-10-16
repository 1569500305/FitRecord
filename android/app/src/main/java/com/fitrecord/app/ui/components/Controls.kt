package com.fitrecord.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitrecord.app.ui.theme.Dimens

/**
 * 分段切换（规格 2.4）。刻意用 Box + clickable 手写而不是 Material3 的
 * SingleChoiceSegmentedButtonRow：后者在 1.3.x 仍是实验性 API 且 itemShape 参数易变，
 * 手写版零风险且视觉上等价（选中段填 primaryContainer）。
 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(Dimens.chipCorner))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(index) }
                    .semantics { contentDescription = "$label 视图" }
                    .padding(horizontal = Dimens.space16),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 周/月切换，做成一颗按钮。
 * 选中段用日历选中日的底色和描边，未选中段留白。
 */
@Composable
fun ViewModeSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimens.chipCorner)
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(Dimens.strokeWidth, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(shape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .then(
                        if (selected) {
                            Modifier.border(Dimens.strokeWidth, MaterialTheme.colorScheme.primary, shape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .semantics { contentDescription = "$label 视图" }
                    .padding(horizontal = Dimens.space16),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** 快捷时长。最后一格是加 5 分钟，不是一个固定时长。 */
private val QUICK_MINUTES = listOf(20, 30, 45, 60, 90)
private const val MAX_MINUTES = 1440
private const val STEP = 5
private const val ADD_FIVE_LABEL = "+5"

/**
 * 时长输入。
 *
 * 数字用大号等宽样式，下面六个快捷键等分一行：20、30、45、60、90 写入对应分钟，
 * +5 在当前时长上加 5。文本与数值双状态，避免敲到一半被清零。
 */
@Composable
fun MinuteField(
    value: Int,
    onValueChange: (Int) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(minutesText(value)) }
    // 只在外部改值（快捷时长、+5）时回写。每次按键都按 value 重建文本会让输入框丢掉焦点
    LaunchedEffect(value) {
        val typed = text.toIntOrNull() ?: 0
        if (typed != value) text = minutesText(value)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            onValueChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text("时长") },
        suffix = {
            Text("分钟", style = MaterialTheme.typography.bodyMedium)
        },
        textStyle = MaterialTheme.typography.titleLarge,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = stableOutlinedTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
    DurationShortcutRow(
        value = value,
        onPick = onValueChange,
        onAddFive = { onValueChange((value + STEP).coerceAtMost(MAX_MINUTES)) },
    )
}

/** 时长快捷键。每格 weight(1)，六个键始终排在同一行，不会换行挤出屏幕。 */
@Composable
private fun DurationShortcutRow(
    value: Int,
    onPick: (Int) -> Unit,
    onAddFive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.space8),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QUICK_MINUTES.forEach { minutes ->
            MinuteShortcut(
                label = minutes.toString(),
                description = "$minutes 分钟",
                selected = value == minutes,
                onClick = { onPick(minutes) },
                modifier = Modifier.weight(1f),
            )
        }
        MinuteShortcut(
            label = ADD_FIVE_LABEL,
            description = "增加 5 分钟",
            selected = false,
            accent = true,
            onClick = onAddFive,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单个时长快捷键。选中时用主色底，+5 用强调色，和固定时长区分开。 */
@Composable
private fun MinuteShortcut(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        accent -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val color = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        accent -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .heightIn(min = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.chipCorner))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 部位多选（规格 P5 / 2.4 TagSelector）。同样必须 FlowRow：8 个部位一行放不下 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelector(
    options: List<Pair<Long, String>>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = id in selected,
                onClick = { onToggle(id) },
                label = { Text(label) },
            )
        }
    }
}

/** 把分钟数写成输入框文本。0 留空，方便直接覆盖。 */
private fun minutesText(minutes: Int): String = if (minutes == 0) "" else minutes.toString()

/**
 * 输入框容器色固定为透明。
 * 默认色在聚焦、报错之间做颜色动画，浅色底上每一帧都会透出一块闪白。
 */
@Composable
fun stableOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
)

/** 单行步进器（P8 的 MET 输入、P6 的数值输入共用） */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = stableOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.space4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            OutlinedButton(onClick = onStepDown, modifier = Modifier.height(Dimens.minTouchTarget)) { Text("−") }
            OutlinedButton(onClick = onStepUp, modifier = Modifier.height(Dimens.minTouchTarget)) { Text("＋") }
        }
    }
}
