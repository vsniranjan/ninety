package com.niranjan.ninety.ui.leave

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.niranjan.ninety.ui.designsystem.ActionIcon
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyDatePicker
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.designsystem.ScreenHeading
import com.niranjan.ninety.ui.displayMessage
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import java.time.LocalDate
import java.util.UUID

@Composable
fun LeaveScreen(semesterId: UUID, snackbar: SnackbarHostState, today: LocalDate,
    pendingCourseId: UUID? = null, onAddDutyLeave: (LocalDate) -> Unit) {
    val model = ninetyViewModel<LeaveViewModel>(key = "leave-$semesterId-$pendingCourseId") {
        LeaveViewModel(it.repository, semesterId, pendingCourseId)
    }
    val events by model.events.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val pendingCourseName by model.pendingCourseName.collectAsStateWithLifecycle(initialValue = null)
    val expanded by model.expandedEventId.collectAsStateWithLifecycle()
    val expandedSessions by model.eventSessions.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    val displayedMessage = message?.let { displayMessage(it) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var pickDay by rememberSaveable { mutableStateOf(false) }
    if (pickDay) NinetyDatePicker(today, onPicked = { pickDay = false; onAddDutyLeave(it) },
        onDismiss = { pickDay = false }, maxDate = today)

    LaunchedEffect(message) { displayedMessage?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pendingCourseId != null) {
                    ScreenHeading(stringResource(R.string.pending_duty_leave_heading), pendingCourseName)
                } else ScreenHeading(stringResource(R.string.duty_leave)) {
                    ActionIcon(R.drawable.ic_info, stringResource(R.string.duty_leave_totals), { showStats = !showStats })
                }
                if (pendingCourseId == null) stats?.let { s ->
                    val periods = s.pending + s.approved + s.rejected
                    Text(stringResource(R.string.leave_summary, pluralStringResource(R.plurals.period_count, periods, periods),
                        pluralStringResource(R.plurals.day_count, s.distinctDates, s.distinctDates),
                        pluralStringResource(R.plurals.request_count, s.events, s.events)),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dl_totals"))
                    if (showStats) {
                    Text(stringResource(R.string.leave_breakdown, s.pending, s.approved, s.rejected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.openingPending + s.openingApproved > 0) {
                        Text(stringResource(R.string.opening_leave_breakdown, stringResource(R.string.before_tracking),
                            s.openingPending, s.openingApproved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                }
            }
        }
        if (events.isEmpty()) {
            if (pendingCourseId != null) {
                item { EmptyState(stringResource(R.string.no_pending_leave_requests), "") }
            } else item { EmptyState(stringResource(R.string.no_duty_leave), "",
                actionLabel = stringResource(R.string.apply_duty_leave), onAction = { pickDay = true }) }
        } else {
            items(events, key = { it.id }) { event ->
                EventCard(event, expanded == event.id,
                    sessions = if (expanded == event.id) expandedSessions else emptyList(),
                    onExpand = { model.expand(if (expanded == event.id) null else event.id) },
                    onStatus = { status, ids -> model.setStatus(event.id, status, ids) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EventCard(event: DlEventView, expanded: Boolean, sessions: List<SessionView>,
    onExpand: () -> Unit, onStatus: (DlStatus, Set<UUID>?) -> Unit) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(when (event.status) {
                DlEventStatus.MIXED -> stringResource(R.string.mixed_approval)
                DlEventStatus.EMPTY -> stringResource(R.string.no_periods_attached)
                DlEventStatus.PENDING -> stringResource(R.string.pending_approval)
                DlEventStatus.APPROVED -> stringResource(R.string.approved)
                DlEventStatus.REJECTED -> stringResource(R.string.rejected)
            }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                ActionIcon(R.drawable.ic_chevron_down, if (expanded) stringResource(R.string.hide_periods) else stringResource(R.string.view_periods), onExpand,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f).testTag("dl_expand_${event.id}"))
            }
            if (expanded) {
                Text(stringResource(R.string.leave_breakdown, event.pending, event.approved, event.rejected),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sessions.forEach { session ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${session.courseName} · ${slotLabel(session.slot)}",
                                style = MaterialTheme.typography.bodyMedium)
                            OutcomeChip(session.outcome)
                        }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            ActionIcon(R.drawable.ic_more, stringResource(R.string.change_approval), { menu = true },
                                modifier = Modifier.testTag("dl_status_${session.id}"))
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
            if (event.status == DlEventStatus.PENDING) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onStatus(DlStatus.APPROVED, null) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("dl_approve_all")) {
                        Text(stringResource(R.string.approve))
                    }
                    TextButton(onClick = { onStatus(DlStatus.REJECTED, null) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("dl_reject_all")) {
                        Text(stringResource(R.string.reject))
                    }
                }
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
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun DlStatus.label(): Int = when (this) {
    DlStatus.PENDING -> R.string.pending
    DlStatus.APPROVED -> R.string.approved
    DlStatus.REJECTED -> R.string.rejected
}
