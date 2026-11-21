package com.niranjan.ninety.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.R
import java.time.ZoneId

@Composable
fun CollegeZoneAction(zoneId: String, enabled: Boolean = true, onClick: () -> Unit) {
    ActionRow(R.drawable.ic_clock, stringResource(R.string.college_timezone), onClick,
        enabled = enabled, showChevron = true,
        supportingText = if (zoneId == ZoneId.systemDefault().id) stringResource(R.string.phone_timezone)
            else zoneId)
}

@Composable
fun CollegeZonePicker(zoneId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val deviceZone = ZoneId.systemDefault().id
    val zones = remember(zoneId, deviceZone) {
        listOf(deviceZone, zoneId).distinct() +
            ZoneId.getAvailableZoneIds().filterNot { it == deviceZone || it == zoneId }.sorted()
    }
    val matches = remember(query, zones) {
        val normalized = query.trim().replace('_', ' ')
        zones.filter { it.replace('_', ' ').contains(normalized, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    BackHandler(onBack = onDismiss)

    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsText(stringResource(R.string.college_timezone), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() })
            ActionIcon(R.drawable.ic_close, stringResource(R.string.cancel), onDismiss)
        }
        NinetyTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_timezones)) }, singleLine = true)
        if (matches.isEmpty()) Text(stringResource(R.string.no_timezones_found))
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(matches, key = { it }) { zone ->
                ActionRow(R.drawable.ic_clock, zone, onClick = { onSelect(zone) },
                    supportingText = when (zone) {
                        deviceZone -> stringResource(if (zone == zoneId) R.string.selected_phone_timezone
                            else R.string.phone_timezone)
                        zoneId -> stringResource(R.string.selected_timezone)
                        else -> null
                    })
            }
        }
    }
}
