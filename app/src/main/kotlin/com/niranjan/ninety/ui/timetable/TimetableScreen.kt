package com.niranjan.ninety.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.data.ConflictInfo
import com.niranjan.ninety.data.CourseEntity
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.WeeklySchedule
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.fullName
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.shortName
import java.time.LocalDate
import java.util.UUID

@Composable
fun TimetableScreen(semesterId: UUID, snackbar: SnackbarHostState, onDone: () -> Unit) {
    val model = ninetyViewModel<TimetableViewModel>(key = "timetable-$semesterId") {
        TimetableViewModel(it.repository, semesterId)
    }
    val versions by model.versions.collectAsStateWithLifecycle()
    val courses by model.courses.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val effectiveFrom by model.effectiveFrom.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    var conflicts by remember { mutableStateOf<List<ConflictInfo>?>(null) }
    var pickEffective by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly timetable", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("Monday to Friday · six periods", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (draft == null) {
                TextButton(onClick = { model.startEditing(versions.lastOrNull()?.template, today) },
                    modifier = Modifier.testTag("timetable_edit")) { Text("Edit") }
            } else {
                TextButton(onClick = { model.cancelEditing() }) { Text("Cancel") }
                Button(onClick = { model.save { conflicts = it.ifEmpty { null } ?: it; if (it.isEmpty()) onDone() } },
                    enabled = !saving, modifier = Modifier.testTag("timetable_save")) { Text("Save") }
            }
        }

        if (draft != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Effective from ${effectiveFrom.medium()}", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { pickEffective = true }) { Text("Change") }
                }
            }
            Spacer(Modifier.height(12.dp))
            TimetableGrid(draft!!, courses, editable = true) { day, slot, course ->
                model.edit(day, slot, course)
            }
        } else if (versions.isEmpty()) {
            Text("No timetable yet. Edit to place your courses on the weekly timetable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp))
        } else {
            TimetableGrid(versions.last().template, courses, editable = false) { _, _, _ -> }
            if (versions.size > 1) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Earlier versions")
                versions.dropLast(1).asReversed().forEach { version ->
                    Text("From ${version.effectiveFrom.medium()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    conflicts?.let { list ->
        AlertDialog(onDismissRequest = { conflicts = null; onDone() },
            title = { Text("Saved with ${list.size} ${if (list.size == 1) "conflict" else "conflicts"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recorded or cancelled periods kept their stored course:")
                    list.take(6).forEach {
                        Text("· ${it.date.medium()} P${it.slot ?: "?"} kept ${it.courseName}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (list.size > 6) Text("· and ${list.size - 6} more",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { conflicts = null; onDone() }) { Text("Done") } })
    }

    if (pickEffective) {
        NinetyDatePicker(effectiveFrom, onPicked = { model.setEffective(it); pickEffective = false },
            onDismiss = { pickEffective = false })
    }
}

@Composable
private fun TimetableGrid(template: WeekTemplate, courses: List<CourseEntity>,
    editable: Boolean, onAssign: (java.time.DayOfWeek, Int, UUID?) -> Unit) {
    WeeklySchedule(template, courses.filter { !it.retired }.associate { UUID.fromString(it.id) to it.name },
        editable = editable, onAssign = onAssign)
}
