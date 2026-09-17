package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FeedingStatsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun event(type: EventType, dateTime: String) =
        Event(type = type, startTime = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli())

    private fun feed(dateTime: String) = event(EventType.FEED, dateTime)

    private fun day(date: String): LocalDate = LocalDate.parse(date)

    @Test
    fun gaps_countOnTheDayOfTheLaterFeeding() {
        val events = listOf(
            feed("2026-09-15T01:00"),
            feed("2026-09-14T18:00"),
            event(EventType.PEE, "2026-09-15T02:00"),
            feed("2026-09-15T03:00"),
            feed("2026-09-14T21:00"),
        )

        val gaps = FeedingStats.gapsPerDay(events, zone)

        assertEquals(FeedingGaps(totalMinutes = 180, count = 1), gaps[day("2026-09-14")]) // 18:00 -> 21:00
        assertEquals(FeedingGaps(totalMinutes = 360, count = 2), gaps[day("2026-09-15")]) // 21:00 -> 01:00 -> 03:00
        assertEquals(180.0, gaps.getValue(day("2026-09-15")).averageMinutes, 1e-9)
    }

    @Test
    fun duplicatesAndGapsOver16Hours_areSkipped() {
        val events = listOf(
            feed("2026-09-14T06:00"),
            feed("2026-09-14T06:00"),
            feed("2026-09-15T08:00"),
            feed("2026-09-15T10:30"),
        )

        val gaps = FeedingStats.gapsPerDay(events, zone)

        assertEquals(mapOf(day("2026-09-15") to FeedingGaps(totalMinutes = 150, count = 1)), gaps)
    }
}
