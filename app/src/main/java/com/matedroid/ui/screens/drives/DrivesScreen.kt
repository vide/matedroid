package com.matedroid.ui.screens.drives

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.Units
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    // Remember scroll state and restore from ViewModel
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.scrollPosition,
        initialFirstVisibleItemScrollOffset = uiState.scrollOffset
    )

    // Initialize ViewModel with carId (only loads data on first call)
    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    // Save scroll position when it changes
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        viewModel.saveScrollPosition(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drives_title)) },
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
                    selectedDateFilter = uiState.dateFilter,
                    selectedDistanceFilter = uiState.distanceFilter,
                    units = uiState.units,
                    palette = palette,
                    listState = listState,
                    onDateFilterSelected = { viewModel.setDateFilter(it) },
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDateFilterSelected: (DriveDateFilter) -> Unit,
    onDistanceFilterSelected: (DriveDistanceFilter) -> Unit,
    onDriveClick: (driveId: Int) -> Unit
) {
    LazyColumn(
        state = listState,
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

        // Drives charts (daily/weekly/monthly based on date range) - swipeable
        if (chartData.isNotEmpty()) {
            item {
                DrivesChartsPager(
                    chartData = chartData,
                    granularity = chartGranularity,
                    units = units,
                    palette = palette
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.drive_history),
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
                            text = stringResource(R.string.no_drives_found),
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
                label = { Text(stringResource(filter.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

@Composable
private fun getDistanceFilterLabel(filter: DriveDistanceFilter, units: Units?): String {
    val isImperial = units?.isImperial == true
    return when (filter) {
        DriveDistanceFilter.ALL -> stringResource(R.string.filter_all)
        DriveDistanceFilter.COMMUTE -> stringResource(if (isImperial) R.string.filter_commute_mi else R.string.filter_commute_km)
        DriveDistanceFilter.DAY_TRIP -> stringResource(if (isImperial) R.string.filter_day_trip_mi else R.string.filter_day_trip_km)
        DriveDistanceFilter.ROAD_TRIP -> stringResource(if (isImperial) R.string.filter_road_trip_mi else R.string.filter_road_trip_km)
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
                label = { Text(getDistanceFilterLabel(filter, units)) },
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
                text = stringResource(R.string.summary),
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
                    label = stringResource(R.string.total_trips),
                    value = summary.totalDrives.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = CustomIcons.SteeringWheel,
                    label = stringResource(R.string.total_distance),
                    value = "%,.1f km".format(summary.totalDistanceKm),
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
                    label = stringResource(R.string.total_time),
                    value = formatDuration(summary.totalDurationMin),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Default.Speed,
                    label = stringResource(R.string.max_speed),
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
                            text = drive.startAddress ?: stringResource(R.string.unknown),
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
                            text = drive.endAddress ?: stringResource(R.string.unknown),
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
                    label = stringResource(R.string.distance),
                    modifier = Modifier.weight(1f)
                )

                // Duration
                DriveStatCard(
                    icon = Icons.Default.Schedule,
                    value = formatDuration(drive.durationMin ?: 0),
                    unit = "",
                    label = stringResource(R.string.duration),
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
                    label = stringResource(R.string.max_speed),
                    modifier = Modifier.weight(1f)
                )

                // Battery used
                val startLevel = drive.startBatteryLevel
                val endLevel = drive.endBatteryLevel
                DriveStatCard(
                    icon = Icons.Default.BatteryChargingFull,
                    value = if (startLevel != null && endLevel != null) "$startLevel% → $endLevel%" else "--",
                    unit = "",
                    label = stringResource(R.string.battery),
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
    return "%d:%02d".format(hours, mins)
}

/**
 * Chart type enum for the swipeable pager
 */
private enum class DrivesChartType {
    COUNT, TIME, DISTANCE, TOP_SPEED
}

/**
 * Swipeable pager containing Count, Time, and Distance charts with page indicator dots
 */
@Composable
private fun DrivesChartsPager(
    chartData: List<DriveChartData>,
    granularity: DriveChartGranularity,
    units: Units?,
    palette: CarColorPalette
) {
    val pagerState = rememberPagerState(pageCount = { DrivesChartType.entries.size })

    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = palette.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val chartType = DrivesChartType.entries[page]
                    DrivesChartPage(
                        chartData = chartData,
                        granularity = granularity,
                        chartType = chartType,
                        units = units,
                        palette = palette
                    )
                }
            }
        }

        // Page indicator dots
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(DrivesChartType.entries.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) palette.accent
                            else palette.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

/**
 * Individual chart page showing Count, Time, or Distance data
 */
@Composable
private fun DrivesChartPage(
    chartData: List<DriveChartData>,
    granularity: DriveChartGranularity,
    chartType: DrivesChartType,
    units: Units?,
    palette: CarColorPalette
) {
    val isImperial = units?.isImperial == true
    val distanceUnit = if (isImperial) "mi" else "km"
    val speedUnit = if (isImperial) "mph" else "km/h"

    val (title, icon) = when (chartType) {
        DrivesChartType.COUNT -> when (granularity) {
            DriveChartGranularity.DAILY -> stringResource(R.string.chart_drives_per_day)
            DriveChartGranularity.WEEKLY -> stringResource(R.string.chart_drives_per_week)
            DriveChartGranularity.MONTHLY -> stringResource(R.string.chart_drives_per_month)
        } to Icons.Default.DirectionsCar
        DrivesChartType.TIME -> when (granularity) {
            DriveChartGranularity.DAILY -> stringResource(R.string.chart_time_per_day)
            DriveChartGranularity.WEEKLY -> stringResource(R.string.chart_time_per_week)
            DriveChartGranularity.MONTHLY -> stringResource(R.string.chart_time_per_month)
        } to Icons.Default.Timer
        DrivesChartType.DISTANCE -> when (granularity) {
            DriveChartGranularity.DAILY -> stringResource(R.string.chart_distance_per_day)
            DriveChartGranularity.WEEKLY -> stringResource(R.string.chart_distance_per_week)
            DriveChartGranularity.MONTHLY -> stringResource(R.string.chart_distance_per_month)
        } to CustomIcons.SteeringWheel
        DrivesChartType.TOP_SPEED -> when (granularity) {
            DriveChartGranularity.DAILY -> stringResource(R.string.chart_speed_per_day)
            DriveChartGranularity.WEEKLY -> stringResource(R.string.chart_speed_per_week)
            DriveChartGranularity.MONTHLY -> stringResource(R.string.chart_speed_per_month)
        } to Icons.Default.Speed
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
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

        val barData = when (chartType) {
            DrivesChartType.COUNT -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.count.toDouble(),
                    displayValue = data.count.toString()
                )
            }
            DrivesChartType.TIME -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.totalDurationMin.toDouble(),
                    displayValue = formatDurationChart(data.totalDurationMin)
                )
            }
            DrivesChartType.DISTANCE -> chartData.map { data ->
                val distance = if (isImperial) data.totalDistance * 0.621371 else data.totalDistance
                BarChartData(
                    label = data.label,
                    value = distance,
                    displayValue = "%.1f $distanceUnit".format(distance)
                )
            }
            DrivesChartType.TOP_SPEED -> chartData.map { data ->
                val speed = if (isImperial) (data.maxSpeed * 0.621371).toInt() else data.maxSpeed
                BarChartData(
                    label = data.label,
                    value = speed.toDouble(),
                    displayValue = "$speed $speedUnit"
                )
            }
        }

        val valueFormatter: (Double) -> String = when (chartType) {
            DrivesChartType.COUNT -> { v -> v.toInt().toString() }
            DrivesChartType.TIME -> { v -> formatDurationChart(v.toInt()) }
            DrivesChartType.DISTANCE -> { v -> "%.1f $distanceUnit".format(v) }
            DrivesChartType.TOP_SPEED -> { v -> "${v.toInt()} $speedUnit" }
        }

        val yAxisFormatter: (Double) -> String = when (chartType) {
            DrivesChartType.TIME -> { v -> formatDurationChart(v.toInt()) }
            else -> { v -> if (v >= 1000) "%.0fk".format(v / 1000) else "%.0f".format(v) }
        }

        // Show max ~6 labels to avoid crowding
        val labelInterval = ((barData.size + 5) / 6).coerceAtLeast(1)

        InteractiveBarChart(
            data = barData,
            modifier = Modifier.fillMaxWidth(),
            barColor = palette.accent,
            labelColor = palette.onSurfaceVariant,
            showEveryNthLabel = labelInterval,
            valueFormatter = valueFormatter,
            yAxisFormatter = yAxisFormatter
        )
    }
}

private fun formatDurationChart(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "%d:%02d".format(hours, mins)
}
