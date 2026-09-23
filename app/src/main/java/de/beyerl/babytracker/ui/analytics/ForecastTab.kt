package de.beyerl.babytracker.ui.analytics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.core.content.ContextCompat
import de.beyerl.babytracker.BabyTrackerApp
import de.beyerl.babytracker.reminder.ReminderPrefs
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
    Spacer(Modifier.height(16.dp))
    FeedCountdownBanner(forecast, now)
    Spacer(Modifier.height(12.dp))
    ReminderCard(forecast, now)
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

/**
 * "Füttern in 24 min ⏱ 12:15" with a bar filling up from the last feeding
 * (or the wake-up) to the next predicted one, like Napper's nap banner.
 */
@Composable
private fun FeedCountdownBanner(forecast: DayForecast, now: Long) {
    val next = forecast.next(ForecastKind.FEED, now) ?: return
    val since = forecast.feeds.lastOrNull { it.start <= now }?.start ?: forecast.wakeUp
    val progress = ((now - since).toFloat() / (next.start - since).coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text("🍼", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Füttern in ${formatCountdown(minutesUntil(next.start, now))}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(formatTime(next.start), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = FeedColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Reminder settings: on/off and the lead time, plus when the next reminder
 * goes off. Switching on asks for the notification permission (Android 13+).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderCard(forecast: DayForecast, now: Long) {
    val context = LocalContext.current
    val prefs = remember { ReminderPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var lead by remember { mutableStateOf(prefs.leadMinutes) }
    fun apply() = (context.applicationContext as? BabyTrackerApp)?.rescheduleReminder()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.enabled = granted
        enabled = granted
        apply()
    }
    fun setEnabled(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        prefs.enabled = on
        enabled = on
        apply()
    }

    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Erinnerung vor dem Füttern", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Benachrichtigung, wenn das Baby laut Prognose gleich Hunger bekommt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Switch(checked = enabled, onCheckedChange = ::setEnabled)
        }
        if (!enabled) return@ChartCard
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderPrefs.LEAD_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = minutes == lead,
                    onClick = {
                        prefs.leadMinutes = minutes
                        lead = minutes
                        apply()
                    },
                    label = { Text(if (minutes == 0) "pünktlich" else "$minutes min vorher") },
                )
            }
        }
        val next = forecast.nextPredictedFeed(maxOf(now + lead * 60_000L, prefs.lastNotifiedFeed))
        Text(
            if (next != null) {
                "Nächste Erinnerung um ${formatTime(next.start - lead * 60_000L)} (Fütterung um ${formatTime(next.start)})"
            } else {
                "Heute ist keine weitere Fütterung prognostiziert."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
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
