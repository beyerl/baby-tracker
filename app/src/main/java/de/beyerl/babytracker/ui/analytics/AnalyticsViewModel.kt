package de.beyerl.babytracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    val data: StateFlow<AnalyticsData> =
        repository.observeAll()
            .map { it.toAnalyticsData(zone) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsData.EMPTY)

    class Factory(private val repository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AnalyticsViewModel(repository) as T
    }
}

private fun List<Event>.toAnalyticsData(zone: ZoneId): AnalyticsData {
    if (isEmpty()) return AnalyticsData.EMPTY
    val counts = HashMap<LocalDate, IntArray>()
    for (e in this) {
        val date = Instant.ofEpochMilli(e.startTime).atZone(zone).toLocalDate()
        val arr = counts.getOrPut(date) { IntArray(EventType.entries.size) }
        arr[e.type.ordinal]++
    }
    val min = counts.keys.minOrNull()!!
    val max = counts.keys.maxOrNull()!!
    val dates = generateSequence(min) { it.plusDays(1) }
        .takeWhile { !it.isAfter(max) }
        .toList()
    val series = EventType.entries.associateWith { type ->
        dates.map { counts[it]?.get(type.ordinal) ?: 0 }
    }
    return AnalyticsData(dates, series)
}
