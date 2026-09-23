package de.beyerl.babytracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.stats.FeedingStats
import de.beyerl.babytracker.stats.SleepStats
import de.beyerl.babytracker.stats.WeekAverage
import de.beyerl.babytracker.stats.weeklyAverages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Inclusive [start]..[end] day range shown in the analytics view. */
data class DateRange(val start: LocalDate, val end: LocalDate)

/**
 * Everything the analytics tabs show, aligned to a continuous day axis
 * (`dates[i]` corresponds to index i of every series). Daily counts are 0 on
 * days without events so the line chart shows real gaps rather than skipping
 * dates; statistic values are null on days without a value.
 */
data class AnalyticsData(
    val dates: List<LocalDate>,
    val series: Map<EventType, List<Int>>,
    val sleepTimes: SleepTimesData,
    val nightSleep: NightSleepData,
    val awake: AwakeData,
    val feeding: FeedingData,
) {
    val isEmpty: Boolean get() = dates.isEmpty()

    companion object {
        val EMPTY = AnalyticsData(
            dates = emptyList(),
            series = emptyMap(),
            sleepTimes = SleepTimesData(emptyList(), emptyList()),
            nightSleep = NightSleepData(emptyList(), emptyList(), emptyList(), emptyList()),
            awake = AwakeData(emptyList(), emptyList()),
            feeding = FeedingData(emptyList(), null, 0),
        )
    }
}

/** Wall-clock minutes of the morning wake-up and the evening bedtime (> 24 h after midnight) per day. */
data class SleepTimesData(val wakeUp: List<Int?>, val bedtime: List<Int?>)

/**
 * Per night (at the evening's date), in minutes: tracked sleep ("Nachtschlaf")
 * and the time awake in between ("Wachphasen nachts"), each with Monday–Sunday averages.
 */
data class NightSleepData(
    val sleep: List<Long?>,
    val awake: List<Long?>,
    val sleepWeeks: List<WeekAverage>,
    val awakeWeeks: List<WeekAverage>,
)

/** Awake time ("Wachzeit") per completed day in minutes and its Monday–Sunday averages. */
data class AwakeData(val values: List<Long?>, val weeks: List<WeekAverage>)

/** Average gap between feedings in minutes: per day, and over all [rangeGapCount] gaps in the range. */
data class FeedingData(
    val averageGap: List<Double?>,
    val rangeAverageGap: Double?,
    val rangeGapCount: Int,
)

class AnalyticsViewModel(repository: EventRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _range = MutableStateFlow(currentMonthRange())
    val range: StateFlow<DateRange> = _range

    val data: StateFlow<AnalyticsData> =
        combine(repository.observeAll(), _range) { events, range ->
            events.toAnalyticsData(zone, range, today = LocalDate.now(zone))
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsData.EMPTY)

    /** Sets the range start; pushes the end out too if it would precede start. */
    fun setStart(date: LocalDate) {
        val cur = _range.value
        _range.value = DateRange(date, if (date.isAfter(cur.end)) date else cur.end)
    }

    /** Sets the range end; pulls the start in too if it would follow end. */
    fun setEnd(date: LocalDate) {
        val cur = _range.value
        _range.value = DateRange(if (date.isBefore(cur.start)) date else cur.start, date)
    }

    class Factory(private val repository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AnalyticsViewModel(repository) as T
    }
}

private fun currentMonthRange(): DateRange {
    val month = YearMonth.now()
    return DateRange(month.atDay(1), month.atEndOfMonth())
}

private fun List<Event>.toAnalyticsData(zone: ZoneId, range: DateRange, today: LocalDate): AnalyticsData {
    if (range.start.isAfter(range.end)) return AnalyticsData.EMPTY
    val counts = HashMap<LocalDate, IntArray>()
    for (e in this) {
        val date = Instant.ofEpochMilli(e.startTime).atZone(zone).toLocalDate()
        if (date.isBefore(range.start) || date.isAfter(range.end)) continue
        val arr = counts.getOrPut(date) { IntArray(EventType.entries.size) }
        arr[e.type.ordinal]++
    }
    val dates = generateSequence(range.start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(range.end) }
        .toList()
    val series = EventType.entries.associateWith { type ->
        dates.map { counts[it]?.get(type.ordinal) ?: 0 }
    }

    // Statistics run over all events and are then read for the range's days, so
    // values assigned across the range boundary (e.g. a bedtime after midnight) stay intact.
    val wakeUps = SleepStats.wakeUps(this, zone)
    val bedtimes = SleepStats.bedtimes(this, zone)
    val nights = SleepStats.nights(this, zone)
    val nightsInRange = dates.mapNotNull { nights[it] }
    // Today isn't over yet; its awake time would be overstated.
    val awake = SleepStats.awakeMinutesPerDay(this, zone, dates.filter { it.isBefore(today) })
    val feedingGaps = FeedingStats.gapsPerDay(this, zone)
    val feedingGapsInRange = dates.mapNotNull { feedingGaps[it] }
    val rangeGapCount = feedingGapsInRange.sumOf { it.count }

    return AnalyticsData(
        dates = dates,
        series = series,
        sleepTimes = SleepTimesData(
            wakeUp = dates.map { wakeUps[it]?.clockMinutes },
            bedtime = dates.map { bedtimes[it]?.clockMinutes },
        ),
        nightSleep = NightSleepData(
            sleep = dates.map { nights[it]?.sleepMinutes },
            awake = dates.map { nights[it]?.awakeMinutes },
            sleepWeeks = weeklyAverages(nightsInRange.associate { it.date to it.sleepMinutes.toDouble() }),
            awakeWeeks = weeklyAverages(nightsInRange.associate { it.date to it.awakeMinutes.toDouble() }),
        ),
        awake = AwakeData(
            values = dates.map { awake[it] },
            weeks = weeklyAverages(awake.mapValues { it.value.toDouble() }),
        ),
        feeding = FeedingData(
            averageGap = dates.map { feedingGaps[it]?.averageMinutes },
            rangeAverageGap = if (rangeGapCount > 0) {
                feedingGapsInRange.sumOf { it.totalMinutes }.toDouble() / rangeGapCount
            } else {
                null
            },
            rangeGapCount = rangeGapCount,
        ),
    )
}
