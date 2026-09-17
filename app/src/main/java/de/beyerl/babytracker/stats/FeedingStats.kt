package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Gaps between feedings that ended on one day: total length in minutes and how many. */
data class FeedingGaps(val totalMinutes: Long, val count: Int) {
    val averageMinutes: Double get() = totalMinutes.toDouble() / count
}

/** Feeding statistics; pure java.time code like [SleepStats]. */
object FeedingStats {

    /** Longer gaps count as untracked time rather than a real interval between feedings. */
    const val MAX_GAP_HOURS = 16L

    /**
     * Gaps between consecutive feedings, grouped by the day of the later feeding
     * so that the gap across midnight counts too. Gaps of zero (duplicate
     * entries) or longer than [MAX_GAP_HOURS] are skipped.
     */
    fun gapsPerDay(events: List<Event>, zone: ZoneId): Map<LocalDate, FeedingGaps> {
        val times = events.filter { it.type == EventType.FEED }.map { it.startTime }.sorted()
        val result = HashMap<LocalDate, FeedingGaps>()
        for (i in 1 until times.size) {
            val gap = times[i] - times[i - 1]
            if (gap <= 0 || gap > MAX_GAP_HOURS * 3_600_000) continue
            val day = Instant.ofEpochMilli(times[i]).atZone(zone).toLocalDate()
            val current = result[day] ?: FeedingGaps(0, 0)
            result[day] = FeedingGaps(current.totalMinutes + gap / 60_000, current.count + 1)
        }
        return result
    }
}
