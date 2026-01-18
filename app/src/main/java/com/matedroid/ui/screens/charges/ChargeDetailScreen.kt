package com.matedroid.ui.screens.charges

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargePoint
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.FullscreenLineChart
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeDetailScreen(
    carId: Int,
    chargeId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: ChargeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(carId, chargeId) {
        viewModel.loadChargeDetail(carId, chargeId)
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
                title = { Text(stringResource(R.string.charge_details_title)) },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.chargeDetail?.let { detail ->
                ChargeDetailContent(
                    detail = detail,
                    stats = uiState.stats,
                    units = uiState.units,
                    currencySymbol = uiState.currencySymbol,
                    isDcCharge = uiState.isDcCharge,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ChargeDetailContent(
    detail: ChargeDetail,
    stats: ChargeDetailStats?,
    units: Units?,
    currencySymbol: String,
    isDcCharge: Boolean,
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
        // Location header card
        LocationHeaderCard(detail = detail, currencySymbol = currencySymbol, isDcCharge = isDcCharge)

        // Map showing charge location
        if (detail.latitude != null && detail.longitude != null) {
            ChargeMapCard(latitude = detail.latitude, longitude = detail.longitude)
        }

        // Stats grid
        stats?.let { s ->
            // Localized labels
            val energyLabel = stringResource(R.string.energy)
            val batteryLabel = stringResource(R.string.battery)
            val powerLabel = stringResource(R.string.power)
            val chargerLabel = stringResource(R.string.charger)
            val temperatureLabel = stringResource(R.string.temperature)
            val costLabel = stringResource(R.string.cost)
            val addedLabel = stringResource(R.string.energy_added)
            val usedLabel = stringResource(R.string.used)
            val efficiencyLabel = stringResource(R.string.efficiency)
            val startLabel = stringResource(R.string.start)
            val endLabel = stringResource(R.string.end)
            val durationLabel = stringResource(R.string.duration)
            val maximumLabel = stringResource(R.string.maximum)
            val minimumLabel = stringResource(R.string.minimum)
            val averageLabel = stringResource(R.string.average)
            val voltageMaxLabel = stringResource(R.string.voltage_max)
            val voltageMinLabel = stringResource(R.string.voltage_min)
            val voltageAvgLabel = stringResource(R.string.voltage_avg)
            val currentMaxLabel = stringResource(R.string.current_max)
            val currentMinLabel = stringResource(R.string.current_min)
            val currentAvgLabel = stringResource(R.string.current_avg)
            val totalLabel = stringResource(R.string.total)
            val perKwhLabel = stringResource(R.string.per_kwh)

            // Energy section
            StatsSectionCard(
                title = energyLabel,
                icon = Icons.Default.EnergySavingsLeaf,
                stats = listOf(
                    StatItem(addedLabel, "%.2f kWh".format(s.energyAdded)),
                    StatItem(usedLabel, "%.2f kWh".format(s.energyUsed)),
                    StatItem(efficiencyLabel, "%.1f%%".format(s.efficiency))
                )
            )

            // Battery section
            StatsSectionCard(
                title = batteryLabel,
                icon = Icons.Default.BatteryChargingFull,
                stats = listOf(
                    StatItem(startLabel, "${s.batteryStart}%"),
                    StatItem(endLabel, "${s.batteryEnd}%"),
                    StatItem(addedLabel, "+${s.batteryAdded}%"),
                    StatItem(durationLabel, formatDuration(s.durationMin))
                )
            )

            // Power section
            if (s.powerMax > 0) {
                StatsSectionCard(
                    title = powerLabel,
                    icon = Icons.Default.Bolt,
                    stats = listOf(
                        StatItem(maximumLabel, "${s.powerMax} kW"),
                        StatItem(minimumLabel, "${s.powerMin} kW"),
                        StatItem(averageLabel, "%.1f kW".format(s.powerAvg))
                    )
                )
            }

            // Voltage & Current section
            // Shown only for AC charges
            if (!isDcCharge) {
                StatsSectionCard(
                    title = chargerLabel,
                    icon = Icons.Default.ElectricalServices,
                    stats = listOf(
                        StatItem(voltageMaxLabel, "${s.voltageMax} V"),
                        StatItem(voltageMinLabel, "${s.voltageMin} V"),
                        StatItem(voltageAvgLabel, "%.0f V".format(s.voltageAvg)),
                        StatItem(currentMaxLabel, "${s.currentMax} A"),
                        StatItem(currentMinLabel, "${s.currentMin} A"),
                        StatItem(currentAvgLabel, "%.1f A".format(s.currentAvg))
                    )
                )
            }

            // Temperature section
            if (s.tempMax > -100) {
                StatsSectionCard(
                    title = temperatureLabel,
                    icon = Icons.Default.DeviceThermostat,
                    stats = listOf(
                        StatItem(maximumLabel, UnitFormatter.formatTemperature(s.tempMax, units)),
                        StatItem(minimumLabel, UnitFormatter.formatTemperature(s.tempMin, units)),
                        StatItem(averageLabel, UnitFormatter.formatTemperature(s.tempAvg, units))
                    )
                )
            }

            // Cost section
            s.cost?.let { cost ->
                if (cost > 0) {
                    StatsSectionCard(
                        title = costLabel,
                        icon = Icons.Default.Paid,
                        stats = listOf(
                            StatItem(totalLabel, "$currencySymbol%.2f".format(cost)),
                            StatItem(perKwhLabel, "$currencySymbol%.3f".format(cost / s.energyAdded.coerceAtLeast(0.001)))
                        )
                    )
                }
            }

            // Charts
            val chargePoints = detail.chargePoints
            if (!chargePoints.isNullOrEmpty() && chargePoints.size > 2) {
                // Extract time range for labels
                val timeLabels = extractTimeLabels(chargePoints)

                // Localized chart titles
                val powerProfileTitle = stringResource(R.string.power_profile)
                val voltageProfileTitle = stringResource(R.string.voltage_profile)
                val currentProfileTitle = stringResource(R.string.current_profile)
                val batteryLevelTitle = stringResource(R.string.battery_level)

                if (chargePoints.any { (it.chargerPower ?: 0) > 0 }) {
                    PowerChartCard(chargePoints = chargePoints, timeLabels = timeLabels, title = powerProfileTitle)
                }
                // Only show voltage and current charts for AC charges
                if (!isDcCharge) {
                    if (chargePoints.any { (it.chargerVoltage ?: 0) > 0 }) {
                        VoltageChartCard(chargePoints = chargePoints, timeLabels = timeLabels, title = voltageProfileTitle)
                    }
                    if (chargePoints.any { (it.chargerCurrent ?: 0) > 0 }) {
                        CurrentChartCard(chargePoints = chargePoints, timeLabels = timeLabels, title = currentProfileTitle)
                    }
                }
                if (chargePoints.any { it.outsideTemp != null }) {
                    TemperatureChartCard(chargePoints = chargePoints, units = units, timeLabels = timeLabels, title = temperatureLabel)
                }
                if (chargePoints.any { it.batteryLevel != null }) {
                    BatteryChartCard(chargePoints = chargePoints, timeLabels = timeLabels, title = batteryLevelTitle)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LocationHeaderCard(detail: ChargeDetail, currencySymbol: String, isDcCharge: Boolean) {
    val locationLabel = stringResource(R.string.location)
    val unknownLocationLabel = stringResource(R.string.unknown_location)
    val startedLabel = stringResource(R.string.started)
    val endedLabel = stringResource(R.string.ended)
    val energyAddedLabel = stringResource(R.string.energy_added_header)
    val costLabel = stringResource(R.string.cost)
    val unknownLabel = stringResource(R.string.unknown)

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
            // Location
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
                        text = locationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = detail.address ?: unknownLocationLabel,
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
                        text = startedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatDateTime(detail.startDate, unknownLabel),
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
                        text = endedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatDateTime(detail.endDate, unknownLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    detail.durationStr?.let { duration ->
                        Text(
                            text = stringResource(R.string.duration_label, duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Energy added and cost summary
            detail.chargeEnergyAdded?.let { energy ->
                HorizontalDivider(
                    modifier = Modifier.padding(start = 36.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Energy Added (left side) with AC/DC badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = energyAddedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "%.2f kWh".format(energy),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ChargeTypeBadge(isDcCharge = isDcCharge)
                            }
                        }
                    }

                    // Cost (right side)
                    detail.cost?.let { cost ->
                        if (cost > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = costLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "$currencySymbol%.2f".format(cost),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeMapCard(latitude: Double, longitude: Double) {
    val context = LocalContext.current
    val locationTitle = stringResource(R.string.location)
    val chargeLocationMarker = stringResource(R.string.charge_location)

    fun openInMaps() {
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        context.startActivity(intent)
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
                text = locationTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

                DisposableEffect(Unit) {
                    Configuration.getInstance().userAgentValue = "MateDroid/1.0"
                    onDispose { }
                }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)

                            val geoPoint = GeoPoint(latitude, longitude)

                            // Add marker at charge location
                            val marker = Marker(this).apply {
                                position = geoPoint
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = chargeLocationMarker
                            }
                            overlays.add(marker)

                            // Center on the location
                            controller.setZoom(16.0)
                            controller.setCenter(geoPoint)
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
    // Get the current screen settings
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    // Define how many columns we want according to the available screen width
    val columnCount = when {
        screenWidth > 600 -> 4 // Big screen or landscape orientation
        screenWidth > 340 -> 3 // Standard screen
        else -> 2              // Small screen
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

            // Divide the list of statistics according to the calculated number of columns
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

                    // Fill the leftover space if the last row is not complete.
                    // This prevents a single item from stretching too much
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
private fun PowerChartCard(chargePoints: List<ChargePoint>, timeLabels: List<String>, title: String) {
    val powers = chargePoints.mapNotNull { it.chargerPower?.toFloat() }
    if (powers.size < 2) return

    ChartCard(
        title = title,
        icon = Icons.Default.Bolt,
        data = powers,
        color = Color(0xFF4CAF50),
        unit = "kW",
        timeLabels = timeLabels
    )
}

@Composable
private fun VoltageChartCard(chargePoints: List<ChargePoint>, timeLabels: List<String>, title: String) {
    val voltages = chargePoints.mapNotNull { it.chargerVoltage?.toFloat() }
    if (voltages.size < 2) return

    ChartCard(
        title = title,
        icon = Icons.Default.ElectricalServices,
        data = voltages,
        color = MaterialTheme.colorScheme.tertiary,
        unit = "V",
        timeLabels = timeLabels
    )
}

@Composable
private fun CurrentChartCard(chargePoints: List<ChargePoint>, timeLabels: List<String>, title: String) {
    val currents = chargePoints.mapNotNull { it.chargerCurrent?.toFloat() }
    if (currents.size < 2) return

    ChartCard(
        title = title,
        icon = Icons.Default.Power,
        data = currents,
        color = MaterialTheme.colorScheme.secondary,
        unit = "A",
        timeLabels = timeLabels
    )
}

@Composable
private fun TemperatureChartCard(chargePoints: List<ChargePoint>, units: Units?, timeLabels: List<String>, title: String) {
    val temps = chargePoints.mapNotNull { it.outsideTemp?.toFloat() }
    if (temps.size < 2) return

    ChartCard(
        title = title,
        icon = Icons.Default.DeviceThermostat,
        data = temps,
        color = Color(0xFFFF9800),
        unit = UnitFormatter.getTemperatureUnit(units),
        timeLabels = timeLabels,
        convertValue = { value ->
            if (units?.unitOfTemperature == "F") (value * 9f / 5f + 32f) else value
        }
    )
}

@Composable
private fun BatteryChartCard(chargePoints: List<ChargePoint>, timeLabels: List<String>, title: String) {
    val batteryLevels = chargePoints.mapNotNull { it.batteryLevel?.toFloat() }
    if (batteryLevels.size < 2) return

    ChartCard(
        title = title,
        icon = Icons.Default.BatteryChargingFull,
        data = batteryLevels,
        color = MaterialTheme.colorScheme.primary,
        unit = "%",
        fixedMinMax = Pair(0f, 100f),
        timeLabels = timeLabels
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

            FullscreenLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                convertValue = convertValue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChargeTypeBadge(isDcCharge: Boolean) {
    val backgroundColor = if (isDcCharge) Color(0xFFFF9800) else Color(0xFF4CAF50)
    val text = if (isDcCharge) stringResource(R.string.charging_dc) else stringResource(R.string.charging_ac)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Extract 5 time labels from charge points for X axis display.
 * Returns list of 5 time strings at 0%, 25%, 50%, 75%, and 100% positions.
 * Following the chart guidelines: start, 1st quarter, half, 3rd quarter, end.
 */
private fun extractTimeLabels(chargePoints: List<ChargePoint>): List<String> {
    if (chargePoints.isEmpty()) return listOf("", "", "", "", "")

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val times = chargePoints.mapNotNull { point ->
        point.date?.let { dateStr ->
            try {
                val dateTime = try {
                    OffsetDateTime.parse(dateStr).toLocalDateTime()
                } catch (e: DateTimeParseException) {
                    LocalDateTime.parse(dateStr.replace("Z", ""))
                }
                dateTime
            } catch (e: Exception) {
                null
            }
        }
    }

    if (times.isEmpty()) return listOf("", "", "", "", "")

    // 5 positions: start (0%), 1st quarter (25%), half (50%), 3rd quarter (75%), end (100%)
    val indices = listOf(0, times.size / 4, times.size / 2, times.size * 3 / 4, times.size - 1)
    return indices.map { idx ->
        times.getOrNull(idx.coerceIn(0, times.size - 1))?.format(timeFormatter) ?: ""
    }
}

private fun formatDateTime(dateStr: String?, unknownLabel: String = "Unknown"): String {
    if (dateStr == null) return unknownLabel
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
