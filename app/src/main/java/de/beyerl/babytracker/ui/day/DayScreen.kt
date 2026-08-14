package de.beyerl.babytracker.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.ui.DayViewModel
import de.beyerl.babytracker.ui.pointCategories
import de.beyerl.babytracker.ui.ui
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    repository: EventRepository,
    date: LocalDate,
    onBack: () -> Unit,
) {
    val vm: DayViewModel = viewModel(factory = DayViewModel.Factory(repository, date))
    val events by vm.events.collectAsState()

    var editorType by remember { mutableStateOf<EventType?>(null) }
    var editing by remember { mutableStateOf<Event?>(null) }

    val title = date.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            DaySummaryBar(events)
            AddButtonRow(onAdd = { type -> editorType = type; editing = null })

            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Einträge", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(events, key = { it.id }) { event ->
                        EventRow(
                            event = event,
                            onClick = { editing = event; editorType = event.type },
                            onDelete = { vm.delete(event) },
                        )
                    }
                }
            }
        }
    }

    val type = editorType
    if (type != null) {
        EventEditorDialog(
            date = date,
            type = type,
            existing = editing,
            onDismiss = { editorType = null; editing = null },
            onConfirmPoint = { dt, note ->
                val e = editing
                if (e == null) vm.addPoint(type, dt, note)
                else vm.update(e.copy(startTime = dt.toEpochMillis(), note = note?.ifBlank { null }))
                editorType = null; editing = null
            },
            onConfirmSleep = { start, end, note ->
                val e = editing
                if (e == null) vm.addSleep(start, end, note)
                else vm.update(
                    e.copy(
                        startTime = start.toEpochMillis(),
                        endTime = end.toEpochMillis(),
                        note = note?.ifBlank { null },
                    )
                )
                editorType = null; editing = null
            },
        )
    }
}

@Composable
private fun DaySummaryBar(events: List<Event>) {
    val stool = events.count { it.type == EventType.STOOL }
    val pee = events.count { it.type == EventType.PEE }
    val feed = events.count { it.type == EventType.FEED }
    val sleep = events.count { it.type == EventType.SLEEP }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryStat(EventType.STOOL, stool)
        SummaryStat(EventType.PEE, pee)
        SummaryStat(EventType.FEED, feed)
        SummaryStat(EventType.SLEEP, sleep)
    }
}

@Composable
private fun SummaryStat(type: EventType, count: Int) {
    val ui = type.ui
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ui.emoji)
        Text(count.toString(), fontWeight = FontWeight.Bold, color = ui.color)
        Text(ui.label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AddButtonRow(onAdd: (EventType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (pointCategories + EventType.SLEEP).forEach { type ->
            val ui = type.ui
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ui.color.copy(alpha = 0.15f))
                    .clickable { onAdd(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.emoji)
                    Text("+ ${ui.label}", style = MaterialTheme.typography.labelSmall, color = ui.color)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: Event, onClick: () -> Unit, onDelete: () -> Unit) {
    val ui = event.type.ui
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ui.color),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.label, fontWeight = FontWeight.SemiBold)
                val time = formatTime(event)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                event.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun formatTime(event: Event): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(event.startTime).atZone(zone).toLocalTime().format(timeFmt)
    val end = event.endTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(timeFmt) }
    return if (end != null) {
        val mins = (event.endTime!! - event.startTime).coerceAtLeast(0) / 60000
        "$start–$end · ${mins / 60}h ${mins % 60}min"
    } else {
        start
    }
}

private fun java.time.LocalDateTime.toEpochMillis(): Long =
    this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
