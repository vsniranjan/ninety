package com.niranjan.ninety.ui.courses

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseStats
import com.niranjan.ninety.domain.AttendanceSummary
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.ScreenHeading
import com.niranjan.ninety.ui.designsystem.caution
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.openingCountError
import com.niranjan.ninety.ui.percentOf
import com.niranjan.ninety.ui.weekdayAndDate
import java.time.LocalDate
import java.util.UUID

@Composable
fun CoursesScreen(semesterId: UUID, snackbar: SnackbarHostState, onEditTimetable: () -> Unit,
    onOpenDay: (LocalDate) -> Unit, onOpenPendingLeave: (String) -> Unit) {
    val model = ninetyViewModel<CoursesViewModel>(key = "courses-$semesterId") {
        CoursesViewModel(it.repository, semesterId)
    }
    val stats by model.stats.collectAsStateWithLifecycle()
    val templates by model.templates.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    val periodCount = templates.lastOrNull { it.effectiveFrom <= today }
        ?.template?.assignments?.values?.sumOf { it.size } ?: 0
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    var dialog by remember(semesterId) { mutableStateOf<CourseDialog?>(null) }
    var showAttendanceRules by rememberSaveable { mutableStateOf(false) }
    var unmarkedCourse by rememberSaveable(semesterId) { mutableStateOf<String?>(null) }

    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ScreenHeading(
            stringResource(R.string.courses), stringResource(R.string.attendance_minimum),
            detailAction = {
                ActionIcon(R.drawable.ic_info, stringResource(R.string.attendance_rules_title),
                    { showAttendanceRules = true }, modifier = Modifier.testTag("attendance_rules"))
            },
        ) {
            ActionIcon(R.drawable.ic_add, "Add course", { dialog = CourseDialog.Add },
                modifier = Modifier.testTag("add_course"), tint = MaterialTheme.colorScheme.primary)
        }

        when (val list = stats) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item {
                    StandingSummary(list)
                    if (list.any { it.summary.hasIncompleteData }) {
                        Text(stringResource(R.string.courses_unmarked_note),
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    ActionRow(R.drawable.ic_calendar, stringResource(R.string.timetable), onEditTimetable,
                        modifier = Modifier.testTag("open_timetable"), showChevron = true,
                        supportingText = stringResource(R.string.weekly_timetable_row, stringResource(R.string.weekdays_range),
                            pluralStringResource(R.plurals.periods_per_week, periodCount, periodCount)))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(list, key = { it.course.id }) { item ->
                    CourseCard(item, onEdit = { dialog = CourseDialog.Edit(item) },
                        onBalance = { dialog = CourseDialog.Balance(item) },
                        onUnmarked = { unmarkedCourse = item.course.id },
                        onOpenPendingLeave = { onOpenPendingLeave(item.course.id) },
                        onResolveDl = { approve -> model.resolveOpeningDl(UUID.fromString(item.course.id), approve) },
                        onRetire = { dialog = CourseDialog.Retire(item) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showAttendanceRules) AttendanceRulesSheet { showAttendanceRules = false }

    stats?.firstOrNull { it.course.id == unmarkedCourse }?.let { item ->
        AlertDialog(
            onDismissRequest = { unmarkedCourse = null },
            title = { Text(stringResource(R.string.course_unmarked_dates, item.course.name)) },
            text = {
                val dates = item.summary.incompleteDates.sortedDescending()
                if (dates.isEmpty()) {
                    Text(stringResource(R.string.no_unmarked_days))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(dates, key = { it.toEpochDay() }) { date ->
                            ActionRow(R.drawable.ic_calendar, date.weekdayAndDate(), onClick = {
                                unmarkedCourse = null
                                onOpenDay(date)
                            }, showChevron = true, modifier = Modifier.testTag("course_unmarked_$date"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { unmarkedCourse = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.done))
                }
            },
        )
    }

    when (val d = dialog) {
        CourseDialog.Add -> CourseFormDialog(null, onDismiss = { dialog = null },
            onSave = { input -> model.addCourse(input, null); dialog = null })
        is CourseDialog.Edit -> CourseFormDialog(d.stats, onDismiss = { dialog = null },
            onSave = { input -> model.updateCourse(UUID.fromString(d.stats.course.id), input); dialog = null })
        is CourseDialog.Balance -> BalanceDialog(d.stats, onDismiss = { dialog = null },
            onSave = { model.correctBalance(it); dialog = null })
        is CourseDialog.Retire -> ConfirmDialog(stringResource(R.string.stop_tracking_question, d.stats.course.name),
            stringResource(R.string.stop_tracking_body), stringResource(R.string.stop_tracking_course), destructive = true,
            onConfirm = { model.retireCourse(UUID.fromString(d.stats.course.id)); dialog = null },
            onDismiss = { dialog = null })
        null -> Unit
    }
}

private sealed interface CourseDialog {
    data object Add : CourseDialog
    data class Edit(val stats: CourseStats) : CourseDialog
    data class Balance(val stats: CourseStats) : CourseDialog
    data class Retire(val stats: CourseStats) : CourseDialog
}

@Composable
private fun CourseCard(item: CourseStats, onEdit: () -> Unit, onBalance: () -> Unit,
    onResolveDl: (Boolean) -> Unit, onRetire: () -> Unit, onUnmarked: () -> Unit, onOpenPendingLeave: () -> Unit) {
    val summary = item.summary
    val standing = summary.standing()
    val hasAttendance = standing != null
    val standingColor = standing.color()
    val hasDetails = hasAttendance || summary.hasIncompleteData || item.balance.pendingDl > 0
    val stackPercentage = LocalDensity.current.fontScale > 1.3f
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val openPendingLeave = when {
        !summary.isProvisional -> null
        summary.pendingDl > item.balance.pendingDl -> onOpenPendingLeave
        else -> ({ expanded = true })
    }
    val pendingLeaveAction = stringResource(R.string.view_pending_leave)

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.course.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(typeLabel(item.course.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasAttendance && !stackPercentage) CoursePercentage(summary, standingColor)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more), "Actions for ${item.course.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit course") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.before_tracking)) }, onClick = { menu = false; onBalance() })
                        if (!item.course.retired) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.stop_tracking_course)) }, onClick = { menu = false; onRetire() })
                        }
                    }
                }
            }
            if (hasAttendance && stackPercentage) CoursePercentage(summary, standingColor)
            if (summary.isProvisional) {
                Text(
                    stringResource(R.string.attendance_if_leave_rejected, percentOf(summary.confirmedCredited, summary.conducted)),
                    modifier = if (stackPercentage) Modifier else Modifier.align(Alignment.End).padding(end = 48.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.caution.foreground,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = if (openPendingLeave != null) {
                            Modifier.heightIn(min = 48.dp).clickable(
                                onClickLabel = pendingLeaveAction, role = Role.Button, onClick = openPendingLeave,
                            )
                        } else Modifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (standing != null) {
                            Icon(painterResource(standing.icon), contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = standingColor)
                        }
                        Text(attendanceNextStep(summary), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = standingColor)
                        if (openPendingLeave != null) {
                            Icon(painterResource(R.drawable.ic_forward), contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = standingColor)
                        }
                    }
                    if (item.course.retired) {
                        Text(stringResource(R.string.no_longer_tracked), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasDetails) {
                    ActionIcon(R.drawable.ic_chevron_down, if (expanded) "Hide details" else "Details",
                        onClick = { expanded = !expanded },
                        modifier = Modifier.rotate(if (expanded) 180f else 0f)
                            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" })
                }
            }
            if (summary.hasIncompleteData) {
                val missing = summary.missingPastPeriods
                ActionRow(R.drawable.ic_history,
                    pluralStringResource(R.plurals.course_unmarked_periods, missing.pluralQuantity(), missing),
                    onUnmarked, showChevron = true, modifier = Modifier.testTag("course_unmarked_${item.course.id}"))
            }
            if (hasDetails && expanded) {
                HorizontalDivider(Modifier.padding(end = 4.dp))
                AttendanceDetails(item, onResolveDl)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun AttendanceDetails(item: CourseStats, onResolveDl: (Boolean) -> Unit) {
    val summary = item.summary
    Column(Modifier.padding(top = 10.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (summary.conducted > 0) {
            Text(stringResource(R.string.counted_as_attended, summary.credited, summary.conducted),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DetailRow(stringResource(R.string.attended_in_person), summary.physicallyPresent.toString())
            if (summary.approvedDl > 0) DetailRow("Approved duty leave", summary.approvedDl.toString())
            if (summary.pendingDl > 0) DetailRow("Pending duty leave", summary.pendingDl.toString())
            if (summary.datedRejectedDl > 0) DetailRow("Rejected duty leave", summary.datedRejectedDl.toString())
            if (summary.isProvisional) DetailRow(stringResource(R.string.without_pending_leave),
                percentOf(summary.confirmedCredited, summary.conducted))
        }
        if (summary.hasIncompleteData) {
            DetailRow(stringResource(R.string.past_periods_not_marked), summary.missingPastPeriods.toString())
        }
        if (item.balance.pendingDl > 0) {
            DetailRow(stringResource(R.string.pending_before_tracking), item.balance.pendingDl.toString())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onResolveDl(true) }) { Text(stringResource(R.string.approve_before_tracking)) }
                TextButton(onClick = { onResolveDl(false) }) { Text(stringResource(R.string.reject_before_tracking)) }
            }
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
private fun CourseFormDialog(existing: CourseStats?, onDismiss: () -> Unit,
    onSave: (CourseInput) -> Unit) {
    var name by remember { mutableStateOf(existing?.course?.name ?: "") }
    var type by remember { mutableStateOf(existing?.course?.type?.let { runCatching { CourseType.valueOf(it) }.getOrDefault(CourseType.THEORY) } ?: CourseType.THEORY) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a course" else "Edit ${existing.course.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Course name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("course_name"))
                ChoiceRow(CourseType.entries.map { it.name to typeLabel(it.name) }, type.name,
                    onSelect = { type = CourseType.valueOf(it) })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(CourseInput(name, type, existing?.course?.colorArgb)) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("course_save")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
