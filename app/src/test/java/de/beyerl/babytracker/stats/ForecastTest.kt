package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.data.SleepMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-09-16")

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun sleep(from: String, to: String, marker: SleepMarker = SleepMarker.NONE) =
        Event(type = EventType.SLEEP, startTime = at(from), endTime = at(to), sleepMarker = marker)

    private fun feed(time: String) = Event(type = EventType.FEED, startTime = at(time))

    /** A history day: woken at [wake], naps and feedings as "HH:MM" pairs / times, bedtime 20:00. */
    private fun day(date: String, wake: String, naps: List<Pair<String, String>>, feeds: List<String>): List<Event> =
        listOf(sleep("${date}T05:00", "${date}T$wake", SleepMarker.WAKE_UP)) +
            naps.map { (s, e) -> sleep("${date}T$s", "${date}T$e") } +
            feeds.map { feed("${date}T$it") } +
            listOf(sleep("${date}T20:00", "${date}T23:00", SleepMarker.BEDTIME))

    private val week =
        day("2026-09-14", "07:00", listOf("09:00" to "10:00", "13:00" to "14:00"), listOf("08:00", "12:00", "16:00")) +
            day("2026-09-15", "08:00", listOf("10:00" to "10:30", "15:00" to "15:30"), listOf("09:00", "13:00"))

    @Test
    fun noWakeUpInThePreviousWeek_noForecast() {
        assertNull(Forecast.forDay(listOf(feed("2026-09-15T08:00")), zone, today))
    }

    @Test
    fun predictsAverageOffsetsFromAverageWakeUp() {
        val f = Forecast.forDay(week, zone, today)!!

        assertEquals(at("2026-09-16T07:30"), f.wakeUp) // mean of 07:00 and 08:00
        assertFalse(f.wakeUpActual)
        assertEquals(at("2026-09-16T20:00"), f.bedtime)
        assertEquals(2, f.historyDays)
        // Nap offsets 2 h / 2 h -> +2 h, lengths 60 / 30 min -> 45 min; 6 h / 7 h -> +6:30.
        assertEquals(
            listOf(at("2026-09-16T09:30") to at("2026-09-16T10:15"), at("2026-09-16T14:00") to at("2026-09-16T14:45")),
            f.naps.map { it.start to it.end },
        )
        assertTrue(f.items.none { it.actual })
        assertEquals(90L, f.napMinutes)
    }

    @Test
    fun feedCount_isMedianOfDailyCounts() {
        val threeDays = week +
            day("2026-09-13", "07:30", emptyList(), listOf("08:30", "11:30", "14:30", "17:30"))

        val f = Forecast.forDay(threeDays, zone, today)!!

        // Counts 4, 3, 2 -> median 3; the third feeding averages over the days that have one.
        assertEquals(3, f.feeds.size)
    }

    @Test
    fun loggedEntriesReplacePredictions_restKeepsItsSpacing() {
        val events = week +
            sleep("2026-09-16T05:00", "2026-09-16T07:30", SleepMarker.WAKE_UP) +
            sleep("2026-09-16T10:00", "2026-09-16T11:00") // first nap 30 min later than predicted

        val f = Forecast.forDay(events, zone, today)!!

        assertTrue(f.wakeUpActual)
        val (first, second) = f.naps
        assertTrue(first.actual)
        assertEquals(at("2026-09-16T10:00"), first.start)
        assertFalse(second.actual)
        assertEquals(1, second.ordinal)
        assertEquals(at("2026-09-16T14:30"), second.start) // 14:00 predicted, shifted by 30 min
    }

    @Test
    fun next_skipsPastItems() {
        val f = Forecast.forDay(week, zone, today)!!

        assertEquals(at("2026-09-16T14:00"), f.next(ForecastKind.NAP, at("2026-09-16T12:00"))?.start)
        assertEquals(ForecastKind.FEED, f.next(at("2026-09-16T12:00"))?.kind) // feeding at 12:30
        assertNull(f.next(at("2026-09-16T19:00")))
    }

    @Test
    fun predict_averagesPerOrdinal() {
        val predicted = Forecast.predict(listOf(listOf(10L to 2L, 20L to 4L), listOf(30L to 4L)))

        // Counts 1, 2 -> median (upper) 2; second ordinal only from the first day.
        assertEquals(listOf(20L to 3L, 20L to 4L), predicted)
    }
}
