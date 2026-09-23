package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One line of a [LineChart]; `values[i]` belongs to `dates[i]`, null = no value
 * that day. [fill] shades the area below the line (single-series charts).
 */
data class ChartLine(val values: List<Float?>, val color: Color, val fill: Boolean = false)

/** Value range, grid lines and label format of a chart's y-axis. */
data class YAxis(
    val min: Float,
    val max: Float,
    val ticks: List<Float>,
    val label: (Float) -> String,
)

/** Up to this many days, every day gets a weekday + date label on the x-axis. */
private const val MAX_DAILY_LABELS = 10

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
 * Napper-style line chart over a continuous day axis, drawn on a Compose Canvas
 * without a chart library: smooth curves, optional area fill and a dotted
 * [average] line. Days without a value are skipped; the curve connects the
 * neighbouring values.
 */
@Composable
fun LineChart(
    dates: List<LocalDate>,
    lines: List<ChartLine>,
    yAxis: YAxis,
    modifier: Modifier = Modifier,
    average: Float? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.outline
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.") }
    val shortDateFmt = remember { DateTimeFormatter.ofPattern("d.M.") }
    val weekdayFmt = remember { DateTimeFormatter.ofPattern("EE", Locale.GERMAN) }

    Canvas(modifier) {
        if (dates.isEmpty()) return@Canvas
        val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
        val tickLabels = yAxis.ticks.map { it to textMeasurer.measure(yAxis.label(it), style = labelStyle) }
        val widestLabel = tickLabels.maxOfOrNull { it.second.size.width } ?: 0
        val n = dates.size
        val dailyLabels = n <= MAX_DAILY_LABELS

        val leftPad = maxOf(30.dp.toPx(), widestLabel + 8.dp.toPx())
        val bottomPad = if (dailyLabels) 32.dp.toPx() else 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 12.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        val baseline = topPad + plotH
        val xStep = if (n > 1) plotW / (n - 1) else 0f
        val span = (yAxis.max - yAxis.min).takeIf { it > 0f } ?: 1f

        fun xAt(i: Int): Float = if (n > 1) leftPad + i * xStep else leftPad + plotW / 2f
        fun yAt(v: Float): Float = baseline - ((v.coerceIn(yAxis.min, yAxis.max) - yAxis.min) / span) * plotH

        // Faint horizontal grid lines + y-axis value labels.
        for ((tick, layout) in tickLabels) {
            val y = yAt(tick)
            drawLine(axisColor.copy(alpha = 0.35f), Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1f)
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }

        // Axes.
        drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, baseline), strokeWidth = 1.dp.toPx())
        drawLine(axisColor, Offset(leftPad, baseline), Offset(leftPad + plotW, baseline), strokeWidth = 1.dp.toPx())

        // x-axis: weekday + date under every day for short ranges, else first and last date.
        val labelTop = baseline + 4.dp.toPx()
        if (dailyLabels) {
            val centered = labelStyle.copy(textAlign = TextAlign.Center)
            dates.forEachIndexed { i, date ->
                val text = date.format(weekdayFmt) + "\n" + date.format(shortDateFmt)
                val layout = textMeasurer.measure(text, style = centered)
                drawText(layout, topLeft = Offset(xAt(i) - layout.size.width / 2f, labelTop))
            }
        } else {
            val firstLabel = textMeasurer.measure(dates.first().format(dateFmt), style = labelStyle)
            drawText(firstLabel, topLeft = Offset(leftPad, labelTop))
            val lastLabel = textMeasurer.measure(dates.last().format(dateFmt), style = labelStyle)
            drawText(lastLabel, topLeft = Offset(leftPad + plotW - lastLabel.size.width, labelTop))
        }

        for (line in lines) {
            val points = line.values.mapIndexedNotNull { i, v -> v?.let { Offset(xAt(i), yAt(it)) } }
            if (points.isEmpty()) continue
            val curve = smoothPath(points)
            if (line.fill && points.size > 1) {
                val area = smoothPath(points).apply {
                    lineTo(points.last().x, baseline)
                    lineTo(points.first().x, baseline)
                    close()
                }
                drawPath(
                    area,
                    brush = Brush.verticalGradient(
                        colors = listOf(line.color.copy(alpha = 0.45f), line.color.copy(alpha = 0.03f)),
                        startY = topPad,
                        endY = baseline,
                    ),
                )
            }
            drawPath(curve, color = line.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            // Dots while they are distinguishable, and always for a lone value (no line to draw).
            if (n <= 31 || points.size == 1) {
                points.forEach { drawCircle(line.color, radius = 2.5.dp.toPx(), center = it) }
            }
        }

        if (average != null) {
            val y = yAt(average)
            drawLine(
                color = labelColor.copy(alpha = 0.8f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 6.dp.toPx())),
            )
        }
    }
}

/**
 * Curve through [points] with horizontal tangents at each point: smooth like
 * the Napper charts, but never overshooting above or below a value.
 */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val midX = (a.x + b.x) / 2f
        cubicTo(midX, a.y, midX, b.y, b.x, b.y)
    }
}
