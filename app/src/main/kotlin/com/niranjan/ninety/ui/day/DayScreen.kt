package com.niranjan.ninety.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DayView
import com.niranjan.ninety.data.SessionView
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import com.niranjan.ninety.data.SlotGroup
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.domain.SessionOrigin
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.fullName
import com.niranjan.ninety.ui.long
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import com.niranjan.ninety.ui.timeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(semesterId: UUID, date: LocalDate, isToday: Boolean,
    onOpenDay: (LocalDate) -> Unit, snackbar: SnackbarHostState) {
    val model = ninetyViewModel<DayViewModel>(key = "day-$semesterId-$date") {
        DayViewModel(it.repository, semesterId, date)
    }
    val day by model.day.collectAsStateWithLifecycle()
    val courses by model.courses.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<SessionView?>(null) }
    var dialog by remember { mutableStateOf<DayDialog?>(null) }
    var dlSelection by remember { mutableStateOf<Set<UUID>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var replaceLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var copyResult by remember { mutableStateOf<com.niranjan.ninety.data.CopyResult?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    val hapticsOn by model.haptics.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val tapHaptic = { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.Confirm) }

    when (val view = day) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag("day_loading"))
        }
        else -> DayContent(view, isToday, busy,
            onPrev = { onOpenDay(view.date.minusDays(1)) },
            onNext = { onOpenDay(view.date.plusDays(1)) },
            onSessionClick = { selected = it },
            onGroupOutcome = { group, outcome ->
                tapHaptic()
                model.recordAll(group.cells.mapNotNull { it.session }
                    .filter { it.loggable && it.counting }.map { it.id }, outcome)
            },
            onGroupLeave = { group ->
                val ids = group.cells.mapNotNull { it.session }
                    .filter { it.loggable && it.counting }.map { it.id }
                model.applyDutyLeave(ids, null, replace = false) {
                    replaceLeave = { model.applyDutyLeave(ids, null, replace = true) }
                }
            },
            onMarkAll = { tapHaptic(); model.markAllPresent() },
            onToggleHoliday = { dialog = DayDialog.Holiday },
            onCopyDay = { dialog = DayDialog.CopyDay },
            onAddExtra = { dialog = DayDialog.Extra },
            onApplyLeave = { dlSelection = emptySet() },
            menuOpen = menuOpen, onMenu = { menuOpen = it },
        )
    }

    selected?.let { session ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            SessionActions(session, day, courses.map { UUID.fromString(it.id) to it.name },
                onRecord = { outcome -> model.record(session.id, outcome); selected = null },
                onLeave = { dlSelection = setOf(session.id); selected = null },
                onCancel = { cancelled -> model.setCancelled(session.id, cancelled); selected = null },
                onSubstitute = { courseId -> model.substitute(session.id, courseId); selected = null },
                onCounting = { counting -> model.setExtraCounting(session.id, counting); selected = null },
                onRemove = { model.removeExtra(session.id); selected = null },
            )
        }
    }

    when (dialog) {
        DayDialog.Holiday -> ConfirmDialog(
            title = if (day?.holiday == true) "Remove holiday?" else "Mark as holiday?",
            body = if (day?.holiday == true)
                "Classes on this day will count toward attendance again."
            else
                "All classes on this date are treated as not conducted. Past records are kept.",
            confirm = if (day?.holiday == true) "Remove" else "Mark holiday",
            onConfirm = { model.setHoliday(day?.holiday != true); dialog = null },
            onDismiss = { dialog = null })
        DayDialog.CopyDay -> CopyDayDialog(
            onPick = { weekday ->
                model.copyDay(weekday) { result -> dialog = null; copyResult = result }
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
        ConfirmDialog("Replace recorded attendance?",
            "Some selected periods already have a result. Applying duty leave replaces those results with pending DL. You can undo this change.",
            "Replace with DL", onConfirm = { replaceLeave = null; replace() },
            onDismiss = { replaceLeave = null })
    }
    copyResult?.takeIf { it.conflicts.isNotEmpty() }?.let { result ->
        AlertDialog(onDismissRequest = { copyResult = null },
            title = { Text("Recorded periods kept") },
            text = { Text("Copied ${result.created} periods. Kept ${result.conflicts.size} recorded or edited periods unchanged. This is a custom day.") },
            confirmButton = { TextButton(onClick = { copyResult = null }) { Text("Done") } })
    }

    dlSelection?.let { selection ->
        val eligible = day?.let { d ->
            (d.groups.flatMap { it.cells.mapNotNull { c -> c.session } } + d.extras)
                .filter { it.loggable && it.counting }
        } ?: emptyList()
        DutyLeaveDialog(eligible, selection,
            onSelectAll = { dlSelection = eligible.map { it.id }.toSet() },
            onToggle = { id -> dlSelection = if (id in selection) selection - id else selection + id },
            onConfirm = { title ->
                val ids = selection.toList()
                if (ids.isEmpty()) return@DutyLeaveDialog
                model.applyDutyLeave(ids, title, replace = false) { conflicts ->
                    replaceLeave = { model.applyDutyLeave(ids, title, replace = true) }
                }
                dlSelection = null
            },
            onDismiss = { dlSelection = null })
    }
}

private enum class DayDialog { Holiday, CopyDay, Extra }

@Composable
private fun DayContent(day: DayView, isToday: Boolean, busy: Boolean,
    onPrev: () -> Unit, onNext: () -> Unit,
    onSessionClick: (SessionView) -> Unit,
    onGroupOutcome: (SlotGroup, AttendanceOutcome) -> Unit,
    onGroupLeave: (SlotGroup) -> Unit,
    onMarkAll: () -> Unit, onToggleHoliday: () -> Unit,
    onCopyDay: () -> Unit, onAddExtra: () -> Unit,
    onApplyLeave: () -> Unit, menuOpen: Boolean, onMenu: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isToday) "Today" else day.date.medium(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(day.date.long(), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("day_title"))
                when {
                    day.holiday -> Banner("Holiday — classes do not count today")
                    day.kind == DayKind.COPIED -> Banner("Copied ${day.copiedWeekday?.name?.lowercase()?.replaceFirstChar { it.uppercase() }} timetable")
                    day.kind == DayKind.CUSTOM -> Banner("Custom day — timetable edited")
                    day.weekend -> Banner("Weekend")
                }
            }
            if (!isToday) {
                IconButton(onClick = onPrev) { Icon(painterResource(R.drawable.ic_back), "Previous day") }
                IconButton(onClick = onNext) { Icon(painterResource(R.drawable.ic_forward), "Next day") }
            }
            Box {
                IconButton(onClick = { onMenu(true) }, modifier = Modifier.testTag("day_menu")) {
                    Icon(painterResource(R.drawable.ic_more), "Day actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                    DropdownMenuItem(text = { Text(if (day.holiday) "Remove holiday" else "Mark as holiday") },
                        onClick = { onMenu(false); onToggleHoliday() }, enabled = day.writable)
                    DropdownMenuItem(text = { Text("Copy a weekday here") },
                        onClick = { onMenu(false); onCopyDay() }, enabled = day.writable)
                    DropdownMenuItem(text = { Text("Add extra class") },
                        onClick = { onMenu(false); onAddExtra() }, enabled = day.writable)
                    DropdownMenuItem(text = { Text("Apply duty leave") },
                        onClick = { onMenu(false); onApplyLeave() }, enabled = day.writable)
                }
            }
        }

        if (day.total > 0 && !day.holiday && !day.future) {
            Text("${day.recorded} of ${day.total} periods recorded" +
                if (day.unrecordedEligible == 0) " · complete" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp).testTag("day_progress"))
        }
        if (day.future) Banner("Scheduled classes · recording opens on this date")

        if (day.writable && day.unrecordedEligible > 0 && !day.holiday) {
            Button(onClick = onMarkAll, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("mark_all"),
                enabled = !busy) {
                Text("Mark all present · ${day.unrecordedEligible} left")
            }
        }

        if (day.groups.isEmpty() && day.extras.isEmpty()) {
            EmptyState(
                title = if (day.weekend) "No classes on weekends" else "No classes scheduled",
                body = if (day.weekend) "Enjoy your weekend."
                    else "This weekday has no timetable classes. Add an extra class or copy a weekday if needed.")
        } else {
            day.groups.forEach { group ->
                GroupCard(group, day.writable && !day.holiday, onSessionClick,
                    onGroupOutcome = { outcome -> onGroupOutcome(group, outcome) },
                    onGroupLeave = { onGroupLeave(group) })
                Spacer(Modifier.height(8.dp))
            }
            day.extras.forEach { extra ->
                SessionCard(extra, null, day.writable && !day.holiday, onSessionClick)
                Spacer(Modifier.height(8.dp))
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
private fun GroupCard(group: SlotGroup, writable: Boolean, onSessionClick: (SessionView) -> Unit,
    onGroupOutcome: (AttendanceOutcome) -> Unit, onGroupLeave: () -> Unit) {
    if (!group.grouped) {
        group.cells.forEach { SessionCard(it.session, it, writable, onSessionClick) }
        return
    }
    val first = group.cells.first().session ?: return
    val actionable = group.cells.mapNotNull { it.session }.filter { it.loggable && it.counting }
    val kind = if (first.courseType == CourseType.LAB) "Lab" else "Class"
    NinetyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(first.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$kind · ${group.cells.size} periods", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (writable && actionable.isNotEmpty()) {
                // Stack actions at the largest text sizes so each word fits intact.
                val actionWidth = if (LocalDensity.current.fontScale > 1.5f) Modifier.fillMaxWidth() else Modifier
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { onGroupOutcome(AttendanceOutcome.PRESENT) },
                        modifier = actionWidth.heightIn(min = 48.dp).testTag("group_present")) { Text("Present") }
                    FilledTonalButton(onClick = { onGroupOutcome(AttendanceOutcome.ABSENT) },
                        modifier = actionWidth.heightIn(min = 48.dp).testTag("group_absent")) { Text("Absent") }
                    FilledTonalButton(onClick = onGroupLeave,
                        modifier = actionWidth.heightIn(min = 48.dp).testTag("group_leave")) { Text("Leave") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            group.cells.forEachIndexed { index, cell ->
                val session = cell.session ?: return@forEachIndexed
                PeriodRow(session, slotLabel(cell.slot), timeRange(session.startMinute, session.endMinute),
                    writable && !session.suppressed) { onSessionClick(session) }
                if (index < group.cells.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionView?, cell: com.niranjan.ninety.data.SlotCell?,
    writable: Boolean, onSessionClick: (SessionView) -> Unit) {
    NinetyCard(Modifier.fillMaxWidth()) {
        if (session == null && cell != null) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(slotLabel(cell.slot), style = MaterialTheme.typography.titleSmall)
                    Text(timeRange(cell.startMinute, cell.endMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(cell.projectedCourseName ?: "Free", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (cell.projectedCourseName == null) Modifier.padding(start = 8.dp)
                        else Modifier.weight(1f).padding(start = 8.dp))
            }
            return@NinetyCard
        }
        session ?: return@NinetyCard
        Box(Modifier.padding(16.dp)) {
            PeriodRow(session,
                if (session.origin == SessionOrigin.EXTRA) "Extra class" else slotLabel(session.slot),
                timeRange(session.startMinute, session.endMinute),
                writable && !session.suppressed) { onSessionClick(session) }
        }
    }
}

@Composable
private fun PeriodRow(session: SessionView, top: String, times: String,
    actionable: Boolean, onClick: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .then(if (actionable) Modifier.clickable(onClick = onClick) else Modifier)
        .testTag("period_${session.id}")
    @Composable fun Details() {
        Text(top, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(session.courseName, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium)
        Text(times, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (largeText) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Details()
            OutcomeChip(session.outcome, cancelled = session.cancelled, counting = session.counting)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) { Details() }
            OutcomeChip(session.outcome, cancelled = session.cancelled, counting = session.counting)
        }
    }
}

@Composable
private fun SessionActions(session: SessionView, day: DayView?,
    courses: List<Pair<UUID, String>>,
    onRecord: (AttendanceOutcome?) -> Unit, onLeave: () -> Unit,
    onCancel: (Boolean) -> Unit,
    onSubstitute: (UUID) -> Unit,
    onCounting: (Boolean) -> Unit,
    onRemove: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${session.courseName} · ${if (session.slot != null) slotLabel(session.slot) else "Extra"}",
            style = MaterialTheme.typography.titleLarge)
        Text(timeRange(session.startMinute, session.endMinute),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (session.cancelled) {
            TextButton(onClick = { onCancel(false) }, modifier = Modifier.testTag("restore_period")) {
                Text("Restore this period")
            }
        }
        if (session.loggable) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRecord(AttendanceOutcome.PRESENT) },
                    modifier = Modifier.testTag("action_present")) { Text("Present") }
                OutlinedButton(onClick = { onRecord(AttendanceOutcome.ABSENT) },
                    modifier = Modifier.testTag("action_absent")) { Text("Absent") }
                if (session.counting) OutlinedButton(onClick = onLeave) { Text("Duty leave") }
            }
            if (session.recorded) {
                TextButton(onClick = { onRecord(null) }) { Text("Clear record") }
            }
            TextButton(onClick = { onCancel(true) }) { Text("Mark not conducted") }
            if (session.origin != SessionOrigin.EXTRA) {
                SubstitutePicker(courses.filter { it.first != session.courseId }, onSubstitute)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Counts toward attendance", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = session.counting, onCheckedChange = onCounting)
                }
                TextButton(onClick = onRemove) {
                    Text("Remove extra class", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SubstitutePicker(courses: List<Pair<UUID, String>>, onPick: (UUID) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (courses.isEmpty()) return
    TextButton(onClick = { open = true }) { Text("Substitute course…") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        courses.forEach { (id, name) ->
            DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) })
        }
    }
}

@Composable
private fun CopyDayDialog(onPick: (DayOfWeek) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Copy a weekday timetable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Place another weekday's timetable on this date.")
                collegeWeekdays().forEach { day ->
                    TextButton(onClick = { onPick(day) }, modifier = Modifier.fillMaxWidth()) {
                        Text(day.fullName())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ExtraClassDialog(courses: List<com.niranjan.ninety.data.CourseEntity>,
    onSave: (UUID, Int, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var courseId by remember { mutableStateOf(courses.firstOrNull()?.id?.let(UUID::fromString)) }
    var start by rememberSaveable { mutableStateOf("17:00") }
    var end by rememberSaveable { mutableStateOf("18:00") }
    var counting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add an extra class") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (courses.isEmpty()) Text("No courses available.")
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = true }, enabled = courses.isNotEmpty()) {
                    Text(courses.firstOrNull { it.id == courseId?.toString() }?.name ?: "Choose course")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    courses.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) },
                            onClick = { open = false; courseId = c.id.toUuidOrNull() })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it },
                        label = { Text("Start (HH:MM)") }, modifier = Modifier.weight(1f),
                        singleLine = true)
                    OutlinedTextField(value = end, onValueChange = { end = it },
                        label = { Text("End (HH:MM)") }, modifier = Modifier.weight(1f),
                        singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = counting, onCheckedChange = { counting = it })
                    Text("Counts toward attendance (off = just a reminder)",
                        style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = parseTime(start); val e = parseTime(end)
                when {
                    courseId == null -> error = "Choose a course."
                    s == null || e == null || s >= e || e > 1440 -> error = "Check the class times."
                    else -> onSave(courseId!!, s, e, counting)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun parseTime(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").find(value.trim()) ?: return null
    val (h, m) = match.destructured
    val total = h.toInt() * 60 + m.toInt()
    return if (h.toInt() in 0..24 && m.toInt() in 0..59 && total <= 1440) total else null
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

@Composable
private fun DutyLeaveDialog(eligible: List<SessionView>, selection: Set<UUID>,
    onSelectAll: () -> Unit, onToggle: (UUID) -> Unit, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Apply duty leave") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll) {
                    Text("Select all periods")
                }
                Text("Pick the periods duty leave covers. It starts pending and credits provisionally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                eligible.forEach { session ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("dl_period_${session.id}").clickable { onToggle(session.id) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = session.id in selection, onCheckedChange = null)
                        Column {
                            Text("${session.courseName} · ${slotLabel(session.slot)}",
                                style = MaterialTheme.typography.bodyMedium)
                            if (session.recorded) {
                                Text("Replaces existing record",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Reason (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.ifBlank { null }) },
                enabled = selection.isNotEmpty(), modifier = Modifier.testTag("dl_confirm")) {
                Text("Apply (${selection.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
