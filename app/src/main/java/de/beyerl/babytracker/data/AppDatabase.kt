package de.beyerl.babytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Event::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 adds [Event.sleepMarker]; existing rows become NONE. SQLite needs a
         * default to add a NOT NULL column. The entity declares none, so Room's
         * schema validation doesn't compare it.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN sleepMarker TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /**
         * v3 adds the sync fields: a random [Event.uuid] per existing row (unique
         * index as Room declares it), [Event.updatedAt] = createdAt, not deleted.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE events SET uuid = lower(hex(randomblob(16))), updatedAt = createdAt")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_events_uuid ON events (uuid)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baby-tracker.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
