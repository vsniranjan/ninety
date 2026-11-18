package com.niranjan.ninety.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.R
import com.niranjan.ninety.domain.PeriodTimes
import com.niranjan.ninety.domain.WeekTemplate
import com.niranjan.ninety.ui.collegeWeekdays
import com.niranjan.ninety.ui.fullName
import com.niranjan.ninety.ui.shortName
import java.time.DayOfWeek
import java.util.UUID

/** Six periods for the selected weekday, with room for full course names. */
@Composable
fun WeeklySchedule(template: WeekTemplate, names: Map<UUID, String>, editable: Boolean = true,
    slotTag: String = "slot_cell", initialWeekday: DayOfWeek = DayOfWeek.MONDAY,
    onAssign: (DayOfWeek, Int, UUID?) -> Unit) {
    var selectedDay by rememberSaveable { mutableIntStateOf(initialWeekday.value) }
    val day = DayOfWeek.of(selectedDay)
    val railWidth = 52.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            collegeWeekdays().forEach { weekday ->
                FilterChip(selected = weekday == day, onClick = { selectedDay = weekday.value },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("weekday_${weekday.value}")
                        .semantics { contentDescription = weekday.fullName() },
                    label = { Text(weekday.shortName()) })
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Column {
                (1..PeriodTimes.SLOT_COUNT).forEach { slot ->
                    if (slot > 1) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    val name = template.courseAt(day, slot)?.let(names::get)
                    val free = stringResource(R.string.free_period)
                    val description = stringResource(R.string.period_description, day.fullName(), slot, name ?: free)
                    var menu by remember(day) { mutableStateOf(false) }
                    Box {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .then(if (editable) Modifier.clickable { menu = true } else Modifier)
                            .testTag(slotTag).semantics {
                                contentDescription = description
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.width(railWidth), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("P$slot", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(PeriodTimes.label(PeriodTimes.slotTimes(day)[slot - 1].first),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(name ?: free, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (name == null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface)
                            if (editable) Icon(painterResource(R.drawable.ic_forward), contentDescription = null,
                                modifier = Modifier.size(20.dp).rotate(90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(free) }, onClick = { menu = false; onAssign(day, slot, null) })
                            names.forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onAssign(day, slot, id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
