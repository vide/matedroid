package com.matedroid.ui.screens.charges

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargePoint
import com.matedroid.ui.components.FullscreenDualAxisLineChart
import com.matedroid.ui.components.FullscreenLineChart
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentChargeScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: CurrentChargeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(carId) {
        viewModel.loadCurrentCharge(carId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.isUnsupportedApi) {
        if (uiState.isUnsupportedApi) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.isNotCharging) {
        if (uiState.isNotCharging) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.current_charge_title))
                        if (uiState.chargeDetail != null && !uiState.isNotCharging) {
                            Spacer(modifier = Modifier.width(8.dp))
                            LiveBadge()
                        }
                    }
                },
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.isUnsupportedApi -> {
                FallbackMessage(
                    message = stringResource(R.string.current_charge_unsupported),
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.isNotCharging -> {
                FallbackMessage(
                    message = stringResource(R.string.current_charge_not_charging),
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.chargeDetail != null -> {
                CurrentChargeContent(
                    detail = uiState.chargeDetail!!,
                    isDcCharge = uiState.isDcCharge,
                    timeToFullCharge = uiState.timeToFullCharge,
                    chargeLimitSoc = uiState.chargeLimitSoc,
                    chronologicalPoints = uiState.chronologicalPoints,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val badgeColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFE53935),
        targetValue = Color(0xFFFF5252),
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveBadgeColor"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.current_charge_live),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun FallbackMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun CurrentChargeContent(
    detail: ChargeDetail,
    isDcCharge: Boolean,
    timeToFullCharge: Double?,
    chargeLimitSoc: Int?,
    chronologicalPoints: List<ChargePoint>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var sharedXFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { isScrolling -> if (isScrolling) sharedXFraction = null }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { sharedXFraction = null } }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header card
        CurrentChargeHeaderCard(
            detail = detail,
            isDcCharge = isDcCharge,
            timeToFullCharge = timeToFullCharge,
            chargeLimitSoc = chargeLimitSoc,
            chronologicalPoints = chronologicalPoints
        )

        // Charts - always show cards, even with few data points
        val timeLabels = extractChronoTimeLabels(chronologicalPoints)
        val powers = chronologicalPoints.mapNotNull { it.chargerPower?.toFloat() }
        val batteryLevels = chronologicalPoints.mapNotNull { it.batteryLevel?.toFloat() }
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val fractionToTimeLabel: (Float) -> String = { fraction ->
            val pts = chronologicalPoints
            val index = (fraction * pts.lastIndex).roundToInt().coerceIn(0, pts.lastIndex)
            pts[index].date?.let { dateStr ->
                try {
                    val dt = try {
                        OffsetDateTime.parse(dateStr).toLocalDateTime()
                    } catch (e: java.time.format.DateTimeParseException) {
                        java.time.LocalDateTime.parse(dateStr.replace("Z", ""))
                    }
                    dt.format(timeFormatter)
                } catch (e: Exception) { "" }
            } ?: ""
        }

        // Power chart (always shown)
        val powerProfileTitle = stringResource(R.string.power_profile)
        LiveChartCard(
            title = powerProfileTitle,
            icon = Icons.Default.Bolt
        ) {
            if (powers.size >= 2) {
                FullscreenLineChart(
                    data = powers,
                    color = Color(0xFF4CAF50),
                    unit = "kW",
                    timeLabels = timeLabels,
                    externalSelectedFraction = sharedXFraction,
                    onXSelected = { sharedXFraction = it },
                    fractionToTimeLabel = fractionToTimeLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Voltage & Current combined chart (AC only)
        if (!isDcCharge) {
            val voltages = chronologicalPoints.mapNotNull { it.chargerVoltage?.toFloat() }
            val currents = chronologicalPoints.mapNotNull { it.chargerCurrent?.toFloat() }

            val vcTitle = stringResource(R.string.voltage_and_current_profile)
            LiveChartCard(
                title = vcTitle,
                icon = Icons.Default.ElectricalServices
            ) {
                if (voltages.size >= 2 && currents.size >= 2) {
                    FullscreenDualAxisLineChart(
                        dataLeft = voltages,
                        dataRight = currents,
                        colorLeft = Color(0xFFFFA726),
                        colorRight = Color(0xFF42A5F5),
                        unitLeft = "V",
                        unitRight = "A",
                        timeLabels = timeLabels,
                        externalSelectedFraction = sharedXFraction,
                        onXSelected = { sharedXFraction = it },
                        fractionToTimeLabel = fractionToTimeLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Battery level chart
        val batteryLevelTitle = stringResource(R.string.battery_level)
        LiveChartCard(
            title = batteryLevelTitle,
            icon = Icons.Default.BatteryChargingFull
        ) {
            if (batteryLevels.size >= 2) {
                FullscreenLineChart(
                    data = batteryLevels,
                    color = MaterialTheme.colorScheme.primary,
                    unit = "%",
                    fixedMinMax = Pair(0f, 100f),
                    timeLabels = timeLabels,
                    externalSelectedFraction = sharedXFraction,
                    onXSelected = { sharedXFraction = it },
                    fractionToTimeLabel = fractionToTimeLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }


        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CurrentChargeHeaderCard(
    detail: ChargeDetail,
    isDcCharge: Boolean,
    timeToFullCharge: Double?,
    chargeLimitSoc: Int?,
    chronologicalPoints: List<ChargePoint>
) {
    val unknownLabel = stringResource(R.string.unknown)
    val estimatedEndLabel = stringResource(R.string.current_charge_estimated_end)
    val elapsedLabel = stringResource(R.string.current_charge_elapsed)

    // Get instant values from the latest data point
    val latestPoint = chronologicalPoints.lastOrNull()
    val instantPower = latestPoint?.chargerPower
    val instantVoltage = latestPoint?.chargerVoltage
    val instantCurrent = latestPoint?.chargerCurrent

    val startLevel = detail.startBatteryLevel ?: 0
    val currentLevel = detail.currentOrEndBatteryLevel ?: 0
    val targetLevel = chargeLimitSoc ?: 100
    val solidGreen = Color(0xFF4CAF50)

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
            // 3-column SoC row: start → current → target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$startLevel%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.soc_start),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                // Current (hero, with bolt icon)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(2f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = solidGreen
                        )
                        Text(
                            text = "$currentLevel%",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = solidGreen
                        )
                    }
                    Text(
                        text = stringResource(R.string.soc_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = solidGreen
                    )
                }

                // Target
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$targetLevel%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.soc_target),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // SoC progress bar
            LiveSocProgressBar(
                currentLevel = currentLevel,
                startLevel = startLevel,
                targetLevel = targetLevel,
                modifier = Modifier.fillMaxWidth()
            )

            // Elapsed time + Estimated end
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Elapsed time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = elapsedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        ElapsedTimeCounter(
                            startDate = detail.startDate,
                            unknownLabel = unknownLabel
                        )
                    }
                }

                // Estimated end time
                timeToFullCharge?.let { hours ->
                    if (hours > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = estimatedEndLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatEstimatedEnd(hours),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Energy added + Instant power (compact row)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Energy added with AC/DC badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = solidGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "%.2f kWh".format(detail.chargeEnergyAdded ?: 0.0),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = solidGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LiveChargeTypeBadge(isDcCharge = isDcCharge)
                        }
                        Text(
                            text = stringResource(R.string.soc_energy_added),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Instant power
                if (instantPower != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$instantPower kW",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.soc_instant_power),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Voltage & Current (AC only)
            if (!isDcCharge && instantVoltage != null && instantCurrent != null) {
                Text(
                    text = "$instantVoltage V \u00B7 $instantCurrent A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LiveChargeTypeBadge(isDcCharge: Boolean) {
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

@Composable
private fun LiveSocProgressBar(
    currentLevel: Int,
    startLevel: Int,
    targetLevel: Int,
    modifier: Modifier = Modifier
) {
    val currentFraction = currentLevel / 100f
    val startFraction = startLevel / 100f
    val targetFraction = targetLevel / 100f
    val solidGreen = Color(0xFF4CAF50)
    val dimmedGreen = Color(0xFF4CAF50).copy(alpha = 0.3f)
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
    val startMarkerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)

    Canvas(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        val width = size.width
        val height = size.height

        // Background track
        drawRect(color = trackColor, size = size)

        // Dimmed green for target area (from current to target)
        if (targetFraction > currentFraction) {
            drawRect(
                color = dimmedGreen,
                topLeft = androidx.compose.ui.geometry.Offset(width * currentFraction, 0f),
                size = androidx.compose.ui.geometry.Size(
                    width * (targetFraction - currentFraction), height
                )
            )
        }

        // Solid green for current charge level
        drawRect(
            color = solidGreen,
            size = androidx.compose.ui.geometry.Size(width * currentFraction, height)
        )

        // Start level marker (thin vertical line)
        if (startFraction > 0f) {
            val markerX = width * startFraction
            drawLine(
                color = startMarkerColor,
                start = androidx.compose.ui.geometry.Offset(markerX, 0f),
                end = androidx.compose.ui.geometry.Offset(markerX, height),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun ElapsedTimeCounter(
    startDate: String?,
    unknownLabel: String
) {
    val startEpochMs = remember(startDate) {
        if (startDate == null) return@remember null
        try {
            val odt = try {
                OffsetDateTime.parse(startDate)
            } catch (e: DateTimeParseException) {
                OffsetDateTime.parse(startDate.replace("Z", "+00:00"))
            }
            odt.toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    val elapsedMs = remember { mutableLongStateOf(0L) }

    LaunchedEffect(startEpochMs) {
        if (startEpochMs == null) return@LaunchedEffect
        while (true) {
            elapsedMs.longValue = System.currentTimeMillis() - startEpochMs
            delay(1000L)
        }
    }

    val displayText = if (startEpochMs == null) {
        unknownLabel
    } else {
        val totalSeconds = elapsedMs.longValue / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Composable
private fun LiveChartCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
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
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            content()
        }
    }
}

private fun extractChronoTimeLabels(chargePoints: List<ChargePoint>): List<String> {
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

    val indices = listOf(0, times.size / 4, times.size / 2, times.size * 3 / 4, times.size - 1)
    return indices.map { idx ->
        times.getOrNull(idx.coerceIn(0, times.size - 1))?.format(timeFormatter) ?: ""
    }
}

private fun formatEstimatedEnd(hoursRemaining: Double): String {
    val totalMinutes = (hoursRemaining * 60).roundToInt()
    val now = java.time.LocalDateTime.now()
    val endTime = now.plusMinutes(totalMinutes.toLong())
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val durationStr = if (h > 0) "${h}h ${m}m" else "${m}m"
    return "${endTime.format(timeFormatter)} ($durationStr)"
}
