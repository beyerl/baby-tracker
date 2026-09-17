package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.WakeColor
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Tab "Schlafenszeiten": morning wake-up and evening bedtime per day. */
@Composable
internal fun SleepTimesTab(data: AnalyticsData) {
    HourChart(
        title = "Aufgewacht",
        caption = "Ende des als Aufwachzeit markierten Schlafs",
        dates = data.dates,
        minutes = data.sleepTimes.wakeUp,
        color = WakeColor,
        yLabel = ::formatClock,
        emptyText = "Keine Aufwachzeit im Zeitraum markiert",
    )
    Spacer(Modifier.height(24.dp))
    HourChart(
        title = "Eingeschlafen",
        caption = "Beginn des als Schlafenszeit markierten Schlafs, am Datum des Abends",
        dates = data.dates,
        minutes = data.sleepTimes.bedtime,
        color = SleepColor,
        yLabel = ::formatClock,
        emptyText = "Keine Schlafenszeit im Zeitraum markiert",
    )
}

/** Title, caption and a one-line chart of per-day minutes on an hour axis, or a hint if empty. */
@Composable
internal fun HourChart(
    title: String,
    caption: String,
    dates: List<LocalDate>,
    minutes: List<Number?>,
    color: Color,
    yLabel: (Float) -> String,
    emptyText: String,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    val hours = minutes.map { it?.toFloat()?.div(60f) }
    val axis = hourAxis(hours, yLabel)
    if (axis == null) {
        EmptyHint(emptyText)
    } else {
        LineChart(
            dates = dates,
            lines = listOf(ChartLine(hours, color)),
            yAxis = axis,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

/** Clock time of an hour value: 7.5 -> "07:30"; values past 24 h wrap (bedtime after midnight). */
internal fun formatClock(hours: Float): String {
    val minutes = (hours * 60).roundToInt()
    return String.format(Locale.ROOT, "%02d:%02d", (minutes / 60) % 24, minutes % 60)
}
