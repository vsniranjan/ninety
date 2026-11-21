package com.niranjan.ninety.ui.day

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DayView
import com.niranjan.ninety.data.SessionView
import com.niranjan.ninety.data.SlotCell
import com.niranjan.ninety.data.SlotGroup
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.domain.SessionOrigin
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.BackToTodayButton
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.GhostButton
import com.niranjan.ninety.ui.designsystem.NinetyAlertDialog
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.NinetyDimensions
import com.niranjan.ninety.ui.designsystem.NinetyMenu
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.designsystem.PeriodProgress
import com.niranjan.ninety.ui.designsystem.PrimaryAction
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.fullName
import com.niranjan.ninety.ui.long
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import com.niranjan.ninety.ui.timeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(semesterId: UUID, initialDate: LocalDate?, today: LocalDate,
    snackbar: SnackbarHostState, onEditTimetable: () -> Unit,
    archived: Boolean = false, semesterStart: LocalDate, resetToToday: Int = 0) {
    var selectedEpochDay by rememberSaveable(semesterId.toString(), resetToToday) { mutableStateOf(initialDate?.toEpochDay()) }
    var catchingUp by rememberSaveable(semesterId.toString(), resetToToday) { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val date = selectedEpochDay?.let(LocalDate::ofEpochDay) ?: today
    val beforeSemester = date < semesterStart
    val model = ninetyViewModel<DayViewModel>(key = "day-$semesterId") {
        DayViewModel(it.repository, semesterId, date)
    }
    LaunchedEffect(model, date) { model.setDate(date) }
    val loadedDay by model.day.collectAsStateWithLifecycle()
    val day = loadedDay?.takeIf { it.date == date }
    val courses by model.courses.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    val busy by model.busy.collectAsStateWithLifecycle()
    val earlierIncomplete = if (initialDate == null) {
        remember(model, today) { model.observeEarlierIncomplete(today) }
            .collectAsStateWithLifecycle(initialValue = emptyList()).value
    } else emptyList()
    fun openDate(target: LocalDate, catchUp: Boolean = false) {
        catchingUp = catchUp
        selectedEpochDay = target.takeUnless { it == today }?.toEpochDay()
    }

    var selected by remember(date) { mutableStateOf<SessionView?>(null) }
    var dialog by rememberSaveable(date.toEpochDay()) { mutableStateOf<DayDialog?>(null) }
    var leavePicker by remember(date) { mutableStateOf<DutyLeaveSelection?>(null) }
    var menuOpen by remember(date) { mutableStateOf(false) }
    var replaceLeave by remember(date) { mutableStateOf<(() -> Unit)?>(null) }
    var copyResult by remember(date) { mutableStateOf<com.niranjan.ninety.data.CopyResult?>(null) }
    var markedCopyConflicts by remember(date) { mutableIntStateOf(0) }

    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    val hapticsOn by model.haptics.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val tapHaptic = { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.Confirm) }

    if (pickDate) NinetyDatePicker(date, onPicked = { openDate(it); pickDate = false },
        onDismiss = { pickDate = false })

    when (val view = day) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag("day_loading"))
        }
        else -> key(date) { DayContent(view, date == today, busy, archived, beforeSemester,
            catchUp = {
                val oldest = earlierIncomplete.firstOrNull { it != date }
                if (date == today && oldest != null) {
                    ActionRow(R.drawable.ic_history,
                        pluralStringResource(R.plurals.catch_up_earlier_days, earlierIncomplete.size, earlierIncomplete.size),
                        onClick = { if (!busy) openDate(oldest, catchUp = true) }, showChevron = true,
                        modifier = Modifier.testTag("catch_up").padding(bottom = 12.dp))
                }
            },
            nextUnmarked = if (catchingUp) earlierIncomplete.firstOrNull { it != date }?.let { next ->
                { openDate(next, catchUp = true) }
            } else null,
            onPrev = { openDate(date.minusDays(1)) },
            onNext = { openDate(date.plusDays(1)) },
            onToday = { openDate(today) },
            onPickDate = { pickDate = true },
            onSessionClick = { selected = it },
            onQuickRecord = { session, outcome -> tapHaptic(); model.record(session.id, outcome) },
            onGroupOutcome = { group, outcome ->
                tapHaptic()
                model.recordUnrecorded(group.pendingPeriods().map { it.id }, outcome)
            },
            onGroupLeave = { group ->
                val ids = group.countingPeriods().filter { it.canMarkDutyLeave }.map { it.id }.toSet()
                leavePicker = DutyLeaveSelection(scope = ids, selected = ids)
            },
            onMarkAll = { tapHaptic(); model.markAllPresent() },
            onToggleHoliday = { dialog = DayDialog.Holiday },
            onCopyDay = { dialog = DayDialog.CopyDay },
            onEditTimetable = onEditTimetable,
            onAddExtra = { dialog = DayDialog.Extra },
            onApplyLeave = { leavePicker = DutyLeaveSelection() },
            menuOpen = menuOpen, onMenu = { menuOpen = it },
        ) }
    }

    selected?.let { session ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState,
            shape = NinetyDimensions.sheet, containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp) {
            SessionActions(session, courses.map { UUID.fromString(it.id) to it.name },
                onRecord = { outcome -> model.record(session.id, outcome); selected = null },
                onCancel = { cancelled -> model.setCancelled(session.id, cancelled); selected = null },
                onSubstitute = { courseId -> model.substitute(session.id, courseId); selected = null },
                onCounting = { counting -> model.setExtraCounting(session.id, counting); selected = null },
                onRemove = { model.removeExtra(session.id); selected = null },
            )
        }
    }

    when (dialog) {
        DayDialog.Holiday -> ConfirmDialog(
            title = stringResource(if (day?.holiday == true) R.string.remove_holiday_question else R.string.mark_holiday_question),
            body = if (day?.future == true)
                stringResource(if (day.holiday) R.string.remove_future_holiday_body else R.string.future_holiday_body)
            else if (day?.holiday == true)
                "Classes on this day will count toward attendance again."
            else
                stringResource(R.string.keep_marked_holiday),
            confirm = stringResource(if (day?.holiday == true) R.string.undo_holiday else R.string.mark_holiday),
            onConfirm = { model.setHoliday(day?.holiday != true); dialog = null },
            onDismiss = { dialog = null })
        DayDialog.CopyDay -> CopyDayDialog(
            onPick = { weekday ->
                val markedIds = (day?.groups.orEmpty().flatMap { group -> group.cells.mapNotNull { it.session } } +
                    day?.extras.orEmpty()).filter { it.recorded }.map { it.id }.toSet()
                model.copyDay(weekday) { result ->
                    markedCopyConflicts = result.conflicts.count { it.sessionId in markedIds }
                    dialog = null
                    copyResult = result
                }
            },
            onDismiss = { dialog = null })
        DayDialog.Extra -> ExtraClassDialog(courses.filter { !it.retired },
            onSave = { courseId, start, end, counting ->
                model.addExtra(courseId, start, end, counting); dialog = null
            },
            onDismiss = { dialog = null })
        null -> Unit
    }

    replaceLeave?.let { replace ->
        ConfirmDialog(stringResource(R.string.replace_attendance),
            stringResource(R.string.replace_attendance_body),
            stringResource(R.string.replace), onConfirm = { replaceLeave = null; replace() },
            onDismiss = { replaceLeave = null })
    }
    copyResult?.takeIf { it.conflicts.isNotEmpty() }?.let { result ->
        NinetyAlertDialog(onDismissRequest = { copyResult = null },
            title = { CapsText(stringResource(R.string.timetable_applied)) },
            text = {
                val added = pluralStringResource(R.plurals.added_periods, result.created, result.created)
                val marked = markedCopyConflicts
                val edited = result.conflicts.size - marked
                val messages = listOfNotNull(added,
                    if (marked > 0) pluralStringResource(R.plurals.kept_marked_periods, marked, marked) else null,
                    if (edited > 0) pluralStringResource(R.plurals.kept_other_periods, edited, edited) else null)
                Text(messages.joinToString(" "))
            },
            confirmButton = { TextButton(onClick = { copyResult = null }) { Text(stringResource(R.string.done)) } })
    }

    leavePicker?.let { picker ->
        val eligible = day?.let { d ->
            (d.groups.flatMap { it.cells.mapNotNull { cell -> cell.session } } + d.extras)
                .filter { it.canMarkDutyLeave && (picker.scope == null || it.id in picker.scope) }
        }.orEmpty()
        val selectedIds = eligible.mapNotNull { it.id.takeIf(picker.selected::contains) }.toSet()
        DutyLeaveDialog(eligible, selectedIds,
            onSelectAll = { leavePicker = picker.copy(selected = eligible.map { it.id }.toSet()) },
            onToggle = { id ->
                val selected = if (id in selectedIds) selectedIds - id else selectedIds + id
                leavePicker = picker.copy(selected = selected)
            },
            onConfirm = {
                val ids = selectedIds.toList()
                model.markDutyLeave(ids) {
                    replaceLeave = { model.markDutyLeave(ids, replace = true) }
                }
                leavePicker = null
            },
            onDismiss = { leavePicker = null })
    }
}

private data class DutyLeaveSelection(val scope: Set<UUID>? = null, val selected: Set<UUID> = emptySet())

private val SessionView.canMarkDutyLeave: Boolean
    get() = loggable && counting && outcome != AttendanceOutcome.DUTY_LEAVE

private enum class DayDialog { Holiday, CopyDay, Extra }

private fun SlotGroup.countingPeriods() = cells.mapNotNull { it.session }.filter { it.loggable && it.counting }
private fun SlotGroup.pendingPeriods() = countingPeriods().filterNot { it.recorded }

@Composable
private fun DayContent(day: DayView, isToday: Boolean, busy: Boolean, archived: Boolean, beforeSemester: Boolean,
    catchUp: @Composable () -> Unit,
    nextUnmarked: (() -> Unit)?,
    onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onPickDate: () -> Unit,
    onSessionClick: (SessionView) -> Unit,
    onQuickRecord: (SessionView, AttendanceOutcome) -> Unit,
    onGroupOutcome: (SlotGroup, AttendanceOutcome) -> Unit,
    onGroupLeave: (SlotGroup) -> Unit,
    onMarkAll: () -> Unit, onToggleHoliday: () -> Unit,
    onCopyDay: () -> Unit, onEditTimetable: () -> Unit, onAddExtra: () -> Unit,
    onApplyLeave: () -> Unit, menuOpen: Boolean, onMenu: (Boolean) -> Unit) {
    val total = day.total
    val recorded = day.recorded
    val remaining = total - recorded
    val complete = !day.future && !day.holiday && total > 0 && remaining == 0
    val showProgress = remaining > 0 && !day.holiday && !day.future
    val hasSchedule = day.extras.isNotEmpty() ||
        day.groups.any { group -> group.cells.any { it.session != null || it.projectedCourseId != null } }
    var showHolidaySchedule by rememberSaveable(day.holiday) { mutableStateOf(false) }
    val canEditDay = day.writable && (!beforeSemester || hasSchedule || day.holiday)
    val canChangeHoliday = canEditDay || (day.future && (!beforeSemester || day.holiday))
    val writable = canEditDay && !day.holiday
    val canMarkLeave = day.groups.any { group -> group.cells.any { it.session?.canMarkDutyLeave == true } } ||
        day.extras.any { it.canMarkDutyLeave }
    val returnToToday: @Composable () -> Unit = {
        BackToTodayButton(onClick = onToday, enabled = !busy, modifier = Modifier.testTag("return_today"))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(R.drawable.ic_back, stringResource(R.string.previous_day), onPrev,
                enabled = !busy, modifier = Modifier.testTag("previous_day"))
            TextButton(onClick = onPickDate, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("day_title").semantics {
                    contentDescription = day.date.long()
                    heading()
                }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.date.dayOfWeek.fullName(), style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center)
                    Text(day.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
            ActionIcon(R.drawable.ic_forward, stringResource(R.string.next_day), onNext,
                enabled = !busy, modifier = Modifier.testTag("next_day"))
            Box {
                IconButton(onClick = { onMenu(true) }, enabled = !busy, modifier = Modifier.testTag("day_menu")) {
                    Icon(painterResource(R.drawable.ic_more), stringResource(R.string.day_actions))
                }
                NinetyMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                    DropdownMenuItem(text = { Text(stringResource(when {
                            day.holiday -> R.string.undo_holiday
                            day.future -> R.string.mark_holiday
                            else -> R.string.no_classes_today
                        })) },
                        onClick = { onMenu(false); onToggleHoliday() }, enabled = canChangeHoliday,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_holiday), null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.use_weekday_timetable_menu)) },
                        onClick = { onMenu(false); onCopyDay() }, enabled = canEditDay,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_copy), null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_extra_class_menu)) },
                        onClick = { onMenu(false); onAddExtra() }, enabled = canEditDay,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.duty_leave_menu)) },
                        onClick = { onMenu(false); onApplyLeave() }, enabled = writable && canMarkLeave,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_leave), null) })
                }
            }
        }

        if (!isToday && !showProgress && !complete) returnToToday()
        if (day.future) Banner(stringResource(R.string.mark_future_day))
        if (beforeSemester) {
            if (!hasSchedule && !day.holiday) {
                EmptyState(stringResource(R.string.before_semester_start))
                return@Column
            }
            Banner(stringResource(R.string.before_semester_start))
        }
        when {
            day.holiday -> Banner(stringResource(R.string.holiday_classes))
            day.kind == DayKind.COPIED || day.kind == DayKind.CUSTOM -> {
                val weekday = day.copiedWeekday
                Banner(if (weekday != null) stringResource(R.string.following_timetable, weekday.fullName())
                    else stringResource(R.string.day_timetable_edited))
            }
            day.weekend && hasSchedule -> Banner(stringResource(R.string.weekend))
        }
        Spacer(Modifier.height(12.dp))
        catchUp()
        if (complete) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)
                .testTag("day_complete").semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_check), null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                val outcomes = (day.groups.flatMap { it.cells.mapNotNull { cell -> cell.session } } + day.extras)
                    .filter { it.counting && it.loggable && it.recorded }
                val present = outcomes.count { it.outcome == AttendanceOutcome.PRESENT }
                val absent = outcomes.count { it.outcome == AttendanceOutcome.ABSENT }
                val summary = stringResource(R.string.all_marked, total, present, absent)
                val dutyLeave = outcomes.count { it.outcome == AttendanceOutcome.DUTY_LEAVE }
                Text(if (dutyLeave > 0) stringResource(R.string.day_duty_leave_total, summary, dutyLeave) else summary,
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (!isToday) returnToToday()
            }
        } else if (showProgress) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mark_progress, recorded, total),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("day_progress"))
                if (!isToday) returnToToday()
            }
            PeriodProgress(recorded, total, Modifier.padding(top = if (isToday) 8.dp else 0.dp, bottom = 12.dp))
        } else if (hasSchedule && !day.future && !day.holiday) {
            Banner(stringResource(R.string.no_attendance_to_mark))
        }
        if (nextUnmarked != null && (remaining == 0 || day.holiday)) {
            TextButton(onClick = nextUnmarked, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("next_unmarked_day")) {
                Text(stringResource(R.string.next_unmarked_day))
            }
        }
        if (writable && remaining > 0) {
            val label = stringResource(if (recorded == 0) R.string.mark_all_present else R.string.mark_remaining_present)
            val modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("mark_all")
            if (archived) {
                GhostButton(onClick = onMarkAll, enabled = !busy, modifier = modifier.heightIn(min = 48.dp)) {
                    CapsText(label)
                }
            } else {
                FilledTonalButton(onClick = onMarkAll, modifier = modifier.heightIn(min = 48.dp),
                    enabled = !busy, shape = NinetyDimensions.pill) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
        if (day.holiday && hasSchedule) {
            TextButton(onClick = { showHolidaySchedule = !showHolidaySchedule },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("holiday_schedule")) {
                Text(stringResource(if (showHolidaySchedule) R.string.hide_schedule else R.string.view_schedule))
            }
        }
        if (!hasSchedule && !day.holiday) {
            EmptyState(
                title = stringResource(if (day.weekend) R.string.no_weekend_classes else R.string.no_timetable_classes),
                actionLabel = stringResource(if (day.weekend) R.string.working_day else R.string.edit_timetable),
                onAction = if (day.weekend) onCopyDay.takeIf { writable } else onEditTimetable)
        } else if (hasSchedule && (!day.holiday || showHolidaySchedule)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                day.groups.filter { !day.holiday || it.courseId != null }.forEach { group ->
                    key(group.cells.first().slot) {
                        GroupCard(group, writable, busy, onSessionClick, onQuickRecord,
                            onGroupOutcome = { outcome -> onGroupOutcome(group, outcome) },
                            onGroupLeave = { onGroupLeave(group) })
                    }
                }
                day.extras.forEach { extra ->
                    key(extra.id) {
                        ScheduleCard {
                            SessionPeriod(extra, writable, busy, onSessionClick, onQuickRecord)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Banner(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun ScheduleCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = NinetyDimensions.listCard,
        color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = NinetyDimensions.content, vertical = NinetyDimensions.small)) {
            content()
        }
    }
}

@Composable
private fun PeriodCaption(period: String, time: String, modifier: Modifier = Modifier) {
    Text("$period · $time", modifier, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GroupCard(group: SlotGroup, writable: Boolean, busy: Boolean,
    onSessionClick: (SessionView) -> Unit,
    onQuickRecord: (SessionView, AttendanceOutcome) -> Unit,
    onGroupOutcome: (AttendanceOutcome) -> Unit, onGroupLeave: () -> Unit) {
    val firstCell = group.cells.first()
    val first = firstCell.session
    if (!group.grouped || first == null) {
        group.cells.forEach { cell ->
            val session = cell.session
            if (session == null) {
                OpenSlot(cell)
            } else {
                ScheduleCard {
                    SessionPeriod(session, writable, busy, onSessionClick, onQuickRecord)
                }
            }
        }
        return
    }

    val eligible = group.countingPeriods()
    val remaining = eligible.count { !it.recorded }
    var expanded by rememberSaveable(first.id.toString()) { mutableStateOf(false) }
    val periodRange = "P${firstCell.slot}–${group.cells.last().slot}"
    ScheduleCard(Modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(first.courseName, style = MaterialTheme.typography.titleMedium)
                PeriodCaption(periodRange, PeriodTimes.label(first.startMinute))
            }
            ActionIcon(R.drawable.ic_chevron_down,
                stringResource(if (expanded) R.string.hide_periods else R.string.view_periods),
                onClick = { expanded = !expanded },
                modifier = Modifier.rotate(if (expanded) 180f else 0f).testTag("group_periods_${first.id}"))
        }

        // The header already identifies every slot when all of them are unmarked.
        if (!expanded && (!writable || remaining != group.cells.size)) {
            FlowRow(Modifier.padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.cells.forEach { cell ->
                    cell.session?.let { session ->
                        OutcomeChip(session.outcome, cancelled = session.cancelled, counting = session.counting,
                            periodLabel = slotLabel(cell.slot))
                    }
                }
            }
        }

        if (writable && remaining > 0) {
            val all = remaining == group.cells.size
            val scope = stringResource(if (all) R.string.all_periods else R.string.remaining_periods)
            if (!all || expanded) {
                Text(scope, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            BoxWithConstraints {
                MarkControl(AttendanceOutcome.UNRECORDED, enabled = !busy,
                    compact = maxWidth < 148.dp * LocalDensity.current.fontScale,
                    tag = "group", presentAction = stringResource(R.string.mark_present_for, scope),
                    absentAction = stringResource(R.string.mark_absent_for, scope), onMark = onGroupOutcome)
            }
        }

        if (expanded) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.cells.forEach { cell ->
                    cell.session?.let { session ->
                        SessionPeriod(session, writable, busy, onSessionClick, onQuickRecord, showCourseName = false)
                    }
                }
            }
            if (writable && eligible.any { it.canMarkDutyLeave }) {
                TextButton(onClick = onGroupLeave, enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("group_leave")) {
                    Text(stringResource(R.string.duty_leave))
                }
            }
        }
    }
}

@Composable
private fun OpenSlot(cell: SlotCell) {
    val title = cell.projectedCourseName
    val period = slotLabel(cell.slot)
    val start = PeriodTimes.label(cell.startMinute)
    if (title == null) {
        PeriodCaption(period, "$start · ${stringResource(R.string.free_period)}",
            Modifier.padding(horizontal = NinetyDimensions.content, vertical = 4.dp))
    } else {
        ScheduleCard {
            Text(title, style = MaterialTheme.typography.titleMedium)
            PeriodCaption(period, start)
        }
    }
}

@Composable
private fun SessionPeriod(session: SessionView, writable: Boolean, busy: Boolean,
    onSessionClick: (SessionView) -> Unit, onQuickRecord: (SessionView, AttendanceOutcome) -> Unit,
    showCourseName: Boolean = true) {
    val period = if (session.origin == SessionOrigin.EXTRA) stringResource(R.string.extra) else slotLabel(session.slot)
    val times = if (showCourseName && session.origin != SessionOrigin.EXTRA) PeriodTimes.label(session.startMinute)
        else timeRange(session.startMinute, session.endMinute)
    val actionable = writable && !session.suppressed
    val canMark = actionable && session.loggable && session.counting && session.outcome != AttendanceOutcome.DUTY_LEAVE
    val identity = "${session.courseName} · $period"
    val presentAction = stringResource(R.string.mark_present_for, identity)
    val absentAction = stringResource(R.string.mark_absent_for, identity)
    val openActions = stringResource(R.string.period_actions_for, identity)
    val markState = when (session.outcome) {
        AttendanceOutcome.PRESENT -> stringResource(R.string.present)
        AttendanceOutcome.ABSENT -> stringResource(R.string.absent)
        else -> stringResource(R.string.not_marked)
    }
    BoxWithConstraints {
        val fontScale = LocalDensity.current.fontScale
        val inlineMarks = !showCourseName && canMark && maxWidth >= 296.dp * fontScale
        val compactMarks = maxWidth < 148.dp * fontScale
        val marks: @Composable () -> Unit = {
            MarkControl(session.outcome, enabled = !busy, compact = compactMarks, tag = "period_${session.id}",
                presentAction = presentAction, absentAction = absentAction, onMark = { outcome ->
                    if (outcome != session.outcome) onQuickRecord(session, outcome)
                })
        }
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (actionable) Modifier.clickable(enabled = !busy, onClickLabel = openActions,
                    onClick = { onSessionClick(session) }) else Modifier)
                .testTag("period_${session.id}")
                .semantics {
                    if (!showCourseName) contentDescription = identity
                    if (canMark) {
                        stateDescription = markState
                        if (!busy) customActions = listOf(
                            CustomAccessibilityAction(presentAction) {
                                if (session.outcome != AttendanceOutcome.PRESENT) onQuickRecord(session, AttendanceOutcome.PRESENT)
                                true
                            },
                            CustomAccessibilityAction(absentAction) {
                                if (session.outcome != AttendanceOutcome.ABSENT) onQuickRecord(session, AttendanceOutcome.ABSENT)
                                true
                            },
                        )
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    if (showCourseName) {
                        Text(session.courseName, style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (session.cancelled) TextDecoration.LineThrough else null)
                    }
                    PeriodCaption(period, times)
                }
                if (inlineMarks) marks()
                if (actionable) ActionIcon(R.drawable.ic_more, openActions,
                    onClick = { onSessionClick(session) }, enabled = !busy,
                    modifier = Modifier.testTag("period_actions_${session.id}"))
            }
            if (canMark && !inlineMarks) marks()
            if (!canMark) OutcomeChip(session.outcome, cancelled = session.cancelled, counting = session.counting,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun SessionActions(session: SessionView,
    courses: List<Pair<UUID, String>>,
    onRecord: (AttendanceOutcome?) -> Unit,
    onCancel: (Boolean) -> Unit,
    onSubstitute: (UUID) -> Unit,
    onCounting: (Boolean) -> Unit,
    onRemove: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${session.courseName} · ${if (session.slot != null) slotLabel(session.slot) else stringResource(R.string.extra)}",
            style = MaterialTheme.typography.titleLarge)
        Text(timeRange(session.startMinute, session.endMinute),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (session.cancelled) {
            PrimaryAction(stringResource(R.string.restore_class), { onCancel(false) }, icon = R.drawable.ic_history,
                modifier = Modifier.testTag("restore_period"))
        }
        if (session.loggable) {
            if (!session.counting || session.outcome == AttendanceOutcome.DUTY_LEAVE) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction(stringResource(R.string.present), { onRecord(AttendanceOutcome.PRESENT) },
                    modifier = Modifier.testTag("action_present"))
                GhostButton(onClick = { onRecord(AttendanceOutcome.ABSENT) },
                    modifier = Modifier.heightIn(min = 52.dp).testTag("action_absent")) {
                    Icon(painterResource(R.drawable.ic_close), null, Modifier.size(20.dp))
                    CapsText(stringResource(R.string.absent), Modifier.padding(start = NinetyDimensions.small))
                }
            }
            if (session.canMarkDutyLeave) {
                ActionRow(R.drawable.ic_leave, stringResource(R.string.duty_leave),
                    { onRecord(AttendanceOutcome.DUTY_LEAVE) }, modifier = Modifier.testTag("action_duty_leave"))
            }
            ActionRow(R.drawable.ic_minus, stringResource(R.string.class_not_held), { onCancel(true) })
            if (session.origin != SessionOrigin.EXTRA) {
                SubstitutePicker(courses.filter { it.first != session.courseId }, onSubstitute)
            }
            if (session.recorded) {
                ActionRow(R.drawable.ic_history, stringResource(R.string.clear_mark), { onRecord(null) })
            }
            if (session.origin == SessionOrigin.EXTRA) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.counts_toward_attendance), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = session.counting, onCheckedChange = onCounting)
                }
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.remove_extra_class), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SubstitutePicker(courses: List<Pair<UUID, String>>, onPick: (UUID) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (courses.isEmpty()) return
    Box {
        ActionRow(R.drawable.ic_courses, stringResource(R.string.different_course), { open = true }, showChevron = true)
        NinetyMenu(expanded = open, onDismissRequest = { open = false }) {
            courses.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) })
            }
        }
    }
}

@Composable
private fun CopyDayDialog(onPick: (DayOfWeek) -> Unit, onDismiss: () -> Unit) {
    NinetyAlertDialog(onDismissRequest = onDismiss, title = { CapsText(stringResource(R.string.choose_weekday_timetable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                collegeWeekdays().forEach { day ->
                    TextButton(onClick = { onPick(day) }, modifier = Modifier.fillMaxWidth()) {
                        Text(day.fullName())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun DutyLeaveDialog(eligible: List<SessionView>, selection: Set<UUID>,
    onSelectAll: () -> Unit, onToggle: (UUID) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NinetyAlertDialog(onDismissRequest = onDismiss, title = { CapsText(stringResource(R.string.duty_leave)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.select_all_periods))
                }
                eligible.forEach { session ->
                    val selected = session.id in selection
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("dl_period_${session.id}")
                        .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle(session.id) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NinetyDimensions.small)) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text("${session.courseName} · ${slotLabel(session.slot)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            if (session.recorded) Text(stringResource(R.string.calendar_marked),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selection.isNotEmpty(),
                modifier = Modifier.testTag("dl_confirm")) {
                Text(pluralStringResource(R.plurals.mark_periods, selection.size, selection.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
