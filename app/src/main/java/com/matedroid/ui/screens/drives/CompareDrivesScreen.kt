package com.matedroid.ui.screens.drives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.ComparableDrive
import com.matedroid.domain.DriveAverage
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareDrivesScreen(
    carId: Int,
    baseDriveId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (Int) -> Unit,
    viewModel: DriveComparisonViewModel = hiltViewModel()
) {
    LaunchedEffect(carId, baseDriveId) { viewModel.load(carId, baseDriveId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isSystemInDarkTheme())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_drives_title)) },
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
            uiState.isLoading -> MateDroidLoadingPlaceholder(
                color = palette.accent,
                modifier = Modifier.padding(padding)
            )
            comparison == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.compare_drives_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> CompareContent(
                comparison = comparison,
                sort = uiState.sort,
                units = uiState.units,
                curves = uiState.curves,
                curvesLoading = uiState.curvesLoading,
                averageCurve = uiState.averageCurve,
                palette = palette,
                onSortChange = viewModel::setSort,
                onDriveClick = onNavigateToDriveDetail,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CompareContent(
    comparison: com.matedroid.domain.DriveComparison,
    sort: DriveCompareSort,
    units: Units?,
    curves: List<DriveSessionCurve>,
    curvesLoading: Boolean,
    averageCurve: List<DriveCurvePoint>,
    palette: CarColorPalette,
    onSortChange: (DriveCompareSort) -> Unit,
    onDriveClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tie-break in favour of the base run, so a run tied for best ranks first (not demoted to #2).
    val ranked = remember(comparison, sort) {
        when (sort) {
            DriveCompareSort.EFFICIENCY -> comparison.all.sortedWith(compareBy({ it.efficiency ?: Double.MAX_VALUE }, { !it.isBase }))
            DriveCompareSort.DURATION -> comparison.all.sortedWith(compareBy({ it.durationSeconds }, { !it.isBase }))
            DriveCompareSort.SPEED -> comparison.all.sortedWith(compareByDescending<ComparableDrive> { it.avgSpeedPrecise }.thenBy { !it.isBase })
        }
    }

    val higherBetter = driveHigherIsBetter(sort)
    val baseVal = driveMetricValue(comparison.base, sort)
    // Per-run delta vs the base run (null for the base itself or when the metric is missing).
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
        Text(
            text = stringResource(R.string.compare_drives_scope, comparison.totalCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.ranked_by).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip(stringResource(R.string.speed), sort == DriveCompareSort.SPEED, palette.accent) { onSortChange(DriveCompareSort.SPEED) }
                SortChip(stringResource(R.string.efficiency), sort == DriveCompareSort.EFFICIENCY, palette.accent) { onSortChange(DriveCompareSort.EFFICIENCY) }
                SortChip(stringResource(R.string.duration), sort == DriveCompareSort.DURATION, palette.accent) { onSortChange(DriveCompareSort.DURATION) }
            }
        }

        // Verdict: this run vs the route's average and best
        val avgVal = driveAvgMetricValue(comparison.average, sort)
        if (baseVal != null && avgVal != null && avgVal != 0.0) {
            val mag = abs(percentDelta(baseVal, avgVal))
            val betterThanAvg = isBetter(baseVal, avgVal, higherBetter)
            val (primary, tone) = if (mag < 1) {
                stringResource(R.string.compare_on_par_average) to DeltaTone.NEUTRAL
            } else {
                val res = when (sort) {
                    DriveCompareSort.EFFICIENCY -> if (betterThanAvg) R.string.compare_eff_better else R.string.compare_eff_worse
                    DriveCompareSort.SPEED -> if (betterThanAvg) R.string.compare_speed_better else R.string.compare_speed_worse
                    DriveCompareSort.DURATION -> if (betterThanAvg) R.string.compare_dur_better else R.string.compare_dur_worse
                }
                stringResource(res, mag) to (if (betterThanAvg) DeltaTone.GOOD else DeltaTone.BAD)
            }
            val rank = ranked.indexOfFirst { it.isBase } + 1
            val isBest = rank == 1
            val bestVal = ranked.firstOrNull()?.let { driveMetricValue(it, sort) }
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

        OverlayChartCard(
            comparison = comparison,
            curves = curves,
            curvesLoading = curvesLoading,
            averageCurve = averageCurve,
            sort = sort,
            units = units,
            palette = palette,
            dismissKey = dismissKey
        )

        // Curate the leaderboard: top 3 of this dimension + the current run with its neighbours,
        // the rest collapsed behind a tappable "+N more", and an averaged reference row.
        var showAll by rememberSaveable { mutableStateOf(false) }
        val baseIndex = ranked.indexOfFirst { it.isBase }.coerceAtLeast(0)
        val keptIndices = if (showAll) {
            ranked.indices.toList()
        } else {
            val keep = sortedSetOf<Int>()
            listOf(0, 1, 2).forEach { if (it in ranked.indices) keep.add(it) }
            for (d in -1..1) (baseIndex + d).let { if (it in ranked.indices) keep.add(it) }
            if (ranked.isNotEmpty()) keep.add(ranked.lastIndex) // always show the worst run
            keep.toList()
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var prev = -1
            keptIndices.forEach { idx ->
                val gap = idx - prev - 1
                if (gap > 0) GapRow(gap) { showAll = true }
                val drive = ranked[idx]
                CompareRow(
                    rank = idx + 1,
                    drive = drive,
                    sort = sort,
                    units = units,
                    palette = palette,
                    delta = if (drive.isBase) null else deltaVs(driveMetricValue(drive, sort)),
                    onClick = if (drive.isBase) null else { { onDriveClick(drive.driveId) } }
                )
                prev = idx
            }
            val tailGap = ranked.lastIndex - prev
            if (tailGap > 0) GapRow(tailGap) { showAll = true }

            ReferenceRow(
                leading = "Ø",
                title = stringResource(R.string.compare_average),
                caption = pluralStringResource(R.plurals.compare_drives_nearby, comparison.average.count, comparison.average.count),
                value = averageValue(comparison.average, sort, units),
                delta = deltaVs(driveAvgMetricValue(comparison.average, sort)),
                palette = palette
            )
        }
    }
}

@Composable
private fun GapRow(hiddenCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⋯  " + pluralStringResource(R.plurals.compare_more, hiddenCount, hiddenCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A reference row (Average, P100, …) styled with an accent outline. */
@Composable
private fun ReferenceRow(
    leading: String,
    title: String,
    caption: String?,
    value: String,
    delta: RowDelta?,
    palette: CarColorPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
            modifier = Modifier.width(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                maxLines = 1
            )
            if (caption != null) Pill(caption)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
            DeltaChip(delta)
        }
    }
}

private fun driveMetricValue(d: ComparableDrive, sort: DriveCompareSort): Double? = when (sort) {
    DriveCompareSort.EFFICIENCY -> d.efficiency
    DriveCompareSort.DURATION -> d.durationSeconds.toDouble()
    DriveCompareSort.SPEED -> d.avgSpeedPrecise
}

private fun driveAvgMetricValue(a: DriveAverage, sort: DriveCompareSort): Double? = when (sort) {
    DriveCompareSort.EFFICIENCY -> a.efficiency
    DriveCompareSort.DURATION -> a.durationSeconds.toDouble()
    DriveCompareSort.SPEED -> a.avgSpeedPrecise
}

private fun driveHigherIsBetter(sort: DriveCompareSort): Boolean = sort == DriveCompareSort.SPEED

private fun averageValue(average: DriveAverage, sort: DriveCompareSort, units: Units?): String =
    when (sort) {
        DriveCompareSort.EFFICIENCY -> average.efficiency?.let { UnitFormatter.formatEfficiency(it, units) } ?: "—"
        DriveCompareSort.DURATION -> formatDurationCompact(average.durationMin)
        DriveCompareSort.SPEED -> UnitFormatter.formatSpeed(average.speedAvg.toDouble(), units)
    }


@Composable
private fun OverlayChartCard(
    comparison: com.matedroid.domain.DriveComparison,
    curves: List<DriveSessionCurve>,
    curvesLoading: Boolean,
    averageCurve: List<DriveCurvePoint>,
    sort: DriveCompareSort,
    units: Units?,
    palette: CarColorPalette,
    dismissKey: Any?
) {
    val isDuration = sort == DriveCompareSort.DURATION
    val isConsumption = sort == DriveCompareSort.EFFICIENCY
    val title = when {
        isDuration -> stringResource(R.string.duration)
        isConsumption -> stringResource(R.string.compare_consumption_vs_distance)
        else -> stringResource(R.string.compare_speed_vs_distance)
    }
    val icon = when {
        isDuration -> Icons.Default.Schedule
        isConsumption -> Icons.Default.Bolt
        else -> Icons.Default.Speed
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
                Icon(imageVector = icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Fixed body height so switching dimension (line + legend vs bars) never jumps.
            Box(modifier = Modifier.fillMaxWidth().height(CHART_BODY_HEIGHT)) {
                if (isDuration) {
                    DurationBars(comparison = comparison, palette = palette)
                } else {
                    LineOverlay(
                        comparison = comparison,
                        curves = curves,
                        curvesLoading = curvesLoading,
                        averageCurve = averageCurve,
                        valueUnit = if (isConsumption) {
                            if (units?.isImperial == true) "Wh/mi" else "Wh/km"
                        } else UnitFormatter.getSpeedUnit(units),
                        distanceUnit = if (units?.isImperial == true) "mi" else "km",
                        palette = palette,
                        dismissKey = dismissKey
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineOverlay(
    comparison: com.matedroid.domain.DriveComparison,
    curves: List<DriveSessionCurve>,
    curvesLoading: Boolean,
    averageCurve: List<DriveCurvePoint>,
    valueUnit: String,
    distanceUnit: String,
    palette: CarColorPalette,
    dismissKey: Any?
) {
    val byId = remember(comparison) { comparison.all.associateBy { it.driveId } }
    val otherColors = listOf(
        Color(0xFF5B8DD9), Color(0xFF4CAF50), Color(0xFF9B6BD9), Color(0xFF6D7A92)
    )
    val thisDriveLabel = stringResource(R.string.compare_this_drive)
    val averageLabel = stringResource(R.string.compare_average)
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Curve point lists hold thousands of Offsets — build them once per data/palette change,
    // not on every recomposition (e.g. while a tooltip is being dragged).
    val allOverlay = remember(curves, averageCurve, palette, comparison) {
        var otherIndex = 0
        val overlay = curves.map { sessionCurve ->
            val meta = byId[sessionCurve.driveId]
            val color = if (sessionCurve.isBase) palette.accent else otherColors[otherIndex++ % otherColors.size]
            val label = if (sessionCurve.isBase) {
                thisDriveLabel
            } else {
                meta?.startDate?.let { parseIsoDateTime(it)?.toLocalDate()?.formatMedium(Locale.getDefault()) } ?: ""
            }
            OverlayCurve(
                label = label,
                color = color,
                isBase = sessionCurve.isBase,
                points = sessionCurve.points.map { Offset(it.distance, it.value) }
            )
        }
        val averageOverlay = averageCurve.takeIf { it.isNotEmpty() }?.let { avg ->
            OverlayCurve(
                label = averageLabel,
                color = averageColor,
                isBase = false,
                points = avg.map { Offset(it.distance, it.value) },
                dashed = true
            )
        }
        overlay + listOfNotNull(averageOverlay)
    }

    if (curves.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (curvesLoading) CircularProgressIndicator(color = palette.accent)
        }
    } else {
        Column {
        PowerSocOverlayChart(
            curves = allOverlay,
            xUnit = " $distanceUnit",
            xCaption = "",
            valueUnit = valueUnit,
            dismissKey = dismissKey
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            allOverlay.forEach { LegendItem(it.color, it.label) }
        }
        }
    }
}

/** Fixed height for the comparison chart body, so line+legend and the duration bars match. */
private val CHART_BODY_HEIGHT = 268.dp

/** Horizontal bars for the Duration dimension: top 3, the current run, average and worst, fastest first. */
@Composable
private fun DurationBars(
    comparison: com.matedroid.domain.DriveComparison,
    palette: CarColorPalette
) {
    val thisDriveLabel = stringResource(R.string.compare_this_drive)
    val averageLabel = stringResource(R.string.compare_average)

    data class Entry(val label: String, val durationMin: Int, val isBase: Boolean, val isAverage: Boolean)

    val sortedAsc = comparison.all.sortedBy { it.durationMin }
    val kept = LinkedHashMap<Int, ComparableDrive>()
    sortedAsc.take(3).forEach { kept[it.driveId] = it }
    kept[comparison.base.driveId] = comparison.base
    sortedAsc.lastOrNull()?.let { kept[it.driveId] = it }

    val entries = kept.values.map { drive ->
        Entry(
            label = if (drive.isBase) thisDriveLabel
            else parseIsoDateTime(drive.startDate)?.toLocalDate()?.formatMedium(Locale.getDefault()) ?: drive.startAddress,
            durationMin = drive.durationMin,
            isBase = drive.isBase,
            isAverage = false
        )
    }.toMutableList()
    entries.add(Entry(averageLabel, comparison.average.durationMin, isBase = false, isAverage = true))

    val ordered = entries.sortedBy { it.durationMin }
    val maxDuration = (ordered.maxOfOrNull { it.durationMin } ?: 1).coerceAtLeast(1)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        ordered.forEach { entry ->
            val barColor = when {
                entry.isBase -> palette.accent
                entry.isAverage -> palette.accent.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
            val labelColor = if (entry.isBase || entry.isAverage) palette.accent else MaterialTheme.colorScheme.onSurface
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (entry.isBase || entry.isAverage) FontWeight.Bold else FontWeight.Normal,
                        color = labelColor,
                        maxLines = 1
                    )
                    Text(
                        text = formatDurationCompact(entry.durationMin),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(entry.durationMin.toFloat() / maxDuration)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}


@Composable
private fun CompareRow(
    rank: Int,
    drive: ComparableDrive,
    sort: DriveCompareSort,
    units: Units?,
    palette: CarColorPalette,
    delta: RowDelta?,
    onClick: (() -> Unit)?
) {
    val bg = if (drive.isBase) palette.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val value = valueFor(drive, sort, units)

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
                text = if (drive.isBase) stringResource(R.string.compare_this_drive)
                else (parseIsoDateTime(drive.startDate)?.toLocalDate()?.formatMedium(Locale.getDefault()) ?: drive.startAddress),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (drive.isBase) palette.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(UnitFormatter.formatSpeed(drive.speedAvg.toDouble(), units))
                drive.outsideTempAvg?.let { Pill(UnitFormatter.formatTemperature(it, units)) }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (drive.isBase) palette.accent else MaterialTheme.colorScheme.onSurface
            )
            DeltaChip(delta)
        }
    }
}


private fun valueFor(drive: ComparableDrive, sort: DriveCompareSort, units: Units?): String =
    when (sort) {
        DriveCompareSort.EFFICIENCY -> drive.efficiency?.let { UnitFormatter.formatEfficiency(it, units) } ?: "—"
        DriveCompareSort.DURATION -> formatDurationCompact(drive.durationMin)
        DriveCompareSort.SPEED -> UnitFormatter.formatSpeed(drive.speedAvg.toDouble(), units)
    }
