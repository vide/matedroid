package com.matedroid.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.matedroid.ui.theme.CarColorPalette
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
    onNavigateToTripDetail: (tripStartDate: String) -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) { viewModel.setCarId(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.trips.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
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
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.padding(padding)) {
                    TripsContent(
                        trips = uiState.trips,
                        totalDistance = uiState.totalDistance,
                        totalDrivingMin = uiState.totalDrivingMin,
                        totalEnergyCharged = uiState.totalEnergyCharged,
                        availableYears = uiState.availableYears,
                        selectedYear = uiState.selectedYear,
                        onYearSelected = { viewModel.setYear(it) },
                        units = uiState.units,
                        palette = palette,
                        onTripClick = onNavigateToTripDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun TripsContent(
    trips: List<Trip>,
    totalDistance: Double,
    totalDrivingMin: Int,
    totalEnergyCharged: Double,
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
    units: Units?,
    palette: CarColorPalette,
    onTripClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Year filter chips
        if (availableYears.size > 1) {
            item {
                YearFilterChips(
                    years = availableYears,
                    selectedYear = selectedYear,
                    palette = palette,
                    onYearSelected = onYearSelected
                )
            }
        }

        // Summary card
        item {
            SummaryCard(
                tripCount = trips.size,
                totalDistance = totalDistance,
                totalDrivingMin = totalDrivingMin,
                totalEnergyCharged = totalEnergyCharged,
                units = units,
                palette = palette
            )
        }

        // Section header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trips_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Trip cards
        itemsIndexed(trips, key = { _, trip -> trip.startDate }) { index, trip ->
            TripItem(
                trip = trip,
                units = units,
                onClick = { onTripClick(trip.startDate) }
            )
        }
    }
}

@Composable
private fun SummaryCard(
    tripCount: Int,
    totalDistance: Double,
    totalDrivingMin: Int,
    totalEnergyCharged: Double,
    units: Units?,
    palette: CarColorPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.trip_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Route,
                    label = stringResource(R.string.trips_title),
                    value = "%,d".format(tripCount),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.total_distance),
                    value = UnitFormatter.formatDistance(totalDistance, units),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.trip_driving_time),
                    value = formatDuration(totalDrivingMin),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.trip_energy_charged),
                    value = "%.1f kWh".format(totalEnergyCharged),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = palette.accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }
    }
}

@Composable
private fun TripItem(
    trip: Trip,
    units: Units?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Route header — single line
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${extractCity(trip.startAddress)} → ${extractCity(trip.endAddress)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
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

            // Stats — 2x2 grid, same DriveStatCard pattern
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripStatCard(
                    icon = Icons.Filled.Speed,
                    value = "%.1f".format(UnitFormatter.formatDistanceValue(trip.totalDistance, units)),
                    unit = UnitFormatter.getDistanceUnit(units),
                    label = stringResource(R.string.distance),
                    modifier = Modifier.weight(1f)
                )
                TripStatCard(
                    icon = Icons.Filled.Schedule,
                    value = formatDuration(trip.totalDurationMin),
                    unit = "",
                    label = stringResource(R.string.trip_total_time),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripStatCard(
                    icon = Icons.Filled.ElectricBolt,
                    value = "${trip.charges.size}",
                    unit = "",
                    label = stringResource(R.string.trip_charge_stops),
                    modifier = Modifier.weight(1f)
                )
                TripStatCard(
                    icon = Icons.Filled.BatteryChargingFull,
                    value = "%.1f".format(trip.totalEnergyConsumed),
                    unit = "kWh",
                    label = stringResource(R.string.trip_energy_consumed),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TripStatCard(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearFilterChips(
    years: List<Int>,
    selectedYear: Int?,
    palette: CarColorPalette,
    onYearSelected: (Int?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedYear == null,
                onClick = { onYearSelected(null) },
                label = { Text(stringResource(R.string.filter_all_time)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
        items(years) { year ->
            FilterChip(
                selected = year == selectedYear,
                onClick = { onYearSelected(year) },
                label = { Text(year.toString()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

/** Extract city name from address like "Ionity Montpellier, Saint-Aunès" → "Saint-Aunès". */
internal fun extractCity(address: String): String {
    val parts = address.split(", ")
    return if (parts.size >= 2) parts.last() else address
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDate(dateStr: String): String {
    return try {
        val inputFormatter = DateTimeFormatter.ISO_DATE_TIME
        val outputFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        val dateTime = LocalDateTime.parse(dateStr, inputFormatter)
        dateTime.format(outputFormatter)
    } catch (e: Exception) {
        dateStr
    }
}
