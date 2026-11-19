package com.niranjan.ninety.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.PeriodProgress
import com.niranjan.ninety.ui.designsystem.PrimaryAction
import com.niranjan.ninety.ui.designsystem.ScreenHeading
import com.niranjan.ninety.ui.designsystem.WeeklySchedule
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.openingCountError
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 24.dp)
            .verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Ninety", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.step + 1} / ${state.stepCount}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                onCancel?.let { cancel -> ActionIcon(R.drawable.ic_close, "Cancel setup", cancel) }
            }
            PeriodProgress(state.step + 1, state.stepCount,
                description = stringResource(R.string.setup_steps, state.step + 1, state.stepCount))
            ScreenHeading(stepTitle(state.step))

            when (state.step) {
                0 -> SemesterStep(state, { pickStart = true },
                    model::setSemesterName, model::setZone)
                1 -> CoursesStep(state, onAdd = { courseDialog = CourseDraft() },
                    onEdit = { editingCourse = it }, onRemove = model::removeCourse)
                2 -> TimetableStep(state, model::copyDay, model::assign)
                else -> ReviewStep(state)
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp).testTag("setup_error"))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (state.step > 0) ActionIcon(R.drawable.ic_back, "Back", { focus.clearFocus(); model.back() })
                if (state.step < state.stepCount - 1) {
                    PrimaryAction("Continue", { focus.clearFocus(); model.next() }, icon = R.drawable.ic_arrow_right,
                        modifier = Modifier.weight(1f).testTag("setup_action"))
                } else {
                    PrimaryAction(if (state.saving) "Saving…" else "Start tracking",
                        { focus.clearFocus(); model.save() }, enabled = !state.saving,
                        modifier = Modifier.weight(1f).testTag("setup_action"))
                }
            }
            Spacer(Modifier.height(32.dp))

        }
    }

    courseDialog?.let { draft ->
        CourseDraftDialog(draft, state.courses, state.startDate, LocalDate.now(ZoneId.of(state.zoneId)),
            onSave = { model.addCourse(it); courseDialog = null },
            onDismiss = { courseDialog = null })
    }
    editingCourse?.let { draft ->
        CourseDraftDialog(draft, state.courses, state.startDate, LocalDate.now(ZoneId.of(state.zoneId)),
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
                ActionIcon(R.drawable.ic_calendar, "Semester start", onPickDate, modifier = Modifier.testTag("start_date"))
            }
        }
        var zoneOpen by remember { mutableStateOf(false) }
        NinetyCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("College timezone", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.zoneId, style = MaterialTheme.typography.titleMedium)

                }
                Box {
                    ActionIcon(R.drawable.ic_clock, "Change college timezone", { zoneOpen = true })
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
        state.courses.forEach { course ->
            NinetyCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(course.name.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(typeLabel(course.type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (course.hasBalance) {
                            val count = course.conducted.toLong()
                            Text(stringResource(R.string.before_tracking_for_course, stringResource(R.string.before_tracking),
                                pluralStringResource(R.plurals.period_count, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ActionIcon(R.drawable.ic_edit, "Edit ${course.name}", { onEdit(course) })
                    IconButton(onClick = { onRemove(course.id) }) {
                        Icon(painterResource(R.drawable.ic_remove), "Remove course")
                    }
                }
            }
        }
        PrimaryAction("Add course", onAdd, icon = R.drawable.ic_add,
            modifier = Modifier.fillMaxWidth().testTag("add_course"))
    }
}

@Composable
private fun TimetableStep(state: SetupState, onCopy: (DayOfWeek, DayOfWeek) -> Unit,
    onAssign: (DayOfWeek, Int, UUID?) -> Unit) {
    val nameOf = state.courses.associate { it.id to it.name }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeeklySchedule(com.niranjan.ninety.domain.WeekTemplate(state.timetable.entries.groupBy(
            { it.key.first }, { it.key.second to it.value }).mapValues { it.value.toMap() }),
            nameOf, slotTag = "setup_slot", onCopy = onCopy, onAssign = onAssign)
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
                Text(stringResource(R.string.setup_review_counts,
                    pluralStringResource(R.plurals.course_count, state.courses.size, state.courses.size),
                    pluralStringResource(R.plurals.period_count, state.timetable.size, state.timetable.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.courses.forEach { course ->
            Text(course.name, style = MaterialTheme.typography.bodyMedium)
            if (course.hasBalance) {
                Text(stringResource(R.string.before_tracking), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.course_before_tracking, course.present.toLong(),
                    course.approvedDl.toLong(), course.pendingDl.toLong(), course.conducted.toLong()),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun CourseDraftDialog(existing: CourseDraft, courses: List<CourseDraft>, semesterStart: LocalDate?, today: LocalDate,
    onSave: (CourseDraft) -> Unit, onDismiss: () -> Unit,
    balanceLabel: Int = R.string.before_tracking, nameTag: String = "draft_name",
    saveTag: String = "draft_save", saving: Boolean = false) {
    val focus = LocalFocusManager.current
    var draft by remember { mutableStateOf(existing) }
    var pickLogging by remember { mutableStateOf(false) }
    var showBalance by remember { mutableStateOf(existing.hasBalance) }
    val nameError = when {
        draft.name.isBlank() -> R.string.course_name_required
        courses.any { it.id != draft.id && it.name.trim().lowercase(Locale.ROOT) ==
            draft.name.trim().lowercase(Locale.ROOT) } -> R.string.course_name_duplicate
        else -> null
    }
    val logging = draft.loggingDate.toLocalDateOrNull()
    val countError = if (showBalance) openingCountError(draft.conducted.toLongOrNull(),
        draft.present.toLongOrNull(), draft.approvedDl.toLongOrNull(), draft.pendingDl.toLongOrNull()) else null
    val dateError = if (showBalance && draft.hasBalance) when {
        logging == null -> R.string.choose_tracking_date
        semesterStart != null && logging <= semesterStart -> R.string.tracking_date_after_start
        logging > today -> R.string.tracking_date_by_today
        else -> null
    } else null
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing.name.isBlank()) R.string.add_course else R.string.edit_course)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.course_name)) }, singleLine = true,
                    isError = nameError != null && (draft.name.isNotEmpty() || existing.name.isNotEmpty()),
                    supportingText = if (nameError != null && (draft.name.isNotEmpty() || existing.name.isNotEmpty()))
                        ({ Text(stringResource(nameError)) }) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag(nameTag))
                ChoiceRow(CourseType.entries.map { it.name to typeLabel(it) }, draft.type.name,
                    onSelect = { draft = draft.copy(type = CourseType.valueOf(it)) })
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = showBalance, role = Role.Checkbox, onValueChange = { showBalance = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showBalance, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(balanceLabel),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (showBalance) {
                    TextButton(onClick = { pickLogging = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.tracking_starts_on,
                            logging?.medium() ?: stringResource(R.string.choose_date)))
                    }
                    dateError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    val fields = listOf(
                        Triple(R.string.classes_held, draft.conducted, { value: String -> draft = draft.copy(conducted = value) }),
                        Triple(R.string.attended_in_person, draft.present, { value: String -> draft = draft.copy(present = value) }),
                        Triple(R.string.duty_leave_count_approved, draft.approvedDl, { value: String -> draft = draft.copy(approvedDl = value) }),
                        Triple(R.string.duty_leave_count_pending, draft.pendingDl, { value: String -> draft = draft.copy(pendingDl = value) }),
                    )
                    fields.forEach { (label, value, onChange) ->
                        OutlinedTextField(value = value, onValueChange = onChange,
                            label = { Text(stringResource(label)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = value.toLongOrNull()?.let { it < 0 } ?: true,
                            modifier = Modifier.fillMaxWidth())
                    }
                    if (countError != null) {
                        Text(stringResource(countError), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        val absent = draft.conducted.toLong() - draft.present.toLong() -
                            draft.approvedDl.toLong() - draft.pendingDl.toLong()
                        Text(stringResource(R.string.absence_count, absent), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(if (showBalance) draft else draft.copy(conducted = "0",
                    present = "0", approvedDl = "0", pendingDl = "0"))
            }, enabled = !saving && nameError == null && countError == null && dateError == null,
                modifier = Modifier.heightIn(min = 48.dp).testTag(saveTag)) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (pickLogging) {
        NinetyDatePicker(logging ?: semesterStart ?: today,
            onPicked = { draft = draft.copy(loggingDate = it.toString()); pickLogging = false },
            onDismiss = { pickLogging = false }, minDate = semesterStart)
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

private fun typeLabel(type: CourseType): String = when (type) {
    CourseType.LAB -> "Lab"; CourseType.OTHER -> "Other"; else -> "Theory"
}
