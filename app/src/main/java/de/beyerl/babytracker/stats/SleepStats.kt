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

/** The night from the bedtime on the evening of [date] to the wake-up on the following morning. */
data class Night(
    val date: LocalDate,
    val bedtime: Long,
    val wakeUp: Long,
    /** Tracked sleep between bedtime and wake-up, in minutes ("Nachtschlaf"). */
    val sleepMinutes: Long,
) {
    /** Time from bedtime to wake-up, in minutes. */
    val inBedMinutes: Long get() = (wakeUp - bedtime) / 60_000

    /** "Wachphasen nachts": time from bedtime to wake-up without tracked sleep, in minutes. */
    val awakeMinutes: Long get() = (inBedMinutes - sleepMinutes).coerceAtLeast(0)
}

/**
 * Sleep statistics derived from SLEEP events and their sleep markers. Pure
 * java.time code (no Android APIs), so it is covered by plain JVM unit tests.
 */
object SleepStats {

    /** Bedtimes starting before this hour belong to the previous evening. */
    const val EVENING_CUTOFF_HOUR = 12

    /** Longest plausible night; longer bedtime/wake-up pairs count as mis-marked. */
    const val MAX_NIGHT_HOURS = 20L

    /**
     * Nights from each evening's [bedtimes] entry to the [wakeUps] entry of the
     * following day, keyed by the evening. Pairs out of order or longer than
     * [MAX_NIGHT_HOURS] are skipped. A night's sleep is the time covered by SLEEP
     * events within bedtime..wake-up; overlapping events count once.
     */
    fun nights(events: List<Event>, zone: ZoneId): Map<LocalDate, Night> {
        val bedtimes = bedtimes(events, zone)
        val wakeUps = wakeUps(events, zone)
        val sleeps = events.sleepIntervals()
        val result = HashMap<LocalDate, Night>()
        for ((evening, bedtime) in bedtimes) {
            val wakeUp = wakeUps[evening.plusDays(1)] ?: continue
            val length = wakeUp.epochMillis - bedtime.epochMillis
            if (length <= 0 || length > MAX_NIGHT_HOURS * 3_600_000) continue
            val sleep = coveredMillis(sleeps, bedtime.epochMillis, wakeUp.epochMillis)
            result[evening] = Night(evening, bedtime.epochMillis, wakeUp.epochMillis, sleep / 60_000)
        }
        return result
    }

    /**
     * Awake time ("Wachzeit") per calendar day in minutes: the day's length (23 or
     * 25 h when the clocks change) minus the time covered by SLEEP events that
     * day. Sleep across midnight is split between both days; days without any
     * sleep are left out. Callers pass completed days only.
     */
    fun awakeMinutesPerDay(events: List<Event>, zone: ZoneId, days: List<LocalDate>): Map<LocalDate, Long> {
        val sleeps = events.sleepIntervals()
        val result = HashMap<LocalDate, Long>()
        for (day in days) {
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val slept = coveredMillis(sleeps, from, to)
            if (slept > 0) result[day] = (to - from - slept) / 60_000
        }
        return result
    }

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
