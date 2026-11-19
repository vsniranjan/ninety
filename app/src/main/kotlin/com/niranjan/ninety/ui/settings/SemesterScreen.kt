package com.niranjan.ninety.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
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
    val displayedMessage = message?.let { displayMessage(it) }
    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessages() } }
    val semester = state.semesters.firstOrNull { it.entity.id == id.toString() }
    if (semester == null) {
        if (state.loaded) EmptyState(stringResource(R.string.semester_unavailable), "")
        return
    }
    val archived = semester.entity.archived
    val current = state.activeSemesterId == id
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(semester.entity.name, style = MaterialTheme.typography.headlineSmall)
        val started = stringResource(R.string.semester_started, semester.semester.startDate.medium())
        Text(when {
            archived -> stringResource(R.string.semester_started_finished, started)
            current -> stringResource(R.string.semester_current_started, started)
            else -> started
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp))
        ActionRow(R.drawable.ic_calendar, stringResource(R.string.view), onViewSemester,
            modifier = Modifier.testTag("browse_$id"))
        ActionRow(R.drawable.ic_edit, stringResource(R.string.edit_semester), { editing = true })
        if (!current && !archived) {
            ActionRow(R.drawable.ic_check, stringResource(R.string.make_current), {
                model.activateSemester(semester)
                onActiveChanged()
            })
        }
        if (current || archived) {
            ActionRow(if (archived) R.drawable.ic_today else R.drawable.ic_check,
                stringResource(if (archived) R.string.reopen else R.string.finish_semester),
                onClick = {
                    if (archived) {
                        model.completeSemester(semester, false)
                        onActiveChanged()
                    } else confirmFinish = true
                },
                modifier = Modifier.testTag("semester_lifecycle_$id"))
        }
    }
    if (confirmFinish) {
        ConfirmDialog(stringResource(R.string.finish_semester_question, semester.entity.name),
            stringResource(R.string.finish_semester_body), stringResource(R.string.finish_semester),
            onConfirm = { model.completeSemester(semester, true); confirmFinish = false },
            onDismiss = { confirmFinish = false })
    }
    if (editing) {
        EditSemesterDialog(semester.entity.name, semester.semester.startDate,
            onSave = { name, date -> model.updateSemester(semester, name, date) { editing = false } },
            onDismiss = { editing = false })
    }
}

@Composable
private fun EditSemesterDialog(originalName: String, originalDate: LocalDate,
    onSave: (String, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(originalName) }
    var startDay by rememberSaveable { mutableLongStateOf(originalDate.toEpochDay()) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(startDay)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_semester)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.semester_name)) }, singleLine = true)
                ActionRow(R.drawable.ic_calendar, stringResource(R.string.semester_started, date.medium()),
                    { pickStart = true }, showChevron = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, date) }, enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (pickStart) NinetyDatePicker(date, onPicked = { startDay = it.toEpochDay(); pickStart = false },
        onDismiss = { pickStart = false })
}
