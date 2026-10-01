package com.matedroid.ui.screens.mileage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.util.formatMedium
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val ChartBlue = Color(0xFF42A5F5)

/**
 * Average efficiency in Wh per distance unit (Wh/km or Wh/mi).
 *
 * The ×1000 converts kWh → Wh — it is NOT a unit-system conversion.
 * Distance is already in the user's unit (the API pre-converts before we
 * store it), so the efficiency denominator is used as-is — no km→mi math.
 */
internal fun efficiencyWhPerUnit(totalEnergyKwh: Double, totalDistance: Double): Double =
    if (totalDistance > 0) (totalEnergyKwh * 1000.0) / totalDistance else 0.0

/** Y-axis label formatter shared by all mileage bar charts. */
private val mileageYAxisFormatter: (Double) -> String =
    { if (it >= 1000) "%.0fk".format(it / 1000) else "%.0f".format(it) }

/**
 * Bar chart card used at every mileage level (yearly, monthly, daily).
 * Shows a title with the road icon, an optional trailing text (e.g. days
 * with data) and an interactive distance bar chart.
 */
@Composable
internal fun MileageChartCard(
    title: String,
    chartData: List<Pair<Int, Double>>,
    palette: CarColorPalette,
    units: Units?,
    trailingText: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CustomIcons.Road,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val barChartData = remember(chartData, units) {
                chartData.map { (label, distance) ->
                    BarChartData(
                        label = label.toString(),
                        value = distance,
                        displayValue = UnitFormatter.formatDistance(distance, units)
                    )
                }
            }

            InteractiveBarChart(
                data = barChartData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                valueFormatter = { UnitFormatter.formatDistance(it, units) },
                yAxisFormatter = mileageYAxisFormatter
            )
        }
    }
}

@Composable
internal fun SummaryRow(
    totalDistance: Double,
    avgDistance: Double,
    avgLabel: String,
    driveCount: Int,
    totalEnergyUsed: Double,
    totalEnergyCost: Double?,
    avgEnergyDistance: Double,
    currencySymbol: String,
    units: Units? = null,
    palette: CarColorPalette? = null,
    firstDriveDate: LocalDate? = null
) {
    val containerColor = palette?.surface ?: MaterialTheme.colorScheme.surfaceVariant
    val iconColor = palette?.accent ?: ChartBlue
    val valueColor = palette?.onSurface ?: MaterialTheme.colorScheme.onSurface
    val labelColor = palette?.onSurfaceVariant ?: MaterialTheme.colorScheme.onSurfaceVariant

    var showAvgInfoDialog by remember { mutableStateOf(false) }

    // Pre-compute localized strings for dialog
    val avgYearTitle = stringResource(R.string.mileage_avg_year_title)
    val okText = stringResource(R.string.ok)

    // Info dialog explaining the avg/year calculation
    if (showAvgInfoDialog && firstDriveDate != null) {
        val formattedDate = firstDriveDate.formatMedium(Locale.getDefault())
        val daysSinceFirst = ChronoUnit.DAYS.between(firstDriveDate, LocalDate.now()).toInt()
        val dialogMessage = stringResource(R.string.mileage_avg_year_message, formattedDate, daysSinceFirst)

        AlertDialog(
            onDismissRequest = { showAvgInfoDialog = false },
            title = { Text(avgYearTitle) },
            text = {
                Text(dialogMessage)
            },
            confirmButton = {
                TextButton(onClick = { showAvgInfoDialog = false }) {
                    Text(okText)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                icon = Icons.Outlined.AllInclusive,
                value = UnitFormatter.formatDistance(totalDistance, units, 0),
                label = stringResource(R.string.mileage_total),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
            SummaryItemWithInfo(
                icon = Icons.Filled.Speed,
                value = UnitFormatter.formatDistance(avgDistance, units, 0),
                label = avgLabel,
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor,
                showInfoIcon = firstDriveDate != null,
                onInfoClick = { showAvgInfoDialog = true }
            )
            SummaryItem(
                icon = Icons.Filled.DirectionsCar,
                value = "%,d".format(driveCount),
                label = stringResource(R.string.mileage_drive_count),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                icon = Icons.Outlined.EnergySavingsLeaf,
                value = UnitFormatter.formatEfficiency(avgEnergyDistance, units),
                label = stringResource(R.string.stats_avg_efficiency),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
            SummaryItem(
                icon = Icons.Filled.AttachMoney,
                value = UnitFormatter.formatCost(totalEnergyCost ?: 0.0, currencySymbol),
                label = stringResource(R.string.mileage_total),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
            SummaryItem(
                icon = Icons.Outlined.BatteryChargingFull,
                value = UnitFormatter.formatEnergy(totalEnergyUsed),
                label = stringResource(R.string.mileage_total),
                iconColor = iconColor,
                valueColor = valueColor,
                labelColor = labelColor
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color = ChartBlue,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

@Composable
private fun SummaryItemWithInfo(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color = ChartBlue,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showInfoIcon: Boolean = false,
    onInfoClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (showInfoIcon) Modifier.clickable { onInfoClick() } else Modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            if (showInfoIcon) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.info_about_calculation),
                    tint = labelColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
internal fun StatChip(
    modifier: Modifier = Modifier,
    prefix: String? = null,
    icon: ImageVector? = null,
    iconText: String? = null,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ChartBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (iconText != null) {
                Text(
                    text = iconText,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
