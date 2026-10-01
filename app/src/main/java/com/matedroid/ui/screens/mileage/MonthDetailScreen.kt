package com.matedroid.ui.screens.mileage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.StatusSuccess
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ============================================================================
// Level 3: Month Detail
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthDetailScreen(
    yearMonth: YearMonth,
    monthData: MonthlyMileage?,
    dailyData: List<DailyMileage>,
    dailyChartData: List<Pair<Int, Double>>,
    currencySymbol: String,
    units: Units? = null,
    palette: CarColorPalette,
    onClose: () -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month summary card
            item {
                MonthSummaryCard(
                    yearMonth = yearMonth,
                    monthData = monthData,
                    currencySymbol = currencySymbol,
                    units = units,
                    palette = palette
                )
            }

            // Daily chart
            if (dailyChartData.isNotEmpty()) {
                item {
                    MileageChartCard(
                        title = stringResource(R.string.mileage_by_day),
                        chartData = dailyChartData,
                        palette = palette,
                        units = units,
                        trailingText = stringResource(R.string.format_days_count, dailyData.size)
                    )
                }
            }

            // Recent trips header
            if (dailyData.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.mileage_recent_trips),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Daily trip rows
                items(dailyData, key = { it.date.toString() }, contentType = { "day" }) { dayData ->
                    DayTripRow(
                        dayData = dayData,
                        units = units,
                        currencySymbol = currencySymbol,
                        onClick = { onDayClick(dayData.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    yearMonth: YearMonth,
    monthData: MonthlyMileage?,
    palette: CarColorPalette,
    currencySymbol: String,
    units: Units? = null
) {
    val totalDistance = monthData?.totalDistance ?: 0.0
    val driveCount = monthData?.driveCount ?: 0
    val avgDistance = if (driveCount > 0) totalDistance / driveCount else 0.0
    val totalBatteryUsage = monthData?.totalBatteryUsage ?: 0.0
    val totalEnergy = monthData?.totalEnergy ?: 0.0
    val avgEfficiency = efficiencyWhPerUnit(totalEnergy, totalDistance)

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
                Column {
                    Text(
                        text = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = yearMonth.year.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = palette.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%,d".format(driveCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    icon = CustomIcons.Road,
                    value = UnitFormatter.formatDistance(totalDistance, units),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    prefix = "Ø",
                    icon = CustomIcons.Road,
                    value = UnitFormatter.formatDistance(avgDistance, units),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    iconText = "🔋",
                    value = "%.0f%%".format(totalBatteryUsage),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    prefix = "Ø",
                    icon = Icons.Outlined.EnergySavingsLeaf,
                    value = UnitFormatter.formatEfficiency(avgEfficiency, units),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    icon = Icons.Filled.ElectricBolt,
                    value = UnitFormatter.formatEnergy(totalEnergy),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    icon = Icons.Filled.AttachMoney,
                    value = UnitFormatter.formatCost(monthData?.totalEnergyCost ?: 0.0, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayTripRow(
    dayData: DailyMileage,
    units: Units?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val dayOfWeek = dayData.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val dateStr = "%d %s".format(
        dayData.date.dayOfMonth,
        dayData.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    )
    val avgEfficiency = efficiencyWhPerUnit(dayData.totalEnergy, dayData.totalDistance)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day info
            Column(modifier = Modifier.width(60.dp)) {
                Text(
                    text = dayOfWeek,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
            // Stats
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    // Distance
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CustomIcons.Road,
                            contentDescription = null,
                            tint = ChartBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = UnitFormatter.formatDistance(dayData.totalDistance, units),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Efficiency
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = UnitFormatter.formatEfficiency(avgEfficiency, units),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Drive count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = dayData.driveCount.toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Energy cost
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = UnitFormatter.formatCost(dayData.totalEnergyCost ?: 0.0, currencySymbol),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Energy
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ElectricBolt,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "%.1f kWh".format(dayData.totalEnergy),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Battery usage
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Battery5Bar,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "%.0f%%".format(dayData.totalBatteryUsage),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // Arrow indicator
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.view_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
