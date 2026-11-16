package com.niranjan.ninety.ui.day

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
import com.niranjan.ninety.data.SlotGroup
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.domain.DayKind
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.domain.SessionOrigin
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.CourseBadge
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
                    model.applyDutyLeave(ids, null, replace = true)
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
            SessionActions(session, day, courses.map { UUID.fromString(it.id) to it.code },
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
                model.copyDay(weekday) { result -> dialog = null }
            },
            onDismiss = { dialog = null })
        DayDialog.Extra -> ExtraClassDialog(courses.filter { !it.retired },
            onSave = { courseId, start, end, counting ->
                model.addExtra(courseId, start, end, counting); dialog = null
            },
            onDismiss = { dialog = null })
        null -> Unit
    }

    dlSelection?.let { selection ->
        val eligible = day?.let { d ->
            (d.groups.flatMap { it.cells.mapNotNull { c -> c.session } } + d.extras)
                .filter { it.loggable && it.counting }
        } ?: emptyList()
        DutyLeaveDialog(eligible, selection,
            onToggle = { id -> dlSelection = if (id in selection) selection - id else selection + id },
            onConfirm = { title ->
                val ids = selection.toList()
                if (ids.isEmpty()) return@DutyLeaveDialog
                model.applyDutyLeave(ids, title, replace = false) { conflicts ->
                    model.applyDutyLeave(ids, title, replace = true)
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
                        onClick = { onMenu(false); onToggleHoliday() })
                    DropdownMenuItem(text = { Text("Copy a weekday here") },
                        onClick = { onMenu(false); onCopyDay() }, enabled = day.writable)
                    DropdownMenuItem(text = { Text("Add extra class") },
                        onClick = { onMenu(false); onAddExtra() }, enabled = day.writable)
                    DropdownMenuItem(text = { Text("Apply duty leave") },
                        onClick = { onMenu(false); onApplyLeave() }, enabled = day.writable)
                }
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

        if (day.writable && day.unrecordedEligible > 0 && !day.holiday) {
            Button(onClick = onMarkAll, modifier = Modifier.fillMaxWidth().testTag("mark_all"),
                enabled = !busy) {
                Text("Mark all present · ${day.unrecordedEligible} left")
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
    NinetyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CourseBadge(first.courseCode, "Lab · ${group.cells.size} periods", Modifier.weight(1f))
            }
            Text(first.courseName, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (writable && actionable.isNotEmpty()) {
                // One-tap whole-lab actions; each row still logs its own unit.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onGroupOutcome(AttendanceOutcome.PRESENT) },
                        modifier = Modifier.weight(1f)) { Text("Present") }
                    FilledTonalButton(onClick = { onGroupOutcome(AttendanceOutcome.ABSENT) },
                        modifier = Modifier.weight(1f)) { Text("Absent") }
                    FilledTonalButton(onClick = onGroupLeave,
                        modifier = Modifier.weight(1f)) { Text("Leave") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            group.cells.forEachIndexed { index, cell ->
                val session = cell.session ?: return@forEachIndexed
                PeriodRow(session, slotLabel(cell.slot), timeRange(session.startMinute, session.endMinute),
                    writable && session.loggable) { onSessionClick(session) }
                if (index < group.cells.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionView?, cell: com.niranjan.ninety.data.SlotCell?,
    writable: Boolean, onSessionClick: (SessionView) -> Unit) {
    NinetyCard(Modifier.fillMaxWidth(),
        onClick = if (session != null && writable && session.loggable) ({ onSessionClick(session) }) else null) {
        if (session == null && cell != null) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(slotLabel(cell.slot), style = MaterialTheme.typography.titleSmall)
                    Text(timeRange(cell.startMinute, cell.endMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Free", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@NinetyCard
        }
        session ?: return@NinetyCard
        PeriodRow(session,
            if (session.origin == SessionOrigin.EXTRA) "Extra class" else slotLabel(session.slot),
            timeRange(session.startMinute, session.endMinute),
            writable && session.loggable) { onSessionClick(session) }
    }
}

@Composable
private fun PeriodRow(session: SessionView, top: String, times: String,
    actionable: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (actionable) Modifier.clickable(onClick = onClick) else Modifier)
        .testTag("period_${session.id}"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(top, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(session.courseName, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium)
            Text("$top · $times", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutcomeChip(session.outcome, cancelled = session.cancelled, counting = session.counting)
    }
}

@Composable
private fun SessionActions(session: SessionView, day: DayView?,
    courses: List<Pair<UUID, String>>,
    onRecord: (AttendanceOutcome?) -> Unit,
    onCancel: (Boolean) -> Unit,
    onSubstitute: (UUID) -> Unit,
    onCounting: (Boolean) -> Unit,
    onRemove: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${session.courseCode} · ${if (session.slot != null) slotLabel(session.slot) else "Extra"}",
            style = MaterialTheme.typography.titleLarge)
        Text(timeRange(session.startMinute, session.endMinute),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (session.loggable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRecord(AttendanceOutcome.PRESENT) },
                    modifier = Modifier.weight(1f).testTag("action_present")) { Text("Present") }
                OutlinedButton(onClick = { onRecord(AttendanceOutcome.ABSENT) },
                    modifier = Modifier.weight(1f).testTag("action_absent")) { Text("Absent") }
            }
            if (session.recorded) {
                TextButton(onClick = { onRecord(null) }) { Text("Clear record") }
            }
            TextButton(onClick = { onCancel(!session.cancelled) }) {
                Text(if (session.cancelled) "Restore this period" else "Mark not conducted")
            }
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
        courses.forEach { (id, code) ->
            DropdownMenuItem(text = { Text(code) }, onClick = { open = false; onPick(id) })
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (courses.isEmpty()) Text("No courses available.")
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = true }, enabled = courses.isNotEmpty()) {
                    Text(courses.firstOrNull { it.id == courseId?.toString() }?.code ?: "Choose course")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    courses.forEach { c ->
                        DropdownMenuItem(text = { Text("${c.code} · ${c.name}") },
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
    onToggle: (UUID) -> Unit, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Apply duty leave") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick the periods duty leave covers. It starts pending and credits provisionally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                eligible.forEach { session ->
                    Row(Modifier.fillMaxWidth().clickable { onToggle(session.id) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = session.id in selection, onCheckedChange = { onToggle(session.id) })
                        Column {
                            Text("${session.courseCode} · ${slotLabel(session.slot)}",
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
