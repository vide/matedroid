package com.matedroid.ui.screens.stats

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sync
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
import com.matedroid.domain.model.MaxDistanceBetweenChargesRecord
import com.matedroid.domain.model.SyncPhase
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
    onYearFilterSelected: (YearFilter) -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    onNavigateToDayDetail: (String) -> Unit,
    onNavigateToCountriesVisited: (Int?) -> Unit, // year (null for all time)
    onRangeRecordClick: (MaxDistanceBetweenChargesRecord) -> Unit,
    onGapRecordClick: (Double, String, String, String) -> Unit,
    onSyncProgressClick: (() -> Unit)? = null
) {
    val recordRows = rememberRecordRows(
        quickStats = stats.quickStats,
        deepStats = stats.deepStats,
        units = units,
        currencySymbol = currencySymbol,
        yearFilter = selectedYearFilter,
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
    val shelves = remember(recordRows) { recordRows.shelves() }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sync progress indicator if deep sync is actively running
            if (isSyncing && deepSyncProgress < 1f && deepSyncProgress > 0f) {
                item(key = "sync-progress") {
                    ProgressRow(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.stats_deep_sync_title),
                        progress = deepSyncProgress,
                        status = stringResource(R.string.stats_sync_complete, (deepSyncProgress * 100).toInt()),
                        palette = palette,
                        onClick = onSyncProgressClick
                    )
                }
            }

            // Geocode progress indicator if location identification is ongoing
            if (isGeocoding && geocodeProgress != null) {
                item(key = "geocode-progress") {
                    ProgressRow(
                        icon = Icons.Default.Place,
                        title = stringResource(R.string.geocode_progress_title),
                        progress = geocodeProgress.percentage,
                        status = stringResource(
                            R.string.geocode_progress_status,
                            geocodeProgress.processed,
                            geocodeProgress.total
                        ),
                        palette = palette,
                        onClick = onSyncProgressClick
                    )
                }
            }

            // The three headline records, side by side
            if (recordRows.any { it.headline != null }) {
                item(key = "headline-trophies") {
                    HeadlineTrophies(rows = recordRows, palette = palette)
                }
            }

            item(key = "plaque") {
                StatsPlaque(
                    quickStats = stats.quickStats,
                    deepStats = stats.deepStats,
                    palette = palette,
                    currencySymbol = currencySymbol,
                    units = units
                )
            }

            // Every other record, on its category shelf
            trophyShelves(shelves = shelves, palette = palette)
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

/**
 * Accent-tinted progress row (deep sync, geocoding): a circled icon, the title, a progress
 * bar and a status line. Tappable in debug builds to open the sync logs.
 */
@Composable
private fun ProgressRow(
    icon: ImageVector,
    title: String,
    progress: Float,
    status: String,
    palette: CarColorPalette,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = palette.accent,
                trackColor = palette.progressTrack
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
