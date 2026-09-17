package de.beyerl.babytracker.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.data.SleepMarker
import de.beyerl.babytracker.ui.label
import de.beyerl.babytracker.ui.sleepMarkerOptions
import de.beyerl.babytracker.ui.ui
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorDialog(
    date: LocalDate,
    type: EventType,
    existing: Event?,
    onDismiss: () -> Unit,
    onConfirmPoint: (LocalDateTime, String?) -> Unit,
    onConfirmSleep: (LocalDateTime, LocalDateTime, SleepMarker, String?) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val startTime: LocalTime = existing?.let {
        Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalTime()
    } ?: LocalTime.now()
    val endTime: LocalTime = existing?.endTime?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
    } ?: LocalTime.now()

    val startState = rememberTimePickerState(startTime.hour, startTime.minute, is24Hour = true)
    val endState = rememberTimePickerState(endTime.hour, endTime.minute, is24Hour = true)
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var marker by remember { mutableStateOf(existing?.sleepMarker ?: SleepMarker.NONE) }

    val ui = type.ui
    val isSleep = type.isInterval

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (existing == null) "Neu: " else "Bearbeiten: ") + ui.emoji + " " + ui.label) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(if (isSleep) "Von" else "Uhrzeit")
                TimeInput(state = startState)
                if (isSleep) {
                    Spacer(Modifier.height(8.dp))
                    Text("Bis")
                    TimeInput(state = endState)
                    Text("Markierung")
                    SleepMarkerSelector(selected = marker, onSelect = { marker = it })
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = date.atTime(startState.hour, startState.minute)
                if (isSleep) {
                    var end = date.atTime(endState.hour, endState.minute)
                    if (!end.isAfter(start)) end = end.plusDays(1) // overnight sleep
                    onConfirmSleep(start, end, marker, note)
                } else {
                    onConfirmPoint(start, note)
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/** Radio buttons marking a sleep as the night's bedtime and/or wake-up. */
@Composable
private fun SleepMarkerSelector(selected: SleepMarker, onSelect: (SleepMarker) -> Unit) {
    Column(Modifier.selectableGroup()) {
        sleepMarkerOptions.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(option.label)
            }
        }
    }
}
