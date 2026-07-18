package com.matedroid.ui.screens.costs

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.CostAnalyticsMetrics
import com.matedroid.domain.TripCostCoverage
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes

/**
 * Cost Analytics screen. Uses only the local Room cache + user settings; there
 * are no live API calls beyond the shared units-of-measure fetch performed by
 * the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostAnalyticsScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: CostAnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) { viewModel.setCarId(carId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cost_analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> MateDroidLoadingPlaceholder(
                color = palette.accent,
                modifier = Modifier.padding(padding),
            )
            else -> CostAnalyticsContent(
                metrics = uiState.metrics,
                range = uiState.range,
                units = uiState.units,
                currencySymbol = uiState.currencySymbol,
                palette = palette,
                onRangeSelected = viewModel::setRange,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun CostAnalyticsContent(
    metrics: CostAnalyticsMetrics,
    range: CostAnalyticsRange,
    units: Units?,
    currencySymbol: String,
    palette: CarColorPalette,
    onRangeSelected: (CostAnalyticsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RangeChips(
                selected = range,
                palette = palette,
                onSelected = onRangeSelected,
            )
        }
        item {
            HeadlineCard(
                metrics = metrics,
                units = units,
                currencySymbol = currencySymbol,
                palette = palette,
            )
        }
        item {
            CoverageCard(
                metrics = metrics,
                currencySymbol = currencySymbol,
                palette = palette,
            )
        }
        if (metrics.chargeCount == 0) {
            item {
                EmptyStateCard(palette = palette)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChips(
    selected: CostAnalyticsRange,
    palette: CarColorPalette,
    onSelected: (CostAnalyticsRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CostAnalyticsRange.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(stringResource(option.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.25f),
                    selectedLabelColor = palette.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun HeadlineCard(
    metrics: CostAnalyticsMetrics,
    units: Units?,
    currencySymbol: String,
    palette: CarColorPalette,
) {
    val unavailable = stringResource(R.string.cost_analytics_unavailable)
    val distanceUnit = UnitFormatter.getDistanceUnit(units)

    // Prefer the effective (recorded + estimated) total for headline display.
    // Fall back to just the recorded value when no rate/estimation is active.
    val headlineCost = (metrics.totalEffectiveCost ?: metrics.totalKnownCost)
        ?.let { UnitFormatter.formatCost(it, currencySymbol) }
        ?: unavailable
    val costPer100 = metrics.costPer100Distance
        ?.let { UnitFormatter.formatCost(it, currencySymbol) }
        ?: unavailable
    val avgPerKwh = metrics.avgCostPerKwh
        ?.let { UnitFormatter.formatCost(it, currencySymbol, perKwh = true) }
        ?: unavailable

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cost_analytics_headline_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Paid,
                    label = stringResource(
                        if (metrics.usesEstimates) {
                            R.string.cost_analytics_effective_cost
                        } else {
                            R.string.cost_analytics_known_cost
                        }
                    ),
                    value = headlineCost,
                    palette = palette,
                    modifier = Modifier.weight(1.2f),
                )
                SummaryItem(
                    icon = Icons.Filled.AttachMoney,
                    label = stringResource(
                        R.string.cost_analytics_cost_per_100,
                        distanceUnit,
                    ),
                    value = costPer100,
                    palette = palette,
                    modifier = Modifier.weight(0.8f),
                )
            }
            if (metrics.usesEstimates) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = palette.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.cost_analytics_estimated_note,
                            metrics.estimatedChargeCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                }
                if (metrics.recordedChargeCount > 0 && metrics.totalKnownCost != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.cost_analytics_recorded_vs_estimated,
                            UnitFormatter.formatCost(metrics.totalKnownCost, currencySymbol),
                            metrics.recordedChargeCount,
                            UnitFormatter.formatCost(metrics.totalEstimatedCost ?: 0.0, currencySymbol),
                            metrics.estimatedChargeCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.BatteryChargingFull,
                    label = stringResource(R.string.cost_analytics_energy_added),
                    value = UnitFormatter.formatEnergy(metrics.energyAddedTotal),
                    palette = palette,
                    modifier = Modifier.weight(1.2f),
                )
                SummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.cost_analytics_avg_cost_per_kwh),
                    value = avgPerKwh,
                    palette = palette,
                    modifier = Modifier.weight(0.8f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Straighten,
                    label = stringResource(R.string.cost_analytics_distance),
                    value = UnitFormatter.formatDistance(
                        metrics.totalDistance,
                        units,
                        decimals = 0,
                    ),
                    palette = palette,
                    modifier = Modifier.weight(1.2f),
                )
                SummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.cost_analytics_charges),
                    value = "%,d".format(metrics.chargeCount),
                    palette = palette,
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
    }
}

@Composable
private fun CoverageCard(
    metrics: CostAnalyticsMetrics,
    currencySymbol: String,
    palette: CarColorPalette,
) {
    if (metrics.chargeCount == 0) return

    val coverageLabel = when (metrics.costCoverage) {
        TripCostCoverage.None -> stringResource(R.string.cost_analytics_coverage_none)
        TripCostCoverage.Partial -> stringResource(
            R.string.cost_analytics_coverage_partial,
            metrics.recordedChargeCount + metrics.estimatedChargeCount,
            metrics.chargeCount,
        )
        TripCostCoverage.Complete -> if (metrics.usesEstimates) {
            stringResource(R.string.cost_analytics_coverage_complete_with_estimates)
        } else {
            stringResource(R.string.cost_analytics_coverage_complete)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cost_analytics_coverage_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Info,
                    label = stringResource(R.string.cost_analytics_coverage_label),
                    value = coverageLabel,
                    palette = palette,
                    modifier = Modifier.weight(1.2f),
                )
                SummaryItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    label = stringResource(R.string.cost_analytics_missing),
                    value = "%,d".format(metrics.missingCostCount),
                    palette = palette,
                    modifier = Modifier.weight(0.8f),
                )
            }
            if (metrics.zeroCostCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryItem(
                        icon = Icons.Filled.MoneyOff,
                        label = stringResource(R.string.cost_analytics_free_sessions),
                        value = "%,d".format(metrics.zeroCostCount),
                        palette = palette,
                        modifier = Modifier.weight(1.2f),
                    )
                    Spacer(modifier = Modifier.weight(0.8f))
                }
            }
            if (metrics.costCoverage != TripCostCoverage.Complete) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = palette.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (metrics.costCoverage) {
                            TripCostCoverage.None -> stringResource(R.string.cost_analytics_coverage_none_note)
                            TripCostCoverage.Partial -> stringResource(
                                R.string.cost_analytics_coverage_partial_note,
                            )
                            TripCostCoverage.Complete -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(palette: CarColorPalette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cost_analytics_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = palette.accent,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface,
            )
        }
    }
}

private fun CostAnalyticsRange.labelRes(): Int = when (this) {
    CostAnalyticsRange.SevenDays -> R.string.cost_analytics_range_7_days
    CostAnalyticsRange.ThirtyDays -> R.string.cost_analytics_range_30_days
    CostAnalyticsRange.NinetyDays -> R.string.cost_analytics_range_90_days
    CostAnalyticsRange.AllTime -> R.string.cost_analytics_range_all_time
}
