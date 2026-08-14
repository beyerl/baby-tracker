package de.beyerl.babytracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Per-day aggregate shown in the month grid. */
data class DaySummary(
    val stool: Int = 0,
    val pee: Int = 0,
    val feed: Int = 0,
    val sleepCount: Int = 0,
) {
    val isEmpty: Boolean get() = stool == 0 && pee == 0 && feed == 0 && sleepCount == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(private val repository: EventRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    val summaries: StateFlow<Map<LocalDate, DaySummary>> =
        _month
            .flatMapLatest { repository.observeMonth(it) }
            .map { events -> events.groupByDay() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun showMonth(month: YearMonth) {
        _month.value = month
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    private fun List<Event>.groupByDay(): Map<LocalDate, DaySummary> {
        val result = mutableMapOf<LocalDate, DaySummary>()
        for (e in this) {
            val date = Instant.ofEpochMilli(e.startTime).atZone(zone).toLocalDate()
            val cur = result[date] ?: DaySummary()
            result[date] = when (e.type) {
                EventType.STOOL -> cur.copy(stool = cur.stool + 1)
                EventType.PEE -> cur.copy(pee = cur.pee + 1)
                EventType.FEED -> cur.copy(feed = cur.feed + 1)
                EventType.SLEEP -> cur.copy(sleepCount = cur.sleepCount + 1)
            }
        }
        return result
    }

    class Factory(private val repository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MonthViewModel(repository) as T
    }
}
