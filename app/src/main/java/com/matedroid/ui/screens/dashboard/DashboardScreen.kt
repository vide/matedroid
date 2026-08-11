package com.matedroid.ui.screens.dashboard

import com.matedroid.BuildConfig
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.matedroid.ui.util.GlowBitmapRenderer
import android.net.Uri
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thermostat
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.domain.model.Trip
import com.matedroid.ui.screens.trips.displayName
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.local.CarImageOverride
import com.matedroid.ui.components.CarImagePickerDialog
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.util.formatDuration
import com.matedroid.util.formatShortNoYear
import com.matedroid.util.formatTime
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarGeodata
import com.matedroid.data.api.models.CarStatus
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.data.api.models.CarStatusDetails
import com.matedroid.data.api.models.Units
import com.matedroid.data.api.models.CarVersions
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.TpmsDetails
import com.matedroid.data.api.models.ClimateDetails
import com.matedroid.domain.model.BatteryTypeHelper
import com.matedroid.ui.components.calculateAcGaugeProgress
import com.matedroid.ui.components.calculateDcGaugeProgress
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning
import kotlin.math.roundToInt

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

/**
 * Two-step date → time picker for the "Where was I?" feature. Picks a past date, then a
 * time of day, and emits an OffsetDateTime timestamp string in the device time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhereWasIDateTimePicker(
    onDismiss: () -> Unit,
    onConfirm: (timestamp: String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val zoneId = java.time.ZoneId.systemDefault()
                val todayUtcDateMillis = java.time.LocalDate.now(zoneId)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
                return utcTimeMillis <= todayUtcDateMillis
            }
        }
    )
    val timePickerState = rememberTimePickerState()

    if (!showTimePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTimePicker = true }) {
                    Text(stringResource(R.string.where_was_i_go))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val selectedDate = java.time.Instant.ofEpochMilli(selectedMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()

                    val localDateTime = selectedDate.atTime(timePickerState.hour, timePickerState.minute)
                    val zonedDateTime = localDateTime.atZone(java.time.ZoneId.systemDefault())
                    onConfirm(zonedDateTime.toOffsetDateTime().toString())
                }) {
                    Text(stringResource(R.string.where_was_i_go))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = {
                val selectedMillis = datePickerState.selectedDateMillis
                val dateText = if (selectedMillis != null) {
                    val date = java.time.Instant.ofEpochMilli(selectedMillis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                    date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
                } else ""
                Text(dateText)
            },
            text = {
                TimePicker(state = timePickerState)
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
private fun CarSelectorPager(
    cars: List<CarData>,
    selectedCarId: Int?,
    onSelectCar: (Int) -> Unit,
    carImageOverrides: Map<Int, CarImageOverride>,
    palette: CarColorPalette,
    isCharging: Boolean,
    isDcCharging: Boolean,
    onNavigateToStats: (() -> Unit)?,
    onCarImageLongPress: (() -> Unit)?,
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    imageOverride: CarImageOverride?
) {
    val isDarkTheme = isSystemInDarkTheme()
    if (cars.size > 1) {
        val initialPage = cars.indexOfFirst { it.carId == selectedCarId }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = initialPage) { cars.size }

        // Sync pager position when selected car changes externally
        LaunchedEffect(selectedCarId) {
            val targetPage = cars.indexOfFirst { it.carId == selectedCarId }.coerceAtLeast(0)
            if (targetPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }

        // Notify viewmodel when user swipes to a new car
        LaunchedEffect(pagerState.settledPage) {
            val car = cars.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            if (car.carId != selectedCarId) {
                onSelectCar(car.carId)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val car = cars[page]
            val isSettled = page == pagerState.settledPage
            val carPalette = CarColorPalettes.forExteriorColor(car.carExterior?.exteriorColor, isDarkTheme)
            CarImage(
                carModel = car.carDetails?.model,
                carTrimBadging = car.carDetails?.trimBadging,
                carExterior = car.carExterior,
                palette = carPalette,
                modifier = Modifier.fillMaxWidth(),
                isCharging = if (isSettled) isCharging else false,
                isDcCharging = if (isSettled) isDcCharging else false,
                accentColor = carPalette.accent,
                carSurfaceColor = carPalette.surface,
                imageOverride = carImageOverrides[car.carId],
                onNavigateToStats = if (isSettled) onNavigateToStats else null,
                onLongPress = if (isSettled) onCarImageLongPress else null
            )
        }

        // Dots indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(cars.size) { index ->
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
    } else {
        // Single car — existing behaviour unchanged
        CarImage(
            carModel = carModel,
            carTrimBadging = carTrimBadging,
            carExterior = carExterior,
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            isCharging = isCharging,
            isDcCharging = isDcCharging,
            accentColor = palette.accent,
            carSurfaceColor = palette.surface,
            imageOverride = imageOverride,
            onNavigateToStats = onNavigateToStats,
            onLongPress = onCarImageLongPress
        )
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

        // Location Section - show if we have coordinates
        if (status.latitude != null && status.longitude != null) {
            LocationCard(
                status = status,
                units = units,
                resolvedAddress = resolvedAddress,
                palette = palette
            )
        }

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

// createGlowBitmap moved to GlowBitmapRenderer for reuse by the home screen widget
private fun createGlowBitmap(source: Bitmap, glowColor: Color, glowRadius: Float): Bitmap =
    GlowBitmapRenderer.createGlowBitmap(source, glowColor, glowRadius)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarImage(
    carModel: String?,
    carTrimBadging: String?,
    carExterior: CarExterior?,
    palette: CarColorPalette,
    modifier: Modifier = Modifier,
    isCharging: Boolean = false,
    isDcCharging: Boolean = false,
    accentColor: Color = Color.Transparent,
    carSurfaceColor: Color = Color.Transparent,
    imageOverride: CarImageOverride? = null,
    onNavigateToStats: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Use override if set and valid for current car config, otherwise auto-detect
    val colorCode = remember(carExterior) { CarImageResolver.mapColor(carExterior?.exteriorColor) }
    val isOverrideValid = remember(carModel, colorCode, carTrimBadging, carExterior, imageOverride) {
        if (imageOverride == null) false
        else CarImageResolver.getVariantsForModel(
            carModel, colorCode, carTrimBadging, carExterior?.wheelType
        ).any { it.id == imageOverride.variant }
    }

    val assetPath = remember(carModel, carTrimBadging, carExterior, imageOverride, isOverrideValid) {
        if (imageOverride != null && isOverrideValid) {
            CarImageResolver.getAssetPathForOverride(
                variant = imageOverride.variant,
                colorCode = colorCode,
                wheelCode = imageOverride.wheelCode
            )
        } else {
            CarImageResolver.getAssetPath(
                model = carModel,
                exteriorColor = carExterior?.exteriorColor,
                wheelType = carExterior?.wheelType,
                trimBadging = carTrimBadging
            )
        }
    }

    val scaleFactor = remember(carModel, carTrimBadging, carExterior, imageOverride, isOverrideValid) {
        if (imageOverride != null && isOverrideValid) {
            CarImageResolver.getScaleFactorForVariant(imageOverride.variant)
        } else {
            CarImageResolver.getScaleFactor(
                model = carModel,
                exteriorColor = carExterior?.exteriorColor,
                wheelType = carExterior?.wheelType,
                trimBadging = carTrimBadging
            )
        }
    }

    val bitmap = remember(assetPath) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            // Try fallback to default
            try {
                val fallbackPath = CarImageResolver.getDefaultAssetPath(carModel)
                context.assets.open(fallbackPath).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e2: Exception) {
                null
            }
        }
    }

    // Glow radius in pixels
    val glowRadius = 70f

    // AC/DC color tint
    val chargeTypeColor = if (isDcCharging) palette.dcColor else palette.acColor

    // Breathing animation - smooth in/out
    val infiniteTransition = rememberInfiniteTransition(label = "chargingBreath")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathProgress"
    )

    // Breathing glow: alpha pulses between 0.3 and 0.9
    val glowAlpha = 0.3f + (breathProgress * 0.6f)
    // Color subtly shifts between accent and a blend with AC/DC color
    val glowColor = androidx.compose.ui.graphics.lerp(accentColor, chargeTypeColor, breathProgress * 0.4f)

    // Create single glow bitmap
    val glowBitmap = remember(bitmap, isCharging) {
        if (isCharging && bitmap != null) {
            createGlowBitmap(
                source = bitmap,
                glowColor = Color.White,
                glowRadius = glowRadius
            )
        } else {
            null
        }
    }

    // Calculate scale compensation for glow (glow bitmap is larger due to padding)
    val glowScaleCompensation = remember(bitmap, glowBitmap) {
        if (bitmap != null && glowBitmap != null) {
            glowBitmap.width.toFloat() / bitmap.width.toFloat()
        } else {
            1f
        }
    }

    if (bitmap != null) {
        Box(
            modifier = modifier
                .height(210.dp)
                .then(
                    if (onNavigateToStats != null || onLongPress != null) {
                        Modifier.combinedClickable(
                            onClick = { onNavigateToStats?.invoke() },
                            onLongClick = { onLongPress?.invoke() }
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Draw breathing glow behind the car when charging
            if (glowBitmap != null && isCharging) {
                Image(
                    bitmap = glowBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleFactor * glowScaleCompensation
                            scaleY = scaleFactor * glowScaleCompensation
                            alpha = glowAlpha
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(glowColor, BlendMode.SrcIn)
                )
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.car_image_tap_for_stats),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                    },
                contentScale = ContentScale.Fit
            )
            // Stats button on middle-right side
            if (onNavigateToStats != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.view_stats),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * An icon with a tooltip that appears on tap
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusIcon(
    icon: ImageVector,
    tooltipText: String,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Int = 18
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        },
        state = tooltipState
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltipText,
            modifier = modifier
                .size(iconSize.dp)
                .clickable { scope.launch { tooltipState.show() } },
            tint = tint
        )
    }
}

/**
 * Formats duration since a given ISO timestamp as "XXm" or "XXh YYm"
 */
private fun formatDurationSince(isoTimestamp: String?, resources: android.content.res.Resources): String? {
    if (isoTimestamp == null) return null
    return try {
        val instant = java.time.OffsetDateTime.parse(isoTimestamp).toInstant()
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        val totalMinutes = duration.toMinutes()
        if (totalMinutes < 0) return null
        formatDuration(resources, totalMinutes.toInt())
    } catch (e: Exception) {
        null
    }
}

/**
 * Formats an ISO timestamp to a human-readable format:
 * - Today: "HH:mm"
 * - Yesterday: "yesterday HH:mm"
 * - Older: "DD/MM HH:mm"
 */
private fun formatTimeFromTimestamp(isoTimestamp: String?, yesterdayStr: String, is24Hour: Boolean): String? {
    if (isoTimestamp == null) return null
    return try {
        val dateTime = java.time.OffsetDateTime.parse(isoTimestamp)
        val localDateTime = dateTime.toLocalDateTime()
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)
        val locale = java.util.Locale.getDefault()
        val timeStr = localDateTime.formatTime(locale, is24Hour)

        when (localDateTime.toLocalDate()) {
            today -> timeStr
            yesterday -> "$yesterdayStr $timeStr"
            else -> "${localDateTime.toLocalDate().formatShortNoYear(locale)} $timeStr"
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusIndicatorsRow(
    status: CarStatus,
    units: Units?,
    palette: CarColorPalette,
    sentryEventCount: Int = 0,
    onNavigateToSentryHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSentryModeActive = status.sentryMode == true
    val isClimateOn = status.isClimateOn == true
    val isOnline = status.state?.lowercase() == "online"
    val isCharging = status.state?.lowercase() == "charging"
    val isDriving = status.state?.lowercase() == "driving"
    val isAwake = isOnline || isCharging || isDriving
    val isAsleep = status.state?.lowercase() in listOf("asleep", "suspended")
    val isOffline = status.state?.lowercase() == "offline"
    val isLocked = status.locked == true

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Status icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // State icon - bedtime when asleep, power icon otherwise
                val yesterdayStr = stringResource(R.string.yesterday)
                val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
                val chargingStr = stringResource(R.string.charging)
                val onlineStr = stringResource(R.string.online)
                val drivingStr = stringResource(R.string.driving)
                val stateTooltip = when {
                    isAsleep -> {
                        val sleepTime = formatTimeFromTimestamp(status.stateSince, yesterdayStr, is24Hour)
                        if (sleepTime != null) {
                            stringResource(R.string.asleep_since, sleepTime)
                        } else {
                            status.state?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.unknown)
                        }
                    }
                    isOffline -> {
                        val offlineTime = formatTimeFromTimestamp(status.stateSince, yesterdayStr, is24Hour)
                        if (offlineTime != null) {
                            stringResource(R.string.offline_since, offlineTime)
                        } else {
                            status.state.replaceFirstChar { it.uppercase() }
                        }
                    }
                    isCharging -> chargingStr
                    isDriving -> drivingStr
                    isOnline -> onlineStr
                    else -> status.state?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.unknown)
                }
                StatusIcon(
                    icon = when {
                        isAsleep -> Icons.Filled.Bedtime
                        isDriving -> CustomIcons.SteeringWheel
                        isCharging -> Icons.Filled.ElectricBolt
                        else -> Icons.Filled.PowerSettingsNew
                    },
                    tooltipText = stateTooltip,
                    tint = if (isAwake) StatusSuccess else palette.onSurfaceVariant
                )

                // Lock icon - grey when locked, light red when unlocked
                StatusIcon(
                    icon = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    tooltipText = stringResource(if (isLocked) R.string.locked else R.string.unlocked),
                    tint = if (isLocked) palette.onSurfaceVariant else StatusError.copy(alpha = 0.7f)
                )

                // Sentry mode red dot (if active) + event count — tapping opens history
                if (isSentryModeActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.clickable { onNavigateToSentryHistory() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .border(2.dp, palette.onSurfaceVariant.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusError, CircleShape)
                            )
                        }
                        if (sentryEventCount > 0) {
                            Text(
                                text = "$sentryEventCount",
                                color = StatusError,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Plug icon (grey, if plugged in)
                if (status.pluggedIn == true) {
                    StatusIcon(
                        icon = Icons.Filled.Power,
                        tooltipText = stringResource(R.string.plugged_in),
                        tint = palette.onSurfaceVariant
                    )
                }
            }

            // Right side: Temperature indicators with labels
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val climateTooltip = stringResource(if (isClimateOn) R.string.climate_active else R.string.climate_inactive)
                val scope = rememberCoroutineScope()

                // Outside temp: "Ext:"
                val extTooltipState = rememberTooltipState(isPersistent = true)
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(climateTooltip) } },
                    state = extTooltipState
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { scope.launch { extTooltipState.show() } }
                    ) {
                        Text(
                            text = stringResource(R.string.temp_ext_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.Thermostat,
                            contentDescription = stringResource(R.string.outside_temp),
                            modifier = Modifier.size(14.dp),
                            tint = palette.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = status.outsideTemp?.let { UnitFormatter.formatTemperature(it, units) } ?: "--",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onSurfaceVariant
                        )
                    }
                }

                // Inside temp: "Int:" (bold and green if climate is on)
                val intTooltipState = rememberTooltipState(isPersistent = true)
                val intColor = if (isClimateOn) StatusSuccess else palette.onSurfaceVariant
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(climateTooltip) } },
                    state = intTooltipState
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { scope.launch { intTooltipState.show() } }
                    ) {
                        Text(
                            text = stringResource(R.string.temp_int_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isClimateOn) FontWeight.Bold else FontWeight.Normal,
                            color = intColor
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.Thermostat,
                            contentDescription = stringResource(R.string.inside_temp),
                            modifier = Modifier.size(14.dp),
                            tint = intColor
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = status.insideTemp?.let { UnitFormatter.formatTemperature(it, units) } ?: "--",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isClimateOn) FontWeight.Bold else FontWeight.Normal,
                            color = intColor
                        )
                    }
                }
            }
        }

        // Show duration for all states
        val stateDuration = formatDurationSince(status.stateSince, LocalContext.current.resources)
        if (stateDuration != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stateDuration,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryCard(
    status: CarStatus,
    units: Units?,
    carModel: String? = null,
    carTrimBadging: String? = null,
    carExterior: CarExterior? = null,
    imageOverride: CarImageOverride? = null,
    cars: List<CarData> = emptyList(),
    selectedCarId: Int? = null,
    onSelectCar: (Int) -> Unit = {},
    carImageOverrides: Map<Int, CarImageOverride> = emptyMap(),
    isCurrentChargeAvailable: Boolean = false,
    sentryEventCount: Int = 0,
    onNavigateToBattery: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToCurrentCharge: () -> Unit = {},
    onCarImageLongPress: () -> Unit = {},
    onNavigateToSentryHistory: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(carExterior?.exteriorColor, isDarkTheme)

    val batteryLevel = status.batteryLevel ?: 0
    val batteryColor = when {
        batteryLevel < 20 -> StatusError
        batteryLevel < 40 -> StatusWarning
        else -> palette.onSurface
    }
    val chargeLimit = status.chargeLimitSoc ?: 100
    var showHighSocDialog by remember { mutableStateOf(false) }

    if (showHighSocDialog) {
        AlertDialog(
            onDismissRequest = { showHighSocDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.high_soc_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.high_soc_warning_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showHighSocDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
        ) {
            // Status indicators row at the top
            StatusIndicatorsRow(
                status = status,
                units = units,
                palette = palette,
                sentryEventCount = sentryEventCount,
                onNavigateToSentryHistory = onNavigateToSentryHistory,
                modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
            )

            // Car image — pager when multiple cars, single image otherwise
            CarSelectorPager(
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                carImageOverrides = carImageOverrides,
                palette = palette,
                isCharging = status.isCharging,
                isDcCharging = status.isDcCharging,
                onNavigateToStats = onNavigateToStats,
                onCarImageLongPress = onCarImageLongPress,
                carModel = carModel,
                carTrimBadging = carTrimBadging,
                carExterior = carExterior,
                imageOverride = imageOverride
            )

            // Battery info row - tappable to navigate to battery health
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.onSurface.copy(alpha = 0.06f))
                    .clickable(onClick = onNavigateToBattery)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Battery percentage with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatteryChargingFull,
                        contentDescription = stringResource(R.string.tap_for_battery_health),
                        modifier = Modifier.size(28.dp),
                        tint = batteryColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                    if (status.isCharging) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Mini charging gauge with AC/DC badge - tappable to open live charge if API available
                        Box(modifier = if (isCurrentChargeAvailable) Modifier.clickable(onClick = onNavigateToCurrentCharge) else Modifier) {
                            ChargingPowerGaugeCompact(
                                status = status,
                                carTrimBadging = carTrimBadging,
                                isTappable = isCurrentChargeAvailable,
                                palette = palette
                            )
                        }
                    }
                    if (batteryLevel > 90 && !status.isCharging) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.high_charge_level),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { showHighSocDialog = true },
                            tint = StatusWarning
                        )
                    }
                }

                // Center: Range and limit
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = status.ratedBatteryRangeKm?.let { UnitFormatter.formatDistance(it, units, 0) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface
                    )
                    Text(
                        text = status.chargeLimitSoc?.let { stringResource(R.string.charge_limit_format, it) }
                            ?: stringResource(R.string.charge_limit_unknown),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }

                // Right: Chevron to indicate tappable
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = palette.onSurfaceVariant
                )
            }

            // Charging section - always reserve space for consistent card height
            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar - always shown but different appearance when not charging
            ChargingProgressBar(
                currentLevel = batteryLevel,
                targetLevel = chargeLimit,
                isCharging = status.isCharging,
                isDcCharging = status.isDcCharging,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Charging info row - shows details when charging, tappable to open live charge if API available
            if (status.isCharging) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isCurrentChargeAvailable) Modifier.clickable(onClick = onNavigateToCurrentCharge) else Modifier)
                ) {
                    ChargingDetailsRow(
                        status = status,
                        palette = palette
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargingProgressBar(
    currentLevel: Int,
    targetLevel: Int,
    isCharging: Boolean = false,
    isDcCharging: Boolean = false,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val currentFraction = currentLevel / 100f
    val targetFraction = targetLevel / 100f
    // Use AC/DC color when charging, StatusSuccess as fallback
    val chargeColor = if (isCharging) {
        if (isDcCharging) palette.dcColor else palette.acColor
    } else {
        StatusSuccess  // Fallback (not used in practice)
    }
    val dimmedChargeColor = chargeColor.copy(alpha = 0.3f)

    Canvas(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        val width = size.width
        val height = size.height

        // Background
        drawRect(
            color = palette.progressTrack,
            size = size
        )

        if (isCharging) {
            // Charging: show AC/DC color with target area
            // Dimmed color for target area (from current to target)
            if (targetFraction > currentFraction) {
                drawRect(
                    color = dimmedChargeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(width * currentFraction, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width * (targetFraction - currentFraction),
                        height
                    )
                )
            }
            // Solid AC/DC color for current charge level
            drawRect(
                color = chargeColor,
                size = androidx.compose.ui.geometry.Size(width * currentFraction, height)
            )
        } else {
            // Not charging: show accent color with limit marker
            // Dimmed accent for limit area
            if (targetFraction > currentFraction) {
                drawRect(
                    color = palette.accentDim,
                    topLeft = androidx.compose.ui.geometry.Offset(width * currentFraction, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width * (targetFraction - currentFraction),
                        height
                    )
                )
            }
            // Solid accent for current charge level
            drawRect(
                color = palette.accent,
                size = androidx.compose.ui.geometry.Size(width * currentFraction, height)
            )
        }
    }
}

/**
 * Compact inline gauge with AC/DC badge for the battery info row.
 */
@Composable
private fun ChargingPowerGaugeCompact(
    status: CarStatus,
    carTrimBadging: String?,
    isTappable: Boolean = false,
    palette: CarColorPalette
) {
    val isDcCharging = status.isDcCharging
    val powerKw = status.chargerPower ?: 0
    val gaugeColor = if (isDcCharging) palette.dcColor else palette.acColor

    // Calculate gauge progress based on charging type
    val gaugeProgress = if (isDcCharging) {
        val maxPower = BatteryTypeHelper.getMaxDcPowerKw(carTrimBadging)
        calculateDcGaugeProgress(powerKw, maxPower)
    } else {
        calculateAcGaugeProgress(
            actualCurrent = status.chargerActualCurrent,
            maxCurrent = status.chargeCurrentRequestMax
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Mini circular gauge with power value
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(36.dp)) {
                val strokeWidth = 3.dp.toPx()
                val arcSize = size.minDimension - strokeWidth
                val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
                val startAngle = 135f
                val sweepAngle = 270f

                // Track
                drawArc(
                    color = gaugeColor.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress
                val progressSweep = sweepAngle * gaugeProgress.coerceIn(0f, 1f)
                if (progressSweep > 0) {
                    drawArc(
                        color = gaugeColor,
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Power value and kW label stacked in center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$powerKw",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor,
                    lineHeight = 10.sp
                )
                Text(
                    text = stringResource(R.string.unit_kw),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = gaugeColor,
                    lineHeight = 8.sp
                )
            }
        }

        // AC/DC badge
        Box(
            modifier = Modifier
                .background(
                    color = gaugeColor,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(if (isDcCharging) R.string.charging_dc else R.string.charging_ac),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White
            )
        }

        // Chevron to indicate tappable - only shown when the live charge API is available
        if (isTappable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = gaugeColor
            )
        }
    }
}

/**
 * Row showing charging details below SoC bar.
 * AC: Voltage, Current, Phases + Energy added + Time remaining
 * DC: Energy added + Time remaining only
 */
@Composable
private fun ChargingDetailsRow(
    status: CarStatus,
    palette: CarColorPalette
) {
    val isDcCharging = status.isDcCharging

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: AC details (Voltage, Current, Phases) or empty for DC
        if (!isDcCharging) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voltage
                Text(
                    text = "${status.chargingDetails?.chargerVoltage ?: "--"} V",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
                // Current
                Text(
                    text = "${status.chargerActualCurrent ?: "--"} A",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
                // Phases badge
                val phases = status.acPhases
                if (phases != null && phases > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = palette.onSurfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${phases}φ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = palette.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Empty spacer for DC
            Spacer(modifier = Modifier.weight(1f))
        }

        // Right: Energy added and time remaining
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Energy added
            Text(
                text = "+${status.chargeEnergyAdded?.let { "%.1f".format(it) } ?: "0"} kWh",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )

            // Time remaining
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = palette.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = status.timeToFullCharge?.let { formatHoursMinutes(it, LocalContext.current.resources) } ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LocationCard(
    status: CarStatus,
    units: Units?,
    resolvedAddress: String? = null,
    palette: CarColorPalette
) {
    val context = LocalContext.current
    val latitude = status.latitude
    val longitude = status.longitude
    val geofence = status.geofence
    val elevation = status.elevation

    val headline = geofence?.takeIf { it.isNotBlank() }
        ?: resolvedAddress?.takeIf { it.isNotBlank() }
        ?: if (latitude != null && longitude != null) "%.5f, %.5f".format(latitude, longitude)
        else stringResource(R.string.unknown)
    // Show the street address as a subline only when the headline is a geofence name.
    val subAddress = resolvedAddress?.takeIf { it.isNotBlank() && it != headline }

    fun openInMaps() {
        if (latitude != null && longitude != null) {
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            val intent = Intent(Intent.ACTION_VIEW, geoUri)
            context.startActivity(intent)
        }
    }

    // The muted map follows the theme: dark map + light text in dark mode,
    // light map + dark text in light mode.
    val dark = isSystemInDarkTheme()
    val onMap = if (dark) Color.White else Color(0xFF0E1216)
    val onMapDim = onMap.copy(alpha = 0.80f)
    val baseColor = if (dark) Color(0xFF12202A) else Color(0xFFE7ECF1)
    val scrimColor = if (dark) Color(0xF00A0C10) else Color(0xF2F8F9FB)
    val tintColor = if (dark) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)
    val pinBorder = if (dark) Color.White else Color(0xFF0E1216)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
        ) {
            // Base (shows while tiles load, or when there are no coordinates).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor)
            )

            if (latitude != null && longitude != null) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(false)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            isClickable = false
                            isFocusable = false
                            // Mute the basemap so roads/labels recede behind the scrim
                            // and pin. Dark theme: grayscale + darken. Light theme:
                            // grayscale + lift toward white to soften the detail.
                            val matrix = ColorMatrix().apply { setSaturation(0f) }
                            matrix.postConcat(
                                if (dark) {
                                    ColorMatrix(
                                        floatArrayOf(
                                            0.55f, 0f, 0f, 0f, 0f,
                                            0f, 0.55f, 0f, 0f, 0f,
                                            0f, 0f, 0.60f, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                } else {
                                    ColorMatrix(
                                        floatArrayOf(
                                            0.92f, 0f, 0f, 0f, 18f,
                                            0f, 0.92f, 0f, 0f, 18f,
                                            0f, 0f, 0.92f, 0f, 18f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                }
                            )
                            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                            controller.setZoom(15.0)
                            controller.setCenter(GeoPoint(latitude, longitude))
                            // Fully inert — no pan, no zoom. Taps are handled by the
                            // Compose overlay above the map.
                            setOnTouchListener { _, _ -> true }
                        }
                    },
                    // factory only runs once — without this the map stays centered on
                    // wherever the car was at first composition while the status polls on.
                    update = { map ->
                        val center = GeoPoint(latitude, longitude)
                        if (map.mapCenter.latitude != center.latitude ||
                            map.mapCenter.longitude != center.longitude
                        ) {
                            map.controller.setCenter(center)
                        }
                    },
                    // onDetach() shuts down osmdroid's tile-loader threads and cache.
                    onRelease = { it.onDetach() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Translucent tint to knock back remaining tile clutter and unify the look.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tintColor)
            )
            // Bottom scrim for text legibility.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            1f to scrimColor
                        )
                    )
            )

            // Glowing pin in the upper third so the place name below it never overlaps.
            if (latitude != null && longitude != null) {
                Box(
                    modifier = Modifier.align(BiasAlignment(0f, -0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(palette.accent.copy(alpha = 0.45f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .background(palette.accent, CircleShape)
                            .border(2.dp, pinBorder, CircleShape)
                    )
                }
            }

            // Overlay: place name, address and detail chips.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (!geofence.isNullOrBlank()) {
                    // Geofence name — big; full address small beneath.
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onMap,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subAddress != null) {
                        Text(
                            text = subAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onMapDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (!resolvedAddress.isNullOrBlank()) {
                    // No geofence: the geocoder formats the address as "<street>, <city>".
                    // Lead with the city (big, bold); show the street smaller beneath so a
                    // long street doesn't force the whole headline to shrink.
                    val parts = resolvedAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val city = parts.lastOrNull() ?: resolvedAddress
                    val street = parts.dropLast(1).joinToString(", ").ifBlank { null }
                    Text(
                        text = city,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onMap,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (street != null) {
                        Text(
                            text = street,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = onMapDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Coordinates fallback (no geofence, no resolved address).
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = onMap,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (elevation != null) {
                        LocationChip(
                            icon = Icons.Filled.Terrain,
                            text = UnitFormatter.formatElevation(elevation, units)
                        )
                    }
                    if (latitude != null && longitude != null) {
                        LocationChip(
                            icon = Icons.Filled.LocationOn,
                            text = "%.4f, %.4f".format(latitude, longitude)
                        )
                    }
                }
            }

            // Chevron affordance — signals the whole card is tappable.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .background(
                        if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.55f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = onMap,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Whole-card tap target, above the inert map, opens the default maps app.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { openInMaps() }
            )
        }
    }
}

/** A small translucent chip used on the immersive Location card's map overlay. */
@Composable
private fun LocationChip(icon: ImageVector, text: String) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0E1216)
    val bg = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content.copy(alpha = 0.9f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.92f),
            maxLines = 1
        )
    }
}

@Composable
private fun VehicleInfoCard(
    status: CarStatus,
    units: Units?,
    palette: CarColorPalette,
    totalCharges: Int?,
    totalDrives: Int?,
    totalTrips: Int? = null,
    latestTrip: Trip? = null,
    onNavigateToCharges: () -> Unit,
    onNavigateToDrives: () -> Unit,
    onNavigateToMileage: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToTrips: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bento: tall Trips hero (left) + Mileage / Charges stacked (right).
            // IntrinsicSize.Min lets the hero match the combined height of the two
            // right-hand tiles so the three cells line up flush.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripsHeroTile(
                    totalTrips = totalTrips,
                    latestTrip = latestTrip,
                    units = units,
                    palette = palette,
                    onClick = onNavigateToTrips,
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavButton(
                        title = stringResource(R.string.nav_mileage),
                        value = status.odometer?.let {
                            val value = it
                            "%,.0f %s".format(value, UnitFormatter.getDistanceUnit(units))
                        } ?: "--",
                        icon = CustomIcons.Road,
                        palette = palette,
                        onClick = onNavigateToMileage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    NavButton(
                        title = stringResource(R.string.nav_charges),
                        value = totalCharges?.let { "%,d".format(it) } ?: "--",
                        icon = Icons.Filled.ElectricBolt,
                        palette = palette,
                        onClick = onNavigateToCharges,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer strip: Drives + Software. Weights match the bento row above
            // (1.15 : 1) so Drives lines up under Trips and Software under the tiles.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavButton(
                    title = stringResource(R.string.nav_drives),
                    value = totalDrives?.let { "%,d".format(it) } ?: "--",
                    icon = CustomIcons.SteeringWheel,
                    palette = palette,
                    onClick = onNavigateToDrives,
                    modifier = Modifier.weight(1.15f)
                )
                NavButton(
                    title = stringResource(R.string.nav_software),
                    value = status.version ?: "--",
                    icon = Icons.Filled.Settings,
                    palette = palette,
                    onClick = onNavigateToUpdates,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The Trips hero tile: a tall, accent-washed cell that anchors the Activity card.
 * Shows the trip count with a "latest trip" teaser, or an inviting empty state when
 * no road-trips (auto-detected drives ≥300 km) have been recorded yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripsHeroTile(
    totalTrips: Int?,
    latestTrip: Trip?,
    units: Units?,
    palette: CarColorPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = palette.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (totalTrips == 0) {
                    // Empty state — no road-trips detected yet.
                    Text(
                        text = stringResource(R.string.trips_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.trips_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant
                    )
                } else {
                    // Big count in the top-left, with "Trips" beside it.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = totalTrips?.let { "%,d".format(it) } ?: "--",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.nav_trips),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (latestTrip != null) {
                        Text(
                            text = stringResource(R.string.trip_latest_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceVariant
                        )
                        Text(
                            text = latestTrip.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = run {
                                    val v = latestTrip.totalDistance
                                    "%,.0f %s".format(v, UnitFormatter.getDistanceUnit(units))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavButton(
    title: String,
    value: String,
    icon: ImageVector,
    palette: CarColorPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = palette.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon, vertically centered so it spans the value + label lines.
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                    maxLines = 1
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                    // Shrink long localized labels (e.g. Spanish "Trayectos",
                    // Catalan "Trajectes") to fit one line instead of wrapping.
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = 11.sp,
                        stepSize = 0.5.sp
                    )
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TirePressureCard(
    tpms: TpmsDetails,
    units: Units?,
    palette: CarColorPalette
) {
    // Status comes purely from Tesla's per-tyre soft-warning flag; the printed number is
    // authoritative (there's no recommended/target pressure in the data).
    val unitLabel = UnitFormatter.getPressureUnit(units)
    val pressures = listOf(tpms.pressureFl, tpms.pressureFr, tpms.pressureRl, tpms.pressureRr)
    val warnings = listOf(
        tpms.warningFl == true, tpms.warningFr == true,
        tpms.warningRl == true, tpms.warningRr == true
    )
    val anyLow = warnings.any { it }
    val statusColor = if (anyLow) StatusWarning else StatusSuccess
    val lineColor = palette.onSurface.copy(alpha = 0.08f)
    // Open by default when a tyre is low; tap the verdict line to fold/unfold. Re-keyed on
    // anyLow so a newly-detected low auto-opens (and an all-clear auto-folds).
    var expanded by remember(anyLow) { mutableStateOf(anyLow) }

    // Verdict meta: lowest warned value when low, else the range across all tyres.
    val metaText = if (anyLow) {
        pressures.filterIndexed { i, _ -> warnings[i] }.filterNotNull().minOrNull()
            ?.let { "%.1f %s".format(it, unitLabel) } ?: unitLabel
    } else {
        val present = pressures.filterNotNull()
        val mn = present.minOrNull()
        val mx = present.maxOrNull()
        when {
            mn == null || mx == null -> unitLabel
            mn == mx -> "%.1f %s".format(mn, unitLabel)
            else -> "%.1f–%.1f %s".format(mn, mx, unitLabel)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Verdict line — the plain-language answer first; tap to fold/unfold detail.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.TireRepair,
                    contentDescription = stringResource(R.string.tire_pressure_title),
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(statusColor.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (anyLow) Icons.Filled.PriorityHigh else Icons.Filled.Check,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(if (anyLow) R.string.tire_pressure_low else R.string.tire_pressure_all_ok),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (anyLow) FontWeight.Bold else FontWeight.Normal,
                    color = if (anyLow) statusColor else palette.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Foldable per-wheel detail — open by default when a tyre is low.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Segment bar — one connected instrument; the low tyre lights its slice.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, lineColor, RoundedCornerShape(12.dp))
                    ) {
                        TyreSegment(stringResource(R.string.tire_fl), tpms.pressureFl, tpms.warningFl == true, palette, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = lineColor)
                        TyreSegment(stringResource(R.string.tire_fr), tpms.pressureFr, tpms.warningFr == true, palette, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = lineColor)
                        TyreSegment(stringResource(R.string.tire_rl), tpms.pressureRl, tpms.warningRl == true, palette, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(color = lineColor)
                        TyreSegment(stringResource(R.string.tire_rr), tpms.pressureRr, tpms.warningRr == true, palette, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** One segment of the tyre bar: status-coloured top edge + faint tint, position label, value. */
@Composable
private fun TyreSegment(
    label: String,
    pressure: Double?,
    warning: Boolean,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val statusColor = if (warning) StatusWarning else StatusSuccess
    Column(
        modifier = modifier.background(statusColor.copy(alpha = if (warning) 0.12f else 0.07f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(statusColor)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = pressure?.let { "%.1f".format(it) } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (warning) statusColor else palette.onSurface,
                maxLines = 1
            )
        }
    }
}

private fun formatHoursMinutes(hours: Double, resources: android.content.res.Resources): String {
    val totalMinutes = (hours * 60).roundToInt()
    return formatDuration(resources, totalMinutes)
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

@Preview(showBackground = true, name = "AC Charging - 11kW")
@Composable
private fun BatteryCardAcChargingPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                batteryDetails = BatteryDetails(
                    batteryLevel = 45,
                    ratedBatteryRange = 180.0
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 11,
                    chargerPhases = 3,  // AC = phases 1-3
                    chargerVoltage = 230,
                    chargerActualCurrent = 16,
                    chargeCurrentRequestMax = 16,  // 16/16 = 100% gauge fill
                    chargeEnergyAdded = 8.5,
                    timeToFullCharge = 2.5,
                    chargeLimitSoc = 80
                )
            ),
            units = null,
            carTrimBadging = "74D"
        )
    }
}

@Preview(showBackground = true, name = "DC Charging - 120kW")
@Composable
private fun BatteryCardDcChargingPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                batteryDetails = BatteryDetails(
                    batteryLevel = 60,
                    ratedBatteryRange = 240.0
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 120,  // 120/250 = 48% gauge fill
                    chargerPhases = 0,  // DC = phases 0 or null
                    chargeEnergyAdded = 35.5,
                    timeToFullCharge = 0.3,
                    chargeLimitSoc = 80
                )
            ),
            units = null,
            carTrimBadging = "74D"  // NMC battery, max 250kW
        )
    }
}

@Preview(showBackground = true, name = "DC Charging - LFP Battery")
@Composable
private fun BatteryCardDcChargingLfpPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "online",
                batteryDetails = BatteryDetails(
                    batteryLevel = 20,
                    ratedBatteryRange = 80.0
                ),
                chargingDetails = ChargingDetails(
                    pluggedIn = true,
                    chargingState = "Charging",
                    chargerPower = 120,
                    chargerPhases = 0,  // DC
                    chargeEnergyAdded = 18.0,
                    timeToFullCharge = 0.4,
                    chargeLimitSoc = 100  // LFP can charge to 100%
                )
            ),
            units = null,
            carTrimBadging = "50"  // LFP battery, max 170kW
        )
    }
}
