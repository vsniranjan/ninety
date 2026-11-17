package com.niranjan.ninety.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.WeeklySchedule
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.fullName
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.shortName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val ZONE_CHOICES = listOf("Asia/Kolkata", "Asia/Dubai", "Asia/Singapore",
    "Europe/London", "Europe/Berlin", "America/New_York", "America/Los_Angeles",
    "Australia/Sydney")

@Composable
fun SetupScreen(model: SetupViewModel, onSaved: () -> Unit = {}, onCancel: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    var courseDialog by remember { mutableStateOf<CourseDraft?>(null) }
    var editingCourse by remember { mutableStateOf<CourseDraft?>(null) }
    var pickStart by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = state.step > 0 || onCancel != null) {
        if (state.step > 0) model.back() else onCancel?.invoke()
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp)
            .verticalScroll(scroll)) {
            Spacer(Modifier.height(20.dp))
            Text("Ninety", style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary)
            onCancel?.let { cancel -> TextButton(onClick = cancel) { Text("Cancel setup") } }
            Text("Set up your semester", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp))
            LinearProgressIndicator(
                progress = { (state.step + 1) / state.stepCount.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            Text(stepTitle(state.step), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(16.dp))
            when (state.step) {
                0 -> SemesterStep(state, { pickStart = true },
                    model::setSemesterName, model::setZone)
                1 -> CoursesStep(state, onAdd = { courseDialog = CourseDraft() },
                    onEdit = { editingCourse = it }, onRemove = model::removeCourse)
                2 -> TimetableStep(state, model::assign)
                else -> ReviewStep(state)
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp).testTag("setup_error"))
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.step > 0) {
                    OutlinedButton(onClick = { focus.clearFocus(); model.back() }) { Text("Back") }
                }
                if (state.step < state.stepCount - 1) {
                    Button(onClick = { focus.clearFocus(); model.next() }, modifier = Modifier.testTag("setup_action")) {
                        Text("Continue")
                    }
                } else {
                    Button(onClick = { focus.clearFocus(); model.save() }, enabled = !state.saving,
                        modifier = Modifier.testTag("setup_action")) {
                        Text(if (state.saving) "Saving…" else "Start tracking")
                    }
                }
            }
            Text("On your device. No account. No cloud backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp))
        }
    }

    courseDialog?.let { draft ->
        CourseDraftDialog(draft, semesterStart = state.startDate,
            onSave = { model.addCourse(it); courseDialog = null },
            onDismiss = { courseDialog = null })
    }
    editingCourse?.let { draft ->
        CourseDraftDialog(draft, semesterStart = state.startDate,
            onSave = { model.updateCourse(it); editingCourse = null },
            onDismiss = { editingCourse = null })
    }
    if (pickStart) {
        NinetyDatePicker(state.startDate ?: LocalDate.now(),
            onPicked = { model.setStartDate(it); pickStart = false },
            onDismiss = { pickStart = false })
    }
}

private fun stepTitle(step: Int): String = when (step) {
    0 -> "Your semester"
    1 -> "Your courses"
    2 -> "Weekly timetable"
    else -> "Review"
}

@Composable
private fun SemesterStep(state: SetupState, onPickDate: () -> Unit,
    onName: (String) -> Unit, onZone: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = state.semesterName, onValueChange = onName,
            label = { Text("Semester name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("semester_name"))
        NinetyCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Semester start", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.startDate?.medium() ?: "Choose a date",
                        style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onPickDate, modifier = Modifier.testTag("start_date")) { Text("Pick") }
            }
        }
        var zoneOpen by remember { mutableStateOf(false) }
        NinetyCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("College timezone", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.zoneId, style = MaterialTheme.typography.titleMedium)
                    Text("Dates stay in this timezone when you travel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { zoneOpen = true }) { Text("Change") }
                    DropdownMenu(expanded = zoneOpen, onDismissRequest = { zoneOpen = false }) {
                        (listOf(state.zoneId) + ZONE_CHOICES + ZoneId.getAvailableZoneIds().sorted())
                            .distinct().take(40).forEach { zone ->
                            DropdownMenuItem(text = { Text(zone) },
                                onClick = { zoneOpen = false; onZone(zone) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursesStep(state: SetupState, onAdd: () -> Unit,
    onEdit: (CourseDraft) -> Unit, onRemove: (UUID) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add each course you attend. Labs are courses too — a three-period lab is one course occupying three slots.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.courses.forEach { course ->
            NinetyCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(course.name.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(typeLabel(course.type) +
                            if (course.hasBalance) " · opens with ${course.conducted} periods" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onEdit(course) }) { Text("Edit") }
                    IconButton(onClick = { onRemove(course.id) }) {
                        Icon(painterResource(R.drawable.ic_remove), "Remove course")
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("add_course")) {
            Text(if (state.courses.isEmpty()) "Add your first course" else "Add another course")
        }
    }
}

@Composable
private fun TimetableStep(state: SetupState,
    onAssign: (DayOfWeek, Int, UUID?) -> Unit) {
    val nameOf = state.courses.associate { it.id to it.name }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Place courses on your recurring week. Leave empty if your timetable varies — you can set this later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        WeeklySchedule(com.niranjan.ninety.domain.WeekTemplate(state.timetable.entries.groupBy(
            { it.key.first }, { it.key.second to it.value }).mapValues { it.value.toMap() }),
            nameOf, slotTag = "setup_slot", onAssign = onAssign)
    }
}

@Composable
private fun ReviewStep(state: SetupState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.semesterName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("Starts ${state.startDate?.medium() ?: "—"} · ${state.zoneId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.courses.size} courses · ${state.timetable.size} timetable slots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.courses.forEach { course ->
            Text(course.name +
                if (course.hasBalance) " (opens at ${course.present}+${course.approvedDl}DL of ${course.conducted})" else "",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CourseDraftDialog(existing: CourseDraft, semesterStart: LocalDate?,
    onSave: (CourseDraft) -> Unit, onDismiss: () -> Unit) {
    val focus = LocalFocusManager.current
    var draft by remember { mutableStateOf(existing) }
    var pickLogging by remember { mutableStateOf(false) }
    var showBalance by remember { mutableStateOf(existing.hasBalance) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (existing.name.isBlank()) "Add a course" else "Edit course") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Course name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("draft_name"))
                ChoiceRow(CourseType.entries.map { it.name to typeLabel(it) }, draft.type.name,
                    onSelect = { draft = draft.copy(type = CourseType.valueOf(it)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showBalance, onCheckedChange = { showBalance = it })
                    Text("I already have attendance for this course",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (showBalance) {
                    TextButton(onClick = { pickLogging = true }) {
                        Text("First logging date: ${draft.loggingDate.ifBlank { "choose" }}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = draft.conducted,
                            onValueChange = { draft = draft.copy(conducted = it) },
                            label = { Text("Conducted") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(value = draft.present,
                            onValueChange = { draft = draft.copy(present = it) },
                            label = { Text("Present") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = draft.approvedDl,
                            onValueChange = { draft = draft.copy(approvedDl = it) },
                            label = { Text("Approved DL") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(value = draft.pendingDl,
                            onValueChange = { draft = draft.copy(pendingDl = it) },
                            label = { Text("Pending DL") }, singleLine = true,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(if (showBalance) draft else draft.copy(conducted = "0",
                    present = "0", approvedDl = "0", pendingDl = "0"))
            }, modifier = Modifier.testTag("draft_save")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (pickLogging) {
        NinetyDatePicker(draft.loggingDate.toLocalDateOrNull() ?: semesterStart ?: LocalDate.now(),
            onPicked = { draft = draft.copy(loggingDate = it.toString()); pickLogging = false },
            onDismiss = { pickLogging = false })
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

private fun typeLabel(type: CourseType): String = when (type) {
    CourseType.LAB -> "Lab"; CourseType.OTHER -> "Other"; else -> "Theory"
}
