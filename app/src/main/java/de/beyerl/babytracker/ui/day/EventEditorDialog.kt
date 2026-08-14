package de.beyerl.babytracker.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
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
    onConfirmSleep: (LocalDateTime, LocalDateTime, String?) -> Unit,
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
                    onConfirmSleep(start, end, note)
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
