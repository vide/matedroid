package com.matedroid.ui.screens.drives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.components.OverlayCurve
import com.matedroid.ui.components.PowerSocOverlayChart
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.parseIsoDateTime
import java.util.Locale

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
    val uiState by viewModel.uiState.collectAsState()
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
    palette: CarColorPalette,
    onSortChange: (DriveCompareSort) -> Unit,
    onDriveClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ranked = remember(comparison, sort) {
        when (sort) {
            DriveCompareSort.EFFICIENCY -> comparison.all.sortedBy { it.efficiency ?: Double.MAX_VALUE }
            DriveCompareSort.DURATION -> comparison.all.sortedBy { it.durationMin }
            DriveCompareSort.SPEED -> comparison.all.sortedByDescending { it.speedAvg }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                SortChip(stringResource(R.string.efficiency), sort == DriveCompareSort.EFFICIENCY, palette.accent) { onSortChange(DriveCompareSort.EFFICIENCY) }
                SortChip(stringResource(R.string.duration), sort == DriveCompareSort.DURATION, palette.accent) { onSortChange(DriveCompareSort.DURATION) }
                SortChip(stringResource(R.string.speed), sort == DriveCompareSort.SPEED, palette.accent) { onSortChange(DriveCompareSort.SPEED) }
            }
        }

        OverlayChartCard(
            comparison = comparison,
            curves = curves,
            curvesLoading = curvesLoading,
            units = units,
            palette = palette
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
                    onClick = if (drive.isBase) null else { { onDriveClick(drive.driveId) } }
                )
                prev = idx
            }
            val tailGap = ranked.lastIndex - prev
            if (tailGap > 0) GapRow(tailGap) { showAll = true }

            AverageRow(average = comparison.average, sort = sort, units = units, palette = palette)
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

@Composable
private fun AverageRow(
    average: DriveAverage,
    sort: DriveCompareSort,
    units: Units?,
    palette: CarColorPalette
) {
    val value = when (sort) {
        DriveCompareSort.EFFICIENCY -> average.efficiency?.let { UnitFormatter.formatEfficiency(it, units) } ?: "—"
        DriveCompareSort.DURATION -> formatDurationCompact(average.durationMin)
        DriveCompareSort.SPEED -> UnitFormatter.formatSpeed(average.speedAvg.toDouble(), units)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Ø",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
            modifier = Modifier.width(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.compare_average),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                maxLines = 1
            )
            Pill(pluralStringResource(R.plurals.compare_drives_nearby, average.count, average.count))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = palette.accent
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayChartCard(
    comparison: com.matedroid.domain.DriveComparison,
    curves: List<DriveSessionCurve>,
    curvesLoading: Boolean,
    units: Units?,
    palette: CarColorPalette
) {
    val byId = remember(comparison) { comparison.all.associateBy { it.driveId } }
    val otherColors = listOf(
        Color(0xFF5B8DD9), Color(0xFF4CAF50), Color(0xFF9B6BD9), Color(0xFF6D7A92)
    )
    val thisDriveLabel = stringResource(R.string.compare_this_drive)
    val distanceUnit = if (units?.isImperial == true) "mi" else "km"
    val speedUnit = UnitFormatter.getSpeedUnit(units)

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
            points = sessionCurve.points.map { Offset(it.distance, it.speed) }
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
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.compare_speed_vs_distance),
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
                PowerSocOverlayChart(
                    curves = overlay,
                    xUnit = " $distanceUnit",
                    xCaption = "",
                    valueUnit = speedUnit
                )
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
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompareRow(
    rank: Int,
    drive: ComparableDrive,
    sort: DriveCompareSort,
    units: Units?,
    palette: CarColorPalette,
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
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (drive.isBase) palette.accent else MaterialTheme.colorScheme.onSurface
        )
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

private fun valueFor(drive: ComparableDrive, sort: DriveCompareSort, units: Units?): String =
    when (sort) {
        DriveCompareSort.EFFICIENCY -> drive.efficiency?.let { UnitFormatter.formatEfficiency(it, units) } ?: "—"
        DriveCompareSort.DURATION -> formatDurationCompact(drive.durationMin)
        DriveCompareSort.SPEED -> UnitFormatter.formatSpeed(drive.speedAvg.toDouble(), units)
    }
