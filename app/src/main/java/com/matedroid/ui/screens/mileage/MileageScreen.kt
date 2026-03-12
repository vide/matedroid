package com.matedroid.ui.screens.mileage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Calculate
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.format.DateTimeFormatter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.DriveData
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.screens.drives.DriveDetailStats
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusSuccess
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ChartBlue = Color(0xFF42A5F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageScreen(
    carId: Int,
    exteriorColor: String? = null,
    targetDay: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit = {},
    viewModel: MileageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    // Auto-navigate to target day if provided
    LaunchedEffect(targetDay, uiState.isLoading) {
        if (targetDay != null && !uiState.isLoading && uiState.allDrives.isNotEmpty()) {
            try {
                val date = LocalDate.parse(targetDay)
                viewModel.navigateToDay(date)
            } catch (e: Exception) {
                // Invalid date format, ignore
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Level 1: Year Overview (main screen)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.mileage_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    YearOverviewContent(
                        uiState = uiState,
                        chartData = viewModel.getYearlyChartData(),
                        palette = palette,
                        onYearClick = { viewModel.selectYear(it) }
                    )
                }
            }
        }

        // Level 2: Year Detail overlay
        AnimatedVisibility(
            visible = uiState.selectedYear != null && uiState.selectedMonth == null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            uiState.selectedYear?.let { year ->
                YearDetailScreen(
                    year = year,
                    uiState = uiState,
                    chartData = viewModel.getMonthlyChartData(),
                    palette = palette,
                    onClose = { viewModel.clearSelectedYear() },
                    onMonthClick = { viewModel.selectMonth(it) }
                )
            }
        }

        // Level 3: Month Detail overlay
        AnimatedVisibility(
            visible = uiState.selectedMonth != null && uiState.selectedDay == null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            uiState.selectedMonth?.let { month ->
                val monthData = uiState.monthlyData.find { it.yearMonth == month }
                MonthDetailScreen(
                    yearMonth = month,
                    monthData = monthData,
                    dailyData = uiState.dailyData,
                    dailyChartData = viewModel.getDailyChartData(),
                    currencySymbol = uiState.currencySymbol,
                    units = uiState.units,
                    palette = palette,
                    onClose = { viewModel.clearSelectedMonth() },
                    onDayClick = { viewModel.selectDay(it) }
                )
            }
        }

        // Level 4: Day Detail overlay
        AnimatedVisibility(
            visible = uiState.selectedDay != null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            uiState.selectedDayData?.let { dayData ->
                DayDetailScreen(
                    dayData = dayData,
                    currencySymbol = uiState.currencySymbol,
                    units = uiState.units,
                    palette = palette,
                    onClose = { viewModel.clearSelectedDay() },
                    onDriveClick = onNavigateToDriveDetail
                )
            }
        }
    }
}

// ============================================================================
// Level 1: Year Overview
// ============================================================================

@Composable
private fun YearOverviewContent(
    uiState: MileageUiState,
    chartData: List<Pair<Int, Double>>,
    palette: CarColorPalette,
    onYearClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Lifetime summary stats
        item {
            SummaryRow(
                totalDistance = uiState.totalLifetimeDistance,
                avgDistance = uiState.avgYearlyDistance,
                avgLabel = stringResource(R.string.mileage_avg_year),
                driveCount = uiState.totalLifetimeDriveCount,
                totalEnergyUsed = uiState.totalLifetimeEnergy,
                totalEnergyCost = uiState.totalLifetimeEnergyCost,
                avgEnergyDistance = uiState.avgLifetimeEnergyDistance,
                currencySymbol = uiState.currencySymbol,
                units = uiState.units,
                palette = palette,
                firstDriveDate = uiState.firstDriveDate
            )
        }

        // Yearly chart
        if (chartData.isNotEmpty()) {
            item {
                YearlyChartCard(chartData = chartData, palette = palette, units = uiState.units)
            }
        }

        // Year list
        items(uiState.yearlyData) { yearData ->
            YearRow(
                yearData = yearData,
                units = uiState.units,
                currencySymbol = uiState.currencySymbol,
                onClick = { onYearClick(yearData.year) }
            )
        }

        // Empty state
        if (uiState.yearlyData.isEmpty() && !uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.mileage_no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun YearlyChartCard(chartData: List<Pair<Int, Double>>, palette: CarColorPalette, units: Units?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CustomIcons.Road,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.mileage_by_year),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val barChartData = chartData.map { (year, distance) ->
                BarChartData(
                    label = year.toString(),
                    value = distance,
                    displayValue = UnitFormatter.formatDistance(distance, units)
                )
            }

            InteractiveBarChart(
                data = barChartData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                valueFormatter = { UnitFormatter.formatDistance(it, units) },
                yAxisFormatter = { if (it >= 1000) "%.0fk".format(it / 1000) else "%.0f".format(it) }
            )
        }
    }
}

@Composable
private fun YearRow(
    yearData: YearlyMileage,
    units: Units?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val avgEfficiency = if (yearData.totalDistance > 0)
        (yearData.totalEnergy * 1000.0) / yearData.totalDistance else 0.0
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
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = yearData.year.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f),horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CustomIcons.Road,
                            contentDescription = null,
                            tint = ChartBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = UnitFormatter.formatDistance(yearData.totalDistance, units, 0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = UnitFormatter.formatEfficiency(avgEfficiency, units),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f),horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%,d".format(yearData.driveCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%,.2f %s".format(yearData.totalEnergyCost ?: 0.0, currencySymbol),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.view_details),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// Level 2: Year Detail
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearDetailScreen(
    year: Int,
    uiState: MileageUiState,
    chartData: List<Pair<Int, Double>>,
    palette: CarColorPalette,
    onClose: () -> Unit,
    onMonthClick: (YearMonth) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(year.toString()) },
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
            // Year summary stats
            item {
                SummaryRow(
                    totalDistance = uiState.yearTotalDistance,
                    avgDistance = uiState.avgMonthlyDistance,
                    avgLabel = stringResource(R.string.mileage_avg_month),
                    driveCount = uiState.yearDriveCount,
                    totalEnergyUsed = uiState.yearTotalEnergy,
                    totalEnergyCost = uiState.yearTotalEnergyCost,
                    avgEnergyDistance = uiState.avgYearEnergyDistance,
                    currencySymbol = uiState.currencySymbol,
                    units = uiState.units,
                    palette = palette
                )
            }

            // Monthly chart
            item {
                MonthlyChartCard(chartData = chartData, palette = palette, units = uiState.units)
            }

            // Monthly list
            items(uiState.monthlyData) { monthData ->
                MonthRow(
                    monthData = monthData,
                    units = uiState.units,
                    currencySymbol = uiState.currencySymbol,
                    onClick = { onMonthClick(monthData.yearMonth) }
                )
            }

            // Empty state
            if (uiState.monthlyData.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.mileage_no_data_year, year),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyChartCard(chartData: List<Pair<Int, Double>>, palette: CarColorPalette, units: Units?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CustomIcons.Road,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.mileage_by_month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val barChartData = chartData.map { (month, distance) ->
                BarChartData(
                    label = month.toString(),
                    value = distance,
                    displayValue = UnitFormatter.formatDistance(distance, units)
                )
            }

            InteractiveBarChart(
                data = barChartData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                valueFormatter = { UnitFormatter.formatDistance(it, units) },
                yAxisFormatter = { if (it >= 1000) "%.0fk".format(it / 1000) else "%.0f".format(it) }
            )
        }
    }
}

@Composable
private fun MonthRow(
    monthData: MonthlyMileage,
    units: Units?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val avgEfficiency = if (monthData.totalDistance > 0)
        (monthData.totalEnergy * 1000.0) / monthData.totalDistance else 0.0
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = monthData.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = monthData.yearMonth.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End) {
                        Icon(
                            imageVector = CustomIcons.Road,
                            contentDescription = null,
                            tint = ChartBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = UnitFormatter.formatDistance(monthData.totalDistance, units, 0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End) {
                        Icon(
                            imageVector = Icons.Outlined.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = UnitFormatter.formatEfficiency(avgEfficiency, units),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%,d".format(monthData.driveCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%,.2f %s".format(monthData.totalEnergyCost ?: 0.0, currencySymbol),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ============================================================================
// Level 3: Month Detail
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDetailScreen(
    yearMonth: YearMonth,
    monthData: MonthlyMileage?,
    dailyData: List<DailyMileage>,
    dailyChartData: List<Pair<Int, Double>>,
    currencySymbol: String,
    units: Units? = null,
    palette: CarColorPalette,
    onClose: () -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                },
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
            // Month summary card
            item {
                MonthSummaryCard(
                    yearMonth = yearMonth,
                    monthData = monthData,
                    currencySymbol = currencySymbol,
                    units = units,
                    palette = palette
                )
            }

            // Daily chart
            if (dailyChartData.isNotEmpty()) {
                item {
                    DailyChartCard(
                        chartData = dailyChartData,
                        daysWithData = dailyData.size,
                        palette = palette,
                        units = units
                    )
                }
            }

            // Recent trips header
            if (dailyData.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.mileage_recent_trips),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Daily trip rows
                items(dailyData) { dayData ->
                    DayTripRow(
                        dayData = dayData,
                        units = units,
                        currencySymbol = currencySymbol,
                        onClick = { onDayClick(dayData.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    yearMonth: YearMonth,
    monthData: MonthlyMileage?,
    palette: CarColorPalette,
    currencySymbol: String,
    units: Units? = null
) {
    val totalDistance = monthData?.totalDistance ?: 0.0
    val driveCount = monthData?.driveCount ?: 0
    val avgDistance = if (driveCount > 0) totalDistance / driveCount else 0.0
    val totalBatteryUsage = monthData?.totalBatteryUsage ?: 0.0
    val totalEnergy = monthData?.totalEnergy ?: 0.0
    val avgEnergy = if (driveCount > 0) totalEnergy / driveCount else 0.0
    val avgEfficiency = if (totalDistance > 0) (totalEnergy * 1000.0) / totalDistance else 0.0

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
                        text = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = yearMonth.year.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onSurfaceVariant
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
                        text = "%,d".format(driveCount),
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
                    value = UnitFormatter.formatDistance(totalDistance, units),
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
                    value = "%.0f%%".format(totalBatteryUsage),
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
                    value = formatEnergy(totalEnergy),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    icon = Icons.Filled.AttachMoney,
                    value = "%,.2f %s".format(monthData?.totalEnergyCost ?: 0.0, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ============================================================================
// Shared Components
// ============================================================================

@Composable
private fun SummaryRow(
    totalDistance: Double,
    avgDistance: Double,
    avgLabel: String,
    driveCount: Int,
    totalEnergyUsed: Double,
    totalEnergyCost: Double?,
    avgEnergyDistance: Double,
    currencySymbol: String,
    units: Units? = null,
    palette: CarColorPalette? = null,
    firstDriveDate: LocalDate? = null
) {
    val containerColor = palette?.surface ?: MaterialTheme.colorScheme.surfaceVariant
    val iconColor = palette?.accent ?: ChartBlue
    val valueColor = palette?.onSurface ?: MaterialTheme.colorScheme.onSurface
    val labelColor = palette?.onSurfaceVariant ?: MaterialTheme.colorScheme.onSurfaceVariant

    var showAvgInfoDialog by remember { mutableStateOf(false) }

    // Pre-compute localized strings for dialog
    val avgYearTitle = stringResource(R.string.mileage_avg_year_title)
    val okText = stringResource(R.string.ok)

    // Info dialog explaining the avg/year calculation
    if (showAvgInfoDialog && firstDriveDate != null) {
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val formattedDate = firstDriveDate.format(dateFormatter)
        val daysSinceFirst = ChronoUnit.DAYS.between(firstDriveDate, LocalDate.now()).toInt()
        val dialogMessage = stringResource(R.string.mileage_avg_year_message, formattedDate, daysSinceFirst)

        AlertDialog(
            onDismissRequest = { showAvgInfoDialog = false },
            title = { Text(avgYearTitle) },
            text = {
                Text(dialogMessage)
            },
            confirmButton = {
                TextButton(onClick = { showAvgInfoDialog = false }) {
                    Text(okText)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                icon = Icons.Outlined.AllInclusive,
                value = UnitFormatter.formatDistance(totalDistance, units, 0),
                label = stringResource(R.string.mileage_total),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
            SummaryItemWithInfo(
                icon = Icons.Filled.Speed,
                value = UnitFormatter.formatDistance(avgDistance, units, 0),
                label = avgLabel,
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor,
                showInfoIcon = firstDriveDate != null,
                onInfoClick = { showAvgInfoDialog = true }
            )
            SummaryItem(
                icon = Icons.Filled.DirectionsCar,
                value = "%,d".format(driveCount),
                label = stringResource(R.string.mileage_drive_count),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                icon = Icons.Outlined.EnergySavingsLeaf,
                value = UnitFormatter.formatEfficiency(avgEnergyDistance, units),
                label = stringResource(R.string.stats_avg_efficiency),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
            SummaryItem(
                icon = Icons.Filled.AttachMoney,
                value = "%,.2f %s".format(totalEnergyCost ?: 0.0, currencySymbol),
                label = stringResource(R.string.mileage_total),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
            SummaryItem(
                icon = Icons.Outlined.BatteryChargingFull,
                value = formatEnergy(totalEnergyUsed),
                label = stringResource(R.string.mileage_total),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color = ChartBlue,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

@Composable
private fun SummaryItemWithInfo(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color = ChartBlue,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showInfoIcon: Boolean = false,
    onInfoClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (showInfoIcon) Modifier.clickable { onInfoClick() } else Modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            if (showInfoIcon) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.info_about_calculation),
                    tint = labelColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    modifier: Modifier = Modifier,
    prefix: String? = null,
    icon: ImageVector? = null,
    iconText: String? = null,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ChartBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (iconText != null) {
                Text(
                    text = iconText,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DailyChartCard(
    chartData: List<Pair<Int, Double>>,
    daysWithData: Int,
    palette: CarColorPalette,
    units: Units?
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CustomIcons.Road,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.mileage_by_day),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.format_days_count, daysWithData),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val barChartData = chartData.map { (day, distance) ->
                BarChartData(
                    label = day.toString(),
                    value = distance,
                    displayValue = UnitFormatter.formatDistance(distance, units)
                )
            }

            InteractiveBarChart(
                data = barChartData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                valueFormatter = { UnitFormatter.formatDistance(it, units) },
                yAxisFormatter = { if (it >= 1000) "%.0fk".format(it / 1000) else "%.0f".format(it) }
            )
        }
    }
}

@Composable
private fun DayTripRow(
    dayData: DailyMileage,
    units: Units?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val dayOfWeek = dayData.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val dateStr = "%d %s".format(
        dayData.date.dayOfMonth,
        dayData.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    )
    val avgEfficiency = if (dayData.totalDistance > 0)
        (dayData.totalEnergy * 1000.0) / dayData.totalDistance else 0.0
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day info
            Column(modifier = Modifier.width(60.dp)) {
                Text(
                    text = dayOfWeek,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
            // Stats
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            text = UnitFormatter.formatDistance(dayData.totalDistance, units),
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = UnitFormatter.formatEfficiency(avgEfficiency, units),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Drive count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = dayData.driveCount.toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Energy cost
                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Icon(
//                            imageVector = Icons.Filled.AttachMoney,
//                            contentDescription = null,
//                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
//                            modifier = Modifier.size(14.dp)
//                        )
//                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%,.2f %s".format(dayData.totalEnergyCost ?: 0.0, currencySymbol),
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
                            text = "%.1f kWh".format(dayData.totalEnergy),
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
                            text = "%.0f%%".format(dayData.totalBatteryUsage),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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

private fun formatEnergy(kwh: Double): String =
    if (kwh >= 1000) "%,.1f MWh".format(kwh / 1000) else "%.0f kWh".format(kwh)

// ============================================================================
// Level 4: Day Detail
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailScreen(
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
                items(dayData.drives) { drive ->
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
    val avgEfficiency = if (dayData.totalDistance > 0) (dayData.totalEnergy * 1000.0) / dayData.totalDistance else 0.0

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
                    value = formatEnergy(dayData.totalEnergy),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    icon = Icons.Filled.AttachMoney,
                    value = "%,.2f %s".format(dayData.totalEnergyCost ?: 0.0, currencySymbol),
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
    val startTime = drive.startDate?.let { parseTime(it) } ?: ""
    val endTime = drive.endDate?.let { parseTime(it) } ?: ""
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
                        text = "${duration}m",
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

private fun parseTime(dateStr: String): String {
    return try {
        val dateTime = OffsetDateTime.parse(dateStr)
        "%02d:%02d".format(dateTime.hour, dateTime.minute)
    } catch (e: Exception) {
        try {
            val dateTime = LocalDateTime.parse(dateStr.replace("Z", ""))
            "%02d:%02d".format(dateTime.hour, dateTime.minute)
        } catch (e2: Exception) {
            ""
        }
    }
}
