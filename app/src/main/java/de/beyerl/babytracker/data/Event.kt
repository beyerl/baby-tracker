package de.beyerl.babytracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single logged event.
 *
 * Times are stored as epoch milliseconds (UTC). For point-in-time events
 * [endTime] is null; for SLEEP it holds the wake-up time. [sleepMarker] only
 * applies to SLEEP and stays [SleepMarker.NONE] for all other types.
 *
 * For syncing between phones, [uuid] identifies the event on every phone
 * ([id] is local only), [updatedAt] orders concurrent changes and [deleted]
 * keeps a deleted event as a tombstone so the deletion reaches the other phone.
 */
@Entity(tableName = "events", indices = [Index(value = ["uuid"], unique = true)])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sleepMarker: SleepMarker = SleepMarker.NONE,
    val uuid: String = UUID.randomUUID().toString(),
    val updatedAt: Long = createdAt,
    val deleted: Boolean = false,
)
