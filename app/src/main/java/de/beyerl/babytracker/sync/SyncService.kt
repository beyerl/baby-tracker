package de.beyerl.babytracker.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.beyerl.babytracker.BabyTrackerApp
import de.beyerl.babytracker.MainActivity
import de.beyerl.babytracker.R

/**
 * Keeps the sync listening while the app is closed, so the other phone can
 * sync whenever it opens its app. A foreground service with a silent,
 * low-priority notification – Android stops plain background work.
 */
class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Synchronisierung", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Hält die Synchronisierung mit dem anderen Handy im Hintergrund bereit"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Synchronisierung bereit")
            .setContentText("Gleicht Einträge im WLAN mit dem anderen Handy ab")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        (application as BabyTrackerApp).sync.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        (application as BabyTrackerApp).sync.release()
        super.onDestroy()
    }

    /** Starts the service again after a reboot or app update. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update(context)
    }

    companion object {
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 2

        /** Runs the service if paired and background sync is on, else stops it. */
        fun update(context: Context) {
            val prefs = SyncPrefs(context)
            val intent = Intent(context, SyncService::class.java)
            if (prefs.isPaired && prefs.backgroundEnabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
