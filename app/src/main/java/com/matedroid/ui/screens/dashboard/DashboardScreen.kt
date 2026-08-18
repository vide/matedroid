package com.matedroid.ui.screens.dashboard

import com.matedroid.BuildConfig
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import com.matedroid.domain.HighSocWarning
import com.matedroid.domain.LowSocWarning
import com.matedroid.domain.SinceLastChargeStats
import com.matedroid.domain.model.Trip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.local.CarImageOverride
import com.matedroid.ui.components.CarImagePickerDialog
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarGeodata
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.CarStatusDetails
import com.matedroid.data.api.models.Units
import com.matedroid.data.api.models.CarVersions
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.ClimateDetails
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    intent: Intent? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToCharges: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToDrives: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToBattery: (carId: Int, efficiency: Double?, exteriorColor: String?) -> Unit = { _, _, _ -> },
    onNavigateToMileage: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToUpdates: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToStats: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToCurrentCharge: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToWhereWasI: (carId: Int, timestamp: String, exteriorColor: String?) -> Unit = { _, _, _ -> },
    onNavigateToSentryHistory: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    onNavigateToTrips: (carId: Int, exteriorColor: String?) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showWhereWasIPicker by remember { mutableStateOf(false) }

    // Only poll for live car status while the dashboard is actually on screen.
    LifecycleStartEffect(Unit) {
        viewModel.resumeAutoRefresh()
        onStopOrDispose { viewModel.pauseAutoRefresh() }
    }

    // When opened from a widget tap, select the car that belongs to that widget.
    // Wait until the cars list is populated before switching, in case the app is
    // cold-starting and the ViewModel hasn't loaded cars yet.
    LaunchedEffect(intent) {
        val carIdFromIntent = intent?.getIntExtra("EXTRA_CAR_ID", -1)?.takeIf { it > 0 }
            ?: return@LaunchedEffect
        snapshotFlow { uiState.cars }
            .first { cars -> cars.any { it.carId == carIdFromIntent } }
        viewModel.selectCar(carIdFromIntent)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Refresh the trip count + latest-trip teaser on every resume so a trip created (or edited)
    // elsewhere shows up on return. refreshTripCount() no-ops until a car is selected, so the
    // initial load (driven by selectCar) isn't disturbed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshTripCount()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.selectedCarName ?: "MateDroid",
                        modifier = if (BuildConfig.DEBUG) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onDoubleClick = {
                                    uiState.selectedCarId?.let { carId ->
                                        onNavigateToSentryHistory(carId, uiState.selectedCarExterior?.exteriorColor)
                                    }
                                }
                            )
                        } else Modifier
                    )
                },
                actions = {
                    val carSelected = uiState.selectedCarId != null
                    // A touch of left inset and taller rows give the menu more breathing room.
                    val menuItemPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stats_title)) },
                            leadingIcon = { Icon(Icons.Filled.Insights, contentDescription = null) },
                            contentPadding = menuItemPadding,
                            enabled = carSelected,
                            onClick = {
                                menuExpanded = false
                                uiState.selectedCarId?.let { carId ->
                                    onNavigateToStats(carId, uiState.selectedCarExterior?.exteriorColor)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.battery_health_title)) },
                            leadingIcon = { Icon(Icons.Filled.BatteryFull, contentDescription = null) },
                            contentPadding = menuItemPadding,
                            enabled = carSelected,
                            onClick = {
                                menuExpanded = false
                                uiState.selectedCarId?.let { carId ->
                                    onNavigateToBattery(carId, uiState.selectedCarEfficiency, uiState.selectedCarExterior?.exteriorColor)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.where_was_i_screen_title)) },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            contentPadding = menuItemPadding,
                            enabled = carSelected,
                            onClick = {
                                menuExpanded = false
                                showWhereWasIPicker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_sentry_events)) },
                            leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null) },
                            contentPadding = menuItemPadding,
                            enabled = carSelected,
                            onClick = {
                                menuExpanded = false
                                uiState.selectedCarId?.let { carId ->
                                    onNavigateToSentryHistory(carId, uiState.selectedCarExterior?.exteriorColor)
                                }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            contentPadding = menuItemPadding,
                            onClick = {
                                menuExpanded = false
                                onNavigateToSettings()
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.carStatus != null -> {
                    DashboardContent(
                        status = uiState.carStatus!!,
                        units = uiState.units,
                        carModel = uiState.selectedCarModel,
                        carTrimBadging = uiState.selectedCarTrimBadging,
                        carExterior = uiState.selectedCarExterior,
                        resolvedAddress = uiState.resolvedAddress,
                        totalCharges = uiState.totalCharges,
                        totalDrives = uiState.totalDrives,
                        totalTrips = uiState.totalTrips,
                        latestTrip = uiState.latestTrip,
                        imageOverride = uiState.carImageOverride,
                        cars = uiState.cars,
                        selectedCarId = uiState.selectedCarId,
                        onSelectCar = { viewModel.selectCar(it) },
                        carImageOverrides = uiState.carImageOverrides,
                        isCurrentChargeAvailable = uiState.isCurrentChargeAvailable,
                        sentryEventCount = uiState.sentryEventCount,
                        dcFinishedPluggedIn = uiState.dcFinishedPluggedIn,
                        sinceLastCharge = uiState.sinceLastCharge,
                        highSocWarningThreshold = uiState.highSocWarningThreshold,
                        lowSocWarningThreshold = uiState.lowSocWarningThreshold,
                        onNavigateToCharges = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToCharges(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToDrives = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToDrives(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToBattery = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToBattery(carId, uiState.selectedCarEfficiency, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToMileage = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToMileage(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToUpdates = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToUpdates(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToStats = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToStats(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToCurrentCharge = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToCurrentCharge(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onSaveCarImageOverride = { override ->
                            viewModel.saveCarImageOverride(override)
                        },
                        onNavigateToSentryHistory = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToSentryHistory(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        },
                        onNavigateToTrips = {
                            uiState.selectedCarId?.let { carId ->
                                onNavigateToTrips(carId, uiState.selectedCarExterior?.exteriorColor)
                            }
                        }
                    )
                }
                uiState.cars.isNotEmpty() -> {
                    // Cars loaded but the selected car's status hasn't (still loading, or it
                    // errored — e.g. a car no longer in the account). Keep the selector
                    // reachable so the user can switch to a working car instead of being
                    // stuck on a dead-end loading/error screen (issue #272).
                    CarUnavailableContent(
                        cars = uiState.cars,
                        selectedCarId = uiState.selectedCarId,
                        carImageOverrides = uiState.carImageOverrides,
                        error = uiState.error,
                        onSelectCar = { viewModel.selectCar(it) },
                        onRetry = { viewModel.retryCarStatus() }
                    )
                }
                uiState.error != null -> {
                    // The car list itself failed to load — full-screen error.
                    ErrorContent(
                        message = uiState.error!!,
                        details = uiState.errorDetails
                    )
                }
                else -> {
                    EmptyContent()
                }
            }
        }
    }

    // "Where was I?" date/time picker — entered from the top-right menu.
    if (showWhereWasIPicker) {
        WhereWasIDateTimePicker(
            onDismiss = { showWhereWasIPicker = false },
            onConfirm = { timestamp ->
                showWhereWasIPicker = false
                uiState.selectedCarId?.let { carId ->
                    onNavigateToWhereWasI(carId, timestamp, uiState.selectedCarExterior?.exteriorColor)
                }
            }
        )
    }
}

@Composable
private fun LoadingContent() {
    MateDroidLoadingPlaceholder()
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_vehicles_found),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.no_vehicles_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    details: String? = null
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.error_loading_data),
                style = MaterialTheme.typography.titleMedium,
                color = StatusError,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (details != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { showDetailsDialog = true }) {
                    Text(stringResource(R.string.error_show_details))
                }
            }
        }
    }

    if (showDetailsDialog && details != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(stringResource(R.string.error_details_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

/**
 * Shown when the cars list loaded but the selected car's status could not be loaded
 * (either still loading, or it errored — e.g. a car removed from the Tesla account
 * that TeslaMate still lists, returning "no info on this car ID"). Keeps the car
 * selector reachable so the user can switch to a working car instead of being stuck
 * (issue #272).
 */
@Composable
private fun CarUnavailableContent(
    cars: List<CarData>,
    selectedCarId: Int?,
    carImageOverrides: Map<Int, CarImageOverride>,
    error: String?,
    onSelectCar: (Int) -> Unit,
    onRetry: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val selectedCar = cars.firstOrNull { it.carId == selectedCarId }
    val palette = CarColorPalettes.forExteriorColor(selectedCar?.carExterior?.exteriorColor, isDarkTheme)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CarSelectorPager(
            cars = cars,
            selectedCarId = selectedCarId,
            onSelectCar = onSelectCar,
            carImageOverrides = carImageOverrides,
            palette = palette,
            isCharging = false,
            isDcCharging = false,
            onNavigateToStats = null,
            onCarImageLongPress = null,
            carModel = selectedCar?.carDetails?.model,
            carTrimBadging = selectedCar?.carDetails?.trimBadging,
            carExterior = selectedCar?.carExterior,
            imageOverride = selectedCarId?.let { carImageOverrides[it] }
        )

        if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_car_unavailable),
                        style = MaterialTheme.typography.titleMedium,
                        color = StatusError,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            // Status still loading for the selected car.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.accent)
            }
        }
    }
}

@Composable
private fun DashboardContent(
    status: CarStatus,
    units: Units? = null,
    carModel: String? = null,
    carTrimBadging: String? = null,
    carExterior: CarExterior? = null,
    resolvedAddress: String? = null,
    totalCharges: Int? = null,
    totalDrives: Int? = null,
    totalTrips: Int? = null,
    latestTrip: Trip? = null,
    imageOverride: CarImageOverride? = null,
    cars: List<CarData> = emptyList(),
    selectedCarId: Int? = null,
    onSelectCar: (Int) -> Unit = {},
    carImageOverrides: Map<Int, CarImageOverride> = emptyMap(),
    isCurrentChargeAvailable: Boolean = false,
    sentryEventCount: Int = 0,
    dcFinishedPluggedIn: Boolean = false,
    sinceLastCharge: SinceLastChargeStats? = null,
    highSocWarningThreshold: Int = HighSocWarning.DEFAULT_THRESHOLD,
    lowSocWarningThreshold: Int = LowSocWarning.DEFAULT_THRESHOLD,
    onNavigateToCharges: () -> Unit = {},
    onNavigateToDrives: () -> Unit = {},
    onNavigateToBattery: () -> Unit = {},
    onNavigateToMileage: () -> Unit = {},
    onNavigateToUpdates: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToCurrentCharge: () -> Unit = {},
    onSaveCarImageOverride: (CarImageOverride?) -> Unit = {},
    onNavigateToSentryHistory: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(carExterior?.exteriorColor, isDarkTheme)

    // State for showing the car image picker dialog
    var showCarImagePicker by remember { mutableStateOf(false) }

    // Car image picker dialog
    if (showCarImagePicker) {
        CarImagePickerDialog(
            model = carModel,
            exteriorColor = carExterior?.exteriorColor,
            wheelType = carExterior?.wheelType,
            trimBadging = carTrimBadging,
            currentOverride = imageOverride,
            onDismiss = { showCarImagePicker = false },
            onConfirm = { override ->
                onSaveCarImageOverride(override)
            },
            onReset = {
                onSaveCarImageOverride(null)
            }
        )
    }

    // Scrollable content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Section with Car Image (tappable for battery health)
        BatteryCard(
            status = status,
            units = units,
            carModel = carModel,
            carTrimBadging = carTrimBadging,
            carExterior = carExterior,
            imageOverride = imageOverride,
            cars = cars,
            selectedCarId = selectedCarId,
            onSelectCar = onSelectCar,
            carImageOverrides = carImageOverrides,
            isCurrentChargeAvailable = isCurrentChargeAvailable,
            sentryEventCount = sentryEventCount,
            highSocWarningThreshold = highSocWarningThreshold,
            lowSocWarningThreshold = lowSocWarningThreshold,
            onNavigateToBattery = onNavigateToBattery,
            onNavigateToStats = onNavigateToStats,
            onNavigateToCurrentCharge = onNavigateToCurrentCharge,
            onCarImageLongPress = { showCarImagePicker = true },
            onNavigateToSentryHistory = onNavigateToSentryHistory
        )

        // DC charge finished but still plugged in warning
        if (dcFinishedPluggedIn) {
            DcUnplugWarningBanner(dcFinishedSince = status.stateSince)
        }

        // Swipeable slot: current position map + "Since last charge" (issue #339).
        // Renders nothing when neither page has data.
        DashboardCarousel(
            status = status,
            units = units,
            resolvedAddress = resolvedAddress,
            sinceLastCharge = sinceLastCharge,
            palette = palette,
            onNavigateToDrives = onNavigateToDrives
        )

        // Activity card — Trips hero + counters bento
        VehicleInfoCard(
            status = status,
            units = units,
            palette = palette,
            totalCharges = totalCharges,
            totalDrives = totalDrives,
            totalTrips = totalTrips,
            latestTrip = latestTrip,
            onNavigateToCharges = onNavigateToCharges,
            onNavigateToDrives = onNavigateToDrives,
            onNavigateToMileage = onNavigateToMileage,
            onNavigateToUpdates = onNavigateToUpdates,
            onNavigateToTrips = onNavigateToTrips
        )

        // Tyre pressure — its own card, shown when TPMS data is available
        val tpms = status.tpmsDetails
        if (tpms != null && (tpms.pressureFl != null || tpms.pressureFr != null)) {
            TirePressureCard(tpms = tpms, units = units, palette = palette)
        }
    }
}

@Composable
private fun DcUnplugWarningBanner(dcFinishedSince: String?) {
    val startEpochMs = remember(dcFinishedSince) {
        if (dcFinishedSince == null) return@remember null
        try {
            val odt = try {
                java.time.OffsetDateTime.parse(dcFinishedSince)
            } catch (_: java.time.format.DateTimeParseException) {
                java.time.OffsetDateTime.parse(dcFinishedSince.replace("Z", "+00:00"))
            }
            odt.toInstant().toEpochMilli()
        } catch (_: Exception) { null }
    }
    val elapsedMs = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(startEpochMs) {
        if (startEpochMs == null) return@LaunchedEffect
        while (true) {
            elapsedMs.longValue = System.currentTimeMillis() - startEpochMs
            kotlinx.coroutines.delay(1000L)
        }
    }
    val elapsedText = if (startEpochMs != null) {
        val totalSeconds = elapsedMs.longValue / 1000
        val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatusWarning)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dc_unplug_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.dc_unplug_warning_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            if (elapsedText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = elapsedText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    MateDroidTheme {
        DashboardContent(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                odometer = 45678.0,
                carStatus = CarStatusDetails(locked = true),
                carGeodata = CarGeodata(geofence = "Home"),
                carVersions = CarVersions(version = "2024.8.7"),
                climateDetails = ClimateDetails(
                    isClimateOn = false,
                    insideTemp = 21.5,
                    outsideTemp = 15.2
                ),
                batteryDetails = BatteryDetails(
                    batteryLevel = 72,
                    ratedBatteryRange = 312.5
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 11,
                    chargerPhases = 3,  // AC charging
                    chargerVoltage = 230,
                    chargerActualCurrent = 16,
                    chargeCurrentRequestMax = 32,
                    chargeEnergyAdded = 15.3,
                    timeToFullCharge = 1.5,
                    chargeLimitSoc = 80
                )
            ),
            carTrimBadging = "74D"
        )
    }
}
