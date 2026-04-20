package com.matedroid.ui.screens.trips

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.components.CostDonutStop
import com.matedroid.ui.components.TripCostDonut
import com.matedroid.ui.components.computeCostShades
import com.matedroid.ui.components.TripEditActions
import com.matedroid.ui.components.TripTimeline
import com.matedroid.ui.components.TripTimelineCountry
import com.matedroid.ui.components.TripWeatherSparklineCard
import com.matedroid.ui.components.createPinMarkerDrawable
import com.matedroid.ui.components.createZapMarkerDrawable
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    carId: Int,
    tripStartDate: String,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToDriveDetail: (driveId: Int) -> Unit = {},
    onNavigateToChargeDetail: (chargeId: Int) -> Unit = {},
    onNavigateToCountryStats: (countryCode: String) -> Unit = {},
    viewModel: TripDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId, tripStartDate) { viewModel.loadTrip(carId, tripStartDate) }

    LaunchedEffect(uiState.justDeleted) {
        if (uiState.justDeleted) onNavigateBack()
    }

    // When the user returns from a child screen (e.g. a drive/charge detail where they may have
    // removed a leg), reload so the trip reflects the current DB state. The ViewModel ignores the
    // first ON_RESUME (on initial composition) by only reloading after at least one ON_PAUSE.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val displayTitle = run {
        val trip = uiState.trip
        val customName = uiState.savedTripName?.takeIf { it.isNotBlank() }
        when {
            customName != null -> customName
            trip != null -> trip.displayName()
            else -> stringResource(R.string.trip_detail_title)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayTitle,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.savedTripId != null) {
                        IconButton(onClick = viewModel::openRenameDialog) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.trip_rename_action)
                            )
                        }
                        IconButton(onClick = viewModel::openDeleteConfirm) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.trip_edit_delete)
                            )
                        }
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
            uiState.trip == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Trip not found") }
            }
            else -> {
                val trip = uiState.trip!!
                Box(modifier = Modifier.padding(padding)) {
                    TripDetailContent(
                        trip = trip,
                        routeSegments = uiState.routeSegments,
                        markers = uiState.markers,
                        isMapLoading = uiState.isMapLoading,
                        countries = uiState.countries,
                        units = uiState.units,
                        palette = palette,
                        dcChargeIds = uiState.dcChargeIds,
                        canEdit = uiState.savedTripId != null,
                        weatherPoints = uiState.weatherPoints,
                        onDriveClick = onNavigateToDriveDetail,
                        onChargeClick = onNavigateToChargeDetail,
                        onCountryClick = onNavigateToCountryStats,
                        onAddLeg = viewModel::openAddLegSheet,
                        onMergeTrip = viewModel::openMergeSheet,
                        currencySymbol = uiState.currencySymbol
                    )
                }
            }
        }
    }

    // Sheets and dialogs — rendered at top level so they overlay regardless of scroll position
    if (uiState.showAddLegSheet && uiState.eligibleLegs != null) {
        AddLegSheet(
            eligible = uiState.eligibleLegs!!,
            dcChargeIds = uiState.dcChargeIds,
            palette = palette,
            onPickLegs = viewModel::pickLegs,
            onDismiss = viewModel::closeAddLegSheet
        )
    }
    if (uiState.showMergeSheet) {
        MergeTripSheet(
            adjacentTrips = uiState.adjacentTrips,
            palette = palette,
            onPick = viewModel::pickMergeTarget,
            onDismiss = viewModel::closeMergeSheet
        )
    }
    if (uiState.pendingMergeTarget != null) {
        MergeConfirmDialog(
            onConfirm = viewModel::confirmMergeTarget,
            onDismiss = viewModel::cancelMergeTarget
        )
    }
    if (uiState.showDeleteConfirm) {
        DeleteTripConfirmDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::closeDeleteConfirm
        )
    }
    if (uiState.showRenameDialog) {
        RenameTripDialog(
            value = uiState.renameDraft,
            onValueChange = viewModel::updateRenameDraft,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::closeRenameDialog
        )
    }
}

@Composable
private fun TripDetailContent(
    trip: Trip,
    routeSegments: List<TripRouteSegment>,
    markers: List<TripMapMarker>,
    isMapLoading: Boolean,
    countries: List<TripCountry>,
    units: Units?,
    palette: CarColorPalette,
    dcChargeIds: Set<Int>,
    canEdit: Boolean,
    weatherPoints: List<com.matedroid.data.repository.TripWeatherPoint>,
    onDriveClick: (driveId: Int) -> Unit,
    onChargeClick: (chargeId: Int) -> Unit,
    onCountryClick: (countryCode: String) -> Unit,
    onAddLeg: () -> Unit,
    onMergeTrip: () -> Unit,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val dateRangeLabel = remember(trip.startDate, trip.endDate) {
            formatTripDateRange(trip.startDate, trip.endDate)
        }
        val distanceLabel = remember(trip.totalDistance, units) {
            UnitFormatter.formatDistance(trip.totalDistance, units, decimals = 0)
        }
        val startCity = remember(trip.startAddress) { extractCity(trip.startAddress) }
        val endCity = remember(trip.endAddress) { extractCity(trip.endAddress) }
        val timelineSegments = remember(trip, dcChargeIds) { buildTimelineSegments(trip, dcChargeIds) }
        val timelineCountries = remember(countries) {
            countries.map { TripTimelineCountry(it.countryCode, it.flagEmoji) }
        }
        val totalChargingDurationMin = remember(trip.charges) {
            trip.charges.sumOf { it.durationMin }
        }

        TripMapCard(
            routeSegments = routeSegments,
            markers = markers,
            isMapLoading = isMapLoading,
            palette = palette,
            dateRangeLabel = dateRangeLabel,
            distanceLabel = distanceLabel,
            onChargeClick = onChargeClick
        )

        TripTimeline(
            segments = timelineSegments,
            startDate = trip.startDate,
            endDate = trip.endDate,
            startCity = startCity,
            endCity = endCity,
            countries = timelineCountries,
            palette = palette,
            totalDurationMin = trip.totalDurationMin,
            totalDrivingDurationMin = trip.totalDrivingDurationMin,
            totalChargingDurationMin = totalChargingDurationMin,
            onCountryClick = onCountryClick
        )

        if ((trip.totalChargeCost ?: 0.0) > 0.0) {
            ChargeCostCard(
                trip = trip,
                currencySymbol = currencySymbol,
                palette = palette,
                dcChargeIds = dcChargeIds,
                units = units,
                onChargeClick = onChargeClick
            )
        }

        BatteryEnergyFlowCard(
            trip = trip,
            dcChargeIds = dcChargeIds,
            palette = palette,
            units = units
        )

        val legs = remember(trip) { buildLegList(trip) }
        var legsExpanded by rememberSaveable { mutableStateOf(false) }
        val legsChevronRotation by animateFloatAsState(
            targetValue = if (legsExpanded) 90f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "legsChevron"
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { legsExpanded = !legsExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.trip_legs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${trip.drives.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = CustomIcons.SteeringWheel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = palette.accent
                    )
                    if (trip.charges.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${trip.charges.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ElectricBolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = palette.accent
                        )
                    }
                    Text(
                        text = ")",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(legsChevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (legsExpanded) {
                    legs.forEach { leg ->
                        when (leg) {
                            is TripLeg.Drive -> DriveLegCard(leg, units, palette) {
                                onDriveClick(leg.drive.driveId)
                            }
                            is TripLeg.Charge -> ChargeLegCard(
                                leg = leg,
                                palette = palette,
                                isDc = leg.charge.chargeId in dcChargeIds
                            ) {
                                onChargeClick(leg.charge.chargeId)
                            }
                        }
                    }
                }
            }
        }

        TripWeatherSparklineCard(
            samples = weatherPoints,
            palette = palette,
            units = units
        )

        if (canEdit) {
            TripEditActions(
                onAddLeg = onAddLeg,
                onMergeTrip = onMergeTrip
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// === Charge Cost Card ===

@Composable
private fun ChargeCostCard(
    trip: Trip,
    currencySymbol: String,
    palette: CarColorPalette,
    dcChargeIds: Set<Int>,
    units: Units?,
    onChargeClick: (chargeId: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevron"
    )

    val costStops = remember(trip, dcChargeIds) {
        trip.charges
            .filter { it.cost != null }
            .map { charge ->
                CostDonutStop(
                    chargeId = charge.chargeId,
                    label = extractCity(charge.address),
                    cost = charge.cost!!,
                    durationMin = charge.durationMin,
                    energyAddedKwh = charge.energyAdded,
                    isDc = charge.chargeId in dcChargeIds
                )
            }
    }
    val shades = remember(costStops, palette) { computeCostShades(costStops, palette) }
    val costCharges = remember(trip) { trip.charges.filter { it.cost != null } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.ElectricBolt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.trip_charge_cost),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (costStops.isNotEmpty()) {
                TripCostDonut(
                    stops = costStops,
                    shades = shades,
                    palette = palette,
                    currencySymbol = currencySymbol
                )

                // Always-visible efficiency pills below the donut — the "at-a-glance verdict"
                // on how expensive the trip was per distance / per energy.
                val distanceUnit = UnitFormatter.getDistanceUnit(units)
                val per100 = trip.totalChargeCost?.takeIf { trip.totalDistance > 0.0 }
                    ?.let { it / trip.totalDistance * 100.0 }
                val perKwh = trip.totalChargeCost?.takeIf { trip.totalEnergyCharged > 0.0 }
                    ?.let { it / trip.totalEnergyCharged }
                if (per100 != null || perKwh != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        per100?.let {
                            EfficiencyPill(
                                value = "%.2f %s".format(it, currencySymbol),
                                unit = "/ 100 $distanceUnit",
                                palette = palette
                            )
                        }
                        perKwh?.let {
                            EfficiencyPill(
                                value = "%.2f %s".format(it, currencySymbol),
                                unit = "/ kWh",
                                palette = palette
                            )
                        }
                    }
                }
            }

            if (expanded) {
                costCharges.forEachIndexed { index, charge ->
                    val shade = shades.getOrNull(index) ?: palette.accent
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChargeClick(charge.chargeId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(shade)
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = extractCity(charge.address),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "+%.1f kWh · %dm".format(charge.energyAdded, charge.durationMin),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "%.2f %s".format(charge.cost, currencySymbol),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = shade
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// === Efficiency pill — surfaces €/100km and €/kWh below the cost donut ===

@Composable
private fun EfficiencyPill(
    value: String,
    unit: String,
    palette: CarColorPalette
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.accent.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.accent
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = palette.accent.copy(alpha = 0.75f)
        )
    }
}

// === Battery energy flow card — horizontal Sankey-style flow (DC+AC in → battery → Used out) ===

@Composable
private fun BatteryEnergyFlowCard(
    trip: Trip,
    dcChargeIds: Set<Int>,
    palette: CarColorPalette,
    units: Units?
) {
    val dcCharges = remember(trip, dcChargeIds) { trip.charges.filter { it.chargeId in dcChargeIds } }
    val acCharges = remember(trip, dcChargeIds) { trip.charges.filter { it.chargeId !in dcChargeIds } }
    val dcKwh = dcCharges.sumOf { it.energyAdded }
    val acKwh = acCharges.sumOf { it.energyAdded }
    val dcDurationMin = dcCharges.sumOf { it.durationMin }
    val acDurationMin = acCharges.sumOf { it.durationMin }
    val dcAvgKw = if (dcDurationMin > 0) dcKwh * 60.0 / dcDurationMin else 0.0
    val acAvgKw = if (acDurationMin > 0) acKwh * 60.0 / acDurationMin else 0.0
    val usedKwh = trip.totalEnergyConsumed
    val efficiencyWhPerDist = trip.avgEfficiency
        ?: trip.totalDistance.takeIf { it > 0.0 }?.let { usedKwh * 1000.0 / it }
    val efficiencyUnit = UnitFormatter.getEfficiencyUnit(units)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.battery),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            HorizontalEnergyFlow(
                dcKwh = dcKwh, dcAvgKw = dcAvgKw,
                acKwh = acKwh, acAvgKw = acAvgKw,
                usedKwh = usedKwh,
                efficiencyWhPerDist = efficiencyWhPerDist,
                efficiencyUnit = efficiencyUnit,
                palette = palette
            )
        }
    }
}

@Composable
private fun HorizontalEnergyFlow(
    dcKwh: Double, dcAvgKw: Double,
    acKwh: Double, acAvgKw: Double,
    usedKwh: Double,
    efficiencyWhPerDist: Double?,
    efficiencyUnit: String,
    palette: CarColorPalette
) {
    val totalCharged = dcKwh + acKwh

    // Layout constants (dp). A little taller so labels can sit above/below the thin leads
    // without crowding the battery.
    val cardHeight = 150.dp
    val batteryW = 128.dp
    val batteryH = 72.dp
    val batteryTipW = 7.dp
    val batteryTipH = 26.dp
    // Very thin source thickness — leaves plenty of room for the labels.
    val startT = 6.dp

    // The stream stays at startT for 80% of its run, then expands to its proportional share
    // over the last 20%. Inverse for the outflow (full-width for the first 20%, thin after).
    val flatFraction = 0.80f

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
    ) {
        val cardW = maxWidth
        val centerY = cardHeight / 2

        val cardWPx = with(density) { cardW.toPx() }
        val cardHPx = with(density) { cardHeight.toPx() }
        val batteryWPx = with(density) { batteryW.toPx() }
        val batteryHPx = with(density) { batteryH.toPx() }
        val batteryTipWPx = with(density) { batteryTipW.toPx() }
        val batteryTipHPx = with(density) { batteryTipH.toPx() }
        val batteryLeftXPx = (cardWPx - batteryWPx) / 2f
        val batteryRightXPx = batteryLeftXPx + batteryWPx
        val batteryTopYPx = (cardHPx - batteryHPx) / 2f
        val batteryBottomYPx = batteryTopYPx + batteryHPx
        val centerYPx = cardHPx / 2f
        val startTPx = with(density) { startT.toPx() }
        val dcShare = if (totalCharged > 0) dcKwh / totalCharged else 0.0
        val splitYPx = batteryTopYPx + (dcShare * batteryHPx).toFloat()

        val accent = palette.accent
        val acColor = palette.acColor
        val dcColor = palette.dcColor
        val usedColor = StatusError

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerPx = 10.dp.toPx()
            val strokePx = 2.5.dp.toPx()

            // x where the inflow transitions from flat-thin to widening
            val inTransitionX = batteryLeftXPx * flatFraction
            // x where the outflow ends its widening (mirror of inTransitionX relative to battery)
            val outRegionWidth = cardWPx - batteryRightXPx
            val outTransitionX = batteryRightXPx + outRegionWidth * (1f - flatFraction)

            // Helper: build a Sankey-style inflow path. Stays at [topY..bottomY] (thickness startT)
            // from x=0 to inTransitionX, then cubic-beziers to (batteryLeftXPx, [batteryTopYAt..
            // batteryBotYAt]).
            fun buildInPath(sourceTopY: Float, sourceBotY: Float, batteryTopYAt: Float, batteryBotYAt: Float): Path {
                val ctrlX = (inTransitionX + batteryLeftXPx) / 2f
                return Path().apply {
                    moveTo(0f, sourceTopY)
                    lineTo(inTransitionX, sourceTopY)
                    cubicTo(
                        ctrlX, sourceTopY,
                        ctrlX, batteryTopYAt,
                        batteryLeftXPx, batteryTopYAt
                    )
                    lineTo(batteryLeftXPx, batteryBotYAt)
                    cubicTo(
                        ctrlX, batteryBotYAt,
                        ctrlX, sourceBotY,
                        inTransitionX, sourceBotY
                    )
                    lineTo(0f, sourceBotY)
                    close()
                }
            }

            // === Inflow streams ===
            when {
                dcKwh > 0 && acKwh > 0 -> {
                    // DC on top: from thin line above center → top portion of battery
                    drawPath(
                        buildInPath(
                            sourceTopY = centerYPx - startTPx,
                            sourceBotY = centerYPx,
                            batteryTopYAt = batteryTopYPx,
                            batteryBotYAt = splitYPx
                        ),
                        brush = Brush.horizontalGradient(
                            colors = listOf(dcColor, dcColor.copy(alpha = 0f)),
                            startX = 0f, endX = batteryLeftXPx
                        )
                    )
                    // AC on bottom
                    drawPath(
                        buildInPath(
                            sourceTopY = centerYPx,
                            sourceBotY = centerYPx + startTPx,
                            batteryTopYAt = splitYPx,
                            batteryBotYAt = batteryBottomYPx
                        ),
                        brush = Brush.horizontalGradient(
                            colors = listOf(acColor, acColor.copy(alpha = 0f)),
                            startX = 0f, endX = batteryLeftXPx
                        )
                    )
                }
                dcKwh > 0 -> {
                    drawPath(
                        buildInPath(
                            sourceTopY = centerYPx - startTPx,
                            sourceBotY = centerYPx + startTPx,
                            batteryTopYAt = batteryTopYPx,
                            batteryBotYAt = batteryBottomYPx
                        ),
                        brush = Brush.horizontalGradient(
                            colors = listOf(dcColor, dcColor.copy(alpha = 0f)),
                            startX = 0f, endX = batteryLeftXPx
                        )
                    )
                }
                acKwh > 0 -> {
                    drawPath(
                        buildInPath(
                            sourceTopY = centerYPx - startTPx,
                            sourceBotY = centerYPx + startTPx,
                            batteryTopYAt = batteryTopYPx,
                            batteryBotYAt = batteryBottomYPx
                        ),
                        brush = Brush.horizontalGradient(
                            colors = listOf(acColor, acColor.copy(alpha = 0f)),
                            startX = 0f, endX = batteryLeftXPx
                        )
                    )
                }
            }

            // === Outflow stream (inverse profile) ===
            // Widens from battery height at batteryRightXPx to thin startT at outTransitionX,
            // then stays thin to cardWPx.
            run {
                val ctrlX = (batteryRightXPx + outTransitionX) / 2f
                val outPath = Path().apply {
                    moveTo(batteryRightXPx, batteryTopYPx)
                    cubicTo(
                        ctrlX, batteryTopYPx,
                        ctrlX, centerYPx - startTPx / 2f,
                        outTransitionX, centerYPx - startTPx / 2f
                    )
                    lineTo(cardWPx, centerYPx - startTPx / 2f)
                    lineTo(cardWPx, centerYPx + startTPx / 2f)
                    lineTo(outTransitionX, centerYPx + startTPx / 2f)
                    cubicTo(
                        ctrlX, centerYPx + startTPx / 2f,
                        ctrlX, batteryBottomYPx,
                        batteryRightXPx, batteryBottomYPx
                    )
                    close()
                }
                drawPath(
                    outPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(usedColor.copy(alpha = 0f), usedColor),
                        startX = batteryRightXPx, endX = cardWPx
                    )
                )
            }

            // === Battery body (drawn on top of the stream edges so seams are clean) ===
            drawRoundRect(
                color = accent.copy(alpha = 0.10f),
                topLeft = Offset(batteryLeftXPx, batteryTopYPx),
                size = Size(batteryWPx, batteryHPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(batteryLeftXPx, batteryTopYPx),
                size = Size(batteryWPx, batteryHPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx)
            )

            // Battery tip (positive terminal) on the right side — makes it unmistakably a battery
            val tipCornerPx = 3.dp.toPx()
            val tipY = batteryTopYPx + (batteryHPx - batteryTipHPx) / 2f
            drawRoundRect(
                color = accent,
                topLeft = Offset(batteryRightXPx, tipY),
                size = Size(batteryTipWPx, batteryTipHPx),
                cornerRadius = CornerRadius(tipCornerPx, tipCornerPx)
            )
        }

        // === Text overlays ===

        // Battery center label: total charged kWh
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (totalCharged > 0) {
                Text(
                    text = "%.1f".format(totalCharged),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = "kWh charged",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.78f)
                )
            } else {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // DC label (top-left) — above the thin DC stream
        if (dcKwh > 0) {
            Column(
                modifier = Modifier.offset(
                    x = 6.dp,
                    y = centerY - startT - 40.dp
                )
            ) {
                Text(
                    text = "%.1f kWh".format(dcKwh),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = dcColor
                )
                Text(
                    text = "%.0f kW avg".format(dcAvgKw),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // AC label (bottom-left) — below the thin AC stream
        if (acKwh > 0) {
            Column(
                modifier = Modifier.offset(
                    x = 6.dp,
                    y = centerY + startT + 6.dp
                )
            ) {
                Text(
                    text = "%.1f kWh".format(acKwh),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = acColor
                )
                Text(
                    text = "%.1f kW avg".format(acAvgKw),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // OUT label (right) — over the thin outflow section
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = -(startT + 20.dp))
                .padding(end = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "%.1f kWh".format(usedKwh),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = usedColor
            )
            if (efficiencyWhPerDist != null) {
                Text(
                    text = "%.0f %s".format(efficiencyWhPerDist, efficiencyUnit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// === Map ===

private const val GEO_JUMP_THRESHOLD_METERS = 10_000.0

private data class MapColors(
    val start: Int,
    val charge: Int,
    val end: Int,
    val oddLeg: Int,
    val evenLeg: Int
)

private data class PreparedRoute(
    val geoPointSegments: List<List<GeoPoint>>,
    val boundingBox: BoundingBox?
)

@Composable
private fun TripMapCard(
    routeSegments: List<TripRouteSegment>,
    markers: List<TripMapMarker>,
    isMapLoading: Boolean,
    palette: CarColorPalette,
    dateRangeLabel: String,
    distanceLabel: String,
    onChargeClick: (chargeId: Int) -> Unit = {}
) {
    // Bridge: Android View click → Compose state → Compose navigation
    var pendingChargeNav by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingChargeNav) {
        if (pendingChargeNav != 0) {
            onChargeClick(pendingChargeNav)
            pendingChargeNav = 0
        }
    }

    // Track when the map has zoomed to the route — hides the world-view flash
    var mapReady by remember { mutableStateOf(false) }

    val mapColors = remember(palette) {
        MapColors(
            start = StatusSuccess.toArgb(),
            charge = palette.accent.toArgb(),
            end = StatusError.toArgb(),
            oddLeg = palette.accent.toArgb(),
            evenLeg = palette.accent.copy(alpha = 0.55f)
                .compositeOver(androidx.compose.ui.graphics.Color.White)
                .toArgb()
        )
    }

    // Cache marker drawables per color — drawables are heavy to create each pass.
    val markerDrawables = remember { mutableMapOf<Pair<Int, Boolean>, android.graphics.drawable.Drawable>() }

    // Track the last applied data so we can skip redrawing overlays when nothing changed.
    val lastApplied = remember { arrayOfNulls<Any>(3) }

    // Defer MapView instantiation by a short delay so the first frame of the surrounding screen
    // paints and touch/scroll handlers become responsive before osmdroid's synchronous MapView
    // constructor runs on the main thread. This matters most on back-navigation: the composition
    // is torn down and rebuilt by NavHost, and mounting the MapView immediately would freeze the
    // main thread for ~100–300ms.
    var mapMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        mapMounted = true
    }

    // Precompute GeoPoint lists + BoundingBox off the main thread. For long trips this moves
    // hundreds-to-thousands of object allocations + min/max scans off the UI thread, so when
    // the AndroidView update runs it only does cheap attach work.
    var preparedRoute by remember(routeSegments) { mutableStateOf<PreparedRoute?>(null) }
    LaunchedEffect(routeSegments) {
        preparedRoute = if (routeSegments.isEmpty()) null else withContext(Dispatchers.Default) {
            val geoPointSegments = routeSegments.map { seg ->
                seg.points.map { GeoPoint(it.latitude, it.longitude) }
            }
            val allPoints = geoPointSegments.flatten()
            val bb = if (allPoints.isNotEmpty()) {
                val north = allPoints.maxOf { it.latitude }
                val south = allPoints.minOf { it.latitude }
                val east = allPoints.maxOf { it.longitude }
                val west = allPoints.minOf { it.longitude }
                val latPad = (north - south) * 0.15
                val lonPad = (east - west) * 0.15
                BoundingBox(
                    north + latPad, east + lonPad,
                    south - latPad, west - lonPad
                )
            } else null
            PreparedRoute(geoPointSegments, bb)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
                if (mapMounted) AndroidView(
                    factory = { mapCtx ->
                        MapView(mapCtx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                        }
                    },
                    update = { mapView ->
                        val prep = preparedRoute ?: return@AndroidView

                        // Skip the expensive overlay rebuild when the inputs haven't changed.
                        // Lists and MapColors compare structurally so this short-circuits on
                        // return-from-child when the VM emits structurally-equal data.
                        if (lastApplied[0] == prep &&
                            lastApplied[1] == markers &&
                            lastApplied[2] == mapColors
                        ) return@AndroidView
                        lastApplied[0] = prep
                        lastApplied[1] = markers
                        lastApplied[2] = mapColors

                        // Defer all overlay creation to the next main-thread message so the
                        // surrounding composition's first frame can paint (and the scroll view
                        // becomes responsive) before we build polylines + markers.
                        mapView.post {
                        mapView.overlays.clear()

                        var previousEnd: GeoPoint? = null
                        prep.geoPointSegments.forEachIndexed { index, geoPoints ->
                            if (geoPoints.size < 2) return@forEachIndexed

                            // If there's a previous leg, check if this leg's start is geographically
                            // far from the previous leg's end (car was transported, or between
                            // non-contiguous trips that were merged). Draw a dashed connector.
                            val firstPoint = geoPoints.first()
                            val prev = previousEnd
                            if (prev != null) {
                                val distanceMeters = prev.distanceToAsDouble(firstPoint)
                                if (distanceMeters > GEO_JUMP_THRESHOLD_METERS) {
                                    val dashed = Polyline().apply {
                                        setPoints(listOf(prev, firstPoint))
                                        outlinePaint.color = mapColors.oddLeg
                                        outlinePaint.strokeWidth = 6f
                                        outlinePaint.strokeCap = Paint.Cap.ROUND
                                        outlinePaint.pathEffect =
                                            android.graphics.DashPathEffect(floatArrayOf(20f, 15f), 0f)
                                    }
                                    mapView.overlays.add(dashed)
                                }
                            }

                            val polyline = Polyline().apply {
                                setPoints(geoPoints)
                                outlinePaint.color =
                                    if (index % 2 == 0) mapColors.oddLeg
                                    else mapColors.evenLeg
                                outlinePaint.strokeWidth = 8f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                            }
                            mapView.overlays.add(polyline)
                            previousEnd = geoPoints.last()
                        }

                        val mapCtx = mapView.context
                        markers.forEach { point ->
                            val color = when (point.type) {
                                TripMapPointType.START -> mapColors.start
                                TripMapPointType.CHARGE -> mapColors.charge
                                TripMapPointType.END -> mapColors.end
                            }
                            val isCharge = point.type == TripMapPointType.CHARGE
                            val markerIcon = markerDrawables.getOrPut(color to isCharge) {
                                if (isCharge) createZapMarkerDrawable(mapCtx.resources, color)
                                else createPinMarkerDrawable(mapCtx.resources, color)
                            }
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(point.latitude, point.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = point.label
                                icon = markerIcon
                                if (point.chargeId != null) {
                                    val cid = point.chargeId
                                    infoWindow = object : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(
                                        org.osmdroid.library.R.layout.bonuspack_bubble, mapView
                                    ) {
                                        override fun onOpen(item: Any?) {
                                            super.onOpen(item)
                                            val clickListener = android.view.View.OnClickListener {
                                                close()
                                                pendingChargeNav = cid
                                            }
                                            view?.setOnClickListener(clickListener)
                                            (view as? android.view.ViewGroup)?.let { vg ->
                                                for (i in 0 until vg.childCount) {
                                                    vg.getChildAt(i).setOnClickListener(clickListener)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            mapView.overlays.add(marker)
                        }

                        val bb = prep.boundingBox
                        if (bb != null) {
                            mapView.zoomToBoundingBox(bb, false)
                            mapView.invalidate()
                            mapReady = true
                        }
                        } // end mapView.post
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Opaque cover hides the world-view zoom until route is drawn
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (mapReady) 0f else 1f,
                    animationSpec = tween(durationMillis = 300),
                    label = "mapOverlay"
                )
                if (overlayAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMapLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }

                MapOverlayChip(
                    text = dateRangeLabel,
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
                MapOverlayChip(
                    text = distanceLabel,
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                )
        }
    }
}

@Composable
private fun MapOverlayChip(
    text: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    val textColor = if (palette.accent.luminance() > 0.5f) Color(0xFF1E2022) else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.accent.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// === Trip Legs ===

private sealed class TripLeg {
    data class Drive(
        val index: Int,
        val drive: com.matedroid.data.local.entity.DriveSummary
    ) : TripLeg()

    data class Charge(
        val index: Int,
        val charge: com.matedroid.data.local.entity.ChargeSummary
    ) : TripLeg()
}

private fun buildLegList(trip: Trip): List<TripLeg> {
    val legs = mutableListOf<TripLeg>()
    var driveIdx = 0
    var chargeIdx = 0
    val allEvents = mutableListOf<Pair<String, Any>>()
    trip.drives.forEach { allEvents.add(it.startDate to it) }
    trip.charges.forEach { allEvents.add(it.startDate to it) }
    allEvents.sortBy { it.first }
    for ((_, event) in allEvents) {
        when (event) {
            is com.matedroid.data.local.entity.DriveSummary -> {
                driveIdx++
                legs.add(TripLeg.Drive(driveIdx, event))
            }
            is com.matedroid.data.local.entity.ChargeSummary -> {
                chargeIdx++
                legs.add(TripLeg.Charge(chargeIdx, event))
            }
        }
    }
    return legs
}

@Composable
private fun DriveLegCard(
    leg: TripLeg.Drive,
    units: Units?,
    palette: CarColorPalette,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                CustomIcons.SteeringWheel,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${extractCity(leg.drive.startAddress)} → ${extractCity(leg.drive.endAddress)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f %s".format(
                        UnitFormatter.formatDistanceValue(leg.drive.distance, units),
                        UnitFormatter.getDistanceUnit(units)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDuration(leg.drive.durationMin),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChargeLegCard(
    leg: TripLeg.Charge,
    palette: CarColorPalette,
    isDc: Boolean,
    onClick: () -> Unit
) {
    val chipColor = if (isDc) palette.dcColor else palette.acColor
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = chipColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ElectricBolt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = chipColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(chipColor)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (isDc) "DC" else "AC",
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = extractCity(leg.charge.address),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+%.1f kWh".format(leg.charge.energyAdded),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = chipColor
                )
                Text(
                    text = formatDuration(leg.charge.durationMin),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// === Formatting ===

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatTripDateRange(startDate: String, endDate: String): String {
    val start = parseTripDate(startDate) ?: return startDate
    val end = parseTripDate(endDate) ?: return endDate
    val dayMonth = java.time.format.DateTimeFormatter.ofPattern("d MMM")
    val dayMonthYear = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
    val sameDay = start.toLocalDate() == end.toLocalDate()
    val sameMonth = start.year == end.year && start.month == end.month
    val sameYear = start.year == end.year
    return when {
        sameDay -> start.format(if (sameYear) dayMonth else dayMonthYear)
        sameMonth -> "${start.dayOfMonth} – ${end.format(dayMonth)}"
        sameYear -> "${start.format(dayMonth)} – ${end.format(dayMonth)}"
        else -> "${start.format(dayMonthYear)} – ${end.format(dayMonthYear)}"
    }
}

private fun parseTripDate(value: String): java.time.LocalDateTime? = try {
    java.time.OffsetDateTime.parse(value).toLocalDateTime()
} catch (_: java.time.format.DateTimeParseException) {
    try {
        java.time.LocalDateTime.parse(value.replace("Z", ""))
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }
}
