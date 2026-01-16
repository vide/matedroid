package com.matedroid.ui.screens.drives

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.api.models.DrivePosition
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.WeatherPoint
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.theme.CarColorPalettes
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveDetailScreen(
    carId: Int,
    driveId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: DriveDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
                title = { Text("Drive Details") },
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.driveDetail?.let { detail ->
                DriveDetailContent(
                    detail = detail,
                    stats = uiState.stats,
                    units = uiState.units,
                    routeColor = palette.accent,
                    weatherPoints = uiState.weatherPoints,
                    isLoadingWeather = uiState.isLoadingWeather,
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
    routeColor: Color,
    weatherPoints: List<WeatherPoint>,
    isLoadingWeather: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Route header card
        RouteHeaderCard(detail = detail)

        // Map showing the route
        if (!detail.positions.isNullOrEmpty()) {
            DriveMapCard(positions = detail.positions, routeColor = routeColor)
        }

        // Stats grid
        stats?.let { s ->
            // Speed section
            StatsSectionCard(
                title = "Speed",
                icon = Icons.Default.Speed,
                stats = listOf(
                    StatItem("Maximum", UnitFormatter.formatSpeed(s.speedMax.toDouble(), units)),
                    StatItem("Average", UnitFormatter.formatSpeed(s.speedAvg, units)),
                    StatItem("Avg (distance)", UnitFormatter.formatSpeed(s.avgSpeedFromDistance, units))
                )
            )

            // Distance & Duration section
            StatsSectionCard(
                title = "Trip",
                icon = CustomIcons.SteeringWheel,
                stats = listOf(
                    StatItem("Distance", UnitFormatter.formatDistance(s.distance, units)),
                    StatItem("Duration", formatDuration(s.durationMin)),
                    StatItem("Efficiency", UnitFormatter.formatEfficiency(s.efficiency, units))
                )
            )

            // Battery section
            StatsSectionCard(
                title = "Battery",
                icon = Icons.Default.BatteryChargingFull,
                stats = listOf(
                    StatItem("Start", "${s.batteryStart}%"),
                    StatItem("End", "${s.batteryEnd}%"),
                    StatItem("Used", "${s.batteryUsed}%"),
                    StatItem("Energy", "%.2f kWh".format(s.energyUsed))
                )
            )

            // Power section
            StatsSectionCard(
                title = "Power",
                icon = Icons.Default.Bolt,
                stats = listOf(
                    StatItem("Max (accel)", "${s.powerMax} kW"),
                    StatItem("Min (regen)", "${s.powerMin} kW"),
                    StatItem("Average", "%.1f kW".format(s.powerAvg))
                )
            )

            // Elevation section
            if (s.elevationMax > 0 || s.elevationMin > 0) {
                StatsSectionCard(
                    title = "Elevation",
                    icon = Icons.Default.Landscape,
                    stats = listOf(
                        StatItem("Maximum", "${s.elevationMax} m"),
                        StatItem("Minimum", "${s.elevationMin} m"),
                        StatItem("Gain", "+${s.elevationGain} m"),
                        StatItem("Loss", "-${s.elevationLoss} m")
                    )
                )
            }

            // Temperature section
            if (s.outsideTempAvg != null || s.insideTempAvg != null) {
                StatsSectionCard(
                    title = "Temperature",
                    icon = Icons.Default.DeviceThermostat,
                    stats = listOfNotNull(
                        s.outsideTempAvg?.let { StatItem("Outside", UnitFormatter.formatTemperature(it, units)) },
                        s.insideTempAvg?.let { StatItem("Inside", UnitFormatter.formatTemperature(it, units)) }
                    )
                )
            }

            // Charts
            if (!detail.positions.isNullOrEmpty() && detail.positions.size > 2) {
                SpeedChartCard(positions = detail.positions, units = units)
                PowerChartCard(positions = detail.positions)
                BatteryChartCard(positions = detail.positions)
                if (detail.positions.any { it.elevation != null && it.elevation != 0 }) {
                    ElevationChartCard(positions = detail.positions)
                }
            }
        }

        // Weather along the way - shown when loading or has data
        if (isLoadingWeather || weatherPoints.isNotEmpty()) {
            WeatherAlongTheWayCard(
                weatherPoints = weatherPoints,
                units = units,
                isLoading = isLoadingWeather
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RouteHeaderCard(detail: DriveDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "From",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = detail.startAddress ?: "Unknown location",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 36.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            // End location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "To",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = detail.endAddress ?: "Unknown location",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 36.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            // Start time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Started",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatDateTime(detail.startDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // End time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Ended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatDateTime(detail.endDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    detail.durationStr?.let { duration ->
                        Text(
                            text = "Duration: $duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
    val validPositions = positions.filter { it.latitude != null && it.longitude != null }

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
                text = "Route Map",
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
                DisposableEffect(Unit) {
                    Configuration.getInstance().userAgentValue = "MateDroid/1.0"
                    onDispose { }
                }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)

                            // Create polyline for the route
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
                            overlays.add(polyline)

                            // Calculate bounding box with padding
                            if (geoPoints.isNotEmpty()) {
                                val north = geoPoints.maxOf { it.latitude }
                                val south = geoPoints.minOf { it.latitude }
                                val east = geoPoints.maxOf { it.longitude }
                                val west = geoPoints.minOf { it.longitude }

                                // Add some padding
                                val latPadding = (north - south) * 0.15
                                val lonPadding = (east - west) * 0.15

                                val boundingBox = BoundingBox(
                                    north + latPadding,
                                    east + lonPadding,
                                    south - latPadding,
                                    west - lonPadding
                                )

                                post {
                                    zoomToBoundingBox(boundingBox, false)
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

data class StatItem(val label: String, val value: String)

@Composable
private fun StatsSectionCard(
    title: String,
    icon: ImageVector,
    stats: List<StatItem>
) {
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

            // Stats grid - 2 or more columns
            val chunked = stats.chunked(2)
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
                    // Fill empty space if odd number
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
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
private fun SpeedChartCard(positions: List<DrivePosition>, units: Units?) {
    val speeds = positions.mapNotNull { it.speed?.toFloat() }
    if (speeds.size < 2) return

    ChartCard(
        title = "Speed Profile",
        icon = Icons.Default.Speed,
        data = speeds,
        color = MaterialTheme.colorScheme.primary,
        unit = UnitFormatter.getSpeedUnit(units),
        convertValue = { value ->
            if (units?.isImperial == true) (value * 0.621371f) else value
        }
    )
}

@Composable
private fun PowerChartCard(positions: List<DrivePosition>) {
    val powers = positions.mapNotNull { it.power?.toFloat() }
    if (powers.size < 2) return

    ChartCard(
        title = "Power Profile",
        icon = Icons.Default.Bolt,
        data = powers,
        color = MaterialTheme.colorScheme.tertiary,
        unit = "kW",
        showZeroLine = true
    )
}

@Composable
private fun BatteryChartCard(positions: List<DrivePosition>) {
    val batteryLevels = positions.mapNotNull { it.batteryLevel?.toFloat() }
    if (batteryLevels.size < 2) return

    ChartCard(
        title = "Battery Level",
        icon = Icons.Default.BatteryChargingFull,
        data = batteryLevels,
        color = MaterialTheme.colorScheme.secondary,
        unit = "%",
        fixedMinMax = Pair(0f, 100f)
    )
}

@Composable
private fun ElevationChartCard(positions: List<DrivePosition>) {
    val elevations = positions.mapNotNull { it.elevation?.toFloat() }
    if (elevations.size < 2) return

    ChartCard(
        title = "Elevation Profile",
        icon = Icons.Default.Landscape,
        data = elevations,
        color = Color(0xFF8B4513), // Brown color for terrain
        unit = "m"
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
    convertValue: (Float) -> Float = { it }
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

            val convertedData = data.map { convertValue(it) }
            val minValue = fixedMinMax?.first ?: convertedData.minOrNull() ?: 0f
            val maxValue = fixedMinMax?.second ?: convertedData.maxOrNull() ?: 1f
            val range = (maxValue - minValue).coerceAtLeast(1f)

            val surfaceColor = MaterialTheme.colorScheme.onSurface
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (convertedData.size - 1).coerceAtLeast(1)

                // Draw grid lines
                val gridLineCount = 4
                for (i in 0..gridLineCount) {
                    val y = height * i / gridLineCount
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Draw zero line if needed (for power chart)
                if (showZeroLine && minValue < 0 && maxValue > 0) {
                    val zeroY = height * (1 - (0f - minValue) / range)
                    drawLine(
                        color = surfaceColor.copy(alpha = 0.5f),
                        start = Offset(0f, zeroY),
                        end = Offset(width, zeroY),
                        strokeWidth = 2f
                    )
                }

                // Draw the line chart
                if (convertedData.size >= 2) {
                    for (i in 0 until convertedData.size - 1) {
                        val x1 = i * stepX
                        val x2 = (i + 1) * stepX
                        val y1 = height * (1 - (convertedData[i] - minValue) / range)
                        val y2 = height * (1 - (convertedData[i + 1] - minValue) / range)

                        drawLine(
                            color = color,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.5f
                        )
                    }
                }

                // Draw Y-axis labels for all grid lines
                drawContext.canvas.nativeCanvas.apply {
                    val textPaint = Paint().apply {
                        this.color = surfaceColor.copy(alpha = 0.7f).toArgb()
                        textSize = 26f
                        isAntiAlias = true
                    }

                    for (i in 0..gridLineCount) {
                        val y = height * i / gridLineCount
                        val value = maxValue - (range * i / gridLineCount)
                        val label = "%.0f".format(value) + " $unit"

                        // Position the label: top labels below line, bottom labels above line
                        val textY = when (i) {
                            0 -> y + textPaint.textSize + 2f
                            gridLineCount -> y - 4f
                            else -> y + textPaint.textSize / 3
                        }

                        drawText(label, 8f, textY, textPaint)
                    }
                }
            }
        }
    }
}

private fun formatDateTime(dateStr: String?): String {
    if (dateStr == null) return "Unknown"
    return try {
        val dateTime = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy 'at' HH:mm")
        dateTime.format(formatter)
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
