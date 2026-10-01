package com.matedroid.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.CarImageOverride
import com.matedroid.domain.HighSocWarning
import com.matedroid.domain.LowSocWarning
import com.matedroid.domain.model.BatteryTypeHelper
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.calculateAcGaugeProgress
import com.matedroid.ui.components.calculateDcGaugeProgress
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning
import com.matedroid.util.formatDuration
import kotlin.math.roundToInt

@Composable
internal fun BatteryCard(
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
    highSocWarningThreshold: Int = HighSocWarning.DEFAULT_THRESHOLD,
    lowSocWarningThreshold: Int = LowSocWarning.DEFAULT_THRESHOLD,
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
        LowSocWarning.isLow(batteryLevel, lowSocWarningThreshold) -> StatusError
        LowSocWarning.isGettingLow(batteryLevel, lowSocWarningThreshold) -> StatusWarning
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
                Column {
                    Text(
                        text = stringResource(R.string.high_soc_warning_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // The LFP owners this warning does not apply to are exactly the people
                    // reading this dialog, so point them at the setting that turns it off.
                    Text(
                        text = stringResource(R.string.high_soc_warning_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    if (HighSocWarning.shouldWarn(batteryLevel, status.isCharging, highSocWarningThreshold)) {
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

private fun formatHoursMinutes(hours: Double, resources: android.content.res.Resources): String {
    val totalMinutes = (hours * 60).roundToInt()
    return formatDuration(resources, totalMinutes)
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

@Preview(showBackground = true, name = "Parked Full - Warning Off")
@Composable
private fun BatteryCardFullWarningDisabledPreview() {
    MateDroidTheme {
        BatteryCard(
            status = CarStatus(
                displayName = "My Tesla",
                state = "asleep",
                batteryDetails = BatteryDetails(
                    batteryLevel = 100,
                    ratedBatteryRange = 400.0
                ),
                chargingDetails = ChargingDetails(chargeLimitSoc = 100)
            ),
            units = null,
            carTrimBadging = "50",
            // What an LFP owner sees once the warning is turned off (issue #310)
            highSocWarningThreshold = HighSocWarning.DISABLED
        )
    }
}
