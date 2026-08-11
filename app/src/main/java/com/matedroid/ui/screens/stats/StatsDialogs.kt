package com.matedroid.ui.screens.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.theme.CarColorPalette

/**
 * Debug-only dialog showing sync logs like adb logcat.
 */
@Composable
internal fun SyncLogsDialog(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.stats_sync_logs_title))
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true // Show newest logs at the bottom
                ) {
                    items(logs.reversed()) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

/**
 * Dialog showing details of a gap record (longest period without charging/driving).
 */
@Composable
internal fun GapRecordDialog(
    gapDays: Double,
    fromDate: String,
    toDate: String,
    title: String,
    palette: CarColorPalette,
    onDismiss: () -> Unit
) {
    // title is now the gap type (Charging/Driving), used for determining emoji
    val isCharging = title == stringResource(R.string.gap_type_charging)
    val emoji = if (isCharging) "⏰" else "🅿️"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.stats_gap_dialog_title, title))
            }
        },
        text = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = palette.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.format_days, gapDays),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date range
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.started),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceVariant
                            )
                            Text(
                                text = fromDate.take(10),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = palette.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.ended),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceVariant
                            )
                            Text(
                                text = toDate.take(10),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = palette.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

/**
 * Dialog showing details of a "longest range" record with scrollable list of drives.
 */
@Composable
internal fun RangeRecordDialog(
    record: MaxDistanceBetweenChargesRecord,
    drives: List<DriveSummary>,
    isLoading: Boolean,
    palette: CarColorPalette,
    units: Units?,
    onDriveClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔋", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.stats_range_record_title))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Summary info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = palette.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.total_distance),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onSurfaceVariant
                            )
                            Text(
                                text = UnitFormatter.formatDistance(record.distance, units),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.from),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.onSurfaceVariant
                                )
                                Text(
                                    text = record.fromDate.take(10),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.to),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.onSurfaceVariant
                                )
                                Text(
                                    text = record.toDate.take(10),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Drives header
                Text(
                    text = stringResource(R.string.stats_drives_count, drives.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable list of drives
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (drives.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.stats_no_drives_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(drives, key = { it.driveId }) { drive ->
                                DriveListItem(
                                    drive = drive,
                                    palette = palette,
                                    units = units,
                                    onClick = { onDriveClick(drive.driveId) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

/**
 * Single drive item in the range record dialog.
 */
@Composable
private fun DriveListItem(
    drive: DriveSummary,
    palette: CarColorPalette,
    units: Units?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = palette.surface.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drive.startDate.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
                Text(
                    text = "${drive.startAddress.take(25)}${if (drive.startAddress.length > 25) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "→ ${drive.endAddress.take(25)}${if (drive.endAddress.length > 25) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = UnitFormatter.formatDistance(drive.distance, units),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Text(
                    text = "${drive.durationMin} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.view_drive),
                modifier = Modifier.size(18.dp),
                tint = palette.onSurfaceVariant
            )
        }
    }
}
