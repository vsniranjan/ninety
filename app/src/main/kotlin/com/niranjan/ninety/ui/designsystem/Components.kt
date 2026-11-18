package com.niranjan.ninety.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.R
import com.niranjan.ninety.data.DlStatus
import com.niranjan.ninety.domain.AttendanceOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun NinetyCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large,
            colors = colors) { content() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors) { content() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp))
}

private data class ChipStyle(val background: androidx.compose.ui.graphics.Color,
    val foreground: androidx.compose.ui.graphics.Color, val label: String)

@Composable
private fun chipStyle(outcome: AttendanceOutcome): ChipStyle {
    val scheme = MaterialTheme.colorScheme
    return when (outcome) {
        AttendanceOutcome.PRESENT -> ChipStyle(scheme.primaryContainer, scheme.onPrimaryContainer, stringResource(R.string.present))
        AttendanceOutcome.ABSENT -> ChipStyle(scheme.errorContainer, scheme.onErrorContainer, stringResource(R.string.absent))
        AttendanceOutcome.DL_PENDING -> ChipStyle(MaterialTheme.caution.container, MaterialTheme.caution.foreground,
            stringResource(R.string.duty_leave_pending))
        AttendanceOutcome.DL_APPROVED -> ChipStyle(scheme.primaryContainer, scheme.onPrimaryContainer, stringResource(R.string.duty_leave_approved))
        AttendanceOutcome.DL_REJECTED -> ChipStyle(scheme.errorContainer, scheme.onErrorContainer, stringResource(R.string.duty_leave_rejected))
        AttendanceOutcome.UNRECORDED -> ChipStyle(scheme.surfaceVariant, scheme.onSurfaceVariant, stringResource(R.string.not_marked))
    }
}

@Composable
fun OutcomeChip(outcome: AttendanceOutcome, modifier: Modifier = Modifier,
    cancelled: Boolean = false, counting: Boolean = true, periodLabel: String? = null) {
    val style = when {
        cancelled -> ChipStyle(MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.not_held))
        !counting -> ChipStyle(MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.does_not_count))
        else -> chipStyle(outcome)
    }
    val background by animateColorAsState(style.background, tween(180), label = "Outcome")
    val icon = when {
        cancelled -> R.drawable.ic_close
        !counting -> R.drawable.ic_minus
        else -> when (outcome) {
            AttendanceOutcome.PRESENT, AttendanceOutcome.DL_APPROVED -> R.drawable.ic_check
            AttendanceOutcome.ABSENT, AttendanceOutcome.DL_REJECTED -> R.drawable.ic_close
            AttendanceOutcome.DL_PENDING -> R.drawable.ic_clock
            AttendanceOutcome.UNRECORDED -> R.drawable.ic_minus
        }
    }
    val showLabel = cancelled || !counting || when (outcome) {
        AttendanceOutcome.PRESENT, AttendanceOutcome.ABSENT -> false
        AttendanceOutcome.UNRECORDED -> periodLabel == null
        else -> true
    }
    Surface(color = background, shape = RoundedCornerShape(8.dp),
        border = if (!counting && !cancelled) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = when {
            periodLabel != null -> modifier.clearAndSetSemantics { contentDescription = "$periodLabel, ${style.label}" }
            showLabel -> modifier
            else -> modifier.semantics { contentDescription = style.label }
        }) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (periodLabel != null) Text(periodLabel, style = MaterialTheme.typography.labelSmall,
                color = style.foreground, modifier = Modifier.padding(end = 4.dp))
            Icon(painterResource(icon), contentDescription = null,
                tint = style.foreground, modifier = Modifier.size(18.dp))
            if (showLabel) {
                Text(style.label, style = MaterialTheme.typography.labelSmall, color = style.foreground,
                    modifier = Modifier.padding(start = 4.dp))
            }
        }
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
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, tag: String = "empty",
    actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_calendar), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag),
            textAlign = TextAlign.Center)
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
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
fun NinetyDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit,
    minDate: LocalDate? = null, maxDate: LocalDate? = null) {
    val initialDate = when {
        minDate != null && initial < minDate -> minDate
        maxDate != null && initial > maxDate -> maxDate
        else -> initial
    }
    val selectableDates = remember(minDate, maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcTimeMillis.toPickerDate()
                return (minDate == null || date >= minDate) && (maxDate == null || date <= maxDate)
            }

            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates)
    val selected = state.selectedDateMillis?.takeIf(selectableDates::isSelectableDate)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { selected?.let { onPicked(it.toPickerDate()) } }, enabled = selected != null) {
            Text(stringResource(R.string.ok))
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) {
        DatePicker(state = state)
    }
}

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

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


/** A labelled action with enough room for the label to wrap on compact screens. */
@Composable
fun ActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, showChevron: Boolean = false, supportingText: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (supportingText != null) Text(supportingText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showChevron) {
            Icon(painterResource(R.drawable.ic_forward), contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionIcon(@DrawableRes icon: Int, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, modifier = modifier.size(48.dp), enabled = enabled) {
            Icon(painterResource(icon), contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_check, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

/** A marker per eligible period; count and semantics remain exact on large schedules. */
@Composable
fun PeriodProgress(recorded: Int, total: Int, modifier: Modifier = Modifier,
    description: String = pluralStringResource(R.plurals.period_progress, total, recorded, total)) {
    Row(modifier.fillMaxWidth().semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(recorded.toFloat(), 0f..total.coerceAtLeast(1).toFloat())
        stateDescription = description
    }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        val visible = total.coerceIn(1, 24)
        repeat(visible) { index ->
            val filled = (index + 1).toFloat() / visible <= recorded.toFloat() / total.coerceAtLeast(1)
            val color by animateColorAsState(if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant, tween(180), label = "Period marker")
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .then(Modifier.background(color)))
        }
    }
}
