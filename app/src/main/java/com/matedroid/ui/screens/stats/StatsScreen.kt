package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn as LogLazyColumn
import androidx.compose.foundation.lazy.items as logItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.matedroid.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.domain.model.CarStats
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.SyncPhase
import com.matedroid.domain.model.YearFilter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit = {},
    onNavigateToChargeDetail: (Int) -> Unit = {},
    onNavigateToDayDetail: (String) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)
    var showSyncLogsDialog by remember { mutableStateOf(false) }

    // State for range record dialog
    var rangeRecordToShow by remember { mutableStateOf<MaxDistanceBetweenChargesRecord?>(null) }
    var rangeRecordDrives by remember { mutableStateOf<List<DriveSummary>>(emptyList()) }
    var isLoadingRangeRecordDrives by remember { mutableStateOf(false) }

    // State for gap record dialog
    data class GapRecordInfo(val gapDays: Double, val fromDate: String, val toDate: String, val title: String)
    var gapRecordToShow by remember { mutableStateOf<GapRecordInfo?>(null) }

    // Load drives when range record dialog is opened
    LaunchedEffect(rangeRecordToShow) {
        rangeRecordToShow?.let { record ->
            isLoadingRangeRecordDrives = true
            rangeRecordDrives = viewModel.getDrivesForRangeRecord(record.fromDate, record.toDate)
            isLoadingRangeRecordDrives = false
        }
    }

    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
    }

    // Periodic sync every 60 seconds while the screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L) // Wait 60 seconds
            viewModel.triggerSync()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Debug sync logs dialog
    if (showSyncLogsDialog && BuildConfig.DEBUG) {
        SyncLogsDialog(
            logs = syncLogs,
            onDismiss = { showSyncLogsDialog = false }
        )
    }

    // Range record details dialog
    rangeRecordToShow?.let { record ->
        RangeRecordDialog(
            record = record,
            drives = rangeRecordDrives,
            isLoading = isLoadingRangeRecordDrives,
            palette = palette,
            onDriveClick = { driveId ->
                rangeRecordToShow = null
                onNavigateToDriveDetail(driveId)
            },
            onDismiss = { rangeRecordToShow = null }
        )
    }

    // Gap record details dialog
    gapRecordToShow?.let { gap ->
        GapRecordDialog(
            gapDays = gap.gapDays,
            fromDate = gap.fromDate,
            toDate = gap.toDate,
            title = gap.title,
            palette = palette,
            onDismiss = { gapRecordToShow = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats for Nerds") },
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
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.carStats == null) {
                EmptyState(
                    message = if (uiState.isSyncing) "Syncing data..." else "No stats available yet.\nPull down to sync.",
                    syncProgress = uiState.deepSyncProgress,
                    syncPhase = uiState.syncProgress?.phase,
                    isSyncing = uiState.isSyncing
                )
            } else {
                StatsContent(
                    stats = uiState.carStats!!,
                    availableYears = uiState.availableYears,
                    selectedYearFilter = uiState.selectedYearFilter,
                    deepSyncProgress = uiState.deepSyncProgress,
                    palette = palette,
                    currencySymbol = uiState.currencySymbol,
                    onYearFilterSelected = { viewModel.setYearFilter(it) },
                    onNavigateToDriveDetail = onNavigateToDriveDetail,
                    onNavigateToChargeDetail = onNavigateToChargeDetail,
                    onNavigateToDayDetail = onNavigateToDayDetail,
                    onRangeRecordClick = { rangeRecordToShow = it },
                    onGapRecordClick = { gapDays, fromDate, toDate, title ->
                        gapRecordToShow = GapRecordInfo(gapDays, fromDate, toDate, title)
                    },
                    onSyncProgressClick = if (BuildConfig.DEBUG) {
                        { showSyncLogsDialog = true }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    syncProgress: Float,
    syncPhase: SyncPhase? = null,
    isSyncing: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Show sync phase info
            if (isSyncing && syncPhase != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (syncPhase) {
                        SyncPhase.SYNCING_SUMMARIES -> "Fetching drives and charges..."
                        SyncPhase.SYNCING_DRIVE_DETAILS -> "Processing drive details..."
                        SyncPhase.SYNCING_CHARGE_DETAILS -> "Processing charge details..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Show progress bar if we have progress
            if (syncProgress > 0 || isSyncing) {
                Spacer(modifier = Modifier.height(16.dp))
                if (syncProgress > 0) {
                    LinearProgressIndicator(
                        progress = { syncProgress },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                    Text(
                        text = "${(syncProgress * 100).toInt()}% synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Indeterminate progress when syncing but no percentage yet
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsContent(
    stats: CarStats,
    availableYears: List<Int>,
    selectedYearFilter: YearFilter,
    deepSyncProgress: Float,
    palette: CarColorPalette,
    currencySymbol: String,
    onYearFilterSelected: (YearFilter) -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    onNavigateToDayDetail: (String) -> Unit,
    onRangeRecordClick: (MaxDistanceBetweenChargesRecord) -> Unit,
    onGapRecordClick: (Double, String, String, String) -> Unit,
    onSyncProgressClick: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Year filter chips
        item {
            YearFilterChips(
                availableYears = availableYears,
                selectedFilter = selectedYearFilter,
                palette = palette,
                onFilterSelected = onYearFilterSelected
            )
        }

        // Sync progress indicator if deep sync is ongoing
        if (deepSyncProgress < 1f && deepSyncProgress > 0f) {
            item {
                SyncProgressCard(
                    progress = deepSyncProgress,
                    palette = palette,
                    onClick = onSyncProgressClick
                )
            }
        }

        // Records (at the top)
        item {
            RecordsCard(
                quickStats = stats.quickStats,
                deepStats = stats.deepStats,
                palette = palette,
                currencySymbol = currencySymbol,
                onDriveClick = onNavigateToDriveDetail,
                onChargeClick = onNavigateToChargeDetail,
                onDayClick = onNavigateToDayDetail,
                onRangeRecordClick = onRangeRecordClick,
                onGapRecordClick = onGapRecordClick
            )
        }

        // Quick Stats - Drives Overview
        item {
            QuickStatsDrivesCard(quickStats = stats.quickStats, palette = palette)
        }

        // Quick Stats - Charges Overview
        item {
            QuickStatsChargesCard(quickStats = stats.quickStats, palette = palette, currencySymbol = currencySymbol)
        }

        // AC/DC Ratio (moved here, near charges)
        stats.deepStats?.let { deepStats ->
            item {
                AcDcRatioCard(deepStats = deepStats, palette = palette)
            }
        }

        // Deep Stats - only if available
        stats.deepStats?.let { deepStats ->
            // Temperature Stats
            item {
                TemperatureStatsCard(deepStats = deepStats, palette = palette)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearFilterChips(
    availableYears: List<Int>,
    selectedFilter: YearFilter,
    palette: CarColorPalette,
    onFilterSelected: (YearFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All Time option
        item {
            FilterChip(
                selected = selectedFilter is YearFilter.AllTime,
                onClick = { onFilterSelected(YearFilter.AllTime) },
                label = { Text("All Time") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }

        // Year options
        items(availableYears) { year ->
            FilterChip(
                selected = selectedFilter is YearFilter.Year && selectedFilter.year == year,
                onClick = { onFilterSelected(YearFilter.Year(year)) },
                label = { Text(year.toString()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

@Composable
private fun SyncProgressCard(
    progress: Float,
    palette: CarColorPalette,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Deep Stats Sync in Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(progress * 100).toInt()}% complete",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ======== Quick Stats Cards ========

@Composable
private fun QuickStatsDrivesCard(quickStats: QuickStats, palette: CarColorPalette) {
    StatsCard(
        title = "Drives Overview",
        icon = Icons.Default.DirectionsCar,
        palette = palette
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "Total Drives",
                value = quickStats.totalDrives.toString(),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Driving Days",
                value = quickStats.totalDrivingDays.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "Total Distance",
                value = "%.0f km".format(quickStats.totalDistanceKm),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Energy Used",
                value = formatEnergy(quickStats.totalEnergyConsumedKwh),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "Avg Efficiency",
                value = "%.0f Wh/km".format(quickStats.avgEfficiencyWhKm),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Top Speed",
                value = quickStats.maxSpeedKmh?.let { "$it km/h" } ?: "N/A",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickStatsChargesCard(quickStats: QuickStats, palette: CarColorPalette, currencySymbol: String) {
    StatsCard(
        title = "Charges Overview",
        icon = Icons.Default.BatteryChargingFull,
        palette = palette
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "Total Charges",
                value = quickStats.totalCharges.toString(),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Energy Added",
                value = formatEnergy(quickStats.totalEnergyAddedKwh),
                modifier = Modifier.weight(1f)
            )
        }
        if (quickStats.totalCost != null && quickStats.totalCost > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = "Total Cost",
                    value = "%.2f %s".format(quickStats.totalCost, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Avg Cost/kWh",
                    value = quickStats.avgCostPerKwh?.let { "%.3f %s".format(it, currencySymbol) } ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Data class for a single record item */
private data class RecordData(
    val emoji: String,
    val label: String,
    val value: String,
    val subtext: String,
    val onClick: (() -> Unit)?
)

/**
 * HARD CONSTRAINT: Each page displays exactly 6 record slots (3 rows × 2 columns).
 * If a category has more than 6 records, it MUST be split into multiple pages.
 * This ensures consistent page height and smooth swiping experience.
 */
private const val RECORDS_PER_PAGE = 6

/** A page of records to display in the pager */
private data class RecordPage(
    val categoryTitle: String,
    val categoryEmoji: String,
    val records: List<RecordData>, // Max RECORDS_PER_PAGE items
    val pageIndex: Int, // 0-based index within the category (for multi-page categories)
    val totalPagesInCategory: Int // Total pages for this category
)

@Composable
private fun RecordsCard(
    quickStats: QuickStats,
    deepStats: DeepStats?,
    palette: CarColorPalette,
    currencySymbol: String,
    onDriveClick: (Int) -> Unit,
    onChargeClick: (Int) -> Unit,
    onDayClick: (String) -> Unit,
    onRangeRecordClick: (MaxDistanceBetweenChargesRecord) -> Unit,
    onGapRecordClick: (Double, String, String, String) -> Unit // gapDays, fromDate, toDate, title
) {
    // Category 1: Drives
    val driveRecords = mutableListOf<RecordData>()
    quickStats.longestDrive?.let { drive ->
        driveRecords.add(RecordData("📏", "Longest Drive", "%.1f km".format(drive.distance), drive.startDate.take(10)) { onDriveClick(drive.driveId) })
    }
    quickStats.fastestDrive?.let { drive ->
        driveRecords.add(RecordData("🏎️", "Top Speed", "${drive.speedMax} km/h", drive.startDate.take(10)) { onDriveClick(drive.driveId) })
    }
    quickStats.mostEfficientDrive?.let { drive ->
        driveRecords.add(RecordData("🌱", "Most Efficient", "%.0f Wh/km".format(drive.efficiency ?: 0.0), drive.startDate.take(10)) { onDriveClick(drive.driveId) })
    }
    quickStats.longestDrivingStreak?.let { streak ->
        driveRecords.add(RecordData("🔥", "Longest Streak", "${streak.streakDays} days", "${streak.startDate} → ${streak.endDate}", null))
    }
    quickStats.busiestDay?.let { day ->
        driveRecords.add(RecordData("📅", "Busiest Day", "${day.count} drives", day.day) { onDayClick(day.day) })
    }

    // Category 2: Battery
    val batteryRecords = mutableListOf<RecordData>()
    quickStats.biggestBatteryGainCharge?.let { record ->
        batteryRecords.add(RecordData("🔋", "Biggest Gain", "+${record.percentChange}%", "${record.startLevel}% → ${record.endLevel}%") { onChargeClick(record.recordId) })
    }
    quickStats.biggestBatteryDrainDrive?.let { record ->
        batteryRecords.add(RecordData("📉", "Biggest Drain", "-${record.percentChange}%", "${record.startLevel}% → ${record.endLevel}%") { onDriveClick(record.recordId) })
    }
    quickStats.biggestCharge?.let { charge ->
        batteryRecords.add(RecordData("⚡", "Biggest Charge", "%.0f kWh".format(charge.energyAdded), charge.startDate.take(10)) { onChargeClick(charge.chargeId) })
    }
    deepStats?.chargeWithMaxPower?.let { record ->
        batteryRecords.add(RecordData("⚡", "Peak Power", "${record.powerKw} kW", record.date?.take(10) ?: "") { onChargeClick(record.chargeId) })
    }
    quickStats.mostExpensiveCharge?.let { charge ->
        charge.cost?.let { cost ->
            batteryRecords.add(RecordData("💸", "Most Expensive", "%.2f %s".format(cost, currencySymbol), charge.startDate.take(10)) { onChargeClick(charge.chargeId) })
        }
    }
    quickStats.mostExpensivePerKwhCharge?.let { charge ->
        charge.cost?.let { cost ->
            if (charge.energyAdded > 0) {
                batteryRecords.add(RecordData("📈", "Priciest/kWh", "%.3f %s".format(cost / charge.energyAdded, currencySymbol), charge.startDate.take(10)) { onChargeClick(charge.chargeId) })
            }
        }
    }

    // Category 3: Weather & Altitude
    val weatherRecords = mutableListOf<RecordData>()
    deepStats?.driveWithMaxElevation?.let { record ->
        weatherRecords.add(RecordData("🏔️", "Highest Point", "${record.elevationM} m", record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
    }
    deepStats?.driveWithMostClimbing?.let { record ->
        weatherRecords.add(RecordData("⛰️", "Most Climbing", record.elevationGainM?.let { "+$it m" } ?: "N/A", record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
    }
    deepStats?.hottestDrive?.let { record ->
        weatherRecords.add(RecordData("🌡️", "Hottest Drive", "%.1f°C".format(record.tempC), record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
    }
    deepStats?.coldestDrive?.let { record ->
        weatherRecords.add(RecordData("🧊", "Coldest Drive", "%.1f°C".format(record.tempC), record.date?.take(10) ?: "") { onDriveClick(record.driveId) })
    }
    deepStats?.hottestCharge?.let { record ->
        weatherRecords.add(RecordData("☀️", "Hottest Charge", "%.1f°C".format(record.tempC), record.date?.take(10) ?: "") { onChargeClick(record.chargeId) })
    }
    deepStats?.coldestCharge?.let { record ->
        weatherRecords.add(RecordData("❄️", "Coldest Charge", "%.1f°C".format(record.tempC), record.date?.take(10) ?: "") { onChargeClick(record.chargeId) })
    }

    // Category 4: Distances & Gaps
    val distanceRecords = mutableListOf<RecordData>()
    quickStats.maxDistanceBetweenCharges?.let { record ->
        distanceRecords.add(RecordData("🔋", "Longest Range", "%.1f km".format(record.distance), "${record.fromDate.take(10)} → ${record.toDate.take(10)}") { onRangeRecordClick(record) })
    }
    quickStats.longestGapWithoutCharging?.let { gap ->
        distanceRecords.add(RecordData("⏰", "Longest w/o Charging", "%.1f days".format(gap.gapDays), "${gap.fromDate.take(10)} → ${gap.toDate.take(10)}") { onGapRecordClick(gap.gapDays, gap.fromDate, gap.toDate, "Without Charging") })
    }
    quickStats.longestGapWithoutDriving?.let { gap ->
        distanceRecords.add(RecordData("🅿️", "Longest w/o Driving", "%.1f days".format(gap.gapDays), "${gap.fromDate.take(10)} → ${gap.toDate.take(10)}") { onGapRecordClick(gap.gapDays, gap.fromDate, gap.toDate, "Without Driving") })
    }
    quickStats.mostDistanceDay?.let { day ->
        distanceRecords.add(RecordData("🛣️", "Most Distance Day", "%.1f km".format(day.totalDistance), day.day) { onDayClick(day.day) })
    }

    // Build list of all categories with their records
    data class CategoryData(val title: String, val emoji: String, val records: List<RecordData>)
    val allCategories = mutableListOf<CategoryData>()
    if (driveRecords.isNotEmpty()) allCategories.add(CategoryData("Drives", "🚗", driveRecords))
    if (batteryRecords.isNotEmpty()) allCategories.add(CategoryData("Battery", "🔋", batteryRecords))
    if (weatherRecords.isNotEmpty()) allCategories.add(CategoryData("Weather & Altitude", "🌡️", weatherRecords))
    if (distanceRecords.isNotEmpty()) allCategories.add(CategoryData("Distances", "📍", distanceRecords))

    // Don't render anything if no categories
    if (allCategories.isEmpty()) return

    // Split categories into pages of max RECORDS_PER_PAGE records each
    val pages = mutableListOf<RecordPage>()
    allCategories.forEach { category ->
        val chunks = category.records.chunked(RECORDS_PER_PAGE)
        chunks.forEachIndexed { index, chunk ->
            pages.add(RecordPage(
                categoryTitle = category.title,
                categoryEmoji = category.emoji,
                records = chunk,
                pageIndex = index,
                totalPagesInCategory = chunks.size
            ))
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = CustomIcons.Trophy,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Records",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Pager with pages (fixed height for 6 records = 3 rows)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    RecordCategoryPage(
                        page = page,
                        palette = palette
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page indicators - group by category with sub-dots for multi-page categories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var pageOffset = 0
                    allCategories.forEach { category ->
                        val categoryPageCount = (category.records.size + RECORDS_PER_PAGE - 1) / RECORDS_PER_PAGE
                        val isCurrentCategory = pagerState.currentPage >= pageOffset &&
                                pagerState.currentPage < pageOffset + categoryPageCount
                        val currentPageInCategory = if (isCurrentCategory) pagerState.currentPage - pageOffset else -1

                        Row(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrentCategory) palette.accent.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.emoji,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isCurrentCategory) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.Bold
                                )
                                // Show page dots for multi-page categories
                                if (categoryPageCount > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    repeat(categoryPageCount) { dotIndex ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 2.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (dotIndex == currentPageInCategory) palette.accent
                                                    else palette.accent.copy(alpha = 0.3f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        pageOffset += categoryPageCount
                    }
                }
            }
        }
    }
}

/**
 * Fixed height for each record card row.
 * This ensures consistent page height regardless of content.
 */
private val RECORD_CARD_HEIGHT = 72.dp

/**
 * A single page showing records for one category.
 * HARD CONSTRAINT: Always renders exactly 3 rows (space for 6 records) to maintain fixed height.
 */
@Composable
private fun RecordCategoryPage(
    page: RecordPage,
    palette: CarColorPalette
) {
    // Pad records to exactly RECORDS_PER_PAGE (6) slots for consistent height
    val paddedRecords = page.records + List(RECORDS_PER_PAGE - page.records.size) { null }
    val rows = paddedRecords.chunked(2) // Always 3 rows of 2

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Records in 2-column grid - always 3 rows for fixed height
        // Note: Category title removed - the swipe indicator at the bottom shows current category
        rows.forEach { rowRecords ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RECORD_CARD_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowRecords.forEach { record ->
                    if (record != null) {
                        RecordCard(
                            emoji = record.emoji,
                            label = record.label,
                            value = record.value,
                            subtext = record.subtext,
                            palette = palette,
                            onClick = record.onClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        // Empty placeholder to maintain grid layout - same size as RecordCard
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

// ======== Deep Stats Cards ========

// Note: ElevationStatsCard removed - highest point now shown in Records section

@Composable
private fun TemperatureStatsCard(deepStats: DeepStats, palette: CarColorPalette) {
    if (deepStats.maxOutsideTempDrivingC == null && deepStats.minOutsideTempDrivingC == null) {
        return // No temperature data
    }

    StatsCard(
        title = "Temperature Extremes",
        icon = Icons.Default.Thermostat,
        palette = palette
    ) {
        Text(
            text = "While Driving",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "Hottest",
                value = deepStats.maxOutsideTempDrivingC?.let { "%.1f°C".format(it) } ?: "N/A",
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "Coldest",
                value = deepStats.minOutsideTempDrivingC?.let { "%.1f°C".format(it) } ?: "N/A",
                modifier = Modifier.weight(1f)
            )
        }

        if (deepStats.maxCabinTempC != null || deepStats.minCabinTempC != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cabin Temperature",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = "Hottest",
                    value = deepStats.maxCabinTempC?.let { "%.1f°C".format(it) } ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Coldest",
                    value = deepStats.minCabinTempC?.let { "%.1f°C".format(it) } ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatEnergy(kwh: Double): String {
    return if (kwh >= 1000) {
        "%.1f MWh".format(kwh / 1000)
    } else {
        "%.0f kWh".format(kwh)
    }
}

@Composable
private fun AcDcRatioCard(deepStats: DeepStats, palette: CarColorPalette) {
    val totalEnergy = deepStats.acChargeEnergyKwh + deepStats.dcChargeEnergyKwh
    if (totalEnergy <= 0) {
        return // No charge data
    }

    val acRatio = (deepStats.acChargeEnergyKwh / totalEnergy).toFloat()
    val acColor = Color(0xFF4CAF50) // Green
    val dcColor = Color(0xFFFFC107) // Yellow/Amber

    StatsCard(
        title = "AC/DC Charging Ratio",
        icon = Icons.Default.BatteryChargingFull,
        palette = palette
    ) {
        // Energy stats row
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = "AC Energy",
                value = formatEnergy(deepStats.acChargeEnergyKwh),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "DC Energy",
                value = formatEnergy(deepStats.dcChargeEnergyKwh),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom ratio bar (thicker, green/yellow)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(dcColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(acRatio)
                    .background(acColor)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Labels with counts below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "AC",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = acColor
                )
                Text(
                    text = "${deepStats.acChargeCount} charges",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "DC",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = dcColor
                )
                Text(
                    text = "${deepStats.dcChargeCount} charges",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
        }
    }
}

// ======== Reusable Components ========

@Composable
private fun StatsCard(
    title: String,
    icon: ImageVector,
    palette: CarColorPalette,
    content: @Composable () -> Unit
) {
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
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = palette.accent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordCard(
    emoji: String,
    label: String,
    value: String,
    subtext: String,
    palette: CarColorPalette,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                    maxLines = 1
                )
                if (subtext.isNotEmpty()) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    modifier = Modifier.size(18.dp),
                    tint = palette.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecordItem(
    emoji: String,
    label: String,
    value: String,
    subtext: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (subtext.isNotEmpty()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Debug-only dialog showing sync logs like adb logcat.
 */
@Composable
private fun SyncLogsDialog(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sync Logs")
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                LogLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true // Show newest logs at the bottom
                ) {
                    logItems(logs.reversed()) { log ->
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
                Text("Close")
            }
        }
    )
}

/**
 * Dialog showing details of a gap record (longest period without charging/driving).
 */
@Composable
private fun GapRecordDialog(
    gapDays: Double,
    fromDate: String,
    toDate: String,
    title: String,
    palette: CarColorPalette,
    onDismiss: () -> Unit
) {
    val emoji = if (title.contains("Charging")) "⏰" else "🅿️"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Longest $title")
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
                            text = "%.1f days".format(gapDays),
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
                                text = "Started",
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
                                text = "Ended",
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
                Text("Close")
            }
        }
    )
}

/**
 * Dialog showing details of a "longest range" record with scrollable list of drives.
 */
@Composable
private fun RangeRecordDialog(
    record: MaxDistanceBetweenChargesRecord,
    drives: List<DriveSummary>,
    isLoading: Boolean,
    palette: CarColorPalette,
    onDriveClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔋", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Longest Range")
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
                                text = "Total Distance",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onSurfaceVariant
                            )
                            Text(
                                text = "%.1f km".format(record.distance),
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
                                    text = "From",
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
                                    text = "To",
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
                    text = "Drives (${drives.size})",
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
                                text = "No drives found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(drives) { drive ->
                                DriveListItem(
                                    drive = drive,
                                    palette = palette,
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
                Text("Close")
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
                    text = "%.1f km".format(drive.distance),
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
                contentDescription = "View drive",
                modifier = Modifier.size(18.dp),
                tint = palette.onSurfaceVariant
            )
        }
    }
}
