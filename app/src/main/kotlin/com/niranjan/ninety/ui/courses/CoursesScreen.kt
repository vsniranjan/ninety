package com.niranjan.ninety.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.ChoiceRow
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
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
                Text("90% minimum", style = MaterialTheme.typography.bodySmall,
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
        is CourseDialog.Retire -> ConfirmDialog("Retire ${d.stats.course.name}?",
            "Scheduling stops from tomorrow. Today's classes and all past attendance are kept.",
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
    val summary = item.summary
    val hasAttendance = summary.conducted > 0
    val hasDetails = hasAttendance || summary.hasIncompleteData || item.balance.pendingDl > 0
    val stackPercentage = LocalDensity.current.fontScale > 1.3f
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    NinetyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.course.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(typeLabel(item.course.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasAttendance && !stackPercentage) CoursePercentage(summary)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more), "Actions for ${item.course.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit course") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Edit opening balance") }, onClick = { menu = false; onBalance() })
                        if (!item.course.retired) {
                            DropdownMenuItem(text = { Text("Retire course") }, onClick = { menu = false; onRetire() })
                        }
                    }
                }
            }
            if (hasAttendance) {
                if (stackPercentage) CoursePercentage(summary)
                AttendanceBar(summary.credited, summary.conducted,
                    modifier = Modifier.padding(top = 10.dp, end = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(attendanceNextStep(summary), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (summary.isProvisional || summary.hasIncompleteData || item.course.retired) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (summary.isProvisional) Text("Provisional", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (summary.hasIncompleteData) Text("Past records missing", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error)
                            if (item.course.retired) Text("Retired", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (hasDetails) {
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
                        Text(if (expanded) "Hide details" else "Details")
                        Spacer(Modifier.width(2.dp))
                        Icon(painterResource(R.drawable.ic_forward), contentDescription = null,
                            modifier = Modifier.size(16.dp).rotate(if (expanded) -90f else 90f))
                    }
                }
            }
            if (hasDetails && expanded) {
                HorizontalDivider(Modifier.padding(end = 4.dp))
                AttendanceDetails(item, onResolveDl)
            }
        }
    }
}

@Composable
private fun CoursePercentage(summary: AttendanceSummary) {
    Text(percentOf(summary.credited, summary.conducted),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        color = if (summary.meetsMinimum == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
}

private fun attendanceNextStep(summary: AttendanceSummary): String {
    val recovery = summary.recovery ?: return "No attendance recorded"
    val skips = summary.safeSkips ?: 0L
    return when {
        recovery > 0 -> "Attend the next ${periodCount(recovery)}"
        skips > 0 -> "Can miss ${periodCount(skips)}"
        else -> "No safe skips"
    }
}

private fun periodCount(count: Long) = "$count ${if (count == 1L) "period" else "periods"}"

@Composable
private fun AttendanceDetails(item: CourseStats, onResolveDl: (Boolean) -> Unit) {
    val summary = item.summary
    Column(Modifier.padding(top = 10.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (summary.conducted > 0) {
            DetailRow("Credited", "${summary.credited} / ${summary.conducted}")
            DetailRow("Physically present", summary.physicallyPresent.toString())
            if (summary.approvedDl > 0) DetailRow("Approved leave", summary.approvedDl.toString())
            if (summary.pendingDl > 0) DetailRow("Pending leave", summary.pendingDl.toString())
            if (summary.datedRejectedDl > 0) DetailRow("Rejected leave", summary.datedRejectedDl.toString())
            if (summary.isProvisional) DetailRow("Confirmed (without pending leave)",
                percentOf(summary.confirmedCredited, summary.conducted))
        }
        if (summary.hasIncompleteData) {
            DetailRow("Past periods unrecorded", summary.missingPastPeriods.toString())
        }
        if (item.balance.pendingDl > 0) {
            DetailRow("Opening leave pending", item.balance.pendingDl.toString())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onResolveDl(true) }) { Text("Approve opening leave") }
                TextButton(onClick = { onResolveDl(false) }) { Text("Reject opening leave") }
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
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Opening balance · ${item.course.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
