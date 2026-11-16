package com.niranjan.ninety.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.CourseInput
import com.niranjan.ninety.data.CourseStats
import com.niranjan.ninety.domain.AttendanceSummary
import com.niranjan.ninety.domain.CourseType
import com.niranjan.ninety.domain.OpeningBalance
import com.niranjan.ninety.ui.designsystem.AttendanceBar
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.CourseBadge
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.percentOf
import java.time.LocalDate
import java.util.UUID

@Composable
fun CoursesScreen(semesterId: UUID, snackbar: SnackbarHostState, onEditTimetable: () -> Unit) {
    val model = ninetyViewModel<CoursesViewModel>(key = "courses-$semesterId") {
        CoursesViewModel(it.repository, semesterId)
    }
    val stats by model.stats.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<CourseDialog?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Courses", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("90% minimum · per course", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEditTimetable) { Text("Timetable") }
            IconButton(onClick = { dialog = CourseDialog.Add }, modifier = Modifier.testTag("add_course")) {
                Icon(painterResource(R.drawable.ic_add), "Add course")
            }
        }

        when (val list = stats) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.course.id }) { item ->
                    CourseCard(item, onEdit = { dialog = CourseDialog.Edit(item) },
                        onBalance = { dialog = CourseDialog.Balance(item) },
                        onResolveDl = { approve -> model.resolveOpeningDl(UUID.fromString(item.course.id), approve) },
                        onRetire = { dialog = CourseDialog.Retire(item) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    when (val d = dialog) {
        CourseDialog.Add -> CourseFormDialog(null, onDismiss = { dialog = null },
            onSave = { input -> model.addCourse(input, null); dialog = null })
        is CourseDialog.Edit -> CourseFormDialog(d.stats, onDismiss = { dialog = null },
            onSave = { input -> model.updateCourse(UUID.fromString(d.stats.course.id), input); dialog = null })
        is CourseDialog.Balance -> BalanceDialog(d.stats, onDismiss = { dialog = null },
            onSave = { model.correctBalance(it); dialog = null })
        is CourseDialog.Retire -> ConfirmDialog("Retire ${d.stats.course.code}?",
            "The course and its history are kept, but it leaves the timetable and lists.",
            "Retire", destructive = true,
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
    onResolveDl: (Boolean) -> Unit, onRetire: () -> Unit) {
    val s = item.summary
    NinetyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CourseBadge(item.course.code, typeLabel(item.course.type))
                    Text(item.course.name, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PercentageBadge(s)
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more), "Course actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit course") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Edit opening balance") }, onClick = { menu = false; onBalance() })
                        DropdownMenuItem(text = { Text("Retire course") }, onClick = { menu = false; onRetire() })
                    }
                }
            }
            AttendanceBar(s.credited, s.conducted)
            StatsLine(s)
            if (s.hasIncompleteData) {
                Text("Incomplete — ${s.missingPastPeriods} past ${if (s.missingPastPeriods == 1L) "period" else "periods"} unrecorded",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            if (item.balance.pendingDl > 0) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${item.balance.pendingDl} opening DL pending",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onResolveDl(true) }) { Text("Approve") }
                    TextButton(onClick = { onResolveDl(false) }) { Text("Reject") }
                }
            }
        }
    }
}

@Composable
private fun PercentageBadge(s: AttendanceSummary) {
    Column(horizontalAlignment = Alignment.End) {
        Text(if (s.conducted == 0L) "—" else percentOf(s.credited, s.conducted),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = when (s.meetsMinimum) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            })
        if (s.isProvisional) {
            Text("provisional", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun StatsLine(s: AttendanceSummary) {
    val text = when {
        s.conducted == 0L -> "No attendance yet"
        else -> buildString {
            append("${s.credited}/${s.conducted} credited")
            s.safeSkips?.let { if (it > 0) append(" · can miss $it") }
            s.recovery?.let { if (it > 0) append(" · attend next $it to reach 90%") }
            if (s.isProvisional) append(" · ${s.pendingDl} pending DL")
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CourseFormDialog(existing: CourseStats?, onDismiss: () -> Unit,
    onSave: (CourseInput) -> Unit) {
    var name by remember { mutableStateOf(existing?.course?.name ?: "") }
    var code by remember { mutableStateOf(existing?.course?.code ?: "") }
    var type by remember { mutableStateOf(existing?.course?.type?.let { runCatching { CourseType.valueOf(it) }.getOrDefault(CourseType.THEORY) } ?: CourseType.THEORY) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a course" else "Edit ${existing.course.code}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Course name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("course_name"))
                OutlinedTextField(value = code, onValueChange = { code = it },
                    label = { Text("Short code") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("course_code"))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CourseType.entries.forEachIndexed { index, t ->
                        SegmentedButton(selected = type == t, onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)) {
                            Text(typeLabel(t.name))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && code.isNotBlank()) onSave(CourseInput(name, code, type)) },
                enabled = name.isNotBlank() && code.isNotBlank(),
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
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Opening balance · ${item.course.code}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Counts cover attendance before the first logging date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { pickDate = true }) { Text("First logging date: $logging") }
                listOf("Conducted periods" to conducted, "Physically present" to present,
                    "Approved duty leave" to approved, "Pending duty leave" to pending
                ).forEachIndexed { index, (label, value) ->
                    OutlinedTextField(value = value, singleLine = true,
                        onValueChange = { v ->
                            when (index) {
                                0 -> conducted = v; 1 -> present = v; 2 -> approved = v; else -> pending = v
                            }
                        },
                        label = { Text(label) }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = conducted.toLongOrNull(); val p = present.toLongOrNull()
                val a = approved.toLongOrNull(); val pe = pending.toLongOrNull()
                when {
                    c == null || p == null || a == null || pe == null -> error = "Enter whole numbers."
                    else -> runCatching {
                        OpeningBalance(UUID.fromString(item.course.id), logging, c, p, a, pe)
                    }.onSuccess(onSave).onFailure { error = it.message }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (pickDate) {
        NinetyDatePicker(logging, onPicked = { logging = it; pickDate = false },
            onDismiss = { pickDate = false })
    }
}

private fun typeLabel(type: String): String = when (type) {
    "LAB" -> "Lab"; "OTHER" -> "Other"; else -> "Theory"
}
