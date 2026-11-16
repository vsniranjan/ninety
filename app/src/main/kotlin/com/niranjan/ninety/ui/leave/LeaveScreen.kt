package com.niranjan.ninety.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niranjan.ninety.data.DlEventStatus
import com.niranjan.ninety.data.DlEventView
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.domain.AttendanceOutcome
import com.niranjan.ninety.ui.designsystem.EmptyState
import com.niranjan.ninety.ui.designsystem.NinetyCard
import com.niranjan.ninety.ui.designsystem.OutcomeChip
import com.niranjan.ninety.ui.designsystem.SectionLabel
import com.niranjan.ninety.ui.medium
import com.niranjan.ninety.ui.ninetyViewModel
import com.niranjan.ninety.ui.slotLabel
import java.time.Instant
import java.time.ZoneId
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
            Column(Modifier.padding(vertical = 12.dp)) {
                Text("Duty leave", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                stats?.let { s ->
                    val parts = buildList {
                        if (s.pending > 0) add("${s.pending} pending")
                        if (s.approved > 0) add("${s.approved} approved")
                        if (s.rejected > 0) add("${s.rejected} rejected")
                        if (s.openingPending > 0) add("${s.openingPending} opening pending")
                    }
                    Text(if (parts.isEmpty()) "Approved leave credits like presence; pending counts provisionally."
                        else parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (events.isEmpty()) {
            item {
                EmptyState("No duty leave yet",
                    "Apply leave from a day screen: open the day menu and choose Apply duty leave.")
            }
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
private fun EventCard(event: DlEventView, expanded: Boolean,
    sessions: List<com.niranjan.ninety.data.SessionView>,
    onExpand: () -> Unit, onStatus: (DlStatus, Set<UUID>?) -> Unit) {
    NinetyCard(Modifier.fillMaxWidth(), onClick = onExpand) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.title ?: "Duty leave", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    val detail = buildList {
                        if (event.dateLabels.isNotEmpty()) {
                            add(event.dateLabels.joinToString(", ") { it.medium() })
                        }
                        add("${event.periods} ${if (event.periods == 1) "period" else "periods"}")
                        if (event.courseCodes.isNotEmpty()) add(event.courseCodes.joinToString(", "))
                    }
                    Text(detail.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(event.status, event.pending, event.approved, event.rejected)
            }
            if (expanded) {
                sessions.forEach { session ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${session.courseCode} · ${slotLabel(session.slot)}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutcomeChip(session.outcome)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (event.status == DlEventStatus.PENDING || event.status == DlEventStatus.MIXED) {
                        OutlinedButton(onClick = { onStatus(DlStatus.APPROVED, null) },
                            modifier = Modifier.weight(1f)) { Text("Approve all") }
                        OutlinedButton(onClick = { onStatus(DlStatus.REJECTED, null) },
                            modifier = Modifier.weight(1f)) { Text("Reject all") }
                    } else if (event.status == DlEventStatus.APPROVED || event.status == DlEventStatus.REJECTED) {
                        TextButton(onClick = { onStatus(DlStatus.PENDING, null) }) {
                            Text("Move back to pending")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DlEventStatus, pending: Int, approved: Int, rejected: Int) {
    val outcome = when (status) {
        DlEventStatus.PENDING -> AttendanceOutcome.DL_PENDING
        DlEventStatus.APPROVED -> AttendanceOutcome.DL_APPROVED
        DlEventStatus.REJECTED -> AttendanceOutcome.DL_REJECTED
        else -> AttendanceOutcome.DL_PENDING
    }
    OutcomeChip(outcome)
}
