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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.matedroid.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.repository.GeocodeProgressInfo
import com.matedroid.domain.model.CarStats
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.SyncPhase
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.domain.model.YearFilter
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
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
    onNavigateToCountriesVisited: (Int?) -> Unit = {}, // year (null for all time)
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)
    var showSyncLogsDialog by remember { mutableStateOf(false) }

    // State for Records pager - remember across filter changes
    var recordsSelectedCategory by rememberSaveable { mutableStateOf("") }

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

    // Periodic sync every 60 seconds, only while the screen is actually visible
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(60_000L) // Wait 60 seconds
                viewModel.triggerSync()
            }
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
            units = uiState.units,
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
                title = { Text(stringResource(R.string.stats_title)) },
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
            if (uiState.isLoading) {
                MateDroidLoadingPlaceholder(color = palette.accent)
            } else if (uiState.carStats == null) {
                val emptyMessage = if (uiState.isSyncing) {
                    stringResource(R.string.stats_syncing)
                } else {
                    stringResource(R.string.stats_empty)
                }
                EmptyState(
                    message = emptyMessage,
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
                    isSyncing = uiState.isSyncing,
                    isUpdating = uiState.isUpdating,
                    geocodeProgress = uiState.geocodeProgress,
                    isGeocoding = uiState.isGeocoding,
                    palette = palette,
                    currencySymbol = uiState.currencySymbol,
                    units = uiState.units,
                    recordsSelectedCategory = recordsSelectedCategory,
                    onRecordsCategoryChanged = { recordsSelectedCategory = it },
                    onYearFilterSelected = { viewModel.setYearFilter(it) },
                    onNavigateToDriveDetail = onNavigateToDriveDetail,
                    onNavigateToChargeDetail = onNavigateToChargeDetail,
                    onNavigateToDayDetail = onNavigateToDayDetail,
                    onNavigateToCountriesVisited = onNavigateToCountriesVisited,
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
                val phaseText = when (syncPhase) {
                    SyncPhase.SYNCING_SUMMARIES -> stringResource(R.string.sync_phase_summaries)
                    SyncPhase.SYNCING_DRIVE_DETAILS -> stringResource(R.string.sync_phase_drives)
                    SyncPhase.SYNCING_CHARGE_DETAILS -> stringResource(R.string.sync_phase_charges)
                    else -> ""
                }
                if (phaseText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        text = stringResource(R.string.stats_sync_percent, (syncProgress * 100).toInt()),
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
    isSyncing: Boolean,
    isUpdating: Boolean,
    geocodeProgress: GeocodeProgressInfo?,
    isGeocoding: Boolean,
    palette: CarColorPalette,
    currencySymbol: String,
    units: Units?,
    recordsSelectedCategory: String,
    onRecordsCategoryChanged: (String) -> Unit,
    onYearFilterSelected: (YearFilter) -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    onNavigateToDayDetail: (String) -> Unit,
    onNavigateToCountriesVisited: (Int?) -> Unit, // year (null for all time)
    onRangeRecordClick: (MaxDistanceBetweenChargesRecord) -> Unit,
    onGapRecordClick: (Double, String, String, String) -> Unit,
    onSyncProgressClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Year filter chips — pinned above the scrollable content
        YearFilterChips(
            availableYears = availableYears,
            selectedFilter = selectedYearFilter,
            palette = palette,
            onFilterSelected = onYearFilterSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = palette.onSurface.copy(alpha = 0.08f))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sync progress indicator if deep sync is actively running
            if (isSyncing && deepSyncProgress < 1f && deepSyncProgress > 0f) {
                item {
                    SyncProgressCard(
                        progress = deepSyncProgress,
                        palette = palette,
                        onClick = onSyncProgressClick
                    )
                }
            }

            // Geocode progress indicator if location identification is ongoing
            if (isGeocoding && geocodeProgress != null) {
                item {
                    GeocodeProgressCard(
                        progress = geocodeProgress,
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
                    units = units,
                    selectedCategory = recordsSelectedCategory,
                    onCategoryChanged = onRecordsCategoryChanged,
                    onDriveClick = onNavigateToDriveDetail,
                    onChargeClick = onNavigateToChargeDetail,
                    onDayClick = onNavigateToDayDetail,
                    onCountriesVisitedClick = {
                        val year = (selectedYearFilter as? YearFilter.Year)?.year
                        onNavigateToCountriesVisited(year)
                    },
                    onRangeRecordClick = onRangeRecordClick,
                    onGapRecordClick = onGapRecordClick
                )
            }

            // Quick Stats - Drives Overview
            item {
                QuickStatsDrivesCard(
                    quickStats = stats.quickStats,
                    palette = palette,
                    currencySymbol = currencySymbol,
                    units = units
                )
            }

            // Quick Stats - Charges Overview
            item {
                QuickStatsChargesCard(
                    quickStats = stats.quickStats,
                    palette = palette,
                    currencySymbol = currencySymbol
                )
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
                    TemperatureStatsCard(deepStats = deepStats, palette = palette, units = units)
                }
            }
        }
        // Progress indicator overlay at the top of the scrollable area
        if (isUpdating) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                color = palette.accent
            )
        }
        } // end Box (scrollable area)
    } // end Column
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearFilterChips(
    availableYears: List<Int>,
    selectedFilter: YearFilter,
    palette: CarColorPalette,
    onFilterSelected: (YearFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All Time option
        item {
            FilterChip(
                selected = selectedFilter is YearFilter.AllTime,
                onClick = { onFilterSelected(YearFilter.AllTime) },
                label = { Text(stringResource(R.string.filter_all_time)) },
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
                    text = stringResource(R.string.stats_deep_sync_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.stats_sync_complete, (progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun GeocodeProgressCard(
    progress: GeocodeProgressInfo,
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
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.geocode_progress_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.percentage },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.geocode_progress_status, progress.processed, progress.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

// ======== Quick Stats Cards ========

@Composable
private fun QuickStatsDrivesCard(quickStats: QuickStats, palette: CarColorPalette, currencySymbol: String, units: Units?) {
    // Calculate cost per 100 km: (totalCost / totalDistanceKm) * 100
    val costPer100Km = if (quickStats.totalCost != null && quickStats.totalCost > 0 && quickStats.totalDistanceKm > 0) {
        (quickStats.totalCost / quickStats.totalDistanceKm) * 100
    } else {
        null
    }

    StatsCard(
        title = stringResource(R.string.stats_drives_overview),
        icon = Icons.Default.DirectionsCar,
        palette = palette
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = stringResource(R.string.stats_total_drives),
                value = "%,d".format(quickStats.totalDrives),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = stringResource(R.string.stats_driving_days),
                value = quickStats.totalDrivingDays?.let { "%,d".format(it) } ?: "-",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = stringResource(R.string.total_distance),
                value = UnitFormatter.formatDistance(quickStats.totalDistanceKm, units, 0),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = stringResource(R.string.stats_energy_used),
                value = UnitFormatter.formatEnergy(quickStats.totalEnergyConsumedKwh),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = stringResource(R.string.stats_avg_efficiency),
                value = UnitFormatter.formatEfficiency(quickStats.avgEfficiencyWhKm, units, 0),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = stringResource(R.string.stats_cost_per_distance, UnitFormatter.getDistanceUnit(units)),
                value = costPer100Km?.let { UnitFormatter.formatCost(it, currencySymbol) } ?: stringResource(R.string.value_not_available),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickStatsChargesCard(quickStats: QuickStats, palette: CarColorPalette, currencySymbol: String) {
    StatsCard(
        title = stringResource(R.string.stats_charges_overview),
        icon = Icons.Default.BatteryChargingFull,
        palette = palette
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = stringResource(R.string.stats_total_charges),
                value = "%,d".format(quickStats.totalCharges),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = stringResource(R.string.energy_added),
                value = UnitFormatter.formatEnergy(quickStats.totalEnergyAddedKwh),
                modifier = Modifier.weight(1f)
            )
        }
        if (quickStats.totalCost != null && quickStats.totalCost > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = stringResource(R.string.total_cost),
                    value = UnitFormatter.formatCost(quickStats.totalCost, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.stats_avg_cost_kwh),
                    value = quickStats.avgCostPerKwh?.let { UnitFormatter.formatCost(it, currencySymbol, perKwh = true) } ?: stringResource(R.string.value_not_available),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ======== Deep Stats Cards ========

// Note: ElevationStatsCard removed - highest point now shown in Records section

@Composable
private fun TemperatureStatsCard(deepStats: DeepStats, palette: CarColorPalette, units: Units?) {
    if (deepStats.maxOutsideTempDrivingC == null && deepStats.minOutsideTempDrivingC == null) {
        return // No temperature data
    }

    StatsCard(
        title = stringResource(R.string.stats_temperature_extremes),
        icon = Icons.Default.Thermostat,
        palette = palette
    ) {
        Text(
            text = stringResource(R.string.stats_while_driving),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = stringResource(R.string.stats_hottest),
                value = deepStats.maxOutsideTempDrivingC?.let { UnitFormatter.formatTemperature(it, units, 1) } ?: stringResource(R.string.value_not_available),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = stringResource(R.string.stats_coldest),
                value = deepStats.minOutsideTempDrivingC?.let { UnitFormatter.formatTemperature(it, units, 1) } ?: stringResource(R.string.value_not_available),
                modifier = Modifier.weight(1f)
            )
        }

        if (deepStats.maxCabinTempC != null || deepStats.minCabinTempC != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.stats_cabin_temperature),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = stringResource(R.string.stats_hottest),
                    value = deepStats.maxCabinTempC?.let { UnitFormatter.formatTemperature(it, units, 1) } ?: stringResource(R.string.value_not_available),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.stats_coldest),
                    value = deepStats.minCabinTempC?.let { UnitFormatter.formatTemperature(it, units, 1) } ?: stringResource(R.string.value_not_available),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AcDcRatioCard(deepStats: DeepStats, palette: CarColorPalette) {
    val totalEnergy = deepStats.acChargeEnergyKwh + deepStats.dcChargeEnergyKwh
    if (totalEnergy <= 0) {
        return // No charge data
    }

    val acRatio = (deepStats.acChargeEnergyKwh / totalEnergy).toFloat()
    val acColor = palette.acColor
    val dcColor = palette.dcColor

    StatsCard(
        title = stringResource(R.string.stats_ac_dc_ratio),
        icon = Icons.Default.BatteryChargingFull,
        palette = palette
    ) {
        // Energy stats row
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                label = stringResource(R.string.stats_ac_energy),
                value = UnitFormatter.formatEnergy(deepStats.acChargeEnergyKwh),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = stringResource(R.string.stats_dc_energy),
                value = UnitFormatter.formatEnergy(deepStats.dcChargeEnergyKwh),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom ratio bar with percentage labels
        val acPercent = (acRatio * 100).toInt()
        val dcPercent = 100 - acPercent
        // Only show percentage if segment is wide enough (>= 15%)
        val showAcPercent = acPercent >= 15
        val showDcPercent = dcPercent >= 15

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(dcColor)
        ) {
            // AC portion (green)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(acRatio)
                    .background(acColor),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showAcPercent) {
                    Text(
                        text = "$acPercent%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = dcColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            // DC percentage label (positioned at the end)
            if (showDcPercent) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "$dcPercent%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = acColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Labels with counts below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.charging_ac),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = acColor
                )
                Text(
                    text = stringResource(R.string.format_charges_count, deepStats.acChargeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.charging_dc),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = dcColor
                )
                Text(
                    text = stringResource(R.string.format_charges_count, deepStats.dcChargeCount),
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
