package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A marked bedtime or wake-up. */
data class SleepMoment(
    val epochMillis: Long,
    /** Wall-clock minutes after midnight of the assigned day; > 24 h for bedtimes after midnight. */
    val clockMinutes: Int,
)

/**
 * Sleep statistics derived from SLEEP events and their sleep markers. Pure
 * java.time code (no Android APIs), so it is covered by plain JVM unit tests.
 */
object SleepStats {

    /** Bedtimes starting before this hour belong to the previous evening. */
    const val EVENING_CUTOFF_HOUR = 12

    /**
     * Evening bedtime per day: the start of a bedtime-marked sleep. A start after
     * midnight (before [EVENING_CUTOFF_HOUR]) counts for the previous evening and
     * plots past 24:00. With several marks per evening the earliest wins.
     */
    fun bedtimes(events: List<Event>, zone: ZoneId): Map<LocalDate, SleepMoment> {
        val result = HashMap<LocalDate, SleepMoment>()
        for (e in events) {
            if (e.type != EventType.SLEEP || !e.sleepMarker.isBedtime) continue
            val local = Instant.ofEpochMilli(e.startTime).atZone(zone).toLocalDateTime()
            val minutes = local.hour * 60 + local.minute
            val evening: LocalDate
            val clockMinutes: Int
            if (local.hour < EVENING_CUTOFF_HOUR) {
                evening = local.toLocalDate().minusDays(1)
                clockMinutes = minutes + 24 * 60
            } else {
                evening = local.toLocalDate()
                clockMinutes = minutes
            }
            val current = result[evening]
            if (current == null || e.startTime < current.epochMillis) {
                result[evening] = SleepMoment(e.startTime, clockMinutes)
            }
        }
        return result
    }

    /**
     * Morning wake-up per day: the end of a wake-up-marked sleep, on the day it
     * ends. With several marks per day the latest wins.
     */
    fun wakeUps(events: List<Event>, zone: ZoneId): Map<LocalDate, SleepMoment> {
        val result = HashMap<LocalDate, SleepMoment>()
        for (e in events) {
            val end = e.endTime ?: continue
            if (e.type != EventType.SLEEP || !e.sleepMarker.isWakeUp) continue
            val local = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime()
            val day = local.toLocalDate()
            val current = result[day]
            if (current == null || end > current.epochMillis) {
                result[day] = SleepMoment(end, local.hour * 60 + local.minute)
            }
        }
        return result
    }
}
