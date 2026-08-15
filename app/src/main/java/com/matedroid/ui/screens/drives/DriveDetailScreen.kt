package com.matedroid.ui.screens.drives

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt
import com.matedroid.R
import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.api.models.DrivePosition
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.WeatherPoint
import com.matedroid.domain.DriveComparison
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.FullscreenLineChart
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.components.RouteMapView
import com.matedroid.ui.components.boundingBoxOf
import com.matedroid.ui.components.extractTimeLabels
import com.matedroid.ui.screens.trips.displayName
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.formatTime
import com.matedroid.util.parseIsoDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveDetailScreen(
    carId: Int,
    driveId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTripDetail: (tripStartDate: String) -> Unit = {},
    onNavigateToCompare: (baseDriveId: Int) -> Unit = {},
    viewModel: DriveDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId, driveId) {
        viewModel.loadDriveDetail(carId, driveId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drive_details_title)) },
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
        if (uiState.isLoading) {
            MateDroidLoadingPlaceholder(
                color = palette.accent,
                modifier = Modifier.padding(padding)
            )
        } else {
            uiState.driveDetail?.let { detail ->
                DriveDetailContent(
                    detail = detail,
                    stats = uiState.stats,
                    units = uiState.units,
                    palette = palette,
                    weatherPoints = uiState.weatherPoints,
                    isLoadingWeather = uiState.isLoadingWeather,
                    containingTrip = uiState.containingTrip,
                    comparison = uiState.comparison,
                    onCompareClick = { onNavigateToCompare(driveId) },
                    onNavigateToTripDetail = onNavigateToTripDetail,
                    onRemoveFromTrip = viewModel::removeFromTrip,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun DriveDetailContent(
    detail: DriveDetail,
    stats: DriveDetailStats?,
    units: Units?,
    palette: CarColorPalette,
    weatherPoints: List<WeatherPoint>,
    isLoadingWeather: Boolean,
    containingTrip: Pair<Long, com.matedroid.domain.model.Trip>?,
    comparison: DriveComparison?,
    onCompareClick: () -> Unit,
    onNavigateToTripDetail: (String) -> Unit,
    onRemoveFromTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val scrollState = rememberScrollState()
    var sharedXFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { isScrolling -> if (isScrolling) sharedXFraction = null }
    }

    val positions = detail.positions
    val hasCharts = positions != null && positions.size > 2
    val timeLabels = remember(positions) {
        if (hasCharts) extractTimeLabels(positions!!.map { it.date }, is24Hour) else emptyList()
    }
    val hasSpeedData = remember(positions) {
        hasCharts && positions!!.any { it.speed != null }
    }
    val fractionToTimeLabel: (Float) -> String = remember(positions) {
        label@{ fraction: Float ->
            val pos = positions
            if (pos == null || pos.size <= 2) return@label ""
            val index = (fraction * pos.lastIndex).roundToInt().coerceIn(0, pos.lastIndex)
            pos[index].date?.let { dateStr ->
                parseIsoDateTime(dateStr)?.formatTime(java.util.Locale.getDefault(), is24Hour) ?: ""
            } ?: ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { sharedXFraction = null } }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero: route, dominant distance, key figures, footer
        DriveHeroSection(detail = detail, stats = stats, units = units, palette = palette, is24Hour = is24Hour)

        // Accent stat tiles — efficiency and its main confounders
        stats?.let { s -> DriveStatTiles(stats = s, units = units, palette = palette) }

        // Compare entry — appears for drives with comparable sessions on the same route
        comparison?.let { cmp ->
            DriveCompareCard(comparison = cmp, palette = palette, onClick = onCompareClick)
        }

        // Primary chart: speed profile, accent-tinted
        if (hasSpeedData) {
            SpeedChartCard(
                positions = positions!!,
                units = units,
                color = palette.accent,
                timeLabels = timeLabels,
                externalSelectedFraction = sharedXFraction,
                onXSelected = { sharedXFraction = it },
                fractionToTimeLabel = fractionToTimeLabel
            )
        }

        // Route map
        if (!detail.positions.isNullOrEmpty()) {
            DriveMapCard(positions = detail.positions, routeColor = palette.accent)
        }

        // Weather along the way
        if (isLoadingWeather || weatherPoints.isNotEmpty()) {
            WeatherAlongTheWayCard(
                weatherPoints = weatherPoints,
                units = units,
                isLoading = isLoadingWeather
            )
        }

        // Everything else behind a tap
        stats?.let { s ->
            DriveMoreDetails(
                detail = detail,
                stats = s,
                units = units,
                palette = palette,
                positions = if (hasCharts) positions else null,
                timeLabels = timeLabels,
                sharedXFraction = sharedXFraction,
                onXSelected = { sharedXFraction = it },
                fractionToTimeLabel = fractionToTimeLabel
            )
        }

        // Part-of-trip banner — kept at the very end
        if (containingTrip != null) {
            val (_, trip) = containingTrip
            com.matedroid.ui.components.PartOfTripCard(
                tripRoute = trip.displayName(),
                onNavigateToTrip = { onNavigateToTripDetail(trip.startDate) },
                onConfirmRemove = onRemoveFromTrip
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Compact hero: route (from → to), the dominant figure (distance) in the car's accent colour, a
 * balanced row of labelled key figures (avg speed, battery swing, duration), and a footer with the
 * start time and energy used. Replaces the old verbose header.
 */
@Composable
private fun DriveHeroSection(
    detail: DriveDetail,
    stats: DriveDetailStats?,
    units: Units?,
    palette: CarColorPalette,
    is24Hour: Boolean
) {
    val unknownLocation = stringResource(R.string.unknown_location)
    val avgLabel = stringResource(R.string.average)
    val batteryLabel = stringResource(R.string.battery)
    val durationLabel = stringResource(R.string.duration)
    val energyLabel = stringResource(R.string.energy)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Route
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RouteLine(
                color = palette.accent,
                label = stringResource(R.string.from),
                value = detail.startAddress ?: unknownLocation
            )
            RouteLine(
                color = MaterialTheme.colorScheme.tertiary,
                label = stringResource(R.string.to),
                value = detail.endAddress ?: unknownLocation
            )
        }

        // Dominant figure: distance
        stats?.let { s ->
            Text(
                text = UnitFormatter.formatDistance(s.distance, units),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )

            // Balanced row of labelled key figures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroStat(
                    label = avgLabel,
                    value = UnitFormatter.formatSpeed(s.speedAvg, units),
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    label = batteryLabel,
                    value = "${s.batteryStart}% → ${s.batteryEnd}%",
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    label = durationLabel,
                    value = formatDurationCompact(s.durationMin),
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Footer: start time and energy used
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = formatDateTime(detail.startDate, is24Hour)
                            ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$energyLabel · %.1f kWh".format(s.energyUsed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteLine(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** A single labelled figure: small uppercase label over a bold value. */
@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** A row of accent tiles for efficiency and its main confounders (temperature, elevation). */
@Composable
private fun DriveStatTiles(
    stats: DriveDetailStats,
    units: Units?,
    palette: CarColorPalette
) {
    val efficiencyLabel = stringResource(R.string.efficiency)
    val temperatureLabel = stringResource(R.string.temperature)
    val elevationLabel = stringResource(R.string.elevation)

    // Climb and descent are shown together: on a downhill drive the climb alone says nothing
    // about where the drive actually went.
    val hasElevation = stats.elevationClimb > 0 || stats.elevationDescent > 0
    val elevationValue = if (hasElevation) {
        stringResource(
            R.string.elevation_climb_descent,
            UnitFormatter.getElevationValue(stats.elevationClimb.toFloat(), units).toInt(),
            UnitFormatter.getElevationValue(stats.elevationDescent.toFloat(), units).toInt(),
            UnitFormatter.getElevationUnit(units)
        )
    } else {
        null
    }

    val tiles = buildList {
        add(efficiencyLabel to UnitFormatter.formatEfficiency(stats.efficiency, units))
        stats.outsideTempAvg?.let { add(temperatureLabel to UnitFormatter.formatTemperature(it, units)) }
        elevationValue?.let { add(elevationLabel to it) }
    }
    if (tiles.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { (label, value) ->
            DriveStatTile(label = label, value = value, accent = palette.accent, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DriveStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            // The elevation tile packs climb and descent into one line, which is far wider than
            // the other tiles' values: shrink it to fit instead of truncating it.
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 13.sp,
                maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                stepSize = 0.5.sp
            )
        )
    }
}

/** Entry point into the drive-comparison screen, shown only for drives with comparable siblings. */
@Composable
private fun DriveCompareCard(
    comparison: DriveComparison,
    palette: CarColorPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
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
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.compare_drives_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(
                    R.plurals.compare_drives_nearby,
                    comparison.others.size,
                    comparison.others.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Collapsible section holding the detailed stat cards and the secondary charts. */
@Composable
private fun DriveMoreDetails(
    detail: DriveDetail,
    stats: DriveDetailStats,
    units: Units?,
    palette: CarColorPalette,
    positions: List<DrivePosition>?,
    timeLabels: List<String>,
    sharedXFraction: Float?,
    onXSelected: (Float?) -> Unit,
    fractionToTimeLabel: (Float) -> String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "driveMoreDetailsChevron")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (expanded) R.string.hide_details else R.string.more_details),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                StatsSectionCard(
                    title = stringResource(R.string.speed),
                    icon = Icons.Default.Speed,
                    stats = listOf(
                        StatItem(stringResource(R.string.maximum), UnitFormatter.formatSpeed(stats.speedMax.toDouble(), units)),
                        StatItem(stringResource(R.string.average), UnitFormatter.formatSpeed(stats.speedAvg, units)),
                        StatItem(stringResource(R.string.avg_distance), UnitFormatter.formatSpeed(stats.avgSpeedFromDistance, units))
                    )
                )
                StatsSectionCard(
                    title = stringResource(R.string.trip),
                    icon = CustomIcons.SteeringWheel,
                    stats = listOf(
                        StatItem(stringResource(R.string.distance), UnitFormatter.formatDistance(stats.distance, units)),
                        StatItem(stringResource(R.string.duration), formatDurationCompact(stats.durationMin)),
                        StatItem(stringResource(R.string.efficiency), UnitFormatter.formatEfficiency(stats.efficiency, units))
                    )
                )
                StatsSectionCard(
                    title = stringResource(R.string.battery),
                    icon = Icons.Default.BatteryChargingFull,
                    stats = listOf(
                        StatItem(stringResource(R.string.start), "${stats.batteryStart}%"),
                        StatItem(stringResource(R.string.end), "${stats.batteryEnd}%"),
                        StatItem(stringResource(R.string.used), "${stats.batteryUsed}%"),
                        StatItem(stringResource(R.string.energy), "%.2f kWh".format(stats.energyUsed))
                    )
                )
                StatsSectionCard(
                    title = stringResource(R.string.power),
                    icon = Icons.Default.Bolt,
                    stats = listOf(
                        StatItem(stringResource(R.string.max_accel), "${stats.powerMax} kW"),
                        StatItem(stringResource(R.string.min_regen), "${stats.powerMin} kW"),
                        StatItem(stringResource(R.string.average), "%.1f kW".format(stats.powerAvg))
                    )
                )
                if (stats.elevationMax > 0 || stats.elevationMin > 0) {
                    StatsSectionCard(
                        title = stringResource(R.string.elevation),
                        icon = Icons.Default.Landscape,
                        stats = listOf(
                            StatItem(stringResource(R.string.maximum), UnitFormatter.formatElevation(stats.elevationMax, units)),
                            StatItem(stringResource(R.string.minimum), UnitFormatter.formatElevation(stats.elevationMin, units)),
                            StatItem(stringResource(R.string.elevation_climb), UnitFormatter.formatSignedElevation(stats.elevationClimb, units)),
                            StatItem(stringResource(R.string.elevation_descent), UnitFormatter.formatSignedElevation(-stats.elevationDescent, units)),
                            StatItem(stringResource(R.string.elevation_net), UnitFormatter.formatSignedElevation(stats.elevationNet, units))
                        )
                    )
                }
                if (stats.outsideTempAvg != null || stats.insideTempAvg != null) {
                    StatsSectionCard(
                        title = stringResource(R.string.temperature),
                        icon = Icons.Default.DeviceThermostat,
                        stats = listOfNotNull(
                            stats.outsideTempAvg?.let { StatItem(stringResource(R.string.outside), UnitFormatter.formatTemperature(it, units)) },
                            stats.insideTempAvg?.let { StatItem(stringResource(R.string.inside), UnitFormatter.formatTemperature(it, units)) }
                        )
                    )
                }

                // Secondary charts
                if (positions != null) {
                    PowerChartCard(
                        positions = positions,
                        timeLabels = timeLabels,
                        externalSelectedFraction = sharedXFraction,
                        onXSelected = onXSelected,
                        fractionToTimeLabel = fractionToTimeLabel
                    )
                    BatteryChartCard(
                        positions = positions,
                        timeLabels = timeLabels,
                        externalSelectedFraction = sharedXFraction,
                        onXSelected = onXSelected,
                        fractionToTimeLabel = fractionToTimeLabel
                    )
                    val hasElevationData = remember(positions) {
                        positions.any { it.elevation != null && it.elevation != 0 }
                    }
                    if (hasElevationData) {
                        ElevationChartCard(
                            positions = positions,
                            timeLabels = timeLabels,
                            externalSelectedFraction = sharedXFraction,
                            onXSelected = onXSelected,
                            fractionToTimeLabel = fractionToTimeLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveMapCard(positions: List<DrivePosition>, routeColor: Color) {
    val context = LocalContext.current
    val routeColorArgb = routeColor.toArgb()
    val validPositions = remember(positions) {
        positions.filter { it.latitude != null && it.longitude != null }
    }

    if (validPositions.isEmpty()) return

    val startPoint = validPositions.firstOrNull()
    val endPoint = validPositions.lastOrNull()

    fun openInMaps() {
        if (startPoint != null && endPoint != null) {
            // Open Google Maps with directions from start to end
            val uri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1" +
                        "&origin=${startPoint.latitude},${startPoint.longitude}" +
                        "&destination=${endPoint.latitude},${endPoint.longitude}" +
                        "&travelmode=driving"
            )
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openInMaps() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.route_map),
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
                RouteMapView(
                    onMapReady = { mapView ->
                        val geoPoints = validPositions.map { pos ->
                            GeoPoint(pos.latitude!!, pos.longitude!!)
                        }

                        val polyline = Polyline().apply {
                            setPoints(geoPoints)
                            outlinePaint.color = routeColorArgb
                            outlinePaint.strokeWidth = 8f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        mapView.overlays.add(polyline)

                        if (geoPoints.isNotEmpty()) {
                            val boundingBox = boundingBoxOf(geoPoints)
                            mapView.post {
                                mapView.zoomToBoundingBox(boundingBox, false)
                                mapView.invalidate()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

data class StatItem(val label: String, val value: String)

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
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                        repeat(emptySlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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

@Composable
private fun SpeedChartCard(
    positions: List<DrivePosition>,
    units: Units?,
    color: Color,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val speeds = remember(positions) { positions.mapNotNull { it.speed?.toFloat() } }
    if (speeds.size < 2) return

    // Speed is already in the user's unit (the API pre-converts before we store
    // it), so the chart plots the raw value — no km/h→mph math here.
    ChartCard(
        title = stringResource(R.string.speed_profile),
        icon = Icons.Default.Speed,
        data = speeds,
        color = color,
        unit = UnitFormatter.getSpeedUnit(units),
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun PowerChartCard(
    positions: List<DrivePosition>,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val powers = remember(positions) { positions.mapNotNull { it.power?.toFloat() } }
    if (powers.size < 2) return

    ChartCard(
        title = stringResource(R.string.power_profile),
        icon = Icons.Default.Bolt,
        data = powers,
        color = MaterialTheme.colorScheme.tertiary,
        unit = "kW",
        showZeroLine = true,
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun BatteryChartCard(
    positions: List<DrivePosition>,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val batteryLevels = remember(positions) { positions.mapNotNull { it.batteryLevel?.toFloat() } }
    if (batteryLevels.size < 2) return
    val fixedMinMax = remember(batteryLevels) {
        var yMin = (kotlin.math.floor(batteryLevels.min() / 10.0) * 10).toFloat()
        var yMax = (kotlin.math.ceil(batteryLevels.max() / 10.0) * 10).toFloat()
        if (yMin == yMax) { yMin -= 1; yMax += 1 }
        Pair(yMin, yMax)
    }

    ChartCard(
        title = stringResource(R.string.battery_level),
        icon = Icons.Default.BatteryChargingFull,
        data = batteryLevels,
        color = MaterialTheme.colorScheme.secondary,
        unit = "%",
        fixedMinMax = fixedMinMax,
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun ElevationChartCard(
    positions: List<DrivePosition>,
    timeLabels: List<String>,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val elevations = remember(positions) { positions.mapNotNull { it.elevation?.toFloat() } }
    if (elevations.size < 2) return

    ChartCard(
        title = stringResource(R.string.elevation_profile),
        icon = Icons.Default.Landscape,
        data = elevations,
        color = Color(0xFF8B4513),
        unit = "m",
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

@Composable
private fun ChartCard(
    title: String,
    icon: ImageVector,
    data: List<Float>,
    color: Color,
    unit: String,
    showZeroLine: Boolean = false,
    fixedMinMax: Pair<Float, Float>? = null,
    timeLabels: List<String> = emptyList(),
    convertValue: (Float) -> Float = { it },
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FullscreenLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                convertValue = convertValue,
                externalSelectedFraction = externalSelectedFraction,
                onXSelected = onXSelected,
                fractionToTimeLabel = fractionToTimeLabel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatDateTime(dateStr: String?, is24Hour: Boolean? = null): String? {
    if (dateStr.isNullOrBlank()) return null
    val dt = parseIsoDateTime(dateStr) ?: return dateStr
    val locale = java.util.Locale.getDefault()
    return "${dt.toLocalDate().formatMedium(locale)} ${dt.formatTime(locale, is24Hour)}"
}
