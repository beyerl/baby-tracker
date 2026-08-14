package de.beyerl.babytracker

import android.app.Application
import de.beyerl.babytracker.data.AppDatabase
import de.beyerl.babytracker.data.EventRepository

/** App entry point; owns the database and repository (simple manual DI). */
class BabyTrackerApp : Application() {
    val repository: EventRepository by lazy {
        EventRepository(AppDatabase.get(this).eventDao())
    }
}
