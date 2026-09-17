package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.ui.ui
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Tabs of the analytics screen; all share the Von/Bis range. */
private enum class AnalyticsTab(val title: String) {
    ENTRIES("Einträge"),
    SLEEP_TIMES("Schlafenszeiten"),
    NIGHT_SLEEP("Gesamtschlaf"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    repository: EventRepository,
    onBack: () -> Unit,
) {
    val vm: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory(repository))
    val data by vm.data.collectAsState()
    val range by vm.range.collectAsState()

    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.ENTRIES) }
    // Categories currently hidden via the legend; empty = all lines shown.
    var hidden by remember { mutableStateOf(emptySet<EventType>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auswertung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField("Von", range.start, Modifier.weight(1f)) { vm.setStart(it) }
                DateField("Bis", range.end, Modifier.weight(1f)) { vm.setEnd(it) }
            }

            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp) {
                AnalyticsTab.entries.forEach { t ->
                    Tab(selected = t == tab, onClick = { tab = t }, text = { Text(t.title) })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (tab) {
                    AnalyticsTab.ENTRIES -> EntriesTab(
                        data = data,
                        hidden = hidden,
                        onToggle = { type -> hidden = if (type in hidden) hidden - type else hidden + type },
                    )
                    AnalyticsTab.SLEEP_TIMES -> SleepTimesTab(data)
                    AnalyticsTab.NIGHT_SLEEP -> NightSleepTab(data)
                }
            }
        }
    }
}

/** Tab "Einträge": daily event counts, one line per category, with a toggleable legend. */
@Composable
private fun EntriesTab(
    data: AnalyticsData,
    hidden: Set<EventType>,
    onToggle: (EventType) -> Unit,
) {
    Text("Einträge pro Tag", style = MaterialTheme.typography.titleMedium)

    val hasData = data.series.values.any { list -> list.any { it > 0 } }
    if (!hasData) {
        EmptyHint("Keine Einträge in diesem Zeitraum")
        return
    }
    val visible = EventType.entries.filter { it !in hidden }
    LineChart(
        dates = data.dates,
        lines = visible.mapNotNull { type ->
            data.series[type]?.let { counts -> ChartLine(counts.map { it.toFloat() }, type.ui.color) }
        },
        // Scale to the tallest currently visible line so hiding a dominant
        // category zooms in on the rest.
        yAxis = countAxis(visible.flatMap { data.series[it].orEmpty() }.maxOrNull() ?: 0),
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(vertical = 12.dp),
    )
    Legend(hidden = hidden, onToggle = onToggle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    modifier: Modifier = Modifier,
    onDateChange: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Icon(Icons.Filled.DateRange, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("$label: ${date.format(dayFmt)}")
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(it.toLocalDateUtc()) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(
    hidden: Set<EventType>,
    onToggle: (EventType) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventType.entries.forEach { type ->
            val ui = type.ui
            val isOn = type !in hidden
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onToggle(type) },
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (isOn) ui.color else MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = ui.label,
                    color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    textDecoration = if (isOn) null else TextDecoration.LineThrough,
                )
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
