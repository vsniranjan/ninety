package com.niranjan.ninety.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.BackupCodec
import com.niranjan.ninety.data.BackupException
import com.niranjan.ninety.ui.courses.AttendanceRulesSheet
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.NinetyAlertDialog
import com.niranjan.ninety.ui.designsystem.NinetyDimensions
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.designsystem.SettingSwitch
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.guard
import com.niranjan.ninety.ui.ninetyViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(snackbar: SnackbarHostState, onNewSemester: () -> Unit = {},
    onOpenSemester: (UUID) -> Unit) {
    val model = ninetyViewModel<SettingsViewModel>(key = "settings") { SettingsViewModel(it) }
    val state by model.appState.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    val info by model.info.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val lastBackup by model.lastBackup.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var pickTime by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf(false) }
    var showRecentChanges by rememberSaveable { mutableStateOf(false) }

    val backupSaved = stringResource(R.string.backup_saved)
    val backupWriteFailed = stringResource(R.string.backup_write_failed)
    val backupReadFailed = stringResource(R.string.backup_read_failed)
    val locale = LocalLocale.current.platformLocale
    val timestampFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(ZoneId.systemDefault())
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            model.exportBackup(onWritten = { model.info.value = backupSaved }) { bytes ->
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw BackupException(backupWriteFailed)
                stream.use { it.write(bytes) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { chosen ->
            scope.launch {
                model.messages.guard {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(chosen)?.use(BackupCodec::read)
                            ?: throw BackupException(backupReadFailed)
                    }
                    model.previewRestore(bytes)
                }
            }
        }
    }
    val permissionMessage = stringResource(R.string.reminder_permission_denied)
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.setReminder(true)
        else model.messages.value = permissionMessage
    }

    LaunchedEffect(message, info) {
        displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessages() }
        info?.let { snackbar.showSnackbar(it); model.dismissMessages() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(R.string.semesters), Modifier.padding(top = 8.dp))
        state.semesters.forEach { semester ->
            val id = UUID.fromString(semester.entity.id)
            ActionRow(R.drawable.ic_calendar, semester.entity.name, { onOpenSemester(id) }, showChevron = true,
                supportingText = semesterSummary(semester, id == state.activeSemesterId),
                modifier = Modifier.testTag("open_semester_$id"))
        }
        ActionRow(R.drawable.ic_add, stringResource(R.string.new_semester), onNewSemester,
            modifier = Modifier.testTag("new_semester"))

        SectionLabel(stringResource(R.string.theme), Modifier.padding(top = NinetyDimensions.content))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth(), space = -NinetyDimensions.tight) {
                val options = listOf("SYSTEM" to R.string.theme_system, "LIGHT" to R.string.theme_light,
                    "DARK" to R.string.theme_dark)
                options.forEach { (key, label) ->
                    SegmentedButton(selected = state.settings.theme == key, onClick = { model.setTheme(key) },
                        shape = NinetyDimensions.pill,
                        icon = {}, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        val labelStyle = MaterialTheme.typography.labelMedium
                        Text(stringResource(label), style = labelStyle, textAlign = TextAlign.Center, maxLines = 1,
                            autoSize = TextAutoSize.StepBased(9.sp, labelStyle.fontSize))
                    }
                }
            }
            SettingSwitch(stringResource(R.string.haptic_feedback), state.settings.haptics, onChange = model::setHaptics)
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Column {
            SettingSwitch(stringResource(R.string.daily_reminder), state.settings.reminderEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    model.setReminder(on)
                }
            }
            if (state.settings.reminderEnabled) {
                val reminderTime = java.time.LocalTime.of(state.settings.reminderMinute / 60,
                    state.settings.reminderMinute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                ActionRow(R.drawable.ic_clock, stringResource(R.string.reminder_time), { pickTime = true },
                    supportingText = reminderTime, showChevron = true)
            }
        }

        SectionLabel(stringResource(R.string.backup), Modifier.padding(top = NinetyDimensions.content))
        Column {
            ActionRow(R.drawable.ic_upload, stringResource(R.string.save_backup),
                { exportLauncher.launch("ninety-backup-${LocalDate.now()}.json") },
                enabled = !busy, modifier = Modifier.testTag("export_backup"))
            ActionRow(R.drawable.ic_download, stringResource(R.string.restore_backup),
                { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                enabled = !busy, modifier = Modifier.testTag("import_backup"))
            val backupTime = lastBackup?.let(timestampFormat::format)
                ?: stringResource(R.string.backup_never_on_device)
            Text(stringResource(R.string.last_backup, backupTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = NinetyDimensions.small).testTag("last_backup"))
            if (model.isBackupOverdue(lastBackup)) {
                Text(stringResource(R.string.backup_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = NinetyDimensions.small, vertical = 4.dp))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        val recentState = stringResource(if (showRecentChanges) R.string.section_expanded else R.string.section_collapsed)
        ActionRow(if (showRecentChanges) R.drawable.ic_minus else R.drawable.ic_chevron_down,
            stringResource(R.string.recent_changes), { showRecentChanges = !showRecentChanges },
            modifier = Modifier.testTag("recent_changes").semantics { stateDescription = recentState })
        if (showRecentChanges) {
            val changes by model.recentChanges.collectAsStateWithLifecycle()
            if (changes?.isEmpty() == true) {
                Text(stringResource(R.string.no_recent_changes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            changes.orEmpty().forEach { change ->
                val changeLabel = displayMessage(change.label)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(changeLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(timestampFormat.format(Instant.ofEpochMilli(change.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val undoDescription = stringResource(R.string.undo_change_description, changeLabel)
                    TextButton(onClick = { model.undoChange(change.batchId) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("undo_change_${change.batchId}")
                            .semantics { contentDescription = undoDescription }) {
                        Text(stringResource(R.string.undo))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        ActionRow(R.drawable.ic_info, stringResource(R.string.about_ninety), { about = true },
            showChevron = true, modifier = Modifier.testTag("open_about"))
        Spacer(Modifier.height(24.dp))
    }

    var showAttendanceRules by rememberSaveable { mutableStateOf(false) }
    if (showAttendanceRules) AttendanceRulesSheet { showAttendanceRules = false }

    if (about) NinetyAlertDialog(onDismissRequest = { about = false }, title = { CapsText(stringResource(R.string.about_ninety)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.about_privacy))
                TextButton(onClick = { about = false; showAttendanceRules = true },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.attendance_rules_title))
                }
            }
        },
        confirmButton = { TextButton(onClick = { about = false }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = { about = false; license = true }) { Text("Font license") } })
    if (license) {
        val notice = remember(resources) { resources.openRawResource(R.raw.d_din_license).bufferedReader().use { it.readText() } }
        NinetyAlertDialog(onDismissRequest = { license = false }, title = { CapsText("D-DIN · SIL OFL 1.1") },
            text = { Text(notice, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { license = false }) { Text(stringResource(R.string.done)) } })
    }

    pendingRestore?.let { (_, preview) ->
        NinetyAlertDialog(onDismissRequest = model::cancelRestore,
            title = { CapsText(stringResource(R.string.restore_question)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val timestamp = preview.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    val date = if (timestamp == null) stringResource(R.string.backup_date_unknown) else {
                        val localDate = timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
                        val dateText = localDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
                        val timeText = DateFormat.getTimeFormat(context).format(Date.from(timestamp))
                        stringResource(R.string.backup_from, stringResource(R.string.backup_date_time, dateText, timeText))
                    }
                    Text(date)
                    Text(stringResource(R.string.backup_counts,
                        pluralStringResource(R.plurals.semester_count, preview.semesters, preview.semesters),
                        pluralStringResource(R.plurals.course_count, preview.courses, preview.courses),
                        pluralStringResource(R.plurals.period_count, preview.sessions, preview.sessions),
                        pluralStringResource(R.plurals.marked_period_count, preview.outcomes, preview.outcomes)))
                    Text(stringResource(R.string.replace_backup_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = model::confirmRestore,
                    modifier = Modifier.testTag("restore_confirm")) {
                    Text(stringResource(R.string.replace_my_data), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = model::cancelRestore) { Text(stringResource(R.string.cancel)) } })
    }

    if (pickTime) {
        val timeState = rememberTimePickerState(state.settings.reminderMinute / 60,
            state.settings.reminderMinute % 60, is24Hour = DateFormat.is24HourFormat(context))
        NinetyAlertDialog(onDismissRequest = { pickTime = false },
            title = { CapsText(stringResource(R.string.reminder_time)) },
            confirmButton = {
                TextButton(onClick = {
                    model.setReminderTime(timeState.hour * 60 + timeState.minute); pickTime = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { androidx.compose.material3.TimeInput(timeState) })
    }
}
