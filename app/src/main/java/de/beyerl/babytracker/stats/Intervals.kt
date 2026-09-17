package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType

/** Start/end (epoch millis) of every SLEEP event that has an end. */
internal fun List<Event>.sleepIntervals(): List<Pair<Long, Long>> =
    mapNotNull { e -> e.endTime?.takeIf { e.type == EventType.SLEEP }?.let { e.startTime to it } }

/**
 * Milliseconds of the window [from, to) covered by at least one of [intervals].
 * Intervals are clipped to the window and overlapping parts count once.
 */
internal fun coveredMillis(intervals: List<Pair<Long, Long>>, from: Long, to: Long): Long {
    var total = 0L
    var coveredUntil = from
    for ((start, end) in intervals.sortedBy { it.first }) {
        val s = maxOf(start, coveredUntil)
        val e = minOf(end, to)
        if (e > s) {
            total += e - s
            coveredUntil = e
        }
    }
    return total
}
