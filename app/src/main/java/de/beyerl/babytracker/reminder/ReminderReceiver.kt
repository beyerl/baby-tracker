package de.beyerl.babytracker.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.beyerl.babytracker.BabyTrackerApp
import de.beyerl.babytracker.MainActivity
import de.beyerl.babytracker.R
import de.beyerl.babytracker.ui.analytics.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows the feeding reminder and plans the next one; re-plans after a reboot or app update. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REMIND) {
            val feedTime = intent.getLongExtra(ReminderScheduler.EXTRA_FEED_TIME, 0L)
            if (feedTime > 0) {
                ReminderPrefs(context).lastNotifiedFeed = feedTime
                notify(context, feedTime)
            }
        }
        val app = context.applicationContext as BabyTrackerApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(context, app.repository.getAll())
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, feedTime: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Baby bekommt gleich Hunger")
            .setContentText("Prognostizierte Fütterung um ${formatTime(feedTime)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_REMIND = "de.beyerl.babytracker.action.FEEDING_REMINDER"
        private const val NOTIFICATION_ID = 1
    }
}
