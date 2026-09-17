package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.data.SleepMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SleepStatsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun day(date: String): LocalDate = LocalDate.parse(date)

    private fun sleep(from: String, to: String, marker: SleepMarker = SleepMarker.NONE) =
        Event(type = EventType.SLEEP, startTime = at(from), endTime = at(to), sleepMarker = marker)

    private val night = listOf(
        sleep("2026-09-14T13:00", "2026-09-14T14:00"),
        sleep("2026-09-14T19:45", "2026-09-14T23:30", SleepMarker.BEDTIME),
        sleep("2026-09-15T00:10", "2026-09-15T06:50", SleepMarker.WAKE_UP),
    )

    @Test
    fun bedtime_isStartOfBedtimeMarkedSleepOnThatEvening() {
        val bedtimes = SleepStats.bedtimes(night, zone)

        assertEquals(setOf(day("2026-09-14")), bedtimes.keys)
        assertEquals(19 * 60 + 45, bedtimes.getValue(day("2026-09-14")).clockMinutes)
        assertEquals(at("2026-09-14T19:45"), bedtimes.getValue(day("2026-09-14")).epochMillis)
    }

    @Test
    fun wakeUp_isEndOfWakeUpMarkedSleepOnTheDayItEnds() {
        val wakeUps = SleepStats.wakeUps(night, zone)

        assertEquals(setOf(day("2026-09-15")), wakeUps.keys)
        assertEquals(6 * 60 + 50, wakeUps.getValue(day("2026-09-15")).clockMinutes)
    }

    @Test
    fun bedtimeAfterMidnight_belongsToPreviousEveningAndPlotsPast24h() {
        val events = listOf(sleep("2026-09-15T00:30", "2026-09-15T07:00", SleepMarker.BEDTIME))

        val bedtime = SleepStats.bedtimes(events, zone).getValue(day("2026-09-14"))

        assertEquals(24 * 60 + 30, bedtime.clockMinutes)
    }

    @Test
    fun bothMarker_countsAsBedtimeAndWakeUp() {
        val events = listOf(sleep("2026-09-14T20:00", "2026-09-15T07:15", SleepMarker.BOTH))

        assertEquals(20 * 60, SleepStats.bedtimes(events, zone).getValue(day("2026-09-14")).clockMinutes)
        assertEquals(7 * 60 + 15, SleepStats.wakeUps(events, zone).getValue(day("2026-09-15")).clockMinutes)
    }

    @Test
    fun severalMarks_earliestBedtimeAndLatestWakeUpWin() {
        val events = listOf(
            sleep("2026-09-14T20:30", "2026-09-14T22:00", SleepMarker.BEDTIME),
            sleep("2026-09-14T19:30", "2026-09-14T20:10", SleepMarker.BEDTIME),
            sleep("2026-09-15T05:30", "2026-09-15T07:30", SleepMarker.WAKE_UP),
            sleep("2026-09-15T03:00", "2026-09-15T05:00", SleepMarker.WAKE_UP),
        )

        assertEquals(19 * 60 + 30, SleepStats.bedtimes(events, zone).getValue(day("2026-09-14")).clockMinutes)
        assertEquals(7 * 60 + 30, SleepStats.wakeUps(events, zone).getValue(day("2026-09-15")).clockMinutes)
    }

    @Test
    fun night_sumsSleepBetweenBedtimeAndNextMorningsWakeUp() {
        val nights = SleepStats.nights(night, zone)

        val n = nights.getValue(day("2026-09-14"))
        assertEquals(setOf(day("2026-09-14")), nights.keys)
        assertEquals(11 * 60 + 5L, n.inBedMinutes) // 19:45 -> 06:50
        assertEquals(3 * 60 + 45 + 6 * 60 + 40L, n.sleepMinutes) // 19:45-23:30 + 00:10-06:50, nap excluded
        assertEquals(40L, n.awakeMinutes) // 23:30-00:10
    }

    @Test
    fun night_sleptThroughInOneEntry() {
        val nights = SleepStats.nights(listOf(sleep("2026-09-14T20:00", "2026-09-15T07:00", SleepMarker.BOTH)), zone)

        val n = nights.getValue(day("2026-09-14"))
        assertEquals(11 * 60L, n.sleepMinutes)
        assertEquals(11 * 60L, n.inBedMinutes)
        assertEquals(0L, n.awakeMinutes)
    }

    @Test
    fun nightSleep_isClippedToTheNightAndCountsOverlapsOnce() {
        val events = listOf(
            sleep("2026-09-14T17:00", "2026-09-14T18:00"), // nap before bedtime
            sleep("2026-09-14T20:00", "2026-09-14T23:00", SleepMarker.BEDTIME),
            sleep("2026-09-14T22:00", "2026-09-14T23:30"), // overlaps the entry above
            sleep("2026-09-15T01:00", "2026-09-15T06:00", SleepMarker.WAKE_UP),
            sleep("2026-09-15T05:30", "2026-09-15T06:30"), // runs past the wake-up
            sleep("2026-09-15T09:00", "2026-09-15T10:00"), // morning nap
        )

        val n = SleepStats.nights(events, zone).getValue(day("2026-09-14"))

        assertEquals(10 * 60L, n.inBedMinutes)
        assertEquals(3 * 60 + 30 + 5 * 60L, n.sleepMinutes) // 20:00-23:30 + 01:00-06:00
        assertEquals(90L, n.awakeMinutes) // 23:30-01:00, not double-reduced by the overlap
    }

    @Test
    fun night_withoutWakeUpOnTheNextDay_isSkipped() {
        val events = listOf(
            sleep("2026-09-14T20:00", "2026-09-14T23:00", SleepMarker.BEDTIME),
            sleep("2026-09-16T01:00", "2026-09-16T06:00", SleepMarker.WAKE_UP),
        )

        assertTrue(SleepStats.nights(events, zone).isEmpty())
    }

    @Test
    fun night_longerThan20Hours_isSkipped() {
        val events = listOf(
            sleep("2026-09-14T12:30", "2026-09-14T14:00", SleepMarker.BEDTIME),
            sleep("2026-09-15T08:00", "2026-09-15T09:00", SleepMarker.WAKE_UP),
        )

        assertTrue(SleepStats.nights(events, zone).isEmpty())
    }

    @Test
    fun unmarkedSleepAndOtherCategories_areIgnored() {
        val events = listOf(
            sleep("2026-09-14T20:00", "2026-09-15T07:00"),
            Event(type = EventType.FEED, startTime = at("2026-09-14T21:00"), sleepMarker = SleepMarker.BOTH),
        )

        assertTrue(SleepStats.bedtimes(events, zone).isEmpty())
        assertTrue(SleepStats.wakeUps(events, zone).isEmpty())
    }
}
