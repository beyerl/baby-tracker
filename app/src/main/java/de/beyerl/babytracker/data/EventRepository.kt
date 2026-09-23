package de.beyerl.babytracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Thin domain wrapper around [EventDao] that translates between local calendar
 * dates and the epoch-millis range stored in the database.
 */
class EventRepository(private val dao: EventDao) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun observeDay(date: LocalDate): Flow<List<Event>> =
        dao.observeBetween(startOfDayMillis(date), startOfDayMillis(date.plusDays(1)))

    fun observeMonth(month: YearMonth): Flow<List<Event>> =
        dao.observeBetween(
            startOfDayMillis(month.atDay(1)),
            startOfDayMillis(month.plusMonths(1).atDay(1))
        )

    suspend fun addPoint(type: EventType, dateTime: LocalDateTime, note: String? = null) {
        dao.insert(
            Event(
                type = type,
                startTime = dateTime.atZone(zone).toInstant().toEpochMilli(),
                note = note?.ifBlank { null },
            )
        )
    }

    suspend fun addSleep(
        start: LocalDateTime,
        end: LocalDateTime,
        marker: SleepMarker,
        note: String? = null,
    ) {
        dao.insert(
            Event(
                type = EventType.SLEEP,
                startTime = start.atZone(zone).toInstant().toEpochMilli(),
                endTime = end.atZone(zone).toInstant().toEpochMilli(),
                note = note?.ifBlank { null },
                sleepMarker = marker,
            )
        )
    }

    /** Every stored event, oldest first – for the Excel export. */
    suspend fun getAll(): List<Event> = dao.getAll()

    /** Reactive stream of all events, oldest first – for the analytics view. */
    fun observeAll(): Flow<List<Event>> = dao.observeAll()

    /** Adds imported events on top of the existing data. */
    suspend fun importAppend(events: List<Event>) = dao.insertAll(events)

    /** Replaces all stored data with the imported events (the old ones become tombstones). */
    suspend fun importReplace(events: List<Event>) {
        dao.deleteAll(System.currentTimeMillis())
        dao.insertAll(events)
    }

    suspend fun update(event: Event) = dao.update(event.copy(updatedAt = System.currentTimeMillis()))

    /** Keeps a tombstone so the deletion reaches the other phone. */
    suspend fun delete(event: Event) =
        dao.update(event.copy(deleted = true, updatedAt = System.currentTimeMillis()))

    /** Every row including tombstones, for the sync. */
    suspend fun getAllForSync(): List<Event> = dao.getAllForSync()

    /** Merges the other phone's events; returns how many rows changed. */
    suspend fun applyRemote(events: List<Event>): Int = dao.applyRemote(events)

    suspend fun currentChangeStamp(): String = dao.observeChangeStamp().first()

    /** Emits on every change, deletions included – a trigger for syncing. */
    fun observeChanges(): Flow<String> = dao.observeChangeStamp()
}
