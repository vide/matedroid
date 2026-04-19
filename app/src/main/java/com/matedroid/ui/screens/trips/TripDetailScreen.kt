package com.matedroid.ui.screens.trips

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
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

        StatsSectionCard(
            title = stringResource(R.string.battery),
            icon = Icons.Filled.BatteryChargingFull,
            stats = listOfNotNull(
                StatItem(stringResource(R.string.trip_energy_consumed), "%.1f kWh".format(trip.totalEnergyConsumed)),
                StatItem(stringResource(R.string.trip_energy_charged), "%.1f kWh".format(trip.totalEnergyCharged)),
                trip.avgEfficiency?.let {
                    StatItem(stringResource(R.string.efficiency), "%.0f %s".format(it, UnitFormatter.getEfficiencyUnit(units)))
                }
            )
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
            }

            if (expanded) {
                if (trip.totalDistance > 0.0 && trip.totalChargeCost != null) {
                    val per100 = trip.totalChargeCost / trip.totalDistance * 100.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.trip_cost_per_100),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%.2f %s / 100 %s".format(
                                per100,
                                currencySymbol,
                                UnitFormatter.getDistanceUnit(units)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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

// === Stats — StatsSectionCard pattern from DriveDetailScreen ===

private data class StatItem(val label: String, val value: String)

@Composable
private fun StatsSectionCard(
    title: String,
    icon: ImageVector,
    stats: List<StatItem>
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val columnCount = when {
        screenWidth > 600 -> 4
        screenWidth > 340 -> 3
        else -> 2
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val chunked = stats.chunked(columnCount)
            chunked.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { stat ->
                        StatItemView(
                            label = stat.label,
                            value = stat.value,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val emptySlots = columnCount - row.size
                    if (emptySlots > 0) {
                        repeat(emptySlots) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                if (index < chunked.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItemView(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
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
