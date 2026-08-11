package com.matedroid.ui.screens.mileage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.util.formatDuration
import com.matedroid.util.formatTime
import com.matedroid.util.parseIsoDateTime
import java.time.format.TextStyle
import java.util.Locale

// ============================================================================
// Level 4: Day Detail
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailScreen(
    dayData: DailyMileage,
    currencySymbol: String,
    units: Units? = null,
    palette: CarColorPalette,
    onClose: () -> Unit,
    onDriveClick: (Int) -> Unit
) {
    val dayOfWeek = dayData.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dateStr = "%d %s %d".format(
        dayData.date.dayOfMonth,
        dayData.date.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        dayData.date.year
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dayOfWeek) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Day summary card
            item {
                DaySummaryCard(
                    dayData = dayData,
                    dateStr = dateStr,
                    currencySymbol = currencySymbol,
                    units = units,
                    palette = palette
                )
            }

            // Drives header
            if (dayData.drives.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.mileage_drives),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Drive rows
                items(dayData.drives, key = { it.driveId }, contentType = { "drive" }) { drive ->
                    DriveRow(
                        drive = drive,
                        units = units,
                        onClick = { onDriveClick(drive.driveId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySummaryCard(
    dayData: DailyMileage,
    dateStr: String,
    currencySymbol: String,
    units: Units? = null,
    palette: CarColorPalette
) {
    val avgDistance = if (dayData.driveCount > 0) dayData.totalDistance / dayData.driveCount else 0.0
    //val avgEnergy = if (dayData.driveCount > 0) dayData.totalEnergy / dayData.driveCount else 0.0
    val avgEfficiency = efficiencyWhPerUnit(dayData.totalEnergy, dayData.totalDistance)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = palette.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dayData.driveCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    icon = CustomIcons.Road,
                    value = UnitFormatter.formatDistance(dayData.totalDistance, units),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    prefix = "Ø",
                    icon = CustomIcons.Road,
                    value = UnitFormatter.formatDistance(avgDistance, units),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    iconText = "🔋",
                    value = "%.0f%%".format(dayData.totalBatteryUsage),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    prefix = "Ø",
                    icon = Icons.Outlined.EnergySavingsLeaf,
                    value = UnitFormatter.formatEfficiency(avgEfficiency, units),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    icon = Icons.Filled.ElectricBolt,
                    value = UnitFormatter.formatEnergy(dayData.totalEnergy),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    icon = Icons.Filled.AttachMoney,
                    value = UnitFormatter.formatCost(dayData.totalEnergyCost ?: 0.0, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DriveRow(
    drive: DriveData,
    units: Units? = null,
    onClick: () -> Unit
) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val startTime = drive.startDate?.let { parseTime(it, is24Hour) } ?: ""
    val endTime = drive.endDate?.let { parseTime(it, is24Hour) } ?: ""
    val distance = drive.distance ?: 0.0
    val duration = drive.durationMin ?: 0
    val energyUsed = drive.energyConsumedNet ?: 0.0
    val batteryStart = drive.batteryDetails?.startBatteryLevel ?: 0
    val batteryEnd = drive.batteryDetails?.endBatteryLevel ?: 0
    val batteryUsage = batteryStart - batteryEnd
    val efficiency = drive.efficiencyWhKm ?: 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time info
            Column(modifier = Modifier.width(50.dp)) {
                Text(
                    text = startTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "→ $endTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(//alignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⏱",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatDuration(LocalContext.current.resources, duration),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Distance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CustomIcons.Road,
                        contentDescription = null,
                        tint = ChartBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = UnitFormatter.formatDistance(distance, units),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Efficiency
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = UnitFormatter.formatEfficiency(efficiency, units),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Energy
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "%.1f kWh".format(energyUsed),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Battery usage
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Battery5Bar,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "%d%%".format(batteryUsage),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Arrow indicator
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.view_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun parseTime(dateStr: String, is24Hour: Boolean): String {
    val dt = parseIsoDateTime(dateStr) ?: return ""
    return dt.formatTime(java.util.Locale.getDefault(), is24Hour)
}
