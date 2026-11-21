package com.niranjan.ninety.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.SemesterInfo
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.CollegeZoneAction
import com.niranjan.ninety.ui.designsystem.CollegeZonePicker
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.GhostButton
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.NinetyTextField
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import java.time.LocalDate
import java.util.UUID

@Composable
fun SemesterScreen(id: UUID, snackbar: SnackbarHostState, onViewSemester: () -> Unit, onActiveChanged: () -> Unit) {
    val model = ninetyViewModel<SettingsViewModel> { SettingsViewModel(it) }
    val state by model.appState.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val editError by model.semesterEditError.collectAsStateWithLifecycle()
    val saving by model.savingSemester.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessages() } }
    val semester = state.semesters.firstOrNull { it.entity.id == id.toString() }
    if (semester == null) {
        if (state.loaded) EmptyState(stringResource(R.string.semester_unavailable))
        return
    }
    val archived = semester.entity.archived
    val current = state.activeSemesterId == id
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) {
        EditSemester(semester.entity.name, semester.semester.startDate, semester.entity.collegeZone,
            saving, editError?.let { displayMessage(it) }, onEdit = model::clearSemesterEditError,
            onSave = { name, date, zone -> model.updateSemester(semester, name, date, zone) { editing = false } },
            onDismiss = { if (!saving) editing = false })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(semester.entity.name, style = MaterialTheme.typography.headlineSmall)
        Text(semesterSummary(semester, current), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        ActionRow(R.drawable.ic_calendar, stringResource(R.string.view_records), onViewSemester,
            modifier = Modifier.testTag("browse_$id"), showChevron = true)
        ActionRow(R.drawable.ic_edit, stringResource(R.string.edit_semester), {
            model.clearSemesterEditError()
            editing = true
        })
        if (!current && !archived) {
            ActionRow(R.drawable.ic_check, stringResource(R.string.make_current), {
                model.activateSemester(semester, onActiveChanged)
            })
        }
        if (current || archived) {
            ActionRow(if (archived) R.drawable.ic_today else R.drawable.ic_check,
                stringResource(if (archived) R.string.reopen else R.string.finish_semester),
                onClick = {
                    if (archived) {
                        model.completeSemester(semester, false, onActiveChanged)
                    } else confirmFinish = true
                },
                modifier = Modifier.testTag("semester_lifecycle_$id"),
                supportingText = if (archived && state.activeSemesterId != null)
                    stringResource(R.string.reopen_requires_no_current) else null)
        }
    }
    if (confirmFinish) {
        ConfirmDialog(stringResource(R.string.finish_semester_question, semester.entity.name),
            stringResource(R.string.finish_semester_body), stringResource(R.string.finish_semester),
            onConfirm = { model.completeSemester(semester, true); confirmFinish = false },
            onDismiss = { confirmFinish = false })
    }
}

@Composable
internal fun semesterSummary(semester: SemesterInfo, current: Boolean): String {
    val started = stringResource(R.string.semester_started, semester.semester.startDate.medium())
    val finished = semester.entity.completedDay?.let(LocalDate::ofEpochDay)
    return when {
        semester.entity.archived && finished != null ->
            stringResource(R.string.semester_started_finished_on, started, finished.medium())
        semester.entity.archived -> stringResource(R.string.semester_name_finished, started)
        current -> stringResource(R.string.semester_current_started, started)
        else -> stringResource(R.string.semester_paused_summary, started)
    }
}

@Composable
private fun EditSemester(originalName: String, originalDate: LocalDate, originalZone: String,
    saving: Boolean, error: String?, onEdit: () -> Unit, onSave: (String, LocalDate, String) -> Unit,
    onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(originalName) }
    var zone by rememberSaveable { mutableStateOf(originalZone) }
    var startDay by rememberSaveable { mutableLongStateOf(originalDate.toEpochDay()) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickZone by rememberSaveable { mutableStateOf(false) }
    if (pickZone) {
        CollegeZonePicker(zone, onSelect = {
            zone = it
            onEdit()
            pickZone = false
        }, onDismiss = { pickZone = false })
        return
    }

    val date = LocalDate.ofEpochDay(startDay)
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.edit_semester_heading), style = MaterialTheme.typography.headlineSmall)
        NinetyTextField(name, { name = it; onEdit() }, modifier = Modifier.fillMaxWidth(), enabled = !saving,
            label = { Text(stringResource(R.string.semester_name)) }, singleLine = true)
        ActionRow(R.drawable.ic_calendar, stringResource(R.string.semester_start_label),
            { pickStart = true }, showChevron = true, enabled = !saving,
            supportingText = date.medium())
        CollegeZoneAction(zone, enabled = !saving, onClick = { pickZone = true })
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
            GhostButton(onClick = { onSave(name, date, zone) }, enabled = !saving && name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)) { CapsText(stringResource(R.string.save)) }
        }
    }
    if (pickStart) NinetyDatePicker(date, onPicked = { startDay = it.toEpochDay(); pickStart = false; onEdit() },
        onDismiss = { pickStart = false })
}
