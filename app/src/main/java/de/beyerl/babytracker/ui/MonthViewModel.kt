package de.beyerl.babytracker.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.export.ExcelExporter
import de.beyerl.babytracker.export.ExcelImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Outcome of parsing an Excel file for import. */
sealed interface ImportResult {
    data class Ready(val count: Int) : ImportResult
    data object Error : ImportResult
}

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

    /**
     * Exports all stored events as an xlsx file to [uri] (a document created via
     * the system "create document" picker). [onResult] is called on the main
     * thread with whether the write succeeded.
     */
    fun exportToExcel(resolver: ContentResolver, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val events = repository.getAll()
                    resolver.openOutputStream(uri)?.use { ExcelExporter.write(events, it) }
                        ?: error("Could not open output stream")
                }.isSuccess
            }
            onResult(success)
        }
    }

    // Parsed-but-not-yet-committed events, awaiting the user's add/replace choice.
    private var pendingImport: List<Event>? = null

    /** Reads and parses an Excel file, then reports how many events were found. */
    fun prepareImport(resolver: ContentResolver, uri: Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { ExcelImporter.read(it) }
                        ?: error("Could not open input stream")
                }
            }
            parsed.fold(
                onSuccess = { events ->
                    pendingImport = events
                    onResult(ImportResult.Ready(events.size))
                },
                onFailure = { onResult(ImportResult.Error) },
            )
        }
    }

    /** Commits the previously parsed import, either appending or replacing. */
    fun confirmImport(replace: Boolean, onDone: (Int) -> Unit) {
        val events = pendingImport
        pendingImport = null
        if (events == null) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (replace) repository.importReplace(events) else repository.importAppend(events)
            }
            onDone(events.size)
        }
    }

    fun cancelImport() {
        pendingImport = null
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
