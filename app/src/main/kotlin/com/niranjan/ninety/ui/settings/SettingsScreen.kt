package com.niranjan.ninety.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.BackupCodec
import com.niranjan.ninety.ui.courses.AttendanceRulesSheet
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.SettingSwitch
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.ninetyViewModel
import java.time.Instant
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
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
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var pickTime by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf(false) }

    val backupSaved = stringResource(R.string.backup_saved)
    val backupWriteFailed = stringResource(R.string.backup_write_failed)
    val backupReadFailed = stringResource(R.string.backup_read_failed)
    val locale = LocalLocale.current.platformLocale
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            model.exportBackup(onWritten = { model.info.value = backupSaved }) { bytes ->
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalArgumentException(backupWriteFailed)
                stream.use { it.write(bytes) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { chosen ->
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(chosen)?.use(BackupCodec::read)
                    }
                    if (bytes == null) model.messages.value = backupReadFailed
                    else model.previewRestore(bytes)
                }.onFailure { model.messages.value = it.message ?: backupReadFailed }
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
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.semesters), Modifier.padding(top = 8.dp))
        state.semesters.forEach { semester ->
            val id = UUID.fromString(semester.entity.id)
            ActionRow(R.drawable.ic_calendar, semester.entity.name, { onOpenSemester(id) }, showChevron = true,
                supportingText = semesterSummary(semester, id == state.activeSemesterId),
                modifier = Modifier.testTag("open_semester_$id"))
        }
        ActionRow(R.drawable.ic_add, stringResource(R.string.new_semester), onNewSemester,
            modifier = Modifier.testTag("new_semester"))

        SectionLabel(stringResource(R.string.display_feedback))
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf("SYSTEM" to R.string.theme_system, "LIGHT" to R.string.theme_light,
                        "DARK" to R.string.theme_dark)
                    options.forEachIndexed { index, (key, label) ->
                        SegmentedButton(selected = state.settings.theme == key, onClick = { model.setTheme(key) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
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
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel(stringResource(R.string.reminder))
        }
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    TextButton(onClick = { pickTime = true }) {
                        Text("Reminder time: %02d:%02d".format(
                            state.settings.reminderMinute / 60, state.settings.reminderMinute % 60))
                    }

                }
            }
        }

        SectionLabel(stringResource(R.string.backup))
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportLauncher.launch("ninety-backup-${LocalDate.now()}.json") },
                        enabled = !busy, modifier = Modifier.testTag("export_backup")) {
                        Icon(painterResource(R.drawable.ic_upload), null, Modifier.size(20.dp))
                        Text(stringResource(R.string.save_backup), Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        enabled = !busy, modifier = Modifier.testTag("import_backup")) {
                        Icon(painterResource(R.drawable.ic_download), null, Modifier.size(20.dp))
                        Text(stringResource(R.string.restore_backup), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        ActionRow(R.drawable.ic_info, stringResource(R.string.about_ninety), { about = true },
            showChevron = true, modifier = Modifier.testTag("open_about"))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Offline · On your device", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }

    var showAttendanceRules by rememberSaveable { mutableStateOf(false) }
    if (showAttendanceRules) AttendanceRulesSheet { showAttendanceRules = false }

    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text(stringResource(R.string.about_ninety)) },
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
        val notice = remember(resources) { resources.openRawResource(R.raw.manrope_license).bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { license = false }, title = { Text("Manrope · SIL OFL 1.1") },
            text = { Text(notice, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { license = false }) { Text(stringResource(R.string.done)) } })
    }

    pendingRestore?.let { (_, preview) ->
        AlertDialog(onDismissRequest = model::cancelRestore,
            title = { Text(stringResource(R.string.restore_question)) },
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
            state.settings.reminderMinute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    model.setReminderTime(timeState.hour * 60 + timeState.minute); pickTime = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { androidx.compose.material3.TimeInput(timeState) })
    }
}
