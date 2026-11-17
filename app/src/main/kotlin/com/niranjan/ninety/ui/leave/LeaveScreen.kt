package com.niranjan.ninety.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.data.DlEventStatus
import com.niranjan.ninety.data.DlEventView
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.data.SessionView
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import java.util.UUID

@Composable
fun LeaveScreen(semesterId: UUID, snackbar: SnackbarHostState) {
    val model = ninetyViewModel<LeaveViewModel>(key = "leave-$semesterId") {
        LeaveViewModel(it.repository, semesterId)
    }
    val events by model.events.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val expanded by model.expandedEventId.collectAsStateWithLifecycle()
    val expandedSessions by model.eventSessions.collectAsStateWithLifecycle()
    val message by model.messages.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duty leave", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("Pending leave counts provisionally; approved leave is confirmed credit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                stats?.let { s ->
                    val periods = s.pending + s.approved + s.rejected
                    Text("${countLabel(s.distinctDates, "date")} · ${countLabel(s.events, "request")} · ${countLabel(periods, "dated period")}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dl_totals"))
                    Text("${s.pending} pending · ${s.approved} approved · ${s.rejected} rejected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.openingPending + s.openingApproved > 0) {
                        Text("Opening balances: ${s.openingPending} pending DL · ${s.openingApproved} approved DL. These aggregate periods have no individual dates or requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (events.isEmpty()) {
            item { EmptyState("No duty leave yet",
                "Apply leave from a day screen: open the day menu and choose Apply duty leave.") }
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
    NinetyCard(Modifier.fillMaxWidth().testTag("dl_event_${event.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.title ?: "Duty leave", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(event.dateLabels.joinToString(", ") { it.medium() } +
                " · ${countLabel(event.periods, "period")} · ${event.courseNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when (event.status) {
                DlEventStatus.MIXED -> "Mixed approval"
                DlEventStatus.EMPTY -> "No periods attached"
                DlEventStatus.PENDING -> "Pending approval"
                DlEventStatus.APPROVED -> "Approved"
                DlEventStatus.REJECTED -> "Rejected"
            }, style = MaterialTheme.typography.labelLarge)
            Text("${event.pending} pending · ${event.approved} approved · ${event.rejected} rejected",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onExpand, modifier = Modifier.testTag("dl_expand_${event.id}")) {
                Text(if (expanded) "Hide periods" else "View periods")
            }
            if (expanded) {
                sessions.forEach { session ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${session.courseName} · ${slotLabel(session.slot)}",
                            style = MaterialTheme.typography.bodyMedium)
                        OutcomeChip(session.outcome)
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            TextButton(onClick = { menu = true },
                                modifier = Modifier.testTag("dl_status_${session.id}")) { Text("Change approval") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DlStatus.entries.forEach { status ->
                                    DropdownMenuItem(text = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = { menu = false; onStatus(status, setOf(session.id)) })
                                }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onStatus(DlStatus.APPROVED, null) },
                        modifier = Modifier.testTag("dl_approve_all")) { Text("Approve all") }
                    OutlinedButton(onClick = { onStatus(DlStatus.REJECTED, null) },
                        modifier = Modifier.testTag("dl_reject_all")) { Text("Reject all") }
                    TextButton(onClick = { onStatus(DlStatus.PENDING, null) }) { Text("Move all to pending") }
                }
            }
        }
    }
}

private fun countLabel(count: Int, label: String) = "$count $label${if (count == 1) "" else "s"}"
