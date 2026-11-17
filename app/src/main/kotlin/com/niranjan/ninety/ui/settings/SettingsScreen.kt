package com.niranjan.ninety.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.niranjan.ninety.data.BackupCodec
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.data.SemesterInfo
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.short
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(snackbar: SnackbarHostState, onNewSemester: () -> Unit = {},
    onViewSemester: (java.util.UUID) -> Unit = {}, onActiveChanged: () -> Unit = {}) {
    val model = ninetyViewModel<SettingsViewModel>(key = "settings") { SettingsViewModel(it) }
    val state by model.appState.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val info by model.info.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmComplete by remember { mutableStateOf<SemesterInfo?>(null) }
    var pickTime by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            model.exportBackup({ file -> model.info.value = "Backup saved to $file" }) { bytes ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                uri.lastPathSegment ?: "backup file"
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
                    if (bytes == null) model.messages.value = "Could not read that file."
                    else model.previewRestore(bytes)
                }.onFailure { model.messages.value = it.message ?: "Could not read that file." }
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.setReminder(true)
        else model.messages.value = "Notifications are off, so reminders can't be delivered."
    }

    LaunchedEffect(message, info) {
        message?.let { snackbar.showSnackbar(it); model.dismissMessages() }
        info?.let { snackbar.showSnackbar(it); model.dismissMessages() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))

        SectionLabel("Appearance")
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceRow(listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark"),
                    selected = state.settings.theme, onSelect = model::setTheme)
                SettingSwitch("Haptic feedback", state.settings.haptics, model::setHaptics)
            }
        }

        SectionLabel("Daily reminder")
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingSwitch("Remind me to record attendance", state.settings.reminderEnabled) { on ->
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
                    Text("Approximate time. Runs fully offline and never changes your records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Semesters", Modifier.weight(1f))
            TextButton(onClick = onNewSemester, modifier = Modifier.testTag("new_semester")) {
                Text("New semester")
            }
        }
        state.semesters.forEach { semester ->
            NinetyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(semester.entity.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Started ${LocalDate.ofEpochDay(semester.entity.startDay).short()}" +
                        if (semester.entity.archived) " · done" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onViewSemester(java.util.UUID.fromString(semester.entity.id)) },
                            modifier = Modifier.testTag("browse_${semester.entity.id}")) { Text("Browse") }
                        if (state.activeSemesterId?.toString() == semester.entity.id) {
                            Text("Active", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 16.dp))
                        } else if (!semester.entity.archived) {
                            TextButton(onClick = { model.activateSemester(semester); onActiveChanged() }) {
                                Text("Activate")
                            }
                        }
                        if (state.activeSemesterId?.toString() == semester.entity.id || semester.entity.archived) {
                            TextButton(onClick = {
                                if (!semester.entity.archived) confirmComplete = semester
                                else { model.completeSemester(semester, false); onActiveChanged() }
                            }, modifier = Modifier.testTag("semester_lifecycle_${semester.entity.id}")) {
                                Text(if (semester.entity.archived) "Reopen" else "Done")
                            }
                        }
                    }
                }
            }
        }

        SectionLabel("Backup")
        NinetyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Everything stays on this device. Export a file you can restore on this or another phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("ninety-backup.json") },
                        enabled = !busy, modifier = Modifier.testTag("export_backup")) { Text("Export") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        enabled = !busy, modifier = Modifier.testTag("import_backup")) { Text("Restore") }
                }
            }
        }

        SectionLabel("Privacy")
        Text("On your device. No account. No cloud backup. No internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
    }

    confirmComplete?.let { semester ->
        ConfirmDialog("Mark ${semester.entity.name} done?",
            "The semester is preserved with all records. You can reopen it anytime.",
            "Mark done", onConfirm = { model.completeSemester(semester, true); confirmComplete = null },
            onDismiss = { confirmComplete = null })
    }

    pendingRestore?.let { (_, preview) ->
        AlertDialog(onDismissRequest = model::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text("Created ${preview.createdAt ?: "unknown"}\n" +
                    "${preview.semesters} semesters · ${preview.courses} courses · " +
                    "${preview.sessions} classes · ${preview.outcomes} records\n\n" +
                    "This replaces everything currently in the app.")
            },
            confirmButton = {
                TextButton(onClick = model::confirmRestore,
                    modifier = Modifier.testTag("restore_confirm")) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = model::cancelRestore) { Text("Cancel") } })
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
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { androidx.compose.material3.TimeInput(timeState) })
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
