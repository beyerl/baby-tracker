package de.beyerl.babytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single logged event.
 *
 * Times are stored as epoch milliseconds (UTC). For point-in-time events
 * [endTime] is null; for SLEEP it holds the wake-up time.
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
