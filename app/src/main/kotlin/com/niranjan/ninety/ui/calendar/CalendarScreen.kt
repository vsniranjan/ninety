package com.niranjan.ninety.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DaySummary
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.day.CalendarAttendanceSheet
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.BackToTodayButton
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.NinetyDimensions
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.long
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.weekdayAndDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun CalendarScreen(semesterId: UUID, semesterStart: LocalDate, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val model = ninetyViewModel<CalendarViewModel>(key = "calendar-$semesterId-$semesterStart-$today") {
        CalendarViewModel(it.repository, semesterId, semesterStart, today)
    }
    val month by model.month.collectAsStateWithLifecycle()
    val days by model.summaries.collectAsStateWithLifecycle()
    val incomplete by model.incomplete.collectAsStateWithLifecycle()
    val byDate = days.associateBy { it.date }
    var selectedEpochDay by rememberSaveable(semesterId) { mutableStateOf<Long?>(null) }
    val openAttendance: (LocalDate) -> Unit = { selectedEpochDay = it.toEpochDay() }

    LazyColumn(Modifier.fillMaxSize().testTag("calendar_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(R.drawable.ic_back, stringResource(R.string.previous_month), { model.moveMonth(-1) },
                    modifier = Modifier.testTag("month_prev"), enabled = month > YearMonth.from(semesterStart))
                CapsText(monthLabel(month), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center)
                ActionIcon(R.drawable.ic_forward, stringResource(R.string.next_month), { model.moveMonth(1) },
                    modifier = Modifier.testTag("month_next"))
            }
            if (month != YearMonth.from(today) && today >= semesterStart) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    BackToTodayButton(onClick = { model.showToday(today) }, modifier = Modifier.testTag("calendar_today"))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthGrid(month, byDate, today, semesterStart, openAttendance)
                CalendarLegend()
            }
        }

        if (incomplete.isNotEmpty()) {
            item {
                SectionLabel(stringResource(R.string.past_days_not_marked,
                    pluralStringResource(R.plurals.day_count, incomplete.size, incomplete.size)),
                    Modifier.padding(horizontal = 24.dp))
            }
            items(incomplete, key = { it.date.toEpochDay() }) { day ->
                val unmarked = day.total - day.recorded
                ActionRow(R.drawable.ic_calendar, day.date.weekdayAndDate(), { openAttendance(day.date) },
                    modifier = Modifier.padding(horizontal = 24.dp), showChevron = true,
                    supportingText = pluralStringResource(R.plurals.course_unmarked_periods, unmarked, unmarked))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    selectedEpochDay?.let { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        CalendarAttendanceSheet(semesterId, date,
            onDismiss = { selectedEpochDay = null },
            onOpenFullDay = {
                selectedEpochDay = null
                onOpenDay(date)
            })
    }
}

private fun monthLabel(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"

@Composable
private fun MonthGrid(month: YearMonth, days: Map<LocalDate, DaySummary>, today: LocalDate, semesterStart: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells = buildList<LocalDate?> {
        repeat(lead) { add(null) }
        (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val gridWidth = maxOf(maxWidth, 48.dp * 7)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            NinetyCard(Modifier.width(gridWidth)) {
                Column(Modifier.padding(vertical = 12.dp)) {
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
                                    if (date != null) DayCell(date, days[date], today, date >= semesterStart, onOpenDay)
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private enum class CalendarMark { MARKED, INCOMPLETE, HOLIDAY, DIFFERENT_TIMETABLE }

@Composable
private fun CalendarLegend() {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((mark, label) in listOf(
            CalendarMark.MARKED to R.string.calendar_marked,
            CalendarMark.INCOMPLETE to R.string.not_marked,
            CalendarMark.HOLIDAY to R.string.holiday,
            CalendarMark.DIFFERENT_TIMETABLE to R.string.calendar_different_timetable,
        )) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CalendarMarker(mark, markColor(mark))
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun markColor(mark: CalendarMark): Color = when (mark) {
    CalendarMark.MARKED -> MaterialTheme.colorScheme.primary
    CalendarMark.INCOMPLETE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun CalendarMarker(mark: CalendarMark, color: Color, modifier: Modifier = Modifier) {
    if (mark == CalendarMark.HOLIDAY) {
        Text(stringResource(R.string.calendar_holiday_symbol), modifier,
            style = MaterialTheme.typography.labelSmall, color = color,
            textDecoration = TextDecoration.LineThrough)
    } else {
        Canvas(modifier.size(8.dp)) {
            when (mark) {
                CalendarMark.MARKED -> drawCircle(color)
                CalendarMark.INCOMPLETE -> {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(color, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                }
                CalendarMark.DIFFERENT_TIMETABLE -> drawPath(Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    close()
                }, color)
                CalendarMark.HOLIDAY -> Unit
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, summary: DaySummary?, today: LocalDate, enabled: Boolean, onOpenDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val weekend = date.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val holiday = summary?.holiday == true
    val differentTimetable = summary != null && summary.kind != DayKind.REGULAR
    val isToday = enabled && date == today
    val mark = when {
        holiday -> CalendarMark.HOLIDAY
        summary?.complete == true -> CalendarMark.MARKED
        summary?.incomplete == true -> CalendarMark.INCOMPLETE
        else -> null
    }
    val background = when {
        isToday -> colors.primary
        holiday -> colors.tertiaryContainer
        else -> Color.Transparent
    }
    val status = stringResource(when (mark) {
        CalendarMark.HOLIDAY -> R.string.holiday
        CalendarMark.MARKED -> R.string.all_periods_marked
        CalendarMark.INCOMPLETE -> R.string.periods_not_marked
        else -> if (weekend) R.string.weekend else R.string.no_marked_periods
    })
    val description = if (!enabled) stringResource(R.string.before_semester_start) else if (differentTimetable) {
        stringResource(R.string.calendar_status_with_timetable, status)
    } else status
    Box(
        Modifier.size(NinetyDimensions.touchTarget).clip(NinetyDimensions.pill).background(background)
            .clickable(enabled = enabled, role = Role.Button) { onOpenDay(date) }
            .testTag("day_cell_$date").semantics {
                contentDescription = date.long()
                stateDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
            color = when {
                !enabled -> colors.onSurface.copy(alpha = 0.38f)
                isToday -> colors.onPrimary
                holiday || weekend -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            textDecoration = if (holiday) TextDecoration.LineThrough else null)
        if (mark != null && mark != CalendarMark.HOLIDAY) {
            CalendarMarker(mark, if (isToday) colors.onPrimary else markColor(mark),
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp))
        }
        if (differentTimetable) {
            CalendarMarker(CalendarMark.DIFFERENT_TIMETABLE,
                if (isToday) colors.onPrimary else markColor(CalendarMark.DIFFERENT_TIMETABLE),
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
        }
    }
}
