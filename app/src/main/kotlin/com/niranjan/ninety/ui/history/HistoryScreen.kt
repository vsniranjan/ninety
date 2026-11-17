package com.niranjan.ninety.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DaySummary
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.long
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.short
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale
import java.util.UUID

@Composable
fun HistoryScreen(semesterId: UUID, today: LocalDate = LocalDate.now(), onOpenDay: (LocalDate) -> Unit) {
    val model = ninetyViewModel<HistoryViewModel>(key = "history-$semesterId-$today") {
        HistoryViewModel(it.repository, semesterId, today)
    }
    val month by model.month.collectAsStateWithLifecycle()
    val days by model.summaries.collectAsStateWithLifecycle()
    val incomplete by model.incomplete.collectAsStateWithLifecycle()
    val byDate = days.associateBy { it.date }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { model.moveMonth(-1) }, modifier = Modifier.testTag("month_prev")) {
                    Icon(painterResource(R.drawable.ic_back), "Previous month")
                }
                Text(monthLabel(month), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center)
                IconButton(onClick = { model.moveMonth(1) }, modifier = Modifier.testTag("month_next")) {
                    Icon(painterResource(R.drawable.ic_forward), "Next month")
                }
            }
        }
        item { MonthGrid(month, byDate, today, onOpenDay) }
        item { Text("Dots show recorded or incomplete days. Tap a date to see its classes and status.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (incomplete.isNotEmpty()) {
            item { SectionLabel("Needs attention · ${incomplete.size} incomplete") }
            items(incomplete.take(12)) { date ->
                NinetyCard(Modifier.fillMaxWidth(), onClick = { onOpenDay(date) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(date.medium(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("Unrecorded", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun monthLabel(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"

@Composable
private fun MonthGrid(month: YearMonth, days: Map<LocalDate, DaySummary>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells = buildList<LocalDate?> {
        repeat(lead) { add(null) }
        (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
    }
    NinetyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                (collegeWeekdays() + listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)).forEach { day ->
                    Text(day.getDisplayName(TextStyle.NARROW, LocalLocale.current.platformLocale),
                        Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (date != null) DayCell(date, days[date], today, onOpenDay)
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, summary: DaySummary?, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val weekend = date.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val bg = when {
        summary?.holiday == true -> MaterialTheme.colorScheme.tertiaryContainer
        summary?.complete == true -> MaterialTheme.colorScheme.primaryContainer
        summary?.incomplete == true -> MaterialTheme.colorScheme.errorContainer
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Column(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (date == today) MaterialTheme.colorScheme.surfaceVariant else bg)
            .clickable { onOpenDay(date) }
            .testTag("day_cell_$date").semantics {
                contentDescription = date.long()
                stateDescription = when {
                    summary?.holiday == true -> "Holiday"
                    summary?.complete == true -> "All periods recorded"
                    summary?.incomplete == true -> "Unrecorded periods"
                    weekend -> "Weekend"
                    else -> "No recorded classes"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
            color = when {
                weekend -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            })
        if (summary != null && summary.total > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val color = when {
                    summary.holiday -> MaterialTheme.colorScheme.tertiary
                    summary.complete -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
                Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                if (summary.kind != DayKind.REGULAR) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                }
            }
        }
    }
}
