package com.niranjan.ninety.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DayView
import com.niranjan.ninety.data.SessionView
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.NinetyDimensions
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.long
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarAttendanceSheet(semesterId: UUID, date: LocalDate,
    onDismiss: () -> Unit, onOpenFullDay: () -> Unit) {
    val model = ninetyViewModel<DayViewModel>(key = "calendar-attendance-$semesterId") {
        DayViewModel(it.repository, semesterId, date)
    }
    LaunchedEffect(model, date) {
        model.dismissMessage()
        model.setDate(date)
    }
    val loaded by model.day.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val hapticsOn by model.haptics.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val day = loaded?.takeIf { it.date == date }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() } * 0.77f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("calendar_attendance_sheet"),
        shape = NinetyDimensions.sheet,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(date.long(), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("calendar_attendance_date").semantics { heading() })
                    val status = when {
                        day == null -> null
                        day.holiday -> stringResource(R.string.holiday_classes)
                        day.future -> stringResource(R.string.mark_future_day)
                        day.total > 0 -> stringResource(R.string.mark_progress, day.recorded, day.total)
                        else -> null
                    }
                    if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                ActionIcon(R.drawable.ic_close, stringResource(R.string.close_attendance_sheet), onDismiss,
                    modifier = Modifier.testTag("calendar_attendance_close"))
            }

            if (day == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (!day.holiday) {
                key(date) {
                    AttendanceList(day, busy, Modifier.weight(1f, fill = false), onRecord = { id, outcome ->
                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        model.record(id, outcome)
                    })
                }
            }
            message?.let {
                Text(displayMessage(it), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (day != null && day.writable && !day.holiday && day.unrecordedEligible > 0) {
                FilledTonalButton(
                    onClick = {
                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        model.markAllPresent()
                    },
                    enabled = !busy,
                    shape = NinetyDimensions.pill,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)
                        .testTag("calendar_mark_all"),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (day.recorded == 0) R.string.mark_all_present else R.string.mark_remaining_present))
                }
            }
            TextButton(onClick = onOpenFullDay, enabled = !busy,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
                    .testTag("calendar_open_full_day")) {
                Text(stringResource(R.string.open_full_day))
                Spacer(Modifier.width(4.dp))
                Icon(painterResource(R.drawable.ic_forward), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AttendanceList(day: DayView, busy: Boolean, modifier: Modifier, onRecord: (UUID, AttendanceOutcome) -> Unit) {
    val groups = day.groups.filter { group -> group.cells.any { it.session != null || it.projectedCourseName != null } }
    if (groups.isEmpty() && day.extras.isEmpty()) {
        Text(stringResource(R.string.no_timetable_classes), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        return
    }

    LazyColumn(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.cells.first().slot }) { group ->
            val name = group.cells.firstNotNullOf { it.session?.courseName ?: it.projectedCourseName }
            if (group.grouped) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            group.cells.forEach { cell ->
                if (cell.session != null || cell.projectedCourseName != null) {
                    AttendancePeriod(
                        course = name, caption = "${slotLabel(cell.slot)} · ${PeriodTimes.label(cell.startMinute)}",
                        session = cell.session, showCourse = !group.grouped, writable = day.writable,
                        busy = busy, onRecord = onRecord,
                    )
                }
            }
        }
        items(day.extras, key = { it.id.toString() }) { session ->
            AttendancePeriod(
                course = session.courseName,
                caption = "${stringResource(R.string.extra)} · ${PeriodTimes.label(session.startMinute)}",
                session = session, showCourse = true, writable = day.writable, busy = busy, onRecord = onRecord,
            )
        }
    }
}

@Composable
private fun AttendancePeriod(course: String, caption: String, session: SessionView?, showCourse: Boolean,
    writable: Boolean, busy: Boolean, onRecord: (UUID, AttendanceOutcome) -> Unit) {
    BoxWithConstraints {
        val compact = maxWidth < 274.dp * LocalDensity.current.fontScale
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (showCourse) Text(course, style = MaterialTheme.typography.titleMedium)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (session != null) {
                if (writable && session.loggable && session.counting && session.outcome != AttendanceOutcome.DUTY_LEAVE) {
                    val identity = "$course · ${slotLabel(session.slot)}"
                    MarkControl(session.outcome, enabled = !busy, compact = compact,
                        tag = "calendar_period_${session.id}",
                        presentAction = stringResource(R.string.mark_present_for, identity),
                        absentAction = stringResource(R.string.mark_absent_for, identity),
                        onMark = { if (it != session.outcome) onRecord(session.id, it) })
                } else if (session.recorded || !session.loggable || !session.counting) {
                    OutcomeChip(session.outcome, cancelled = session.cancelled, counting = session.counting)
                }
            }
        }
    }
}
