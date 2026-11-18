package com.niranjan.ninety.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.R
import com.niranjan.ninety.domain.AttendanceOutcome

@Composable
internal fun MarkControl(
    outcome: AttendanceOutcome,
    enabled: Boolean,
    compact: Boolean,
    tag: String,
    presentAction: String,
    absentAction: String,
    onMark: (AttendanceOutcome) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Surface(color = scheme.surfaceVariant, shape = shape) {
        Row(Modifier.clip(shape).selectableGroup()) {
            MarkSegment(
                R.string.present, R.drawable.ic_check, presentAction, "${tag}_present",
                outcome == AttendanceOutcome.PRESENT, enabled, compact,
                scheme.primaryContainer, scheme.onPrimaryContainer,
            ) { onMark(AttendanceOutcome.PRESENT) }
            MarkSegment(
                R.string.absent, R.drawable.ic_close, absentAction, "${tag}_absent",
                outcome == AttendanceOutcome.ABSENT, enabled, compact,
                scheme.errorContainer, scheme.onErrorContainer,
            ) { onMark(AttendanceOutcome.ABSENT) }
        }
    }
}

@Composable
private fun MarkSegment(
    label: Int,
    icon: Int,
    action: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    compact: Boolean,
    selectedBackground: Color,
    selectedForeground: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (selected) selectedForeground else scheme.onSurfaceVariant
    Box(
        Modifier.widthIn(min = 56.dp).heightIn(min = 48.dp)
            .testTag(tag)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = action }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = if (selected) selectedBackground else scheme.surfaceVariant,
            contentColor = foreground,
            border = if (selected) BorderStroke(1.dp, foreground) else null,
            shape = MaterialTheme.shapes.small,
        ) {
            Box(
                Modifier.widthIn(min = 52.dp).heightIn(min = 32.dp).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (compact) Icon(painterResource(icon), null, Modifier.size(18.dp))
                else Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
