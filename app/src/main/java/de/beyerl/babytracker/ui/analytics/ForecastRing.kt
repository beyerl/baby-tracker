package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.beyerl.babytracker.stats.DayForecast
import de.beyerl.babytracker.stats.ForecastItem
import de.beyerl.babytracker.stats.ForecastKind
import de.beyerl.babytracker.ui.theme.BedtimeColor
import de.beyerl.babytracker.ui.theme.FeedColor
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.SunriseColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** The ring starts bottom-left (wake-up) and runs clockwise to bottom-right (bedtime). */
private const val START_ANGLE = 135f
private const val SWEEP = 270f

private val clockFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(clockFmt)

/** "24 min" below an hour, else "1h 19m". */
internal fun formatCountdown(minutes: Long): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"

/** Whole minutes from [now] until [time], rounded up so the countdown never shows 0 early. */
internal fun minutesUntil(time: Long, now: Long): Long = (time - now + 59_999) / 60_000

private val ordinals = listOf("Erstes", "Zweites", "Drittes", "Viertes", "Fünftes", "Sechstes", "Siebtes")

/** "Zweites Nickerchen" / "Füttern" for the centre of the ring and the list. */
internal fun ForecastItem.title(): String = when (kind) {
    ForecastKind.NAP -> (ordinals.getOrNull(ordinal) ?: "${ordinal + 1}.") + " Nickerchen"
    ForecastKind.FEED -> "Füttern"
}

/**
 * Napper-style day ring: a 270° track from wake-up to bedtime with naps as arc
 * segments and feedings as dots – logged entries solid, predictions dotted –
 * the time passed so far highlighted, and the next event counted down in the middle.
 */
@Composable
internal fun ForecastRing(forecast: DayForecast, now: Long, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.outline
    val nowColor = MaterialTheme.colorScheme.onSurface

    Box(modifier.fillMaxWidth().aspectRatio(0.95f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val labelSpace = 48.dp.toPx()
            val radius = minOf(size.width, size.height) / 2f - labelSpace
            val center = Offset(size.width / 2f, radius + labelSpace)
            val trackW = 34.dp.toPx()
            val span = (forecast.bedtime - forecast.wakeUp).coerceAtLeast(1L).toFloat()
            fun angleOf(t: Long) = START_ANGLE + SWEEP * ((t - forecast.wakeUp) / span).coerceIn(0f, 1f)
            fun pointAt(angle: Float, r: Float): Offset {
                val rad = Math.toRadians(angle.toDouble())
                return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            }
            val ringRect = Rect(center, radius)

            // Track and the part of the day already passed.
            drawArc(track, START_ANGLE, SWEEP, false, ringRect.topLeft, ringRect.size, style = Stroke(trackW, cap = StrokeCap.Round))
            if (now > forecast.wakeUp) {
                drawArc(
                    SleepColor.copy(alpha = 0.18f), START_ANGLE, angleOf(now) - START_ANGLE, false,
                    ringRect.topLeft, ringRect.size, style = Stroke(trackW, cap = StrokeCap.Round),
                )
            }

            val labelStyle = TextStyle(fontSize = 12.sp, color = labelColor, textAlign = TextAlign.Center)
            for (item in forecast.items) {
                val a0 = angleOf(item.start)
                when (item.kind) {
                    ForecastKind.NAP -> {
                        val a1 = maxOf(angleOf(item.end ?: item.start), a0 + 4f)
                        drawNap(center, radius, trackW * 0.78f, a0, a1, item.actual)
                        val text = formatTime(item.start) + "\n" + formatTime(item.end ?: item.start)
                        drawLabel(textMeasurer, text, pointAt((a0 + a1) / 2f, radius + trackW / 2f + 24.dp.toPx()), labelStyle)
                    }
                    ForecastKind.FEED -> {
                        val p = pointAt(a0, radius)
                        val r = 6.dp.toPx()
                        if (item.actual) {
                            drawCircle(FeedColor, r, p)
                        } else {
                            drawCircle(FeedColor.copy(alpha = 0.25f), r, p)
                            drawCircle(FeedColor, r, p, style = Stroke(1.5.dp.toPx(), pathEffect = dots()))
                        }
                        drawLabel(
                            textMeasurer, formatTime(item.start),
                            pointAt(a0, radius - trackW / 2f - 14.dp.toPx()),
                            labelStyle.copy(color = FeedColor, fontSize = 11.sp),
                        )
                    }
                }
            }

            // Wake-up and bedtime at the ring ends, times below them.
            val endStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            for ((time, angle, color) in listOf(
                Triple(forecast.wakeUp, START_ANGLE, SunriseColor),
                Triple(forecast.bedtime, START_ANGLE + SWEEP, BedtimeColor),
            )) {
                val p = pointAt(angle, radius)
                drawCircle(color, 11.dp.toPx(), p)
                drawCircle(color.copy(alpha = 0.3f), 16.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                drawLabel(textMeasurer, formatTime(time), p + Offset(0f, 30.dp.toPx()), endStyle.copy(color = color))
            }

            if (now in forecast.wakeUp..forecast.bedtime) {
                drawCircle(nowColor, 4.dp.toPx(), pointAt(angleOf(now), radius))
            }
        }

        CenterText(forecast, now, muted)
    }
}

/** Arc segment of a nap on the ring: filled when logged, translucent with a dotted outline when predicted. */
private fun DrawScope.drawNap(center: Offset, radius: Float, width: Float, a0: Float, a1: Float, actual: Boolean) {
    val outer = Rect(center, radius + width / 2f)
    val inner = Rect(center, radius - width / 2f)
    val path = Path().apply {
        arcTo(outer, a0, a1 - a0, forceMoveTo = true)
        arcTo(inner, a1, a0 - a1, forceMoveTo = false)
        close()
    }
    if (actual) {
        drawPath(path, SleepColor)
    } else {
        drawPath(path, SleepColor.copy(alpha = 0.3f))
        drawPath(path, SleepColor, style = Stroke(1.5.dp.toPx(), pathEffect = dots()))
    }
}

private fun DrawScope.dots() = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx()))

/** Text centred on [center], moved inwards where it would stick out of the canvas. */
private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val left = (center.x - w / 2f).coerceIn(0f, maxOf(0f, size.width - w))
    val top = (center.y - h / 2f).coerceIn(0f, maxOf(0f, size.height - h))
    drawText(layout, topLeft = Offset(left, top))
}

/** "Drittes Nickerchen in / 1h 19m / Um 15:31", or the bedtime once nothing else is left. */
@Composable
private fun CenterText(forecast: DayForecast, now: Long, muted: Color) {
    val next = forecast.next(now)
    val (title, big, small) = when {
        next != null -> Triple("${next.title()} in", formatCountdown(minutesUntil(next.start, now)), "Um ${formatTime(next.start)}")
        now < forecast.bedtime ->
            Triple("Schlafenszeit in", formatCountdown(minutesUntil(forecast.bedtime, now)), "Um ${formatTime(forecast.bedtime)}")
        else -> Triple("Schlafenszeit", formatTime(forecast.bedtime), "Gute Nacht")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 72.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(big, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
        Text(small, style = MaterialTheme.typography.titleMedium, color = muted)
    }
}
