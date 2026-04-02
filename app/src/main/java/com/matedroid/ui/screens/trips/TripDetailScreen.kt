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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.BuildConfig
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.components.createPinMarkerDrawable
import com.matedroid.ui.components.createZapMarkerDrawable
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_detail_title)) },
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
                        onDriveClick = onNavigateToDriveDetail,
                        onChargeClick = onNavigateToChargeDetail,
                        onCountryClick = onNavigateToCountryStats,
                        currencySymbol = uiState.currencySymbol
                    )
                }
            }
        }
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
    onDriveClick: (driveId: Int) -> Unit,
    onChargeClick: (chargeId: Int) -> Unit,
    onCountryClick: (countryCode: String) -> Unit,
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
        RouteHeaderCard(trip, countries, onCountryClick)

        TripMapCard(
            routeSegments = routeSegments,
            markers = markers,
            isMapLoading = isMapLoading,
            palette = palette,
            onChargeClick = onChargeClick
        )

        StatsSectionCard(
            title = stringResource(R.string.trip_summary),
            icon = Icons.Filled.Route,
            stats = listOfNotNull(
                StatItem(stringResource(R.string.distance), UnitFormatter.formatDistance(trip.totalDistance, units)),
                StatItem(stringResource(R.string.trip_total_time), formatDuration(trip.totalDurationMin)),
                StatItem(stringResource(R.string.trip_driving_time), formatDuration(trip.totalDrivingDurationMin)),
                StatItem(stringResource(R.string.trip_legs), "${trip.drives.size + trip.charges.size}"),
                StatItem(stringResource(R.string.trip_charge_stops), "${trip.charges.size}")
            )
        )

        if (trip.totalChargeCost != null) {
            ChargeCostCard(trip = trip, currencySymbol = currencySymbol, onChargeClick = onChargeClick)
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

        Text(
            text = stringResource(R.string.trip_legs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val legs = buildLegList(trip)
        legs.forEach { leg ->
            when (leg) {
                is TripLeg.Drive -> DriveLegCard(leg, units, palette) {
                    onDriveClick(leg.drive.driveId)
                }
                is TripLeg.Charge -> ChargeLegCard(leg, palette) {
                    onChargeClick(leg.charge.chargeId)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// === Route Header — matches DriveDetailScreen ===

@Composable
private fun RouteHeaderCard(
    trip: Trip,
    countries: List<TripCountry>,
    onCountryClick: (countryCode: String) -> Unit
) {
    val startFlag = countries.firstOrNull()?.flagEmoji
    val endFlag = if (countries.size >= 2) countries.last().flagEmoji else startFlag
    val midFlags = if (countries.size > 2) countries.subList(1, countries.size - 1) else emptyList()
    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Start stop: flag + city + time
            val startCountry = countries.firstOrNull()
            TimelineStop(
                flag = startCountry?.flagEmoji,
                fallbackColor = StatusSuccess,
                city = extractCity(trip.startAddress),
                label = stringResource(R.string.from),
                time = formatDateTime(trip.startDate),
                onFlagClick = startCountry?.let { { onCountryClick(it.countryCode) } }
            )

            // Line + intermediate countries
            if (midFlags.isEmpty()) {
                TimelineLine(lineColor, height = 16)
            } else {
                midFlags.forEach { country ->
                    TimelineLine(lineColor, height = 8)
                    TimelineStop(
                        flag = country.flagEmoji,
                        flagSize = 20,
                        city = null,
                        label = null,
                        onFlagClick = { onCountryClick(country.countryCode) }
                    )
                }
                TimelineLine(lineColor, height = 8)
            }

            // End stop: flag + city + time
            val endCountry = if (countries.size >= 2) countries.last() else startCountry
            TimelineStop(
                flag = endCountry?.flagEmoji,
                fallbackColor = StatusError,
                city = extractCity(trip.endAddress),
                label = stringResource(R.string.to),
                time = formatDateTime(trip.endDate),
                onFlagClick = endCountry?.let { { onCountryClick(it.countryCode) } }
            )
        }
    }
}

/** A single stop on the timeline: flag marker + city label + optional right-aligned time. */
@Composable
private fun TimelineStop(
    flag: String?,
    flagSize: Int = 24,
    fallbackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    city: String?,
    label: String?,
    time: String? = null,
    onFlagClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Flag or colored dot, centered in 32dp column
        Box(
            modifier = Modifier
                .width(32.dp)
                .then(if (onFlagClick != null) Modifier.clickable(onClick = onFlagClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (flag != null) {
                Text(text = flag, fontSize = flagSize.sp)
            } else {
                Box(
                    modifier = Modifier
                        .size((flagSize - 4).dp)
                        .background(fallbackColor, shape = RoundedCornerShape(50))
                )
            }
        }
        if (city != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (time != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** Vertical connecting line between timeline stops. */
@Composable
private fun TimelineLine(
    color: androidx.compose.ui.graphics.Color,
    height: Int = 16
) {
    Box(
        modifier = Modifier.width(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(height.dp)
                .background(color.copy(alpha = 0.3f))
        )
    }
}

// === Charge Cost Card ===

@Composable
private fun ChargeCostCard(
    trip: Trip,
    currencySymbol: String,
    onChargeClick: (chargeId: Int) -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                trip.totalChargeCost?.let {
                    Text(
                        text = "%.2f %s".format(it, currencySymbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Per-charge breakdown
            trip.charges.filter { it.cost != null }.forEach { charge ->
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
                            fontWeight = FontWeight.Bold
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

@Composable
private fun TripMapCard(
    routeSegments: List<TripRouteSegment>,
    markers: List<TripMapMarker>,
    isMapLoading: Boolean,
    palette: CarColorPalette,
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

    val startColorArgb = StatusSuccess.toArgb()
    val chargeColorArgb = palette.accent.toArgb()
    val endColorArgb = StatusError.toArgb()
    val oddLegColorArgb = palette.accent.toArgb()
    val evenLegColorArgb = palette.accent.copy(alpha = 0.55f)
        .compositeOver(androidx.compose.ui.graphics.Color.White)
        .toArgb()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.trip_route_map),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (isMapLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
                } else if (routeSegments.isNotEmpty()) {
                    DisposableEffect(Unit) {
                        Configuration.getInstance().userAgentValue =
                            "MateDroid/${BuildConfig.VERSION_NAME}"
                        onDispose { }
                    }
                    AndroidView(
                        factory = { mapCtx ->
                            MapView(mapCtx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)

                                routeSegments.forEachIndexed { index, segment ->
                                    val geoPoints = segment.points.map {
                                        GeoPoint(it.latitude, it.longitude)
                                    }
                                    if (geoPoints.size >= 2) {
                                        val polyline = Polyline().apply {
                                            setPoints(geoPoints)
                                            outlinePaint.color =
                                                if (index % 2 == 0) oddLegColorArgb
                                                else evenLegColorArgb
                                            outlinePaint.strokeWidth = 8f
                                            outlinePaint.strokeCap = Paint.Cap.ROUND
                                            outlinePaint.strokeJoin = Paint.Join.ROUND
                                        }
                                        overlays.add(polyline)
                                    }
                                }

                                val mapView = this
                                markers.forEach { point ->
                                    val color = when (point.type) {
                                        TripMapPointType.START -> startColorArgb
                                        TripMapPointType.CHARGE -> chargeColorArgb
                                        TripMapPointType.END -> endColorArgb
                                    }
                                    val markerIcon = when (point.type) {
                                        TripMapPointType.CHARGE -> createZapMarkerDrawable(mapCtx.resources, color)
                                        else -> createPinMarkerDrawable(mapCtx.resources, color)
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
                                                    // Set click on every child so any tap on the bubble navigates
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
                                    overlays.add(marker)
                                }

                                val allPoints = routeSegments.flatMap { it.points }
                                if (allPoints.isNotEmpty()) {
                                    val north = allPoints.maxOf { it.latitude }
                                    val south = allPoints.minOf { it.latitude }
                                    val east = allPoints.maxOf { it.longitude }
                                    val west = allPoints.minOf { it.longitude }
                                    val latPad = (north - south) * 0.15
                                    val lonPad = (east - west) * 0.15
                                    val bb = BoundingBox(
                                        north + latPad, east + lonPad,
                                        south - latPad, west - lonPad
                                    )
                                    post {
                                        zoomToBoundingBox(bb, false)
                                        invalidate()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.trip_leg_drive, leg.index),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${extractCity(leg.drive.startAddress)} → ${extractCity(leg.drive.endAddress)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = palette.dcColor.copy(alpha = 0.1f)
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
                tint = palette.dcColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.trip_leg_charge, leg.index),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = leg.charge.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+%.1f kWh".format(leg.charge.energyAdded),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.dcColor
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

private fun formatDateTime(dateStr: String): String {
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        dt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        dateStr
    }
}
