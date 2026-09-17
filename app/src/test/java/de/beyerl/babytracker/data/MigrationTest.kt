package de.beyerl.babytracker.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Updating the app must keep the events already stored on the phone. Builds a
 * database the way app versions up to 0.5.0 created it, then opens it with the
 * current [AppDatabase]; Room runs the migrations and validates the schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_keepsEventsAndMarksThemNone() {
        createVersion1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            val dao = db.eventDao()
            val events = runBlocking { dao.getAll() }
            assertEquals(2, events.size)

            val sleep = events.single { it.type == EventType.SLEEP }
            assertEquals(1_000L, sleep.startTime)
            assertEquals(2_000L, sleep.endTime)
            assertEquals("Mittagsschlaf", sleep.note)
            assertEquals(3_000L, sleep.createdAt)
            assertEquals(SleepMarker.NONE, sleep.sleepMarker)
            assertEquals(SleepMarker.NONE, events.single { it.type == EventType.FEED }.sleepMarker)

            runBlocking { dao.update(sleep.copy(sleepMarker = SleepMarker.BEDTIME)) }
            assertEquals(SleepMarker.BEDTIME, runBlocking { dao.getById(sleep.id) }?.sleepMarker)
        } finally {
            db.close()
        }
    }

    /** Schema, Room bookkeeping table and version exactly as written by version 1. */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, " +
                    "`note` TEXT, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'version-1')")
            db.insert(
                "events",
                null,
                ContentValues().apply {
                    put("type", "SLEEP")
                    put("startTime", 1_000L)
                    put("endTime", 2_000L)
                    put("note", "Mittagsschlaf")
                    put("createdAt", 3_000L)
                },
            )
            db.insert(
                "events",
                null,
                ContentValues().apply {
                    put("type", "FEED")
                    put("startTime", 4_000L)
                    put("createdAt", 5_000L)
                },
            )
            db.version = 1
        }
    }
}
