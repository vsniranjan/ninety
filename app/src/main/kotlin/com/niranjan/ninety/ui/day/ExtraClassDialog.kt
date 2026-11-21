package com.niranjan.ninety.ui.day

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.R
import com.niranjan.ninety.data.CourseEntity
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.ui.designsystem.CapsText
import com.niranjan.ninety.ui.designsystem.NinetyAlertDialog
import com.niranjan.ninety.ui.designsystem.NinetyDimensions
import com.niranjan.ninety.ui.designsystem.NinetyMenu
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtraClassDialog(
    courses: List<CourseEntity>,
    onSave: (UUID, Int, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }
    val course = courses.firstOrNull { it.id == courseId } ?: courses.firstOrNull()
    var start by rememberSaveable { mutableIntStateOf(17 * 60) }
    var end by rememberSaveable { mutableIntStateOf(18 * 60) }
    var counting by rememberSaveable { mutableStateOf(false) }
    var editingTime by rememberSaveable { mutableStateOf<ExtraTimeField?>(null) }
    var courseMenu by remember { mutableStateOf(false) }
    val invalidTimes = start >= end
    NinetyAlertDialog(
        onDismissRequest = onDismiss,
        title = { CapsText(stringResource(R.string.add_extra_class)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    ExtraFormField(
                        label = stringResource(R.string.extra_class_course),
                        value = course?.name ?: stringResource(R.string.extra_class_no_courses),
                        tag = "extra_course",
                        onClick = { courseMenu = true },
                        enabled = course != null,
                        icon = R.drawable.ic_chevron_down,
                    )
                    NinetyMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                        courses.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = {
                                courseId = option.id
                                courseMenu = false
                            })
                        }
                    }
                }
                ExtraFormField(stringResource(R.string.extra_class_start), PeriodTimes.label(start),
                    tag = "extra_start", onClick = { editingTime = ExtraTimeField.Start }, icon = R.drawable.ic_clock)
                ExtraFormField(stringResource(R.string.extra_class_end), PeriodTimes.label(end),
                    tag = "extra_end", onClick = { editingTime = ExtraTimeField.End }, icon = R.drawable.ic_clock)
                if (invalidTimes) {
                    Text(stringResource(R.string.extra_class_time_error),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(counting, role = Role.Checkbox, onValueChange = { counting = it })
                        .testTag("extra_counting"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = counting, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.counts_toward_attendance), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { course?.let { onSave(UUID.fromString(it.id), start, end, counting) } },
                enabled = course != null && !invalidTimes,
                modifier = Modifier.testTag("extra_add"),
            ) { Text(stringResource(R.string.extra_class_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    editingTime?.let { field ->
        val initial = if (field == ExtraTimeField.Start) start else end
        ExtraTimePicker(field, initial, onDismiss = { editingTime = null }) { minute ->
            when (field) {
                ExtraTimeField.Start -> start = minute
                ExtraTimeField.End -> end = if (minute == 0) 1440 else minute
            }
            editingTime = null
        }
    }
}

private enum class ExtraTimeField(val label: Int) {
    Start(R.string.extra_class_start),
    End(R.string.extra_class_end),
}

@Composable
private fun ExtraFormField(
    label: String,
    value: String,
    tag: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(onClick = onClick, enabled = enabled, shape = NinetyDimensions.pill,
        border = BorderStroke(1.dp, if (enabled) colors.outline else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp),
                tint = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraTimePicker(field: ExtraTimeField, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60 % 24, initialMinute = initial % 60, is24Hour = true)
    val window = LocalWindowInfo.current.containerSize
    val useInput = with(LocalDensity.current) {
        window.height.toDp() < 500.dp || window.width.toDp() < 340.dp || fontScale > 1.3f
    }
    var keyboard by rememberSaveable { mutableStateOf(useInput) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { CapsText(stringResource(field.label)) },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }, modifier = Modifier.testTag("extra_set_time")) {
                Text(stringResource(R.string.extra_class_set_time))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modeToggleButton = {
            if (!useInput) TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { keyboard = !keyboard },
                displayMode = if (keyboard) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker,
            )
        },
    ) {
        if (keyboard || useInput) TimeInput(state) else TimePicker(state)
    }
}
