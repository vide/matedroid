package com.matedroid.ui.screens.mileage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusSuccess
import java.time.LocalDate

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    // System back closes the innermost overlay level instead of popping the whole screen.
    BackHandler(enabled = uiState.selectedYear != null) {
        when {
            uiState.selectedDay != null -> viewModel.clearSelectedDay()
            uiState.selectedMonth != null -> viewModel.clearSelectedMonth()
            else -> viewModel.clearSelectedYear()
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
                    MateDroidLoadingPlaceholder(color = palette.accent)
                } else {
                    YearOverviewContent(
                        uiState = uiState,
                        chartData = remember(uiState.yearlyData) { viewModel.getYearlyChartData() },
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
                    chartData = remember(uiState.monthlyData) { viewModel.getMonthlyChartData() },
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
                    dailyChartData = remember(uiState.dailyData, uiState.selectedMonth) { viewModel.getDailyChartData() },
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
                MileageChartCard(
                    title = stringResource(R.string.mileage_by_year),
                    chartData = chartData,
                    palette = palette,
                    units = uiState.units
                )
            }
        }

        // Year list
        items(uiState.yearlyData, key = { it.year }, contentType = { "year" }) { yearData ->
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
private fun YearRow(
    yearData: YearlyMileage,
    units: Units?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val avgEfficiency = efficiencyWhPerUnit(yearData.totalEnergy, yearData.totalDistance)
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
                            text = UnitFormatter.formatCost(yearData.totalEnergyCost ?: 0.0, currencySymbol),
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
