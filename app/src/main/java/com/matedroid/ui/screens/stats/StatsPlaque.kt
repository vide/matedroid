package com.matedroid.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.CostPerKwhBasis
import com.matedroid.domain.model.DeepStats
import com.matedroid.domain.model.QuickStats
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.SummaryItem
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import java.util.Locale

/**
 * The "engraved plaque": the driving and charging totals the dashboard does not already show
 * (drive/charge counts and total distance live there), then the AC/DC energy split. A 1 dp
 * outline inset 6 dp inside the card gives it the engraved look.
 */
@Composable
internal fun StatsPlaque(
    quickStats: QuickStats,
    deepStats: DeepStats?,
    palette: CarColorPalette,
    currencySymbol: String,
    units: Units?
) {
    val locale = Locale.getDefault()
    val notAvailable = stringResource(R.string.value_not_available)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val hasCost = quickStats.totalCost != null && quickStats.totalCost > 0
    // Cost per 100 distance units: (totalCost / totalDistance) * 100
    val costPer100 = if (quickStats.totalCost != null && quickStats.totalCost > 0 && quickStats.totalDistanceKm > 0) {
        (quickStats.totalCost / quickStats.totalDistanceKm) * 100
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val inset = 6.dp.toPx()
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(inset + stroke / 2, inset + stroke / 2),
                        size = Size(size.width - 2 * inset - stroke, size.height - 2 * inset - stroke),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = stroke)
                    )
                }
                .padding(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 18.dp)
        ) {
            // Rule: hairline — trophy — hairline
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(outline))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = CustomIcons.Trophy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = palette.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(outline))
            }
            Spacer(modifier = Modifier.height(4.dp))

            PlaqueGroupLabel(stringResource(R.string.stats_driving_summary).uppercase(locale), palette)
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Default.CalendarMonth,
                    label = stringResource(R.string.stats_driving_days),
                    value = quickStats.totalDrivingDays?.let { "%,d".format(it) } ?: notAvailable,
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
            Spacer(modifier = Modifier.height(2.dp))
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

            PlaqueGroupLabel(stringResource(R.string.stats_charging_summary).uppercase(locale), palette)
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Default.BatteryChargingFull,
                    label = stringResource(R.string.energy_added_header),
                    value = UnitFormatter.formatEnergy(quickStats.totalEnergyAddedKwh),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                if (hasCost) {
                    SummaryItem(
                        icon = Icons.Default.Paid,
                        label = stringResource(R.string.total_cost),
                        value = UnitFormatter.formatCost(quickStats.totalCost ?: 0.0, currencySymbol),
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
                            ?: notAvailable,
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
}

@Composable
private fun PlaqueGroupLabel(text: String, palette: CarColorPalette) {
    Text(
        text = text,
        style = capsStyle(11.sp, FontWeight.Bold, 1.sp),
        color = palette.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 2.dp)
    )
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
