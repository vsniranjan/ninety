package com.niranjan.ninety.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.domain.AttendanceOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun NinetyCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large,
            colors = colors, border = CardDefaults.outlinedCardBorder()) { content() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors,
            border = CardDefaults.outlinedCardBorder()) { content() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp))
}

private data class ChipStyle(val background: androidx.compose.ui.graphics.Color,
    val foreground: androidx.compose.ui.graphics.Color, val label: String)

@Composable
private fun chipStyle(outcome: AttendanceOutcome): ChipStyle {
    val scheme = MaterialTheme.colorScheme
    return when (outcome) {
        AttendanceOutcome.PRESENT -> ChipStyle(scheme.primaryContainer, scheme.onPrimaryContainer, "Present")
        AttendanceOutcome.ABSENT -> ChipStyle(scheme.errorContainer, scheme.onErrorContainer, "Absent")
        AttendanceOutcome.DL_PENDING -> ChipStyle(scheme.tertiaryContainer, scheme.onTertiaryContainer, "DL pending")
        AttendanceOutcome.DL_APPROVED -> ChipStyle(scheme.primaryContainer, scheme.onPrimaryContainer, "DL approved")
        AttendanceOutcome.DL_REJECTED -> ChipStyle(scheme.errorContainer, scheme.onErrorContainer, "DL rejected")
        AttendanceOutcome.UNRECORDED -> ChipStyle(scheme.surfaceVariant, scheme.onSurfaceVariant, "Not recorded")
    }
}

@Composable
fun OutcomeChip(outcome: AttendanceOutcome, modifier: Modifier = Modifier,
    cancelled: Boolean = false, counting: Boolean = true) {
    val style = when {
        cancelled -> ChipStyle(MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, "Not conducted")
        !counting -> ChipStyle(MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, "Not counted")
        else -> chipStyle(outcome)
    }
    val background by animateColorAsState(style.background, label = "chip")
    Surface(color = background, shape = CircleShape, modifier = modifier) {
        Text(style.label, style = MaterialTheme.typography.labelMedium, color = style.foreground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun DlStatusChip(status: DlStatus, modifier: Modifier = Modifier) {
    OutcomeChip(when (status) {
        DlStatus.PENDING -> AttendanceOutcome.DL_PENDING
        DlStatus.APPROVED -> AttendanceOutcome.DL_APPROVED
        DlStatus.REJECTED -> AttendanceOutcome.DL_REJECTED
    }, modifier = modifier)
}

@Composable
fun AttendanceBar(credited: Long, conducted: Long, modifier: Modifier = Modifier) {
    val fraction = if (conducted <= 0) 0f else (credited.toFloat() / conducted).coerceIn(0f, 1f)
    val meets = conducted > 0 && 10 * credited >= 9 * conducted
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = if (meets) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, tag: String = "empty") {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
        Text(body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp))
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, destructive: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_action")) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Modal date picker returning a LocalDate; keeps text input out of forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NinetyDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = {
            state.selectedDateMillis?.let {
                onPicked(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
            }
        }) { Text("OK") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) {
        DatePicker(state = state)
    }
}

@Composable
fun FieldButton(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tag: String? = null) {
    Column(modifier.semantics { contentDescription = "$label: $value" }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick, modifier = Modifier.padding(top = 2.dp)) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Choices keep their natural label widths and wrap as whole controls at large text sizes. */
@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) },
                label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
