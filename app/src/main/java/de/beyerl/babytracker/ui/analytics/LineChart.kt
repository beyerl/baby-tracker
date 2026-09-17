package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

/** One line of a [LineChart]; `values[i]` belongs to `dates[i]`, null = no value that day. */
data class ChartLine(val values: List<Float?>, val color: Color)

/** Value range, grid lines and label format of a chart's y-axis. */
data class YAxis(
    val min: Float,
    val max: Float,
    val ticks: List<Float>,
    val label: (Float) -> String,
)

/** 0..[maxCount] axis for event counts with up to four grid steps. */
fun countAxis(maxCount: Int): YAxis {
    val max = maxCount.coerceAtLeast(1)
    val steps = minOf(max, 4)
    return YAxis(0f, max.toFloat(), (0..steps).map { (max * it / steps).toFloat() }) { it.toInt().toString() }
}

/**
 * Axis in hours fitted to [values]: whole hours with a grid line per hour, or
 * per second hour above a 6 h span. Null if there is no value to show.
 */
fun hourAxis(values: List<Float?>, label: (Float) -> String): YAxis? {
    val present = values.filterNotNull()
    if (present.isEmpty()) return null
    val min = floor(present.min())
    val max = ceil(present.max()).let { if (it > min) it else min + 1f }
    val step = if (max - min > 6f) 2f else 1f
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max }.toList()
    return YAxis(min, max, ticks, label)
}

/**
 * Line chart over a continuous day axis, drawn on a Compose Canvas without a
 * chart library. Days without a value are skipped; the line connects the
 * neighbouring values.
 */
@Composable
fun LineChart(
    dates: List<LocalDate>,
    lines: List<ChartLine>,
    yAxis: YAxis,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.outline
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.") }

    Canvas(modifier) {
        if (dates.isEmpty()) return@Canvas
        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
        val tickLabels = yAxis.ticks.map { it to textMeasurer.measure(yAxis.label(it), style = labelStyle) }
        val widestLabel = tickLabels.maxOfOrNull { it.second.size.width } ?: 0

        val leftPad = maxOf(30.dp.toPx(), widestLabel + 8.dp.toPx())
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        val n = dates.size
        val xStep = if (n > 1) plotW / (n - 1) else 0f
        val span = (yAxis.max - yAxis.min).takeIf { it > 0f } ?: 1f

        fun xAt(i: Int): Float = if (n > 1) leftPad + i * xStep else leftPad + plotW / 2f
        fun yAt(v: Float): Float = topPad + plotH - ((v - yAxis.min) / span) * plotH

        // Horizontal grid lines + y-axis value labels.
        for ((tick, layout) in tickLabels) {
            val y = yAt(tick)
            drawLine(
                color = axisColor.copy(alpha = 0.4f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
            )
            drawText(
                layout,
                topLeft = Offset(leftPad - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f),
            )
        }

        // Axes.
        drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotH), strokeWidth = 1.dp.toPx())
        drawLine(
            axisColor,
            Offset(leftPad, topPad + plotH),
            Offset(leftPad + plotW, topPad + plotH),
            strokeWidth = 1.dp.toPx(),
        )

        // x-axis date labels (first and, if distinct, last).
        val firstLabel = textMeasurer.measure(dates.first().format(dateFmt), style = labelStyle)
        drawText(firstLabel, topLeft = Offset(leftPad, topPad + plotH + 4.dp.toPx()))
        if (n > 1) {
            val lastLabel = textMeasurer.measure(dates.last().format(dateFmt), style = labelStyle)
            drawText(
                lastLabel,
                topLeft = Offset(leftPad + plotW - lastLabel.size.width, topPad + plotH + 4.dp.toPx()),
            )
        }

        for (line in lines) {
            val path = Path()
            var points = 0
            line.values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                if (points++ == 0) path.moveTo(xAt(i), yAt(v)) else path.lineTo(xAt(i), yAt(v))
            }
            drawPath(path, color = line.color, style = Stroke(width = 2.dp.toPx()))
            // Dots while they are distinguishable, and always for a lone value (no line to draw).
            if (n <= 62 || points == 1) {
                line.values.forEachIndexed { i, v ->
                    if (v != null) drawCircle(line.color, radius = 2.5.dp.toPx(), center = Offset(xAt(i), yAt(v)))
                }
            }
        }
    }
}
