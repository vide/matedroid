package com.matedroid.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.util.formatDuration
import com.matedroid.util.formatShortNoYear
import com.matedroid.util.formatTime
import kotlinx.coroutines.launch

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
internal fun StatusIndicatorsRow(
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
