package com.matedroid.ui.screens.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.domain.AppTimeZone
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picker for [AppTimeZone.mode]: the two automatic modes plus "pick a zone", which opens a
 * searchable list of every zone the platform knows about.
 */
@Composable
fun TimeZonePicker(
    mode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showZoneDialog by remember { mutableStateOf(false) }

    val serverLabel = stringResource(R.string.settings_timezone_server)
    val deviceLabel = stringResource(
        R.string.settings_timezone_device,
        ZoneId.systemDefault().id
    )
    val pickLabel = stringResource(R.string.settings_timezone_pick)

    val currentLabel = when (mode) {
        AppTimeZone.MODE_SERVER -> serverLabel
        AppTimeZone.MODE_DEVICE -> deviceLabel
        else -> zoneLabel(mode)
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            label = { Text(stringResource(R.string.settings_timezone_label)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.settings_timezone_select)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            DropdownMenuItem(
                text = { Text(serverLabel) },
                onClick = {
                    onModeChange(AppTimeZone.MODE_SERVER)
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(deviceLabel) },
                onClick = {
                    onModeChange(AppTimeZone.MODE_DEVICE)
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(pickLabel) },
                onClick = {
                    menuExpanded = false
                    showZoneDialog = true
                }
            )
        }
    }

    if (showZoneDialog) {
        ZoneSelectionDialog(
            onDismiss = { showZoneDialog = false },
            onZoneSelected = { zoneId ->
                onModeChange(zoneId)
                showZoneDialog = false
            }
        )
    }
}

/**
 * Searchable list of every available zone id. There are several hundred, so the list is
 * filtered rather than scrolled — typing "madrid" or "+02" narrows it immediately.
 */
@Composable
private fun ZoneSelectionDialog(
    onDismiss: () -> Unit,
    onZoneSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Sorted once per dialog; ~600 ids, cheap enough and avoids re-sorting on each keystroke.
    val allZones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val labelled = remember(allZones) { allZones.map { it to zoneLabel(it) } }
    val filtered = remember(query, labelled) {
        if (query.isBlank()) {
            labelled
        } else {
            labelled.filter { (_, label) -> label.contains(query.trim(), ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_timezone_pick)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.settings_timezone_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { (id, _) -> id }) { (id, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onZoneSelected(id) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * "Europe/Madrid (+02:00)" — the current offset makes the list searchable by offset and
 * disambiguates the many zone ids that share a city name.
 */
private fun zoneLabel(zoneId: String): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return zoneId
    val offset = zone.rules.getOffset(Instant.now())
    val text = if (offset == ZoneOffset.UTC) "+00:00" else offset.id
    return "$zoneId ($text)"
}
