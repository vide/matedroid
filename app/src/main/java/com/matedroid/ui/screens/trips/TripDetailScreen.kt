package com.matedroid.ui.screens.trips

import android.graphics.Paint
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.BuildConfig
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.components.createPinMarkerDrawable
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    carId: Int,
    tripIndex: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: TripDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId, tripIndex) { viewModel.loadTrip(carId, tripIndex) }

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
                TripDetailContent(
                    trip = trip,
                    routePoints = uiState.routePoints,
                    markers = uiState.markers,
                    isMapLoading = uiState.isMapLoading,
                    units = uiState.units,
                    palette = palette,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun TripDetailContent(
    trip: Trip,
    routePoints: List<TripRoutePoint>,
    markers: List<TripMapMarker>,
    isMapLoading: Boolean,
    units: Units?,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Route header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = extractCity(trip.startAddress),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = extractCity(trip.endAddress),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = formatDate(trip.startDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                        )
                    }
                }
            }
        }

        // Map
        item {
            TripMapCard(
                routePoints = routePoints,
                markers = markers,
                isMapLoading = isMapLoading,
                palette = palette
            )
        }

        // Summary stats — palette-colored summary card
        item {
            SummaryStatsCard(trip = trip, units = units, palette = palette)
        }

        // Legs header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.trip_legs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Interleaved legs
        val legs = buildLegList(trip)
        itemsIndexed(legs) { _, leg ->
            when (leg) {
                is TripLeg.Drive -> DriveLegCard(leg, units, palette)
                is TripLeg.Charge -> ChargeLegCard(leg, palette)
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun TripMapCard(
    routePoints: List<TripRoutePoint>,
    markers: List<TripMapMarker>,
    isMapLoading: Boolean,
    palette: CarColorPalette
) {
    val startColorArgb = StatusSuccess.toArgb()
    val chargeColorArgb = palette.accent.toArgb()
    val endColorArgb = StatusError.toArgb()
    val routeColorArgb = palette.accent.toArgb()

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
                } else if (routePoints.isNotEmpty()) {
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

                                // Route polyline from actual GPS positions
                                val geoPoints = routePoints.map {
                                    GeoPoint(it.latitude, it.longitude)
                                }
                                val polyline = Polyline().apply {
                                    setPoints(geoPoints)
                                    outlinePaint.color = routeColorArgb
                                    outlinePaint.strokeWidth = 8f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                }
                                overlays.add(polyline)

                                // Markers for start, charges, end
                                markers.forEach { point ->
                                    val color = when (point.type) {
                                        TripMapPointType.START -> startColorArgb
                                        TripMapPointType.CHARGE -> chargeColorArgb
                                        TripMapPointType.END -> endColorArgb
                                    }
                                    val marker = Marker(this).apply {
                                        position =
                                            GeoPoint(point.latitude, point.longitude)
                                        setAnchor(
                                            Marker.ANCHOR_CENTER,
                                            Marker.ANCHOR_BOTTOM
                                        )
                                        title = point.label
                                        icon = createPinMarkerDrawable(
                                            mapCtx.resources, color
                                        )
                                    }
                                    overlays.add(marker)
                                }

                                // Zoom to fit route
                                if (geoPoints.isNotEmpty()) {
                                    val north = geoPoints.maxOf { it.latitude }
                                    val south = geoPoints.minOf { it.latitude }
                                    val east = geoPoints.maxOf { it.longitude }
                                    val west = geoPoints.minOf { it.longitude }
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

@Composable
private fun SummaryStatsCard(trip: Trip, units: Units?, palette: CarColorPalette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.trip_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PaletteSummaryItem(
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.distance),
                    value = UnitFormatter.formatDistance(trip.totalDistance, units),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                PaletteSummaryItem(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.trip_total_time),
                    value = formatDuration(trip.totalDurationMin),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PaletteSummaryItem(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.trip_driving_time),
                    value = formatDuration(trip.totalDrivingDurationMin),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                PaletteSummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.trip_charge_stops),
                    value = "${trip.charges.size}",
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PaletteSummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.trip_energy_consumed),
                    value = "%.1f kWh".format(trip.totalEnergyConsumed),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                PaletteSummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.trip_energy_charged),
                    value = "%.1f kWh".format(trip.totalEnergyCharged),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PaletteSummaryItem(
                    icon = Icons.Filled.BatteryChargingFull,
                    label = stringResource(R.string.battery),
                    value = "${trip.startBatteryLevel}% → ${trip.endBatteryLevel}%",
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                trip.avgEfficiency?.let { eff ->
                    PaletteSummaryItem(
                        icon = Icons.Filled.Speed,
                        label = stringResource(R.string.efficiency),
                        value = "%.0f %s".format(eff, UnitFormatter.getEfficiencyUnit(units)),
                        palette = palette,
                        modifier = Modifier.weight(0.8f)
                    )
                } ?: Spacer(modifier = Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun PaletteSummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = palette.accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }
    }
}

// Leg types for the interleaved legs list
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
    palette: CarColorPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
        }
    }
}

@Composable
private fun ChargeLegCard(
    leg: TripLeg.Charge,
    palette: CarColorPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDate(dateStr: String): String {
    return try {
        val inputFormatter = DateTimeFormatter.ISO_DATE_TIME
        val outputFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        val dateTime = LocalDateTime.parse(dateStr, inputFormatter)
        dateTime.format(outputFormatter)
    } catch (e: Exception) {
        dateStr
    }
}
