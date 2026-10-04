package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.CostPerKwhBasis
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.SummaryItem
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette

/** "Driving" summary: totals and averages over every drive in the selected year filter. */
@Composable
internal fun DrivingSummaryCard(
    quickStats: QuickStats,
    palette: CarColorPalette,
    currencySymbol: String,
    units: Units?
) {
    // Cost per 100 distance units: (totalCost / totalDistance) * 100
    val costPer100 = if (quickStats.totalCost != null && quickStats.totalCost > 0 && quickStats.totalDistanceKm > 0) {
        (quickStats.totalCost / quickStats.totalDistanceKm) * 100
    } else {
        null
    }
    val notAvailable = stringResource(R.string.value_not_available)

    StatsSummaryCard(title = stringResource(R.string.stats_driving_summary), palette = palette) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryItem(
                icon = Icons.Default.DirectionsCar,
                label = stringResource(R.string.stats_total_drives),
                value = "%,d".format(quickStats.totalDrives),
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Default.CalendarMonth,
                label = stringResource(R.string.stats_driving_days),
                value = quickStats.totalDrivingDays?.let { "%,d".format(it) } ?: notAvailable,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryItem(
                icon = CustomIcons.Road,
                label = stringResource(R.string.total_distance),
                value = UnitFormatter.formatDistance(quickStats.totalDistanceKm, units, 0),
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Default.ElectricBolt,
                label = stringResource(R.string.stats_energy_used),
                value = UnitFormatter.formatEnergy(quickStats.totalEnergyConsumedKwh),
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryItem(
                icon = Icons.Outlined.EnergySavingsLeaf,
                label = stringResource(R.string.stats_avg_efficiency),
                value = UnitFormatter.formatEfficiency(quickStats.avgEfficiencyWhKm, units, 0),
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Default.Paid,
                label = stringResource(R.string.stats_cost_per_distance, UnitFormatter.getDistanceUnit(units)),
                value = costPer100?.let { UnitFormatter.formatCost(it, currencySymbol) } ?: notAvailable,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * "Charging" summary: totals over every charge in the selected year filter, followed by the
 * AC/DC energy split once the deep stats have been computed.
 */
@Composable
internal fun ChargingSummaryCard(
    quickStats: QuickStats,
    deepStats: DeepStats?,
    palette: CarColorPalette,
    currencySymbol: String
) {
    StatsSummaryCard(title = stringResource(R.string.stats_charging_summary), palette = palette) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryItem(
                icon = Icons.Default.ElectricBolt,
                label = stringResource(R.string.stats_total_charges),
                value = "%,d".format(quickStats.totalCharges),
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                icon = Icons.Default.BatteryChargingFull,
                label = stringResource(R.string.energy_added_header),
                value = UnitFormatter.formatEnergy(quickStats.totalEnergyAddedKwh),
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }
        if (quickStats.totalCost != null && quickStats.totalCost > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Default.Paid,
                    label = stringResource(R.string.total_cost),
                    value = UnitFormatter.formatCost(quickStats.totalCost, currencySymbol),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    icon = Icons.Default.Paid,
                    label = stringResource(
                        if (CostPerKwhBasis.current == CostPerKwhBasis.ENERGY_USED) {
                            R.string.stats_avg_cost_kwh_used
                        } else {
                            R.string.stats_avg_cost_kwh_added
                        }
                    ),
                    value = quickStats.avgCostPerKwh?.let { UnitFormatter.formatCost(it, currencySymbol, perKwh = true) }
                        ?: stringResource(R.string.value_not_available),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (deepStats != null && deepStats.acChargeEnergyKwh + deepStats.dcChargeEnergyKwh > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            AcDcRatioBar(deepStats = deepStats, palette = palette)
        }
    }
}

@Composable
private fun StatsSummaryCard(
    title: String,
    palette: CarColorPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * AC/DC split of the charged energy: a rounded bar with the AC share in the AC colour and the
 * DC remainder in the DC colour, percentages drawn inside each segment wide enough to hold
 * them, and a legend underneath with the session count and energy of each side.
 */
@Composable
private fun AcDcRatioBar(deepStats: DeepStats, palette: CarColorPalette) {
    val totalEnergy = deepStats.acChargeEnergyKwh + deepStats.dcChargeEnergyKwh
    val acRatio = (deepStats.acChargeEnergyKwh / totalEnergy).toFloat()
    val acColor = palette.acColor
    val dcColor = palette.dcColor

    val acPercent = (acRatio * 100).toInt()
    val dcPercent = 100 - acPercent
    // Only show a percentage if its segment is wide enough (>= 15%)
    val showAcPercent = acPercent >= 15
    val showDcPercent = dcPercent >= 15

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(dcColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(acRatio)
                .background(acColor),
            contentAlignment = Alignment.CenterStart
        ) {
            if (showAcPercent) {
                Text(
                    text = "$acPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = dcColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (showDcPercent) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "$dcPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = acColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AcDcLegend(
            label = stringResource(R.string.charging_ac),
            detail = stringResource(R.string.format_charges_count, deepStats.acChargeCount) +
                " · " + UnitFormatter.formatEnergy(deepStats.acChargeEnergyKwh),
            color = acColor,
            palette = palette
        )
        AcDcLegend(
            label = stringResource(R.string.charging_dc),
            detail = stringResource(R.string.format_charges_count, deepStats.dcChargeCount) +
                " · " + UnitFormatter.formatEnergy(deepStats.dcChargeEnergyKwh),
            color = dcColor,
            palette = palette
        )
    }
}

@Composable
private fun AcDcLegend(
    label: String,
    detail: String,
    color: Color,
    palette: CarColorPalette
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant
        )
    }
}
