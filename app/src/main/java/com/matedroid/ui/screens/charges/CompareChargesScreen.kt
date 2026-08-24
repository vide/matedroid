package com.matedroid.ui.screens.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.ChargeAverage
import com.matedroid.domain.ComparableCharge
import com.matedroid.domain.CostPerKwhBasis
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.ComparisonVerdict
import com.matedroid.ui.components.DeltaChip
import com.matedroid.ui.components.DeltaTone
import com.matedroid.ui.components.LegendItem
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.components.OverlayCurve
import com.matedroid.ui.components.Pill
import com.matedroid.ui.components.PowerSocOverlayChart
import com.matedroid.ui.components.RowDelta
import com.matedroid.ui.components.SortChip
import com.matedroid.ui.components.isBetter
import com.matedroid.ui.components.percentDelta
import com.matedroid.ui.components.rankBadge
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.parseIsoDateTime
import java.util.Locale
import kotlin.math.abs
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                curves = uiState.curves,
                curvesLoading = uiState.curvesLoading,
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
    curves: List<SessionCurve>,
    curvesLoading: Boolean,
    palette: CarColorPalette,
    onSortChange: (CompareSort) -> Unit,
    onChargeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tie-break in favour of the base charge, so a charge tied for best ranks first (not #2).
    val ranked = remember(comparison, sort) {
        when (sort) {
            CompareSort.PEAK -> comparison.all.sortedWith(compareByDescending<ComparableCharge> { it.peakKw ?: -1 }.thenBy { !it.isBase })
            CompareSort.DURATION -> comparison.all.sortedWith(compareBy({ it.durationSeconds }, { !it.isBase }))
            CompareSort.COST -> comparison.all.sortedWith(compareBy({ it.costPerKwh ?: Double.MAX_VALUE }, { !it.isBase }))
        }
    }
    val radiusKm = (comparison.radiusMeters / 1000.0).roundToInt()

    val higherBetter = chargeHigherIsBetter(sort)
    val baseVal = chargeMetricValue(comparison.base, sort)
    fun deltaVs(value: Double?): RowDelta? {
        if (value == null || baseVal == null || baseVal == 0.0) return null
        return RowDelta(percentDelta(value, baseVal), isBetter(value, baseVal, higherBetter))
    }

    // Bumped on an outside tap or a scroll to dismiss the chart tooltip.
    var dismissKey by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { if (it) dismissKey++ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { dismissKey++ } }
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

        // Verdict: this charge vs the area's average and best. Cost is compared via the Cost sort,
        // so the verdict doesn't repeat €/kWh on every dimension.
        val avgVal = chargeAvgMetricValue(comparison.average, sort)
        if (baseVal != null && avgVal != null && avgVal != 0.0) {
            val mag = abs(percentDelta(baseVal, avgVal))
            val betterThanAvg = isBetter(baseVal, avgVal, higherBetter)
            val (primary, tone) = if (mag < 1) {
                stringResource(R.string.compare_on_par_average) to DeltaTone.NEUTRAL
            } else {
                val res = when (sort) {
                    CompareSort.PEAK -> if (betterThanAvg) R.string.compare_peak_better else R.string.compare_peak_worse
                    CompareSort.DURATION -> if (betterThanAvg) R.string.compare_dur_better else R.string.compare_dur_worse
                    CompareSort.COST -> if (betterThanAvg) R.string.compare_cost_better else R.string.compare_cost_worse
                }
                stringResource(res, mag) to (if (betterThanAvg) DeltaTone.GOOD else DeltaTone.BAD)
            }
            val rank = ranked.indexOfFirst { it.isBase } + 1
            val isBest = rank == 1
            val bestVal = ranked.firstOrNull()?.let { chargeMetricValue(it, sort) }
            val offBest = if (!isBest && bestVal != null) abs(percentDelta(baseVal, bestVal)) else null
            val rankText = stringResource(R.string.compare_rank_of, rank, comparison.totalCount)
            ComparisonVerdict(
                accent = palette.accent,
                primary = primary,
                tone = tone,
                secondary = if (offBest != null) "$rankText · " + stringResource(R.string.compare_pct_off_best, offBest) else rankText,
                costLine = null,
                badge = if (isBest) stringResource(R.string.compare_personal_best) else null
            )
        }

        // Power-vs-SoC overlay of the top sessions for the current sort
        OverlayChartCard(
            comparison = comparison,
            curves = curves,
            curvesLoading = curvesLoading,
            palette = palette,
            dismissKey = dismissKey
        )

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
                    delta = if (charge.isBase) null else deltaVs(chargeMetricValue(charge, sort)),
                    onClick = if (charge.isBase) null else { { onChargeClick(charge.chargeId) } }
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayChartCard(
    comparison: com.matedroid.domain.ChargeComparison,
    curves: List<SessionCurve>,
    curvesLoading: Boolean,
    palette: CarColorPalette,
    dismissKey: Any?
) {
    val byId = remember(comparison) { comparison.all.associateBy { it.chargeId } }
    val otherColors = listOf(
        Color(0xFF5B8DD9), Color(0xFF4CAF50), Color(0xFF9B6BD9), Color(0xFF6D7A92)
    )
    val thisChargeLabel = stringResource(R.string.compare_this_charge)

    var otherIndex = 0
    val overlay = curves.map { sessionCurve ->
        val meta = byId[sessionCurve.chargeId]
        val color = if (sessionCurve.isBase) palette.accent else otherColors[otherIndex++ % otherColors.size]
        val label = if (sessionCurve.isBase) {
            thisChargeLabel
        } else {
            meta?.brand?.takeIf { it.isNotBlank() }
                ?: meta?.startDate?.let { parseIsoDateTime(it)?.toLocalDate()?.formatMedium(Locale.getDefault()) }
                ?: ""
        }
        OverlayCurve(
            label = label,
            color = color,
            isBase = sessionCurve.isBase,
            points = sessionCurve.points.map { Offset(it.soc, it.power) }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.compare_power_vs_soc),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (overlay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (curvesLoading) CircularProgressIndicator(color = palette.accent)
                }
            } else {
                PowerSocOverlayChart(curves = overlay, dismissKey = dismissKey)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    overlay.forEach { LegendItem(it.color, it.label) }
                }
            }
        }
    }
}


@Composable
private fun CompareRow(
    rank: Int,
    charge: ComparableCharge,
    sort: CompareSort,
    currencySymbol: String,
    units: Units?,
    palette: CarColorPalette,
    delta: RowDelta?,
    onClick: (() -> Unit)?
) {
    val bg = if (charge.isBase) palette.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val (value, unit) = valueFor(charge, sort, currencySymbol, stringResource(R.string.charge_free), perKwhSuffix())

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
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (charge.isBase) palette.accent else MaterialTheme.colorScheme.onSurface
            )
            // Keep the unit (e.g. kW) visible on every row, with the delta chip alongside it.
            if (unit.isNotEmpty() || delta != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DeltaChip(delta)
                }
            }
        }
    }
}

private fun chargeMetricValue(c: ComparableCharge, sort: CompareSort): Double? = when (sort) {
    CompareSort.PEAK -> c.peakKw?.toDouble()
    CompareSort.DURATION -> c.durationSeconds.toDouble()
    CompareSort.COST -> c.costPerKwh
}

private fun chargeAvgMetricValue(a: ChargeAverage, sort: CompareSort): Double? = when (sort) {
    CompareSort.PEAK -> a.peakKw?.toDouble()
    CompareSort.DURATION -> a.durationSeconds.toDouble()
    CompareSort.COST -> a.costPerKwh
}

private fun chargeHigherIsBetter(sort: CompareSort): Boolean = sort == CompareSort.PEAK


/** Basis-qualified "/kWh" suffix so the compared figure is never ambiguous (issue #257). */
@Composable
private fun perKwhSuffix(): String = stringResource(
    if (CostPerKwhBasis.current == CostPerKwhBasis.ENERGY_USED) {
        R.string.per_kwh_suffix_used
    } else {
        R.string.per_kwh_suffix_added
    }
)

private fun valueFor(
    charge: ComparableCharge,
    sort: CompareSort,
    currencySymbol: String,
    freeLabel: String,
    perKwhSuffix: String
): Pair<String, String> =
    when (sort) {
        CompareSort.PEAK -> (charge.peakKw?.let { "$it" } ?: "—") to "kW"
        CompareSort.DURATION -> formatDurationCompact(charge.durationMin) to ""
        CompareSort.COST -> {
            val cpk = charge.costPerKwh
            when {
                cpk == null -> "—" to ""
                cpk <= 0.0 -> freeLabel to ""
                else -> "$currencySymbol${"%.3f".format(cpk)}" to perKwhSuffix
            }
        }
    }
