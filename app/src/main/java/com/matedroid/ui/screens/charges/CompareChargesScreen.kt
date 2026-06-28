package com.matedroid.ui.screens.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.ComparableCharge
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.parseIsoDateTime
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareChargesScreen(
    carId: Int,
    baseChargeId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit,
    viewModel: ChargeComparisonViewModel = hiltViewModel()
) {
    LaunchedEffect(carId, baseChargeId) { viewModel.load(carId, baseChargeId) }
    val uiState by viewModel.uiState.collectAsState()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isSystemInDarkTheme())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_charges_title)) },
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
        }
    ) { padding ->
        val comparison = uiState.comparison
        when {
            uiState.isLoading -> MateDroidLoadingPlaceholder(modifier = Modifier.padding(padding))
            comparison == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.compare_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> CompareContent(
                comparison = comparison,
                sort = uiState.sort,
                currencySymbol = uiState.currencySymbol,
                units = uiState.units,
                palette = palette,
                onSortChange = viewModel::setSort,
                onChargeClick = onNavigateToChargeDetail,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CompareContent(
    comparison: com.matedroid.domain.ChargeComparison,
    sort: CompareSort,
    currencySymbol: String,
    units: Units?,
    palette: CarColorPalette,
    onSortChange: (CompareSort) -> Unit,
    onChargeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ranked = remember(comparison, sort) {
        when (sort) {
            CompareSort.PEAK -> comparison.all.sortedByDescending { it.peakKw ?: -1 }
            CompareSort.DURATION -> comparison.all.sortedBy { it.durationMin }
            CompareSort.COST -> comparison.all.sortedBy { it.costPerKwh ?: Double.MAX_VALUE }
        }
    }
    val radiusKm = (comparison.radiusMeters / 1000.0).roundToInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Scope
        Text(
            text = stringResource(R.string.compare_scope, comparison.totalCount, radiusKm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Sort control
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.ranked_by).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip(stringResource(R.string.peak), sort == CompareSort.PEAK, palette.accent) { onSortChange(CompareSort.PEAK) }
                SortChip(stringResource(R.string.duration), sort == CompareSort.DURATION, palette.accent) { onSortChange(CompareSort.DURATION) }
                SortChip(stringResource(R.string.cost), sort == CompareSort.COST, palette.accent) { onSortChange(CompareSort.COST) }
            }
        }

        // Leaderboard
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ranked.forEachIndexed { index, charge ->
                CompareRow(
                    rank = index + 1,
                    charge = charge,
                    sort = sort,
                    currencySymbol = currencySymbol,
                    units = units,
                    palette = palette,
                    onClick = if (charge.isBase) null else { { onChargeClick(charge.chargeId) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.16f),
            selectedLabelColor = accent
        )
    )
}

@Composable
private fun CompareRow(
    rank: Int,
    charge: ComparableCharge,
    sort: CompareSort,
    currencySymbol: String,
    units: Units?,
    palette: CarColorPalette,
    onClick: (() -> Unit)?
) {
    val bg = if (charge.isBase) palette.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val (value, unit) = valueFor(charge, sort, currencySymbol)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rankBadge(rank),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (charge.isBase) stringResource(R.string.compare_this_charge)
                else (charge.brand?.takeIf { it.isNotBlank() } ?: charge.address),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (charge.isBase) palette.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                parseIsoDateTime(charge.startDate)?.toLocalDate()?.let {
                    Pill(it.formatMedium(Locale.getDefault()))
                }
                Pill("${charge.startBattery} → ${charge.endBattery}%")
                charge.outsideTempAvg?.let { Pill(UnitFormatter.formatTemperature(it, units)) }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (charge.isBase) palette.accent else MaterialTheme.colorScheme.onSurface
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

private fun rankBadge(rank: Int): String = when (rank) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> "$rank"
}

private fun valueFor(charge: ComparableCharge, sort: CompareSort, currencySymbol: String): Pair<String, String> =
    when (sort) {
        CompareSort.PEAK -> (charge.peakKw?.let { "$it" } ?: "—") to "kW"
        CompareSort.DURATION -> formatDurationCompact(charge.durationMin) to ""
        CompareSort.COST -> (charge.costPerKwh?.let { "$currencySymbol${"%.3f".format(it)}" } ?: "—") to "/kWh"
    }
