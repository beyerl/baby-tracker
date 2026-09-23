package de.beyerl.babytracker.ui.analytics

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.beyerl.babytracker.stats.DayForecast
import de.beyerl.babytracker.stats.Forecast
import de.beyerl.babytracker.stats.ForecastKind
import de.beyerl.babytracker.ui.theme.BedtimeColor
import de.beyerl.babytracker.ui.theme.FeedColor
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.SunriseColor

private const val PREFS = "forecast"
private const val KEY_INTRO_SEEN = "intro_seen"

/**
 * Tab "Prognose": today's naps and feedings predicted from the previous week,
 * as a Napper-style ring with a countdown to the next event, the planned nap
 * time and the full list of times.
 */
@Composable
internal fun ForecastTab(forecast: DayForecast?, now: Long) {
    if (forecast == null) {
        SectionTitle("Prognose für heute")
        ChartCard {
            EmptyHint(
                "Für eine Prognose wird mindestens ein Tag der letzten ${Forecast.HISTORY_DAYS} Tage " +
                    "mit markierter Aufwachzeit benötigt.",
            )
        }
        return
    }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var introSeen by remember { mutableStateOf(prefs.getBoolean(KEY_INTRO_SEEN, false)) }

    ForecastRing(forecast, now)

    if (!introSeen) {
        IntroCard(onContinue = {
            prefs.edit().putBoolean(KEY_INTRO_SEEN, true).apply()
            introSeen = true
        })
        return
    }

    NapTotalPill(forecast.napMinutes)
    SectionSpacer()
    SectionTitle("Tagesablauf")
    ChartCard { Timeline(forecast, now) }
    Text(
        "Grundlage: Nickerchen und Fütterungen von ${forecast.historyDays} der letzten ${Forecast.HISTORY_DAYS} Tage " +
            "(Tage mit Aufwachzeit), jeweils ab dem Aufwachen gerechnet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** "Juhu, deine erste Prognose!" – shown once, as in Napper's first forecast. */
@Composable
private fun IntroCard(onContinue: () -> Unit) {
    ChartCard {
        Text("Juhu, deine erste Prognose!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Da ist sie! Die Prognose, wie der Rest deines Tages aussehen wird – berechnet aus den " +
                "Nickerchen und Fütterungen der letzten Woche.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(onClick = onContinue, shape = RoundedCornerShape(50)) {
            Text("Weiter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
        }
    }
}

/** Planned nap time of the day under the ring (logged + predicted). */
@Composable
private fun NapTotalPill(minutes: Long) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 28.dp, vertical = 10.dp),
        ) {
            Text("Nickerchen gesamt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Text(formatCountdown(minutes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Wake-up, every nap and feeding, and bedtime in order; logged ones marked as such. */
@Composable
private fun Timeline(forecast: DayForecast, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TimelineRow(SunriseColor, "Aufwachen", formatTime(forecast.wakeUp), forecast.wakeUpActual, past = true)
        forecast.items.forEach { item ->
            val time = formatTime(item.start) + (item.end?.let { "–" + formatTime(it) } ?: "")
            val color = if (item.kind == ForecastKind.NAP) SleepColor else FeedColor
            TimelineRow(color, item.title(), time, item.actual, past = item.start <= now)
        }
        TimelineRow(BedtimeColor, "Schlafenszeit", formatTime(forecast.bedtime), forecast.bedtimeActual, past = false)
    }
}

@Composable
private fun TimelineRow(color: Color, title: String, time: String, actual: Boolean, past: Boolean) {
    val textColor = if (past && !actual) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(12.dp))
        Text(title, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            if (actual) "erfasst" else "Prognose",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(time, color = textColor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
