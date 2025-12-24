package com.matedroid.ui.screens.drives

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.Units
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class DriveDateFilter(val label: String, val days: Long?) {
    LAST_7_DAYS("Last 7 days", 7),
    LAST_30_DAYS("Last 30 days", 30),
    LAST_90_DAYS("Last 90 days", 90),
    LAST_YEAR("Last year", 365),
    ALL_TIME("All time", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivesScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (driveId: Int) -> Unit,
    viewModel: DrivesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(DriveDateFilter.LAST_7_DAYS) }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
        // Apply default 7-day filter on initial load
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(7)
        viewModel.setDateFilter(startDate, endDate)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    fun applyDateFilter(filter: DriveDateFilter) {
        selectedFilter = filter
        if (filter.days != null) {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(filter.days)
            viewModel.setDateFilter(startDate, endDate)
        } else {
            viewModel.clearDateFilter()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drives") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && !uiState.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                DrivesContent(
                    drives = uiState.drives,
                    chartData = uiState.chartData,
                    chartGranularity = uiState.chartGranularity,
                    summary = uiState.summary,
                    selectedDateFilter = selectedFilter,
                    selectedDistanceFilter = uiState.distanceFilter,
                    units = uiState.units,
                    palette = palette,
                    onDateFilterSelected = { applyDateFilter(it) },
                    onDistanceFilterSelected = { viewModel.setDistanceFilter(it) },
                    onDriveClick = onNavigateToDriveDetail
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrivesContent(
    drives: List<DriveData>,
    chartData: List<DriveChartData>,
    chartGranularity: DriveChartGranularity,
    summary: DrivesSummary,
    selectedDateFilter: DriveDateFilter,
    selectedDistanceFilter: DriveDistanceFilter,
    units: Units?,
    palette: CarColorPalette,
    onDateFilterSelected: (DriveDateFilter) -> Unit,
    onDistanceFilterSelected: (DriveDistanceFilter) -> Unit,
    onDriveClick: (driveId: Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DateFilterChips(
                selectedFilter = selectedDateFilter,
                palette = palette,
                onFilterSelected = onDateFilterSelected
            )
        }

        item {
            DistanceFilterChips(
                selectedFilter = selectedDistanceFilter,
                units = units,
                palette = palette,
                onFilterSelected = onDistanceFilterSelected
            )
        }

        item {
            SummaryCard(summary = summary, palette = palette)
        }

        // Drives chart (daily/weekly/monthly based on date range)
        if (chartData.isNotEmpty()) {
            item {
                DrivesChart(chartData = chartData, granularity = chartGranularity, palette = palette)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Drive History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (drives.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No drives found for selected period",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(drives, key = { it.id }) { drive ->
                DriveItem(
                    drive = drive,
                    onClick = { onDriveClick(drive.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChips(
    selectedFilter: DriveDateFilter,
    palette: CarColorPalette,
    onFilterSelected: (DriveDateFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DriveDateFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DistanceFilterChips(
    selectedFilter: DriveDistanceFilter,
    units: Units?,
    palette: CarColorPalette,
    onFilterSelected: (DriveDistanceFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DriveDistanceFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.getLabel(units)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: DrivesSummary, palette: CarColorPalette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryItem(
                    icon = Icons.Default.DirectionsCar,
                    label = "Total Trips",
                    value = summary.totalDrives.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = CustomIcons.SteeringWheel,
                    label = "Total Distance",
                    value = "%.1f km".format(summary.totalDistanceKm),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryItem(
                    icon = Icons.Default.Timer,
                    label = "Total Time",
                    value = formatDuration(summary.totalDurationMin),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Default.Speed,
                    label = "Max Speed",
                    value = "${summary.maxSpeedKmh} km/h",
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
private fun DriveItem(
    drive: DriveData,
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
            // Header card with route
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = drive.startAddress ?: "Unknown",
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
                            text = drive.endAddress ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    drive.startDate?.let { dateStr ->
                        Text(
                            text = formatDate(dateStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                        )
                    }
                }
            }

            // Stats row with individual cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Distance
                DriveStatCard(
                    icon = CustomIcons.SteeringWheel,
                    value = "%.1f".format(drive.distance ?: 0.0),
                    unit = "km",
                    label = "Distance",
                    modifier = Modifier.weight(1f)
                )

                // Duration
                DriveStatCard(
                    icon = Icons.Default.Schedule,
                    value = formatDuration(drive.durationMin ?: 0),
                    unit = "",
                    label = "Duration",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Max Speed
                DriveStatCard(
                    icon = Icons.Default.Speed,
                    value = "${drive.speedMax ?: 0}",
                    unit = "km/h",
                    label = "Max Speed",
                    modifier = Modifier.weight(1f)
                )

                // Battery used
                val startLevel = drive.startBatteryLevel
                val endLevel = drive.endBatteryLevel
                DriveStatCard(
                    icon = Icons.Default.BatteryChargingFull,
                    value = if (startLevel != null && endLevel != null) "$startLevel% → $endLevel%" else "--",
                    unit = "",
                    label = "Battery",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DriveStatCard(
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
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
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

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@Composable
private fun DrivesChart(
    chartData: List<DriveChartData>,
    granularity: DriveChartGranularity,
    palette: CarColorPalette
) {
    val title = when (granularity) {
        DriveChartGranularity.DAILY -> "Drives per Day"
        DriveChartGranularity.WEEKLY -> "Drives per Week"
        DriveChartGranularity.MONTHLY -> "Drives per Month"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = palette.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val barData = chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.count.toDouble(),
                    displayValue = "${data.count} drives"
                )
            }

            // Show max ~6 labels to avoid crowding
            val labelInterval = ((barData.size + 5) / 6).coerceAtLeast(1)

            InteractiveBarChart(
                data = barData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                showEveryNthLabel = labelInterval,
                valueFormatter = { "%.0f drives".format(it) }
            )
        }
    }
}
