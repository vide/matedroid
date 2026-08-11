package com.matedroid.ui.screens.mileage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
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
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ============================================================================
// Level 2: Year Detail
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YearDetailScreen(
    year: Int,
    uiState: MileageUiState,
    chartData: List<Pair<Int, Double>>,
    palette: CarColorPalette,
    onClose: () -> Unit,
    onMonthClick: (YearMonth) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(year.toString()) },
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
            // Year summary stats
            item {
                SummaryRow(
                    totalDistance = uiState.yearTotalDistance,
                    avgDistance = uiState.avgMonthlyDistance,
                    avgLabel = stringResource(R.string.mileage_avg_month),
                    driveCount = uiState.yearDriveCount,
                    totalEnergyUsed = uiState.yearTotalEnergy,
                    totalEnergyCost = uiState.yearTotalEnergyCost,
                    avgEnergyDistance = uiState.avgYearEnergyDistance,
                    currencySymbol = uiState.currencySymbol,
                    units = uiState.units,
                    palette = palette
                )
            }

            // Monthly chart
            item {
                MileageChartCard(
                    title = stringResource(R.string.mileage_by_month),
                    chartData = chartData,
                    palette = palette,
                    units = uiState.units
                )
            }

            // Monthly list
            items(uiState.monthlyData, key = { it.yearMonth.toString() }, contentType = { "month" }) { monthData ->
                MonthRow(
                    monthData = monthData,
                    units = uiState.units,
                    currencySymbol = uiState.currencySymbol,
                    onClick = { onMonthClick(monthData.yearMonth) }
                )
            }

            // Empty state
            if (uiState.monthlyData.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.mileage_no_data_year, year),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthRow(
    monthData: MonthlyMileage,
    units: Units?,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val avgEfficiency = efficiencyWhPerUnit(monthData.totalEnergy, monthData.totalDistance)
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = monthData.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = monthData.yearMonth.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End) {
                        Icon(
                            imageVector = CustomIcons.Road,
                            contentDescription = null,
                            tint = ChartBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = UnitFormatter.formatDistance(monthData.totalDistance, units, 0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End) {
                        Icon(
                            imageVector = Icons.Outlined.EnergySavingsLeaf,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = UnitFormatter.formatEfficiency(avgEfficiency, units),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%,d".format(monthData.driveCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = UnitFormatter.formatCost(monthData.totalEnergyCost ?: 0.0, currencySymbol),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
