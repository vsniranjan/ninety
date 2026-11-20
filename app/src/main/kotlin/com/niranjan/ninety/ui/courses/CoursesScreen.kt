package com.niranjan.ninety.ui.courses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseSetup
import com.niranjan.ninety.data.CourseStats
import com.niranjan.ninety.domain.AttendanceSummary
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.caution
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.openingCountError
import com.niranjan.ninety.ui.percentOf
import com.niranjan.ninety.ui.setup.CourseDraft
import com.niranjan.ninety.ui.setup.CourseDraftDialog
import com.niranjan.ninety.ui.weekdayAndDate
import java.time.LocalDate
import java.util.UUID

@Composable
fun CoursesScreen(semesterId: UUID, semesterStart: LocalDate, today: LocalDate, snackbar: SnackbarHostState,
    onEditTimetable: () -> Unit, onOpenLeave: () -> Unit, onOpenCourse: (String) -> Unit) {
    val model = ninetyViewModel<CoursesViewModel>(key = "courses-$semesterId-$today") {
        CoursesViewModel(it.repository, semesterId, today)
    }
    val stats by model.stats.collectAsStateWithLifecycle()
    val templates by model.templates.collectAsStateWithLifecycle()
    val leaveStats by model.leaveStats.collectAsStateWithLifecycle()
    val pending = leaveStats?.let { it.pending + it.openingPending }
    val saving by model.saving.collectAsStateWithLifecycle()
    val added by model.courseAdded.collectAsStateWithLifecycle()
    val timetable = templates.lastOrNull { it.effectiveFrom <= maxOf(today, semesterStart) }
    val periodCount = timetable?.template?.assignments?.values?.sumOf { it.size } ?: 0
    var adding by rememberSaveable(semesterId) { mutableStateOf(false) }
    var showAttendanceRules by rememberSaveable { mutableStateOf(false) }
    var showStopped by rememberSaveable(semesterId) { mutableStateOf(false) }
    val addedMessage = stringResource(R.string.course_added_timetable)
    val timetableLabel = stringResource(R.string.timetable)

    CourseMessages(model, snackbar)
    LaunchedEffect(added) {
        if (added) {
            adding = false
            try {
                if (snackbar.showSnackbar(addedMessage, actionLabel = timetableLabel,
                        duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    onEditTimetable()
                }
            } finally {
                model.courseAdded.value = false
            }
        }
    }

    val list = stats
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("courses_list"),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.attendance_minimum), style = MaterialTheme.typography.titleMedium)
                ActionIcon(R.drawable.ic_info, stringResource(R.string.attendance_rules_title),
                    { showAttendanceRules = true }, modifier = Modifier.testTag("attendance_rules"))
            }
            StandingSummary(list)
            if (list.any { it.summary.hasIncompleteData }) {
                Text(stringResource(R.string.courses_unmarked_note), Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            ActionRow(R.drawable.ic_calendar, timetableLabel, onEditTimetable,
                modifier = Modifier.testTag("open_timetable"), showChevron = true,
                supportingText = stringResource(R.string.weekly_timetable_row, stringResource(R.string.weekdays_range),
                    pluralStringResource(R.plurals.periods_per_week, periodCount, periodCount)))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow(R.drawable.ic_leave, stringResource(R.string.duty_leave), onOpenLeave,
                modifier = Modifier.testTag("open_duty_leave"), showChevron = true,
                supportingText = pending?.let {
                    pluralStringResource(R.plurals.duty_leave_periods_pending, it.pluralQuantity(), it)
                })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        items(list.filterNot { it.course.retired }, key = { it.course.id }) { item ->
            CourseRow(item) { onOpenCourse(item.course.id) }
        }
        item {
            ActionRow(R.drawable.ic_add, stringResource(R.string.add_course), { adding = true },
                modifier = Modifier.testTag("add_course"))
        }
        val stopped = list.filter { it.course.retired }
        if (stopped.isNotEmpty()) {
            item {
                val sectionState = stringResource(if (showStopped) R.string.section_expanded else R.string.section_collapsed)
                ActionRow(if (showStopped) R.drawable.ic_minus else R.drawable.ic_chevron_down,
                    stringResource(R.string.no_longer_tracked), { showStopped = !showStopped },
                    modifier = Modifier.semantics {
                        stateDescription = sectionState
                    })
            }
            if (showStopped) items(stopped, key = { it.course.id }) { item ->
                CourseRow(item) { onOpenCourse(item.course.id) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAttendanceRules) AttendanceRulesSheet { showAttendanceRules = false }
    if (adding) {
        val draft = remember { CourseDraft(loggingDate = today.toString()) }
        val names = list.orEmpty().map { CourseDraft(id = UUID.fromString(it.course.id), name = it.course.name) }
        CourseDraftDialog(draft, names, semesterStart, today,
            onSave = { input ->
                val balance = if (input.hasBalance) OpeningBalance(input.id, LocalDate.parse(input.loggingDate),
                    input.conducted.toLong(), input.present.toLong(), input.approvedDl.toLong(), input.pendingDl.toLong())
                    else null
                model.addCourse(CourseSetup(input.id, CourseInput(input.name, input.type), balance))
            },
            onDismiss = { if (!saving) adding = false },
            balanceLabel = R.string.attendance_so_far, nameTag = "course_name", saveTag = "course_save", saving = saving)
    }
}

@Composable
fun CourseDetailScreen(semesterId: UUID, courseId: String, today: LocalDate, snackbar: SnackbarHostState,
    onOpenDay: (LocalDate) -> Unit, onOpenLeave: () -> Unit) {
    val model = ninetyViewModel<CoursesViewModel>(key = "course-$semesterId-$today") {
        CoursesViewModel(it.repository, semesterId, today)
    }
    val stats by model.stats.collectAsStateWithLifecycle()
    val item = stats?.firstOrNull { it.course.id == courseId }
    CourseMessages(model, snackbar)
    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (stats == null) CircularProgressIndicator()
            else EmptyState(stringResource(R.string.course_unavailable), "")
        }
        return
    }

    val summary = item.summary
    var dialog by rememberSaveable(courseId) { mutableStateOf<CourseDialog?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("course_detail")) {
        item {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.course.name, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(typeLabel(item.course.type), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.course.retired) Text(stringResource(R.string.no_longer_tracked),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AttendanceStanding(summary)
                if (summary.hasIncompleteData) {
                    Text(stringResource(R.string.courses_unmarked_note), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            SectionLabel(stringResource(R.string.attendance_breakdown))
            AttendanceDetails(item)
        }
        item {
            ActionRow(R.drawable.ic_leave, stringResource(R.string.duty_leave_for_course), onOpenLeave,
                modifier = Modifier.testTag("course_duty_leave"), showChevron = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow(R.drawable.ic_edit, stringResource(R.string.rename), { dialog = CourseDialog.Edit },
                modifier = Modifier.testTag("course_rename"))
            ActionRow(R.drawable.ic_history, stringResource(R.string.attendance_before_tracking_action),
                { dialog = CourseDialog.Balance }, modifier = Modifier.testTag("course_balance"))
            if (!item.course.retired) ActionRow(R.drawable.ic_remove, stringResource(R.string.stop_tracking_course),
                { dialog = CourseDialog.StopTracking }, modifier = Modifier.testTag("course_stop"))
        }
        val dates = summary.incompleteDates.sortedDescending()
        if (dates.isNotEmpty()) {
            item {
                SectionLabel(pluralStringResource(R.plurals.course_unmarked_periods,
                    summary.missingPastPeriods.pluralQuantity(), summary.missingPastPeriods),
                    Modifier.padding(top = 16.dp))
            }
            items(dates, key = { it.toEpochDay() }) { date ->
                ActionRow(R.drawable.ic_calendar, date.weekdayAndDate(), { onOpenDay(date) },
                    showChevron = true, modifier = Modifier.testTag("course_unmarked_$date"))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    when (dialog) {
        CourseDialog.Edit -> CourseFormDialog(item, onDismiss = { dialog = null },
            onSave = { input -> model.updateCourse(UUID.fromString(courseId), input); dialog = null })
        CourseDialog.Balance -> BalanceDialog(item, onDismiss = { dialog = null },
            onSave = { model.correctBalance(it); dialog = null })
        CourseDialog.StopTracking -> ConfirmDialog(stringResource(R.string.stop_tracking_question, item.course.name),
            stringResource(R.string.stop_tracking_body), stringResource(R.string.stop_tracking_course), destructive = true,
            onConfirm = { model.retireCourse(UUID.fromString(courseId)); dialog = null },
            onDismiss = { dialog = null })
        null -> Unit
    }
}

private enum class CourseDialog { Edit, Balance, StopTracking }

@Composable
private fun CourseMessages(model: CoursesViewModel, snackbar: SnackbarHostState) {
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessage() } }
}

@Composable
private fun CourseRow(item: CourseStats, onClick: () -> Unit) {
    val summary = item.summary
    val stack = LocalDensity.current.fontScale > 1.3f
    val openCourse = stringResource(R.string.open_course, item.course.name)
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("course_row_${item.course.id}")
        .clickable(role = Role.Button, onClickLabel = openCourse, onClick = onClick).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(typeLabel(item.course.type), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!stack && summary.conducted > 0) CoursePercentage(summary, summary.standing().color())
        }
        AttendanceStanding(summary, showPercentage = stack)
        if (summary.hasIncompleteData) {
            Text(pluralStringResource(R.plurals.course_unmarked_periods, summary.missingPastPeriods.pluralQuantity(),
                summary.missingPastPeriods), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AttendanceStanding(summary: AttendanceSummary, showPercentage: Boolean = true) {
    val standing = summary.standing()
    val standingColor = standing.color()
    if (showPercentage && summary.conducted > 0) CoursePercentage(summary, standingColor)
    if (summary.isProvisional) {
        Text(stringResource(R.string.attendance_if_leave_rejected, percentOf(summary.confirmedCredited, summary.conducted)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.caution.foreground)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (standing != null) Icon(painterResource(standing.icon), contentDescription = null,
            modifier = Modifier.size(18.dp), tint = standingColor)
        Text(attendanceNextStep(summary), style = MaterialTheme.typography.bodyMedium, color = standingColor)
    }
}

private enum class CourseStanding(val icon: Int) {
    BELOW(R.drawable.ic_close),
    AT_LIMIT(R.drawable.ic_minus),
    SAFE(R.drawable.ic_check),
}

private fun AttendanceSummary.standing(): CourseStanding? {
    val periodsToRecover = recovery ?: return null
    return when {
        periodsToRecover > 0 -> CourseStanding.BELOW
        safeSkips == 0L -> CourseStanding.AT_LIMIT
        else -> CourseStanding.SAFE
    }
}

@Composable
private fun CourseStanding?.color(): Color = when (this) {
    CourseStanding.BELOW -> MaterialTheme.colorScheme.error
    CourseStanding.AT_LIMIT -> MaterialTheme.caution.foreground
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun StandingSummary(courses: List<CourseStats>) {
    val standings = courses.mapNotNull { it.summary.standing() }
    val below = standings.count { it == CourseStanding.BELOW }
    val atLimit = standings.count { it == CourseStanding.AT_LIMIT }
    if (standings.isNotEmpty()) {
        Text(stringResource(R.string.course_standing_summary, below, atLimit),
            modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoursePercentage(summary: AttendanceSummary, color: Color) {
    Text(percentOf(summary.credited, summary.conducted),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun attendanceNextStep(summary: AttendanceSummary): String {
    val recovery = summary.recovery ?: return stringResource(R.string.no_attendance_recorded)
    val skips = summary.safeSkips ?: 0L
    val allowance = when {
        recovery > 0 -> pluralStringResource(R.plurals.attend_next_periods, recovery.pluralQuantity(), recovery)
        skips > 0 -> pluralStringResource(R.plurals.can_miss_periods, skips.pluralQuantity(), skips)
        else -> stringResource(R.string.no_absences_to_spare)
    }
    return if (summary.isProvisional) {
        pluralStringResource(R.plurals.provisional_allowance, summary.pendingDl.pluralQuantity(),
            allowance, summary.pendingDl)
    } else allowance
}

private fun Long.pluralQuantity() = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun AttendanceDetails(item: CourseStats) {
    val summary = item.summary
    Column(Modifier.padding(top = 10.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (summary.conducted > 0) {
            Text(stringResource(R.string.counted_as_attended, summary.credited, summary.conducted),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DetailRow(stringResource(R.string.attended_in_person), summary.physicallyPresent.toString())
            DetailRow(stringResource(R.string.duty_leave_count_approved), summary.approvedDl.toString())
            DetailRow(stringResource(R.string.duty_leave_count_pending), summary.pendingDl.toString())
            DetailRow(stringResource(R.string.duty_leave_count_rejected), summary.datedRejectedDl.toString())
            if (summary.isProvisional) DetailRow(stringResource(R.string.without_pending_leave),
                percentOf(summary.confirmedCredited, summary.conducted))
        }
        if (item.balance.pendingDl > 0) {
            DetailRow(stringResource(R.string.pending_before_tracking), item.balance.pendingDl.toString())
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CourseFormDialog(existing: CourseStats, onDismiss: () -> Unit,
    onSave: (CourseInput) -> Unit) {
    var name by remember { mutableStateOf(existing.course.name) }
    var type by remember { mutableStateOf(runCatching { CourseType.valueOf(existing.course.type) }.getOrDefault(CourseType.THEORY)) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_course, existing.course.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.course_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("course_name"))
                ChoiceRow(CourseType.entries.map { it.name to typeLabel(it.name) }, type.name,
                    onSelect = { type = CourseType.valueOf(it) })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(CourseInput(name, type, existing.course.colorArgb)) },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("course_save")) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun BalanceDialog(item: CourseStats, onDismiss: () -> Unit, onSave: (OpeningBalance) -> Unit) {
    val b = item.balance
    var logging by remember { mutableStateOf(b.loggingStartDate) }
    var conducted by remember { mutableStateOf(b.conducted.toString()) }
    var present by remember { mutableStateOf(b.present.toString()) }
    var approved by remember { mutableStateOf(b.approvedDl.toString()) }
    var pending by remember { mutableStateOf(b.pendingDl.toString()) }
    var pickDate by remember { mutableStateOf(false) }
    val c = conducted.toLongOrNull()
    val p = present.toLongOrNull()
    val a = approved.toLongOrNull()
    val pe = pending.toLongOrNull()
    val error = openingCountError(c, p, a, pe)
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(R.string.before_tracking_for_course, stringResource(R.string.before_tracking), item.course.name))
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { pickDate = true }) {
                Text(stringResource(R.string.tracking_starts_on, logging.medium()))
            }
            listOf(R.string.classes_held to conducted, R.string.attended_in_person to present,
                R.string.duty_leave_count_approved to approved, R.string.duty_leave_count_pending to pending
            ).forEachIndexed { index, (label, value) ->
                OutlinedTextField(value = value, singleLine = true,
                    onValueChange = { v ->
                        when (index) {
                            0 -> conducted = v
                            1 -> present = v
                            2 -> approved = v
                            else -> pending = v
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
            }
            if (error != null) {
                Text(stringResource(error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            } else if (c != null && p != null && a != null && pe != null) {
                Text(stringResource(R.string.absence_count, c - p - a - pe), style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (c != null && p != null && a != null && pe != null && error == null) {
                onSave(OpeningBalance(UUID.fromString(item.course.id), logging, c, p, a, pe))
            }
        }, enabled = error == null) { Text(stringResource(R.string.save)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (pickDate) {
        NinetyDatePicker(logging, onPicked = { logging = it; pickDate = false },
            onDismiss = { pickDate = false })
    }
}

private fun typeLabel(type: String): String = when (type) {
    "LAB" -> "Lab"; "OTHER" -> "Other"; else -> "Theory"
}
