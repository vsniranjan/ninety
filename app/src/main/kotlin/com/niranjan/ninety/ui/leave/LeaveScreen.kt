package com.niranjan.ninety.ui.leave

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DlEventStatus
import com.niranjan.ninety.data.DlEventView
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.data.SessionView
import com.niranjan.ninety.ui.designsystem.ActionRow
import com.niranjan.ninety.ui.designsystem.ConfirmDialog
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.designsystem.caution
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import java.time.LocalDate
import java.util.UUID

@Composable
fun LeaveScreen(semesterId: UUID, semesterStart: LocalDate, snackbar: SnackbarHostState, today: LocalDate,
    courseId: UUID? = null, onOpenDay: (LocalDate) -> Unit, onAddDutyLeave: (LocalDate) -> Unit) {
    val model = ninetyViewModel<LeaveViewModel>(key = "leave-$semesterId-$courseId-$today") {
        LeaveViewModel(it.repository, semesterId, courseId, today)
    }
    val events by model.events.collectAsStateWithLifecycle()
    val opening by model.opening.collectAsStateWithLifecycle()
    val expanded by model.expandedEvent.collectAsStateWithLifecycle()
    val expandedPeriods by model.eventPeriods.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    var pickDay by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    if (pickDay) NinetyDatePicker(today, onPicked = { pickDay = false; onAddDutyLeave(it) },
        onDismiss = { pickDay = false }, minDate = semesterStart, maxDate = today)

    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    val requests = events
    val openingPending = opening.filter { it.balance.pendingDl > 0 }
    val openingApproved = opening.sumOf { it.balance.approvedDl }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("duty_leave_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (courseId != null) opening.firstOrNull()?.let {
                    Text(it.course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (today >= semesterStart) {
                    ActionRow(R.drawable.ic_add, stringResource(R.string.apply_duty_leave), { pickDay = true },
                        modifier = Modifier.testTag("add_duty_leave"))
                }
                if (courseId == null && requests != null) {
                    val periods = requests.sumOf { it.periods }
                    val dates = requests.flatMap { it.dateLabels }.distinct().size
                    Text(stringResource(R.string.leave_summary,
                        pluralStringResource(R.plurals.period_count, periods, periods),
                        pluralStringResource(R.plurals.day_count, dates, dates),
                        pluralStringResource(R.plurals.request_count, requests.size, requests.size)),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dl_totals"))
                    Text(stringResource(R.string.leave_breakdown, requests.sumOf { it.pending },
                        requests.sumOf { it.approved }, requests.sumOf { it.rejected }),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (requests == null) {
            item { CircularProgressIndicator(Modifier.padding(16.dp)) }
        } else {
            if (requests.isEmpty() && openingPending.isEmpty() && openingApproved == 0L) {
                item { EmptyState(stringResource(R.string.no_duty_leave), "") }
            }
            items(requests, key = { it.id.toString() }) { event ->
                EventCard(event, expanded == event.id,
                    periods = if (expanded == event.id) expandedPeriods.filter { it.dlEventId == event.id }
                        else emptyList(),
                    onExpand = { model.expand(if (expanded == event.id) null else event.id) },
                    onStatus = { status, ids -> model.setStatus(event.id, status, ids) },
                    onOpenDay = onOpenDay, onRemove = { confirmRemove = event.id.toString() })
            }
        }
        if (openingPending.isNotEmpty() || openingApproved > 0) {
            item {
                SectionLabel(stringResource(R.string.duty_leave_before_tracking), Modifier.padding(top = 8.dp))
                if (openingApproved > 0) {
                    Text(pluralStringResource(R.plurals.duty_leave_periods_approved,
                        openingApproved.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), openingApproved),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(openingPending, key = { it.course.id }) { item ->
                val pending = item.balance.pendingDl
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(item.course.name, style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.duty_leave_periods_pending,
                        pending.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), pending),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.caution.foreground)
                    ApprovalActions(
                        onApprove = { model.resolveOpening(item.balance.courseId, true) },
                        onReject = { model.resolveOpening(item.balance.courseId, false) },
                        approveTag = "dl_opening_approve_${item.course.id}",
                        rejectTag = "dl_opening_reject_${item.course.id}",
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    confirmRemove?.let { id ->
        ConfirmDialog(stringResource(R.string.remove_duty_leave_question), stringResource(R.string.remove_duty_leave_body),
            stringResource(R.string.remove_request), destructive = true,
            onConfirm = { confirmRemove = null; model.removeRequest(UUID.fromString(id)) },
            onDismiss = { confirmRemove = null })
    }
}

@Composable
private fun EventCard(event: DlEventView, expanded: Boolean, periods: List<SessionView>,
    onExpand: () -> Unit, onStatus: (DlStatus, Set<UUID>?) -> Unit, onOpenDay: (LocalDate) -> Unit,
    onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize().testTag("dl_event_${event.id}")) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.title ?: stringResource(R.string.duty_leave), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.leave_event_summary,
                event.dateLabels.joinToString(", ") { it.medium() },
                pluralStringResource(R.plurals.period_count, event.periods, event.periods),
                event.courseNames.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when (event.status) {
                DlEventStatus.MIXED -> stringResource(R.string.mixed_approval)
                DlEventStatus.PENDING -> stringResource(R.string.pending_approval)
                DlEventStatus.APPROVED -> stringResource(R.string.approved)
                DlEventStatus.REJECTED -> stringResource(R.string.rejected)
                DlEventStatus.EMPTY -> ""
            }, style = MaterialTheme.typography.labelLarge,
                color = if (event.pending > 0) MaterialTheme.caution.foreground else MaterialTheme.colorScheme.onSurface)
            ActionRow(if (expanded) R.drawable.ic_minus else R.drawable.ic_chevron_down,
                stringResource(if (expanded) R.string.hide_periods else R.string.view_periods), onExpand,
                modifier = Modifier.testTag("dl_expand_${event.id}"))
            if (expanded) {
                Text(stringResource(R.string.leave_breakdown, event.pending, event.approved, event.rejected),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (session in periods) key(session.id) {
                    val period = "${session.courseName} · ${slotLabel(session.slot)}"
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionRow(R.drawable.ic_calendar, period, { onOpenDay(session.date) }, showChevron = true,
                            supportingText = session.date.medium(), modifier = Modifier.testTag("dl_open_day_${session.id}"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutcomeChip(session.outcome)
                            Box {
                                var menu by remember { mutableStateOf(false) }
                                TextButton(onClick = { menu = true },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("dl_status_${session.id}")) {
                                    Text(stringResource(R.string.change_approval))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DlStatus.entries.forEach { status ->
                                        DropdownMenuItem(text = { Text(stringResource(status.label())) },
                                            onClick = { menu = false; onStatus(status, setOf(session.id)) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (event.status == DlEventStatus.PENDING) {
                ApprovalActions({ onStatus(DlStatus.APPROVED, null) }, { onStatus(DlStatus.REJECTED, null) },
                    "dl_approve_all", "dl_reject_all")
            }
            if (event.periods > 0 && (event.status != DlEventStatus.PENDING || expanded)) {
                var change by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { change = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.change_all_approvals))
                    }
                    DropdownMenu(expanded = change, onDismissRequest = { change = false }) {
                        DlStatus.entries.forEach { status ->
                            DropdownMenuItem(text = { Text(stringResource(status.label())) },
                                onClick = { change = false; onStatus(status, null) })
                        }
                    }
                }
            }
            if (expanded) {
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("dl_remove_${event.id}")) {
                    Text(stringResource(R.string.remove_request), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ApprovalActions(onApprove: () -> Unit, onReject: () -> Unit, approveTag: String, rejectTag: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onApprove, modifier = Modifier.heightIn(min = 48.dp).testTag(approveTag)) {
            Text(stringResource(R.string.approve))
        }
        TextButton(onClick = onReject, modifier = Modifier.heightIn(min = 48.dp).testTag(rejectTag)) {
            Text(stringResource(R.string.reject))
        }
    }
}

private fun DlStatus.label(): Int = when (this) {
    DlStatus.PENDING -> R.string.pending
    DlStatus.APPROVED -> R.string.approved
    DlStatus.REJECTED -> R.string.rejected
}
