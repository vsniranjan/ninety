package com.niranjan.ninety.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.AppSettings
import com.niranjan.ninety.data.SemesterInfo
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.CollegeZoneAction
import com.niranjan.ninety.ui.designsystem.CollegeZonePicker
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.NinetyAlertDialog
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.NinetySnackbarHost
import com.niranjan.ninety.ui.designsystem.NinetyTextField
import com.niranjan.ninety.ui.designsystem.PeriodProgress
import com.niranjan.ninety.ui.designsystem.PrimaryAction
import com.niranjan.ninety.ui.designsystem.ScreenHeading
import com.niranjan.ninety.ui.designsystem.SettingSwitch
import com.niranjan.ninety.ui.designsystem.WeeklySchedule
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.openingCountError
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(model: SetupViewModel, currentSemester: SemesterInfo? = null, settings: AppSettings = AppSettings(),
    onSaved: () -> Unit = {}, onCancel: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val today = model.today(state.zoneId)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo)
    val removedMessage = stringResource(R.string.setup_course_removed)
    val permissionMessage = stringResource(R.string.reminder_permission_denied)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.setEnableReminder(granted)
        if (!granted) scope.launch { snackbar.showSnackbar(permissionMessage) }
    }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    var courseDialog by remember { mutableStateOf<CourseDraft?>(null) }
    var editingCourse by remember { mutableStateOf<CourseDraft?>(null) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickZone by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val requestCancel: () -> Unit = {
        if (state.hasDraft) confirmCancel = true else onCancel?.invoke()
    }

    BackHandler(enabled = state.step > 0 || onCancel != null) {
        if (!state.saving) {
            if (state.step > 0) model.back() else requestCancel()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    if (pickZone) {
        Scaffold { padding ->
            CollegeZonePicker(state.zoneId, onSelect = { zone ->
                model.setZone(zone)
                pickZone = false
            }, onDismiss = { pickZone = false }, modifier = Modifier.padding(padding))
        }
        return
    }

    Scaffold(snackbarHost = { NinetySnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 24.dp)
            .verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CapsText("Ninety", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                if (onCancel != null) {
                    ActionIcon(R.drawable.ic_close, stringResource(R.string.cancel_setup), requestCancel,
                        enabled = !state.saving)
                }
            }
            PeriodProgress(state.step + 1, state.stepCount,
                description = stringResource(R.string.setup_steps, state.step + 1, state.stepCount))
            ScreenHeading(stepTitle(state.step))

            when (state.step) {
                0 -> SemesterStep(state, today, { pickStart = true },
                    model::setSemesterName, { pickZone = true }, model::setEnterAttendanceSoFar)
                1 -> CoursesStep(state, today, onAdd = { courseDialog = CourseDraft() },
                    onEdit = { editingCourse = it }, onRemove = { id ->
                        model.removeCourse(id)?.let { removed ->
                            scope.launch {
                                val result = snackbar.showSnackbar(removedMessage, actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) model.restoreCourse(removed)
                            }
                        }
                    })
                2 -> TimetableStep(state, model::copyDay, model::assign)
                else -> {
                    ReviewStep(state, today)
                    if (!settings.reminderEnabled) {
                        Spacer(Modifier.height(12.dp))
                        val reminderTime = java.time.LocalTime.of(settings.reminderMinute / 60, settings.reminderMinute % 60)
                            .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
                        SettingSwitch(stringResource(R.string.setup_reminder_at, reminderTime), state.enableReminder,
                            enabled = !state.saving) { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                model.setEnableReminder(enabled)
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp).testTag("setup_error"))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (state.step > 0) ActionIcon(R.drawable.ic_back, "Back", { focus.clearFocus(); model.back() },
                    enabled = !state.saving)
                if (state.step < state.stepCount - 1) {
                    PrimaryAction("Continue", { focus.clearFocus(); model.next() }, icon = R.drawable.ic_arrow_right,
                        modifier = Modifier.weight(1f).testTag("setup_action"))
                } else {
                    PrimaryAction(if (state.saving) "Saving…" else "Start tracking",
                        {
                            focus.clearFocus()
                            if (currentSemester == null) model.save(settings = settings) else confirmFinish = true
                        }, enabled = !state.saving,
                        modifier = Modifier.weight(1f).testTag("setup_action"))
                }
            }
            Spacer(Modifier.height(32.dp))

        }
    }

    courseDialog?.let { draft ->
        CourseDraftDialog(if (state.offersAttendanceSoFar(today)) state.courseForSetup(draft, today) else draft,
            state.courses, state.startDate, today,
            allowBalance = state.offersAttendanceSoFar(today),
            expandBalance = state.prefillsAttendanceSoFar(today),
            onSave = { model.addCourse(it); courseDialog = null },
            onDismiss = { courseDialog = null })
    }
    editingCourse?.let { draft ->
        CourseDraftDialog(if (state.offersAttendanceSoFar(today)) state.courseForSetup(draft, today) else draft,
            state.courses, state.startDate, today,
            allowBalance = state.offersAttendanceSoFar(today),
            expandBalance = state.prefillsAttendanceSoFar(today),
            onSave = { model.updateCourse(it); editingCourse = null },
            onDismiss = { editingCourse = null })
    }
    if (confirmCancel) {
        ConfirmDialog(stringResource(R.string.discard_setup), body = null,
            stringResource(R.string.discard), onConfirm = {
                confirmCancel = false
                onCancel?.invoke()
            }, onDismiss = { confirmCancel = false })
    }
    if (confirmFinish && currentSemester != null) {
        NinetyAlertDialog(onDismissRequest = { confirmFinish = false },
            title = { CapsText(stringResource(R.string.finish_semester_question, currentSemester.entity.name)) },
            text = { Text(stringResource(R.string.finish_previous_semester_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    model.save(UUID.fromString(currentSemester.entity.id), settings)
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.finish_semester)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    model.save(settings = settings)
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.keep_semester_paused)) }
            })
    }
    if (state.confirmEmptyTimetable) {
        NinetyAlertDialog(onDismissRequest = model::dismissEmptyTimetable,
            title = { CapsText(stringResource(R.string.empty_timetable_title)) },
            text = { Text(stringResource(R.string.empty_timetable_body)) },
            confirmButton = {
                TextButton(onClick = model::skipEmptyTimetable, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.skip_timetable))
                }
            },
            dismissButton = {
                TextButton(onClick = model::dismissEmptyTimetable, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.add_timetable_classes))
                }
            })
    }
    if (pickStart) {
        NinetyDatePicker(state.startDate ?: today,
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
private fun SemesterStep(state: SetupState, today: LocalDate, onPickDate: () -> Unit,
    onName: (String) -> Unit, onPickZone: () -> Unit, onPastAttendance: (Boolean) -> Unit) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NinetyTextField(value = state.semesterName, onValueChange = onName,
            label = { Text("Semester name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("semester_name"))
        ActionRow(R.drawable.ic_calendar, stringResource(R.string.semester_start_label), onPickDate,
            supportingText = state.startDate?.medium(),
            modifier = Modifier.testTag("start_date"), showChevron = true)
        if (state.asksAboutPastAttendance(today)) {
            Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.setup_midsemester_question), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp))
                listOf(true to R.string.setup_enter_attendance, false to R.string.setup_mark_past_days).forEach { (enter, label) ->
                    val selected = state.enterAttendanceSoFar == enter
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected, role = Role.RadioButton, onClick = { onPastAttendance(enter) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        CollegeZoneAction(state.zoneId, onClick = onPickZone)
    }
}

@Composable
private fun CoursesStep(state: SetupState, today: LocalDate, onAdd: () -> Unit,
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
                        val recorded = state.courseForSetup(course, today)
                        if (recorded.hasBalance) {
                            val count = recorded.conducted.toLong()
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
        ActionRow(R.drawable.ic_add, stringResource(R.string.add_course), onAdd,
            modifier = Modifier.testTag("add_course"))
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
private fun ReviewStep(state: SetupState, today: LocalDate) {
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
                    pluralStringResource(R.plurals.periods_per_week, state.timetable.size, state.timetable.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val scheduledCourses = state.timetable.values.toSet()
        state.courses.map { state.courseForSetup(it, today) }.forEach { course ->
            Column {
                Text(course.name, style = MaterialTheme.typography.bodyMedium)
                if (course.id !in scheduledCourses) {
                    Text(stringResource(R.string.course_not_on_timetable), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (course.loggingDate.isNotBlank()) {
                    course.loggingDate.toLocalDateOrNull()?.let { date ->
                        Text(stringResource(R.string.tracking_starts_on, date.medium()),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (course.hasBalance) {
                    val credited = course.present.toLong() + course.dutyLeave.toLong()
                    Text(stringResource(R.string.setup_attendance_review, credited, course.conducted.toLong()),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun CourseDraftDialog(existing: CourseDraft, courses: List<CourseDraft>, semesterStart: LocalDate?, today: LocalDate,
    onSave: (CourseDraft) -> Unit, onDismiss: () -> Unit,
    balanceLabel: Int = R.string.before_tracking, nameTag: String = "draft_name",
    saveTag: String = "draft_save", saving: Boolean = false,
    allowBalance: Boolean = true, expandBalance: Boolean = false) {
    val focus = LocalFocusManager.current
    var draft by remember { mutableStateOf(existing) }
    var pickLogging by remember { mutableStateOf(false) }
    var showBalance by remember { mutableStateOf(allowBalance && (expandBalance || existing.hasBalance)) }
    val nameError = when {
        draft.name.isBlank() -> R.string.course_name_required
        courses.any { it.id != draft.id && it.name.trim().lowercase(Locale.ROOT) ==
            draft.name.trim().lowercase(Locale.ROOT) } -> R.string.course_name_duplicate
        else -> null
    }
    val logging = draft.loggingDate.toLocalDateOrNull()
    val countError = if (showBalance) openingCountError(draft.conducted.toLongOrNull(),
        draft.present.toLongOrNull(), draft.dutyLeave.toLongOrNull()) else null
    val dateError = if (showBalance && (expandBalance || draft.hasBalance)) when {
        logging == null -> R.string.choose_tracking_date
        semesterStart != null && (logging < semesterStart || (draft.hasBalance && logging == semesterStart)) -> R.string.tracking_date_after_start
        logging > today -> R.string.tracking_date_by_today
        else -> null
    } else null
    NinetyAlertDialog(onDismissRequest = onDismiss,
        title = { CapsText(stringResource(if (existing.name.isBlank()) R.string.add_course else R.string.edit_course)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NinetyTextField(value = draft.name,
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
                if (expandBalance) {
                    Text(stringResource(R.string.attendance_so_far), style = MaterialTheme.typography.titleSmall)
                } else if (allowBalance) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = showBalance, role = Role.Checkbox, onValueChange = { showBalance = it }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showBalance, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                        Text(stringResource(balanceLabel),
                            style = MaterialTheme.typography.bodyMedium)
                    }
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
                        Triple(R.string.duty_leave, draft.dutyLeave, { value: String -> draft = draft.copy(dutyLeave = value) }),
                    )
                    fields.forEach { (label, value, onChange) ->
                        NinetyTextField(value = value, onValueChange = onChange,
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
                            draft.dutyLeave.toLong()
                        Text(stringResource(R.string.absence_count, absent), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!allowBalance) onSave(existing.copy(name = draft.name, type = draft.type))
                else onSave(if (showBalance) draft else draft.copy(conducted = "0",
                    present = "0", dutyLeave = "0"))
            }, enabled = !saving && nameError == null && countError == null && dateError == null,
                modifier = Modifier.heightIn(min = 48.dp).testTag(saveTag)) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (pickLogging) {
        NinetyDatePicker(logging ?: semesterStart ?: today,
            onPicked = { draft = draft.copy(loggingDate = it.toString()); pickLogging = false },
            onDismiss = { pickLogging = false }, minDate = semesterStart, maxDate = today)
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

private fun typeLabel(type: CourseType): String = when (type) {
    CourseType.LAB -> "Lab"; CourseType.OTHER -> "Other"; else -> "Theory"
}
