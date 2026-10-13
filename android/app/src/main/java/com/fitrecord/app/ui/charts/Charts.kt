package com.fitrecord.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.toColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ChartPoint(val label: String, val value: Float)

/**
 * 图表数学（纯函数，JVM 可测）。
 * 把"算"与"画"分开，是为了让刻度这类容易错的东西有测试覆盖，
 * 而不是只能靠肉眼看渲染结果。
 */
object ChartMath {

    private val MANTISSAS = listOf(1f, 2f, 2.5f, 5f, 10f)

    /** 轴上限向上取到"好看"的刻度（1/2/2.5/5 × 10^n） */
    fun scaleMax(values: List<Float>): Float {
        val raw = values.maxOrNull()?.takeIf { it > 0f } ?: return 1f
        val exponent = when {
            raw >= 1f -> (Math.log10(raw.toDouble()).toInt())
            else -> 0
        }
        val base = 10f.pow(exponent)
        return MANTISSAS.map { it * base }.firstOrNull { it >= raw } ?: (10f.pow(exponent + 1))
    }

    private fun Float.pow(other: Int): Float {
        var acc = 1f
        repeat(other) { acc *= this }
        return acc
    }

    /** 归一到 0..1；全 0 时返回全 0（不能除零） */
    fun normalize(values: List<Float>): List<Float> {
        val max = values.maxOrNull() ?: 0f
        if (max <= 0f) return values.map { 0f }
        return values.map { it / max }
    }

    /** Y 轴刻度，含 0 与 max */
    fun yTicks(max: Float, count: Int = 4): List<Float> =
        (0..count).map { (max * it / count).roundToInt().toFloat() }

    /** 热力档位：0/1/2/3/≥4 段 → 0..4（规格 2.5） */
    fun heatLevel(segments: Int): Int = when {
        segments <= 0 -> 0
        segments >= 4 -> 4
        else -> segments
    }

    /** 环形图各片的起止角度（度），从 -90°（12 点）起顺时针 */
    fun donutSlices(values: List<Float>): List<Pair<Float, Float>> {
        val total = values.sum()
        if (total <= 0f) return emptyList()
        var cursor = -90f
        return values.map { value ->
            val sweep = value / total * 360f
            val pair = cursor to cursor + sweep
            cursor += sweep
            pair
        }
    }

    /** 雷达图第 index 轴的顶点；0 号轴指向正上方 */
    fun radarVertex(
        center: Offset,
        radius: Float,
        index: Int,
        axisCount: Int,
        ratio: Float,
    ): Offset {
        val angle = -PI / 2 + 2 * PI * index / axisCount
        return Offset(
            center.x + (cos(angle) * radius * ratio).toFloat(),
            center.y + (sin(angle) * radius * ratio).toFloat(),
        )
    }
}

/** 文字画笔：用 android.graphics.Paint 直接写进 nativeCanvas，避开 TextMeasurer 的实验性 API 变动。
 *  注意：这是普通函数，**不能**标 @Composable —— 它在 Canvas 的 DrawScope 里被调用。 */
private fun chartPaint(color: Color, textSizePx: Float) = android.graphics.Paint().apply {
    isAntiAlias = true
    this.color = android.graphics.Color.argb(
        (color.alpha * 255).roundToInt(),
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )
    textSize = textSizePx
}

/** 柱状图（周维度频率分布） */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    barColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.chartHeight,
    onBarClick: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val labelSizePx = with(density) { Dimens.space12.toPx() }
    // 文字与网格线随主题取色：Canvas 的 DrawScope 不是 composable 作用域，只能在此捕获后注入（DoD-3 深色无硬编码色）
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    Canvas(modifier.fillMaxWidth().height(height).pointerInput(points) {
            detectTapGestures { offset ->
                if (points.isEmpty()) return@detectTapGestures
                val slot = size.width / points.size
                val index = (offset.x / slot).toInt().coerceIn(0, points.size - 1)
                onBarClick(index)
            }
        }) {
            if (points.isEmpty()) return@Canvas
            val top = labelSizePx * 1.6f
            val plotHeight = size.height - top - labelSizePx * 1.4f
            val max = ChartMath.scaleMax(points.map { it.value })
            val slot = size.width / points.size
            val barWidth = slot * 0.55f

            // 网格线
            ChartMath.yTicks(max, 3).forEach { tick ->
                val y = top + plotHeight * (1f - tick / max)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }

            points.forEachIndexed { index, point ->
                val ratio = if (max > 0f) point.value / max else 0f
                val barHeight = maxOf(plotHeight * ratio, if (point.value > 0f) 4f else 2f)
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (point.value > 0f) barColor else barColor.copy(alpha = 0.15f),
                    topLeft = Offset(left, top + plotHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    point.label,
                    index * slot + slot / 2,
                    size.height - labelSizePx * 0.3f,
                    chartPaint(labelColor, labelSizePx).apply { textAlign = android.graphics.Paint.Align.CENTER },
                )
            }
        }
}

/** 折线图 + 面积（时长趋势） */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.chartHeight,
) {
    val density = LocalDensity.current
    val labelSizePx = with(density) { Dimens.space12.toPx() }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val top = labelSizePx * 1.6f
        val plotHeight = size.height - top - labelSizePx * 1.4f
        val max = ChartMath.scaleMax(points.map { it.value })
        val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f

        fun pos(index: Int): Offset {
            val ratio = if (max > 0f) points[index].value / max else 0f
            val x = if (points.size > 1) index * stepX else size.width / 2
            return Offset(x, top + plotHeight * (1f - ratio))
        }

        val path = Path()
        points.indices.forEach { i -> if (i == 0) path.moveTo(pos(i).x, pos(i).y) else path.lineTo(pos(i).x, pos(i).y) }
        val area = Path().apply { addPath(path); lineTo(pos(points.lastIndex).x, top + plotHeight); lineTo(pos(0).x, top + plotHeight); close() }
        drawPath(area, lineColor.copy(alpha = 0.12f))
        drawPath(path, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        points.indices.forEach { i ->
            val p = pos(i)
            if (points[i].value > 0f) {
                drawCircle(lineColor, radius = 4f, center = p)
            } else {
                drawCircle(lineColor.copy(alpha = 0.5f), radius = 3.5f, center = p, style = Stroke(width = 1.5f))
            }
        }

        // 只标首尾与中点，避免 8 个标签挤成一团
        val labelIdx = setOf(0, points.size / 2, points.lastIndex)
        points.forEachIndexed { i, point ->
            if (i in labelIdx) {
                drawContext.canvas.nativeCanvas.drawText(
                    point.label,
                    pos(i).x.coerceIn(labelSizePx * 2, (size.width - labelSizePx * 2).coerceAtLeast(labelSizePx * 2)),
                    size.height - labelSizePx * 0.3f,
                    chartPaint(labelColor, labelSizePx).apply { textAlign = android.graphics.Paint.Align.CENTER },
                )
            }
        }
    }
}

/** 环形图（运动方式占比） */
@Composable
fun DonutChart(
    slices: List<Pair<String, Long>>,
    values: List<Float>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    chartSize: Dp = Dimens.donutSize,
    selected: Int? = null,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { Dimens.space12.toPx() }
    val centerColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.fillMaxWidth().height(chartSize)) {
        val pairs = ChartMath.donutSlices(values)
        if (pairs.isEmpty()) return@Canvas
        val stroke = min(size.width, size.height) * 0.225f
        val radius = (min(size.width, size.height) - stroke) / 2f
        val center = Offset(size.width / 2, size.height / 2)
        val bounds = androidx.compose.ui.geometry.Rect(
            center.x - radius, center.y - radius, center.x + radius, center.y + radius,
        )
        pairs.forEachIndexed { index, (start, sweep) ->
            val (_, colorLong) = slices[index]
            drawArc(
                color = colorLong.toColor().copy(alpha = if (selected != null && selected != index) 0.35f else 1f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                style = Stroke(width = if (selected == index) stroke * 1.15f else stroke),
            )
        }
        val centerPaint = chartPaint(centerColor, textSizePx * 1.25f).apply { textAlign = android.graphics.Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText(centerValue, center.x, center.y + textSizePx * 0.4f, centerPaint)
        drawContext.canvas.nativeCanvas.drawText(
            centerLabel, center.x, center.y + textSizePx * 1.9f,
            chartPaint(labelColor, textSizePx * 0.85f).apply { textAlign = android.graphics.Paint.Align.CENTER },
        )
    }
}

/** 雷达图（部位训练占比，固定 8 轴） */
@Composable
fun RadarChart(
    axes: List<String>,
    values: List<Float>,
    fillColor: Color,
    modifier: Modifier = Modifier,
    chartSize: Dp = Dimens.radarSize,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { Dimens.space12.toPx() } * 0.8f
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.fillMaxWidth().height(chartSize)) {
        if (axes.isEmpty()) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2f - textSizePx * 2.2f

        // 4 环网格
        (1..4).forEach { ring ->
            val ratio = ring / 4f
            val path = Path()
            axes.indices.forEach { i ->
                val p = ChartMath.radarVertex(center, radius, i, axes.size, ratio)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, gridColor, style = Stroke(width = 1f))
        }
        // 轴线
        axes.indices.forEach { i ->
            drawLine(gridColor, center, ChartMath.radarVertex(center, radius, i, axes.size, 1f), 1f)
        }
        // 数据面
        val normalized = ChartMath.normalize(values)
        val dataPath = Path()
        normalized.forEachIndexed { i, ratio ->
            val p = ChartMath.radarVertex(center, radius, i, axes.size, max(ratio, 0.02f))
            if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, fillColor.copy(alpha = 0.2f))
        drawPath(dataPath, fillColor, style = Stroke(width = 2f))
        // 轴名
        val labelPaint = chartPaint(labelColor, textSizePx).apply { textAlign = android.graphics.Paint.Align.CENTER }
        axes.forEachIndexed { i, name ->
            val p = ChartMath.radarVertex(center, radius, i, axes.size, 1.24f)
            drawContext.canvas.nativeCanvas.drawText(name, p.x, p.y + textSizePx * 0.35f, labelPaint)
        }
    }
}

/** 日历热力图（月维度频率分布），5 档色阶 + 格内数字（不单靠颜色） */
@Composable
fun CalendarHeatmap(
    days: List<LocalDateAlias>,
    segments: List<Int>,
    levelColors: List<Color>,
    modifier: Modifier = Modifier,
    onDayClick: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { Dimens.space12.toPx() } * 0.75f
    val numberColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.fillMaxWidth().height(sizeOf(days.size))) {
        if (days.isEmpty()) return@Canvas
        val columns = 7
        val rows = (days.size + columns - 1) / columns
        val cellW = size.width / columns
        val cellH = size.height / rows
        days.indices.forEach { index ->
            val col = index % columns
            val row = index / columns
            val level = ChartMath.heatLevel(segments.getOrElse(index) { 0 })
            val left = col * cellW
            val top = row * cellH
            drawRoundRect(
                color = levelColors.getOrElse(level) { levelColors.last() },
                topLeft = Offset(left + 1f, top + 1f),
                size = Size(cellW - 2f, cellH - 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
            if (segments.getOrElse(index) { 0 } > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    segments[index].toString(),
                    left + cellW / 2,
                    top + cellH / 2 + textSizePx * 0.35f,
                    chartPaint(numberColor, textSizePx).apply { textAlign = android.graphics.Paint.Align.CENTER },
                )
            }
        }
    }
}

/** 热力图高度按行数算，避免出现内部依赖 */
private fun sizeOf(days: Int): Dp {
    val rows = max(1, (days + 6) / 7)
    return Dp((rows * 34).toFloat())
}

/** 避免在本文件顶部引入 java.time 别名造成误读 */
typealias LocalDateAlias = java.time.LocalDate
