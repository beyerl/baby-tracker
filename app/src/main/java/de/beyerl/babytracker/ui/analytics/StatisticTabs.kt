package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.beyerl.babytracker.stats.WeekAverage
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.WakeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private val dayMonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

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

/** Tab "Gesamtschlaf": tracked sleep per night with Monday–Sunday averages below. */
@Composable
internal fun NightSleepTab(data: AnalyticsData) {
    HourChart(
        title = "Gesamtschlaf",
        caption = "Geschlafene Zeit von der Schlafenszeit bis zur Aufwachzeit am Folgetag, am Datum des Abends",
        dates = data.dates,
        minutes = data.nightSleep.sleep,
        color = SleepColor,
        yLabel = ::formatHours,
        emptyText = "Keine Nacht mit Schlafens- und Aufwachzeit im Zeitraum",
    )
    WeeklyAverageTable(
        columns = listOf(WeeklyColumn("Gesamtschlaf", data.nightSleep.weeks)),
        countText = { if (it == 1) "1 Nacht" else "$it Nächte" },
    )
}

/** One metric in a [WeeklyAverageTable]. */
internal class WeeklyColumn(val title: String, val weeks: List<WeekAverage>)

/**
 * Monday–Sunday averages, one block per calendar week of the first column:
 * week number, date span and number of values, then each column's average.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeklyAverageTable(columns: List<WeeklyColumn>, countText: (Int) -> String) {
    val weeks = columns.firstOrNull()?.weeks.orEmpty()
    if (weeks.isEmpty()) return
    Text(
        "Wochendurchschnitt (Mo–So)",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    weeks.forEach { week ->
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "KW ${week.monday.get(WeekFields.ISO.weekOfWeekBasedYear())}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "  ${week.monday.format(dayMonthFmt)}–${week.monday.plusDays(6).format(dayMonthFmt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Text(countText(week.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            columns.forEach { column ->
                val average = column.weeks.firstOrNull { it.monday == week.monday }?.average
                Text(
                    "${column.title}: " + (average?.let { "Ø " + formatDuration(it) } ?: "–"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
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

/** Axis label for a duration in hours: 10.0 -> "10 h". */
internal fun formatHours(hours: Float): String = "${hours.roundToInt()} h"

/** Duration in minutes as hours and minutes: 605.0 -> "10 h 05 min". */
internal fun formatDuration(minutes: Double): String {
    val total = minutes.roundToInt()
    return String.format(Locale.ROOT, "%d h %02d min", total / 60, total % 60)
}
