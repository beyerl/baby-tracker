package de.beyerl.babytracker.data

import kotlinx.coroutines.flow.Flow
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

    suspend fun addSleep(start: LocalDateTime, end: LocalDateTime, note: String? = null) {
        dao.insert(
            Event(
                type = EventType.SLEEP,
                startTime = start.atZone(zone).toInstant().toEpochMilli(),
                endTime = end.atZone(zone).toInstant().toEpochMilli(),
                note = note?.ifBlank { null },
            )
        )
    }

    /** Every stored event, oldest first – for the Excel export. */
    suspend fun getAll(): List<Event> = dao.getAll()

    suspend fun update(event: Event) = dao.update(event)

    suspend fun delete(event: Event) = dao.delete(event)
}
