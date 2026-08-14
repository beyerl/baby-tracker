package de.beyerl.babytracker.ui.analytics

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.ui.ui
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    repository: EventRepository,
    onBack: () -> Unit,
) {
    val vm: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory(repository))
    val data by vm.data.collectAsState()
    val range by vm.range.collectAsState()

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
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Einträge pro Tag", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField("Von", range.start, Modifier.weight(1f)) { vm.setStart(it) }
                DateField("Bis", range.end, Modifier.weight(1f)) { vm.setEnd(it) }
            }

            val hasData = data.series.values.any { list -> list.any { it > 0 } }
            if (!hasData) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Einträge in diesem Zeitraum", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LineChart(
                    data = data,
                    visible = EventType.entries.toSet() - hidden,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(vertical = 12.dp),
                )
                Legend(
                    hidden = hidden,
                    onToggle = { type ->
                        hidden = if (type in hidden) hidden - type else hidden + type
                    },
                )
            }
        }
    }
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

@Composable
private fun LineChart(
    data: AnalyticsData,
    visible: Set<EventType>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.outline
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.") }

    // Scale to the tallest currently visible line so hiding a dominant
    // category zooms in on the rest.
    val maxCount = (visible.flatMap { data.series[it].orEmpty() }.maxOrNull() ?: 0)
        .coerceAtLeast(1)

    Canvas(modifier) {
        val leftPad = 30.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        val n = data.dates.size
        val xStep = if (n > 1) plotW / (n - 1) else 0f

        fun xAt(i: Int): Float = if (n > 1) leftPad + i * xStep else leftPad + plotW / 2f
        fun yAt(v: Int): Float = topPad + plotH - (v.toFloat() / maxCount) * plotH

        // Horizontal grid lines + y-axis value labels.
        val steps = minOf(maxCount, 4)
        for (s in 0..steps) {
            val value = maxCount * s / steps
            val y = yAt(value)
            drawLine(
                color = axisColor.copy(alpha = 0.4f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
            )
            val layout = textMeasurer.measure(
                value.toString(),
                style = TextStyle(fontSize = 9.sp, color = labelColor),
            )
            drawText(
                layout,
                topLeft = Offset(leftPad - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f),
            )
        }

        // Axes.
        drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotH), strokeWidth = 1.dp.toPx())
        drawLine(
            axisColor,
            Offset(leftPad, topPad + plotH),
            Offset(leftPad + plotW, topPad + plotH),
            strokeWidth = 1.dp.toPx(),
        )

        // x-axis date labels (first and, if distinct, last).
        val firstLabel = textMeasurer.measure(
            data.dates.first().format(dateFmt),
            style = TextStyle(fontSize = 9.sp, color = labelColor),
        )
        drawText(firstLabel, topLeft = Offset(leftPad, topPad + plotH + 4.dp.toPx()))
        if (n > 1) {
            val lastLabel = textMeasurer.measure(
                data.dates.last().format(dateFmt),
                style = TextStyle(fontSize = 9.sp, color = labelColor),
            )
            drawText(
                lastLabel,
                topLeft = Offset(leftPad + plotW - lastLabel.size.width, topPad + plotH + 4.dp.toPx()),
            )
        }

        // One line per visible category.
        for (type in EventType.entries) {
            if (type !in visible) continue
            val values = data.series[type] ?: continue
            val color = type.ui.color
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = xAt(i)
                val y = yAt(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
            if (n <= 62) {
                values.forEachIndexed { i, v ->
                    drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(xAt(i), yAt(v)))
                }
            }
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
