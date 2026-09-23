package de.beyerl.babytracker

import android.app.Application
import de.beyerl.babytracker.data.AppDatabase
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.reminder.ReminderScheduler
import de.beyerl.babytracker.sync.SyncManager
import de.beyerl.babytracker.sync.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** App entry point; owns the database and repository (simple manual DI). */
class BabyTrackerApp : Application() {
    val repository: EventRepository by lazy {
        EventRepository(AppDatabase.get(this).eventDao())
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Phone-to-phone sync; runs while the app is in the foreground or [SyncService] holds it. */
    val sync: SyncManager by lazy { SyncManager(this, repository, appScope) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        // Every new, edited or deleted entry changes the forecast, so re-plan the reminder.
        appScope.launch {
            repository.observeAll().debounce(1_000).collect { ReminderScheduler.reschedule(this@BabyTrackerApp, it) }
        }
    }

    /** Re-plans the reminder now, e.g. after its settings changed. */
    fun rescheduleReminder() {
        appScope.launch { ReminderScheduler.reschedule(this@BabyTrackerApp, repository.getAll()) }
    }
}
