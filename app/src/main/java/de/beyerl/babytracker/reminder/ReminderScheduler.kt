package de.beyerl.babytracker.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.stats.Forecast
import de.beyerl.babytracker.stats.ForecastItem
import java.time.Instant
import java.time.ZoneId

/**
 * Plans one alarm for the next predicted feeding (minus the lead time), from the
 * same [Forecast] the "Prognose" tab shows. Called whenever entries or reminder
 * settings change, after each reminder and after a reboot; each call replaces
 * the previous alarm. Inexact alarms are enough for "baby gets hungry soon" and
 * need no exact-alarm permission.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "feeding_reminder"
    internal const val EXTRA_FEED_TIME = "feed_time"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Erinnerung vor der prognostizierten Fütterung"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The feeding to remind of next, or null if the reminder is off or no feeding is predicted. */
    fun nextFeed(prefs: ReminderPrefs, events: List<Event>, now: Long, zone: ZoneId = ZoneId.systemDefault()): ForecastItem? {
        if (!prefs.enabled) return null
        val after = maxOf(now + prefs.leadMinutes * 60_000L, prefs.lastNotifiedFeed)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // After the day's last feeding, plan tomorrow's first one.
        return Forecast.forDay(events, zone, today)?.nextPredictedFeed(after)
            ?: Forecast.forDay(events, zone, today.plusDays(1))?.nextPredictedFeed(after)
    }

    fun reschedule(context: Context, events: List<Event>) {
        val prefs = ReminderPrefs(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val feed = nextFeed(prefs, events, System.currentTimeMillis())
        if (feed == null) {
            alarms.cancel(pendingIntent(context, 0L))
            return
        }
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            feed.start - prefs.leadMinutes * 60_000L,
            pendingIntent(context, feed.start),
        )
    }

    private fun pendingIntent(context: Context, feedTime: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(EXTRA_FEED_TIME, feedTime),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
