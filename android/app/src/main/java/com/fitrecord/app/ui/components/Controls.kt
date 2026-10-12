package com.fitrecord.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** 快捷时长（规格 P5） */
private val QUICK_MINUTES = listOf(15, 30, 45, 60, 90)
private const val MAX_MINUTES = 1440
private const val STEP = 5

/**
 * 时长输入（规格 2.4 MinuteField）。
 *
 * 快捷 chip 必须放在 FlowRow 里：上一轮把 7 个控件塞进裸 Row，最后一颗 chip
 * 被排到屏幕外，点击落空（T13 台账记录）。文本与数值双状态，避免"敲到一半被清零"。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinuteField(
    value: Int,
    onValueChange: (Int) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            onValueChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text("时长（分钟）") },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
    ) {
        OutlinedButton(
            onClick = { onValueChange((value - STEP).coerceAtLeast(0)) },
            modifier = Modifier.height(Dimens.minTouchTarget),
        ) { Text("−5") }
        OutlinedButton(
            onClick = { onValueChange((value + STEP).coerceAtMost(MAX_MINUTES)) },
            modifier = Modifier.height(Dimens.minTouchTarget),
        ) { Text("+5") }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
    ) {
        QUICK_MINUTES.forEach { minutes ->
            FilterChip(
                selected = value == minutes,
                onClick = { onValueChange(minutes) },
                label = { Text(minutes.toString()) },
            )
        }
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
