package de.beyerl.babytracker.reminder

import android.content.Context
import android.content.SharedPreferences

/** Settings of the feeding reminder, stored in SharedPreferences. */
class ReminderPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** How many minutes before the predicted feeding the reminder goes off. */
    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD, 10)
        set(value) = prefs.edit().putInt(KEY_LEAD, value).apply()

    /** Start of the feeding last reminded of, so it isn't reminded of twice. */
    var lastNotifiedFeed: Long
        get() = prefs.getLong(KEY_LAST_NOTIFIED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NOTIFIED, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEAD = "lead_minutes"
        private const val KEY_LAST_NOTIFIED = "last_notified_feed"

        /** Lead times offered in the forecast tab. */
        val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30)
    }
}
