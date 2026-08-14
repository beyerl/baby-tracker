package de.beyerl.babytracker.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beyerl.babytracker.data.EventRepository
import de.beyerl.babytracker.ui.DaySummary
import de.beyerl.babytracker.ui.MonthViewModel
import de.beyerl.babytracker.ui.theme.FeedColor
import de.beyerl.babytracker.ui.theme.PeeColor
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.StoolColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    repository: EventRepository,
    onDayClick: (LocalDate) -> Unit,
) {
    val vm: MonthViewModel = viewModel(factory = MonthViewModel.Factory(repository))
    val month by vm.month.collectAsState()
    val summaries by vm.summaries.collectAsState()

    val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { vm.previousMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voriger Monat")
                    }
                    IconButton(onClick = { vm.nextMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
        ) {
            WeekdayHeader()
            val cells = buildMonthCells(month.year, month.monthValue)
            LazyVerticalGrid(columns = GridCells.Fixed(7)) {
                items(cells) { date ->
                    if (date == null) {
                        Box(Modifier.aspectRatio(0.8f))
                    } else {
                        DayCell(
                            date = date,
                            summary = summaries[date],
                            isToday = date == today,
                            onClick = { onDayClick(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val days = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        days.forEach {
            Text(
                text = it,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Box(
        modifier = Modifier
            .aspectRatio(0.8f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            if (summary != null && !summary.isEmpty) {
                CountRow(summary)
            }
        }
    }
}

@Composable
private fun CountRow(summary: DaySummary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            if (summary.stool > 0) CountChip(summary.stool, StoolColor)
            if (summary.pee > 0) CountChip(summary.pee, PeeColor)
        }
        Row(horizontalArrangement = Arrangement.Center) {
            if (summary.feed > 0) CountChip(summary.feed, FeedColor)
            if (summary.sleepCount > 0) CountChip(summary.sleepCount, SleepColor)
        }
    }
}

@Composable
private fun CountChip(count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 1.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = count.toString(),
            fontSize = 9.sp,
            modifier = Modifier.padding(start = 1.dp),
        )
    }
}

/** Monday-first grid cells: leading nulls for blank days, then each date of the month. */
private fun buildMonthCells(year: Int, monthValue: Int): List<LocalDate?> {
    val first = LocalDate.of(year, monthValue, 1)
    val leading = first.dayOfWeek.value - 1 // Monday=1 -> 0 leading
    val length = first.lengthOfMonth()
    val cells = ArrayList<LocalDate?>(leading + length)
    repeat(leading) { cells.add(null) }
    for (d in 1..length) cells.add(LocalDate.of(year, monthValue, d))
    return cells
}
