package com.matedroid.ui.screens.trips

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.theme.CarColorPalettes
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToTripDetail: (tripIndex: Int) -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, darkTheme = true)

    LaunchedEffect(carId) { viewModel.setCarId(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.trips.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Route,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.trips_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (uiState.syncWarning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.trips_sync_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sync warning
                    if (uiState.syncWarning) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.trips_sync_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Summary card
                    item {
                        TripsSummaryCard(
                            tripCount = uiState.trips.size,
                            totalDistance = uiState.totalDistance,
                            totalDrivingMin = uiState.totalDrivingMin,
                            totalEnergyCharged = uiState.totalEnergyCharged,
                            units = uiState.units
                        )
                    }

                    // Trip cards
                    itemsIndexed(uiState.trips, key = { _, trip -> trip.startDate }) { index, trip ->
                        TripCard(
                            trip = trip,
                            units = uiState.units,
                            onClick = { onNavigateToTripDetail(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripsSummaryCard(
    tripCount: Int,
    totalDistance: Double,
    totalDrivingMin: Int,
    totalEnergyCharged: Double,
    units: Units?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryItem(
                icon = Icons.Filled.Route,
                label = stringResource(R.string.trips_title),
                value = "$tripCount",
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.distance),
                value = "%.0f %s".format(
                    UnitFormatter.formatDistanceValue(totalDistance, units, 0),
                    UnitFormatter.getDistanceUnit(units)
                ),
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Filled.Schedule,
                label = stringResource(R.string.trip_driving_time),
                value = formatDuration(totalDrivingMin),
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Filled.ElectricBolt,
                label = stringResource(R.string.trip_energy_charged),
                value = "%.1f kWh".format(totalEnergyCharged),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    units: Units?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Route header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Route,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = trip.startAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = trip.endAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatDate(trip.startDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                    )
                }
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripStatCard(
                    label = stringResource(R.string.distance),
                    value = "%.1f %s".format(
                        UnitFormatter.formatDistanceValue(trip.totalDistance, units, 1),
                        UnitFormatter.getDistanceUnit(units)
                    ),
                    icon = Icons.Filled.Speed,
                    modifier = Modifier.weight(1f)
                )
                TripStatCard(
                    label = stringResource(R.string.trip_total_time),
                    value = formatDuration(trip.totalDurationMin),
                    icon = Icons.Filled.Schedule,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripStatCard(
                    label = stringResource(R.string.trip_charge_stops),
                    value = "${trip.charges.size}",
                    icon = Icons.Filled.ElectricBolt,
                    modifier = Modifier.weight(1f)
                )
                TripStatCard(
                    label = stringResource(R.string.battery),
                    value = "${trip.startBatteryLevel}% → ${trip.endBatteryLevel}%",
                    icon = Icons.Filled.BatteryChargingFull,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TripStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDate(dateStr: String): String {
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        dateStr
    }
}
