package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.beyerl.babytracker.stats.WeekAverage
import de.beyerl.babytracker.ui.theme.BedtimeColor
import de.beyerl.babytracker.ui.theme.FeedColor
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.WakeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private val dayMonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

/** What a chart's minute values mean: a time of day or a duration. Sets axis and average format. */
internal enum class ValueKind(val axisLabel: (Float) -> String, val format: (Double) -> String) {
    CLOCK(::formatClock, { formatClock((it / 60).toFloat()) }),
    DURATION(::formatHours, ::formatDuration),
}

/** Tab "Schlafenszeiten": morning wake-up and evening bedtime per day. */
@Composable
internal fun SleepTimesTab(data: AnalyticsData) {
    HourChart(
        title = "Aufgewacht",
        caption = "Ende des als Aufwachzeit markierten Schlafs",
        dates = data.dates,
        minutes = data.sleepTimes.wakeUp,
        color = WakeColor,
        kind = ValueKind.CLOCK,
        emptyText = "Keine Aufwachzeit im Zeitraum markiert",
    )
    SectionSpacer()
    HourChart(
        title = "Eingeschlafen",
        caption = "Beginn des als Schlafenszeit markierten Schlafs, am Datum des Abends",
        dates = data.dates,
        minutes = data.sleepTimes.bedtime,
        color = BedtimeColor,
        kind = ValueKind.CLOCK,
        emptyText = "Keine Schlafenszeit im Zeitraum markiert",
    )
}

/**
 * Tab "Nachtschlaf": tracked sleep per night, the time awake in between
 * ("Wachphasen nachts") and Monday–Sunday averages of both below.
 */
@Composable
internal fun NightSleepTab(data: AnalyticsData) {
    HourChart(
        title = "Nachtschlaf",
        caption = "Geschlafene Zeit von der Schlafenszeit bis zur Aufwachzeit am Folgetag, am Datum des Abends",
        dates = data.dates,
        minutes = data.nightSleep.sleep,
        color = SleepColor,
        kind = ValueKind.DURATION,
        emptyText = "Keine Nacht mit Schlafens- und Aufwachzeit im Zeitraum",
    )
    if (data.nightSleep.sleep.none { it != null }) return
    SectionSpacer()
    HourChart(
        title = "Wachphasen nachts",
        caption = "Zeit von der Schlafenszeit bis zur Aufwachzeit minus geschlafene Zeit",
        dates = data.dates,
        minutes = data.nightSleep.awake,
        color = WakeColor,
        kind = ValueKind.DURATION,
        emptyText = "",
    )
    WeeklyAverageTable(
        columns = listOf(
            WeeklyColumn("Nachtschlaf", data.nightSleep.sleepWeeks),
            WeeklyColumn("Wachphasen nachts", data.nightSleep.awakeWeeks),
        ),
        countText = { if (it == 1) "1 Nacht" else "$it Nächte" },
    )
}

/**
 * Tab "Gesamtschlaf": all sleep per completed calendar day 00:00–24:00 (night
 * sleep and naps), with Monday–Sunday averages below.
 */
@Composable
internal fun DailySleepTab(data: AnalyticsData) {
    HourChart(
        title = "Gesamtschlaf",
        caption = "Alle Schlafzeiten des Kalendertags 00:00–24:00 inkl. Nickerchen; nur abgeschlossene Tage",
        dates = data.dates,
        minutes = data.dailySleep.values,
        color = SleepColor,
        kind = ValueKind.DURATION,
        emptyText = "Keine Schlaf-Einträge an abgeschlossenen Tagen im Zeitraum",
    )
    WeeklyAverageTable(
        columns = listOf(WeeklyColumn("Gesamtschlaf", data.dailySleep.weeks)),
        countText = { if (it == 1) "1 Tag" else "$it Tage" },
    )
}

/** Tab "Wachzeit": 24 h minus all sleep per completed day, with Monday–Sunday averages below. */
@Composable
internal fun AwakeTab(data: AnalyticsData) {
    HourChart(
        title = "Wachzeit",
        caption = "24 h minus alle Schlafzeiten des Tages; nur abgeschlossene Tage",
        dates = data.dates,
        minutes = data.awake.values,
        color = WakeColor,
        kind = ValueKind.DURATION,
        emptyText = "Keine Schlaf-Einträge an abgeschlossenen Tagen im Zeitraum",
    )
    WeeklyAverageTable(
        columns = listOf(WeeklyColumn("Wachzeit", data.awake.weeks)),
        countText = { if (it == 1) "1 Tag" else "$it Tage" },
    )
}

/**
 * Tab "Fütterungen": average gap between two feedings per day; the pill shows
 * the average over all gaps in the range (not the mean of the daily averages).
 */
@Composable
internal fun FeedingTab(data: AnalyticsData) {
    val count = data.feeding.rangeGapCount
    HourChart(
        title = "Ø Abstand zwischen Fütterungen",
        caption = "Pro Tag, gezählt am Tag der späteren Fütterung; Abstände über 16 h gelten als Erfassungslücke",
        dates = data.dates,
        minutes = data.feeding.averageGap,
        color = FeedColor,
        kind = ValueKind.DURATION,
        emptyText = "Keine zwei aufeinanderfolgenden Fütterungen im Zeitraum",
        averageMinutes = data.feeding.rangeAverageGap,
        averageNote = if (count == 1) "aus 1 Abstand" else "aus $count Abständen",
    )
}

/** One metric in a [WeeklyAverageTable]. */
internal class WeeklyColumn(val title: String, val weeks: List<WeekAverage>)

/**
 * Monday–Sunday averages in a card, one block per calendar week of the first
 * column: week number, date span and number of values, then each column's average.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeklyAverageTable(columns: List<WeeklyColumn>, countText: (Int) -> String) {
    val weeks = columns.firstOrNull()?.weeks.orEmpty()
    if (weeks.isEmpty()) return
    SectionSpacer()
    SectionTitle("Wochendurchschnitt (Mo–So)")
    ChartCard {
        weeks.forEachIndexed { index, week ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
}

/**
 * Section title, then a card with caption, a smooth area chart of per-day
 * minutes on an hour axis with a dotted average line, and a
 * "Durchschnittlich: …" pill – or a hint if there is nothing to show.
 * [averageMinutes] defaults to the mean of the shown values.
 */
@Composable
internal fun HourChart(
    title: String,
    caption: String,
    dates: List<LocalDate>,
    minutes: List<Number?>,
    color: Color,
    kind: ValueKind,
    emptyText: String,
    averageMinutes: Double? = minutes.filterNotNull().map { it.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
    averageNote: String? = null,
) {
    SectionTitle(title)
    ChartCard {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        val hours = minutes.map { it?.toFloat()?.div(60f) }
        val axis = hourAxis(hours, kind.axisLabel)
        if (axis == null) {
            EmptyHint(emptyText)
            return@ChartCard
        }
        LineChart(
            dates = dates,
            lines = listOf(ChartLine(hours, color, fill = true)),
            yAxis = axis,
            average = averageMinutes?.let { (it / 60).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(vertical = 12.dp),
        )
        if (averageMinutes != null) AveragePill(kind.format(averageMinutes), averageNote)
    }
}

/** Napper-style section heading above a card. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun SectionSpacer() = Spacer(Modifier.height(24.dp))

/** Rounded card holding a chart or table. */
@Composable
internal fun ChartCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        content = content,
    )
}

/** "Durchschnittlich: 09:10" pill under a chart, with an optional note below. */
@Composable
internal fun AveragePill(value: String, note: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildAnnotatedString {
                append("Durchschnittlich: ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                    append(value)
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
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
