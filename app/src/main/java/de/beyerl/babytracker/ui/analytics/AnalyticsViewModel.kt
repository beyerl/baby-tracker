package de.beyerl.babytracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Inclusive [start]..[end] day range shown in the analytics view. */
data class DateRange(val start: LocalDate, val end: LocalDate)

/**
 * Daily event counts per category, aligned to a continuous day axis
 * (`dates[i]` corresponds to `series[type][i]`). Days without events are 0 so
 * the line chart shows real gaps rather than skipping dates.
 */
data class AnalyticsData(
    val dates: List<LocalDate>,
    val series: Map<EventType, List<Int>>,
) {
    val isEmpty: Boolean get() = dates.isEmpty()

    companion object {
        val EMPTY = AnalyticsData(emptyList(), emptyMap())
    }
}

class AnalyticsViewModel(repository: EventRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _range = MutableStateFlow(currentMonthRange())
    val range: StateFlow<DateRange> = _range

    val data: StateFlow<AnalyticsData> =
        combine(repository.observeAll(), _range) { events, range ->
            events.toAnalyticsData(zone, range)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsData.EMPTY)

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

private fun List<Event>.toAnalyticsData(zone: ZoneId, range: DateRange): AnalyticsData {
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
    return AnalyticsData(dates, series)
}
