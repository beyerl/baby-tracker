package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.beyerl.babytracker.ui.theme.BedtimeColor
import de.beyerl.babytracker.ui.theme.NapColors
import de.beyerl.babytracker.ui.theme.SunriseColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

private val rowDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.")

private val napNames = listOf("Erstes", "Zweites", "Drittes", "Viertes", "Fünftes")

/** Color of the nap at [index] within its day; the fifth color covers all later naps. */
private fun napColor(index: Int): Color = NapColors[minOf(index, NapColors.lastIndex)]

/**
 * Tab "Schlafmuster": one row per day with wake-up, naps and bedtime on a
 * clock axis, or – with the switch on – relative to that day's wake-up.
 */
@Composable
internal fun SleepPatternTab(data: AnalyticsData) {
    var relative by rememberSaveable { mutableStateOf(false) }
    SectionTitle("Schlafmuster tagsüber")
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Relativ zum Aufwachen anzeigen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = relative, onCheckedChange = { relative = it })
        }
        // Relative mode needs a wake-up to measure from.
        val days = data.pattern
            .filter { it.wakeUp != null || it.bedtime != null || it.naps.isNotEmpty() }
            .filter { !relative || it.wakeUp != null }
        if (days.isEmpty()) {
            EmptyHint(
                if (relative) "Keine Tage mit Aufwachzeit im Zeitraum"
                else "Keine Aufwach-, Nickerchen- oder Schlafenszeiten im Zeitraum",
            )
            return@ChartCard
        }
        SleepPatternChart(days, relative)
        Spacer(Modifier.height(12.dp))
        PatternLegend(maxNaps = days.maxOf { it.naps.size })
    }
}

@Composable
private fun SleepPatternChart(days: List<DayPattern>, relative: Boolean) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val rowHeight = 28.dp

    // Minutes on the x-axis: after midnight, or after the day's wake-up.
    fun DayPattern.shift(m: Int): Int = if (relative) m - (wakeUp ?: 0) else m
    val allMinutes = days.flatMap { d ->
        listOfNotNull(d.wakeUp, d.bedtime).map { d.shift(it) } + d.naps.flatMap { listOf(d.shift(it.first), d.shift(it.second)) }
    }
    val fromHour = floor(allMinutes.min() / 60f).toInt()
    val toHour = ceil(allMinutes.max() / 60f).toInt().coerceAtLeast(fromHour + 1)
    val tickStep = if (toHour - fromHour > 9) 3 else if (toHour - fromHour > 4) 2 else 1

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(rowHeight * days.size + 24.dp)
            .padding(top = 8.dp),
    ) {
        val style = TextStyle(fontSize = 11.sp, color = labelColor)
        val leftPad = 40.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 20.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val rowH = rowHeight.toPx()
        val span = ((toHour - fromHour) * 60).toFloat()
        fun xAt(minutes: Int): Float = leftPad + (minutes - fromHour * 60) / span * plotW

        // Hour grid with labels on top.
        for (hour in fromHour..toHour step tickStep) {
            val x = xAt(hour * 60)
            drawLine(gridColor.copy(alpha = 0.5f), Offset(x, topPad), Offset(x, topPad + rowH * days.size), strokeWidth = 1f)
            val text = if (relative) "+$hour h" else String.format(Locale.ROOT, "%02d:00", Math.floorMod(hour, 24))
            val layout = textMeasurer.measure(text, style.copy(textAlign = TextAlign.Center))
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 0f))
        }

        days.forEachIndexed { row, day ->
            val centerY = topPad + row * rowH + rowH / 2f
            val label = textMeasurer.measure(day.date.format(rowDateFmt), style)
            drawText(label, topLeft = Offset(0f, centerY - label.size.height / 2f))

            val barH = rowH * 0.42f
            day.naps.forEachIndexed { i, (start, end) ->
                val x0 = xAt(day.shift(start))
                val x1 = maxOf(xAt(day.shift(end)), x0 + 3.dp.toPx())
                drawRoundRect(
                    color = napColor(i),
                    topLeft = Offset(x0, centerY - barH / 2f),
                    size = Size(x1 - x0, barH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            val tickW = 3.dp.toPx()
            fun tick(minutes: Int, color: Color) = drawRoundRect(
                color = color,
                topLeft = Offset(xAt(day.shift(minutes)) - tickW / 2f, centerY - barH / 2f),
                size = Size(tickW, barH),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            day.wakeUp?.let { tick(it, SunriseColor) }
            day.bedtime?.let { tick(it, BedtimeColor) }
        }
    }
}

/** Legend: Aufgewacht, the nap colors up to the most naps in a day, Schlafenszeit. */
@Composable
private fun PatternLegend(maxNaps: Int) {
    val entries = buildList {
        add("Aufgewacht" to SunriseColor)
        for (i in 0 until minOf(maxNaps, NapColors.size)) {
            val name = if (i == NapColors.lastIndex && maxNaps > NapColors.size) "${napNames[i]}+" else napNames[i]
            add("$name Nickerchen" to napColor(i))
        }
        add("Schlafenszeit" to BedtimeColor)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.size(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
