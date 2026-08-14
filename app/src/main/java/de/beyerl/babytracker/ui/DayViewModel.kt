package de.beyerl.babytracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class DayViewModel(
    private val repository: EventRepository,
    val date: LocalDate,
) : ViewModel() {

    val events: StateFlow<List<Event>> =
        repository.observeDay(date)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addPoint(type: EventType, dateTime: LocalDateTime, note: String?) {
        viewModelScope.launch { repository.addPoint(type, dateTime, note) }
    }

    fun addSleep(start: LocalDateTime, end: LocalDateTime, note: String?) {
        viewModelScope.launch { repository.addSleep(start, end, note) }
    }

    fun update(event: Event) {
        viewModelScope.launch { repository.update(event) }
    }

    fun delete(event: Event) {
        viewModelScope.launch { repository.delete(event) }
    }

    class Factory(
        private val repository: EventRepository,
        private val date: LocalDate,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DayViewModel(repository, date) as T
    }
}
