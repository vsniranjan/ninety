package com.niranjan.ninety.ui.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.ConflictInfo
import com.niranjan.ninety.data.CourseEntity
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.GhostButton
import com.niranjan.ninety.ui.designsystem.NinetyAlertDialog
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.designsystem.WeeklySchedule
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.weekdayAndDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

internal typealias TimetableExitHandler = (() -> Unit) -> Unit

@Composable
internal fun TimetableScreen(semesterId: UUID, today: LocalDate, snackbar: SnackbarHostState, onDone: () -> Unit,
    registerExitHandler: (TimetableExitHandler?) -> Unit) {
    val model = ninetyViewModel<TimetableViewModel>(key = "timetable-$semesterId") {
        TimetableViewModel(it.repository, semesterId)
    }
    val versions by model.versions.collectAsStateWithLifecycle()
    val courses by model.courses.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val effectiveFrom by model.effectiveFrom.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    val saving by model.saving.collectAsStateWithLifecycle()
    val preview by model.preview.collectAsStateWithLifecycle()
    var conflicts by remember { mutableStateOf<List<ConflictInfo>?>(null) }
    var pickEffective by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun showSaveResult(result: List<ConflictInfo>) {
        if (result.isEmpty()) onDone() else conflicts = result
    }

    fun requestExit(leave: () -> Unit) {
        if (model.saving.value) return
        if (model.hasChanges()) {
            pendingExit = leave
        } else {
            model.cancelEditing()
            leave()
        }
    }
    DisposableEffect(model) {
        registerExitHandler(::requestExit)
        onDispose { registerExitHandler(null) }
    }
    BackHandler(enabled = draft != null) { requestExit(onDone) }

    pendingExit?.let { leave ->
        NinetyAlertDialog(onDismissRequest = { pendingExit = null },
            title = { CapsText(stringResource(R.string.discard_timetable)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExit = null
                    model.cancelEditing()
                    leave()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExit = null }) { Text(stringResource(R.string.keep_editing)) }
            })
    }

    val weekday = today.dayOfWeek.takeIf { it.value <= DayOfWeek.FRIDAY.value } ?: DayOfWeek.MONDAY

    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        val savedVersions = versions
        if (draft == null && savedVersions != null) {
            ActionRow(R.drawable.ic_edit, stringResource(R.string.edit_timetable),
                { model.startEditing(savedVersions.lastOrNull()?.template, today) },
                modifier = Modifier.testTag("timetable_edit"))
        }

        val template = draft
        if (template != null) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { requestExit {} }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("timetable_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
                GhostButton(onClick = {
                    model.save(today, ::showSaveResult)
                }, enabled = !saving && effectiveFrom >= today,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("timetable_save")) {
                    CapsText(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(8.dp))
            ActionRow(R.drawable.ic_calendar, stringResource(R.string.timetable_effective_label),
                onClick = { pickEffective = true }, enabled = !saving, showChevron = true,
                modifier = Modifier.testTag("timetable_effective_date"),
                supportingText = effectiveFrom.weekdayAndDate())
            if (effectiveFrom < today) Text(stringResource(R.string.effective_date_past),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(12.dp))
            TimetableGrid(template, courses, weekday, editable = !saving, onCopy = model::copyDay) { day, slot, course ->
                model.edit(day, slot, course)
            }
        } else if (savedVersions == null) {
            CircularProgressIndicator()
        } else if (savedVersions.isEmpty()) {
            Text(stringResource(R.string.no_timetable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp))
        } else {
            TimetableGrid(savedVersions.last().template, courses, weekday, editable = false) { _, _, _ -> }
            if (savedVersions.size > 1) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.earlier_versions))
                savedVersions.dropLast(1).asReversed().forEach { version ->
                    Text(stringResource(R.string.effective_from, version.effectiveFrom.weekdayAndDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    preview?.let { list ->
        TimetableConflictDialog(list, preview = true,
            onConfirm = { model.confirmSave(::showSaveResult) }, onDismiss = model::cancelPreview)
    }

    conflicts?.let { list ->
        val close = { conflicts = null; onDone() }
        TimetableConflictDialog(list, preview = false, onConfirm = close, onDismiss = close)
    }

    if (pickEffective) {
        NinetyDatePicker(effectiveFrom, onPicked = { model.setEffective(it); pickEffective = false },
            onDismiss = { pickEffective = false }, minDate = today)
    }
}

@Composable
private fun TimetableConflictDialog(conflicts: List<ConflictInfo>, preview: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NinetyAlertDialog(onDismissRequest = onDismiss,
        title = { CapsText(stringResource(if (preview) R.string.timetable_preview_title else R.string.timetable_saved)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(
                    if (preview) R.plurals.timetable_preview_conflicts else R.plurals.timetable_saved_conflicts,
                    conflicts.size, conflicts.size))
                conflicts.forEach { conflict ->
                    val period = conflict.slot?.let { stringResource(R.string.numbered_period, it) }
                        ?: stringResource(R.string.extra_class)
                    Text(stringResource(R.string.timetable_conflict_period,
                        conflict.date.weekdayAndDate(), period, conflict.courseName))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (preview) R.string.save_anyway else R.string.done))
            }
        },
        dismissButton = if (preview) ({
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
        }) else null)
}

@Composable
private fun TimetableGrid(template: WeekTemplate, courses: List<CourseEntity>, initialWeekday: DayOfWeek,
    editable: Boolean, onCopy: (DayOfWeek, DayOfWeek) -> Unit = { _, _ -> },
    onAssign: (DayOfWeek, Int, UUID?) -> Unit) {
    WeeklySchedule(template, courses.filter { !it.retired }.associate { UUID.fromString(it.id) to it.name },
        editable = editable, initialWeekday = initialWeekday, onCopy = onCopy, onAssign = onAssign)
}
