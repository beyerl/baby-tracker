package de.beyerl.babytracker.stats

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import java.time.LocalDate
import java.time.ZoneId

enum class ForecastKind { NAP, FEED }

/**
 * A nap or feeding on the forecast day. [ordinal] counts per kind from 0 (first
 * nap, second nap, …). [end] is set for naps only. [actual] items are logged
 * entries; the others are predictions.
 */
data class ForecastItem(
    val kind: ForecastKind,
    val ordinal: Int,
    val start: Long,
    val end: Long?,
    val actual: Boolean,
)

/** The forecast day from wake-up to bedtime, epoch millis throughout. */
data class DayForecast(
    val date: LocalDate,
    val wakeUp: Long,
    val wakeUpActual: Boolean,
    val bedtime: Long,
    val bedtimeActual: Boolean,
    /** Naps and feedings, sorted by start. */
    val items: List<ForecastItem>,
    /** Previous-week days with a wake-up that the prediction is based on. */
    val historyDays: Int,
) {
    val naps: List<ForecastItem> get() = items.filter { it.kind == ForecastKind.NAP }
    val feeds: List<ForecastItem> get() = items.filter { it.kind == ForecastKind.FEED }

    /** Sum of all nap lengths of the day, logged and predicted, in minutes. */
    val napMinutes: Long get() = naps.sumOf { (it.end ?: it.start) - it.start } / 60_000

    /** The first nap or feeding still ahead at [now], or null if none is left today. */
    fun next(now: Long): ForecastItem? = items.firstOrNull { it.start > now }

    /** The first item of [kind] still ahead at [now]. */
    fun next(kind: ForecastKind, now: Long): ForecastItem? = items.firstOrNull { it.kind == kind && it.start > now }
}

/**
 * Forecast of a day's naps and feedings from the previous week, like the
 * Napper day forecast. Pure java.time code like [SleepStats].
 *
 * Every history day with a wake-up contributes its naps ([SleepStats.naps]) and
 * its feedings between wake-up and bedtime, each as time since that wake-up.
 * The n-th nap (feeding) is predicted at the average offset of all n-th naps
 * (feedings), with the average nap length; the number of naps (feedings) is
 * the median of the daily counts. Entries already logged on the forecast day
 * replace the predictions in order; the remaining predictions keep their
 * spacing to the last logged entry of the same kind.
 */
object Forecast {

    const val HISTORY_DAYS = 7L

    /** Feedings more than this after wake-up count as night feedings without a bedtime mark. */
    private const val MAX_DAY_HOURS = 16L

    fun forDay(events: List<Event>, zone: ZoneId, day: LocalDate): DayForecast? {
        val wakeUps = SleepStats.wakeUps(events, zone)
        val bedtimes = SleepStats.bedtimes(events, zone)
        val naps = SleepStats.naps(events, zone)
        val feedTimes = events.filter { it.type == EventType.FEED }.map { it.startTime }.sorted()

        fun midnight(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        fun dayEnd(d: LocalDate, wake: Long) = bedtimes[d]?.epochMillis ?: (wake + MAX_DAY_HOURS * 3_600_000)
        fun feedsOf(d: LocalDate, wake: Long) = feedTimes.filter { it >= wake && it < dayEnd(d, wake) }

        val history = (HISTORY_DAYS downTo 1).map { day.minusDays(it) }.filter { it in wakeUps }
        if (history.isEmpty()) return null

        // Offsets from each history day's wake-up, in millis.
        val napHistory = history.map { d ->
            val wake = wakeUps.getValue(d).epochMillis
            naps[d].orEmpty().map { (start, end) -> (start - wake) to (end - start) }
        }
        val feedHistory = history.map { d ->
            val wake = wakeUps.getValue(d).epochMillis
            feedsOf(d, wake).map { (it - wake) to 0L }
        }

        val actualWake = wakeUps[day]?.epochMillis
        val wake = actualWake
            ?: (midnight(day) + history.map { wakeUps.getValue(it).clockMinutes }.average().toLong() * 60_000)
        val bedtimeHistory = history.mapNotNull { bedtimes[it]?.clockMinutes }

        val todayNaps = naps[day].orEmpty().filter { it.first >= wake }
        val todayFeeds = feedsOf(day, wake).map { it to it }

        val items = merge(ForecastKind.NAP, predict(napHistory), todayNaps, wake) +
            merge(ForecastKind.FEED, predict(feedHistory), todayFeeds, wake)

        val bedtime = bedtimes[day]?.epochMillis
            ?: bedtimeHistory.takeIf { it.isNotEmpty() }?.let { midnight(day) + it.average().toLong() * 60_000 }
            ?: ((items.maxOfOrNull { it.end ?: it.start } ?: wake) + 3_600_000)

        return DayForecast(
            date = day,
            wakeUp = wake,
            wakeUpActual = actualWake != null,
            bedtime = bedtime,
            bedtimeActual = day in bedtimes,
            items = items.filter { it.actual || it.start < bedtime }.sortedBy { it.start },
            historyDays = history.size,
        )
    }

    /**
     * Average (offset, length) per ordinal over the days that have that many
     * entries, for as many ordinals as the median day has.
     */
    internal fun predict(days: List<List<Pair<Long, Long>>>): List<Pair<Long, Long>> {
        val counts = days.map { it.size }.sorted()
        val count = if (counts.isEmpty()) 0 else counts[counts.size / 2]
        return (0 until count).map { n ->
            val nth = days.mapNotNull { it.getOrNull(n) }
            nth.map { it.first }.average().toLong() to nth.map { it.second }.average().toLong()
        }
    }

    /**
     * Logged entries ([actual] start/end, sorted) first, then the predictions for
     * the later ordinals, shifted so they keep their predicted spacing to the
     * last logged entry.
     */
    private fun merge(
        kind: ForecastKind,
        predicted: List<Pair<Long, Long>>,
        actual: List<Pair<Long, Long>>,
        wake: Long,
    ): List<ForecastItem> {
        val isNap = kind == ForecastKind.NAP
        val logged = actual.mapIndexed { i, (start, end) ->
            ForecastItem(kind, i, start, if (isNap) end else null, actual = true)
        }
        val shift = if (actual.isNotEmpty() && actual.size <= predicted.size) {
            actual.last().first - (wake + predicted[actual.size - 1].first)
        } else {
            0L
        }
        val upcoming = predicted.drop(actual.size).mapIndexed { i, (offset, length) ->
            val start = wake + offset + shift
            ForecastItem(kind, actual.size + i, start, if (isNap) start + length else null, actual = false)
        }
        return logged + upcoming
    }
}
