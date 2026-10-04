package com.matedroid.ui.screens.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.AccentStatTile
import com.matedroid.ui.components.HeroStat
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    carId: Int,
    efficiency: Double?,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: BatteryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) {
        viewModel.setCarId(carId, efficiency)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_health_title)) },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && !uiState.isRefreshing) {
                MateDroidLoadingPlaceholder(color = palette.accent)
            } else {
                val stats = uiState.stats
                if (stats != null) {
                    BatteryHealthContent(
                        stats = stats,
                        units = uiState.units,
                        palette = palette
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_battery_data),
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
private fun BatteryHealthContent(
    stats: BatteryStats,
    units: Units?,
    palette: CarColorPalette
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BatteryHeroSection(stats = stats, units = units, palette = palette)

        if (stats.maxRangeNew > 0) {
            RangeTiles(stats = stats, units = units, palette = palette)
        }

        RightNowCard(stats = stats, units = units, palette = palette)
    }
}

/**
 * Hero in the detail-screen style: the estimated health as the dominant figure with a slim bar,
 * usable capacity now vs. when new and what was lost, and the rated efficiency in the footer.
 */
@Composable
private fun BatteryHeroSection(
    stats: BatteryStats,
    units: Units?,
    palette: CarColorPalette
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = stringResource(R.string.estimated_degradation_title),
            message = stringResource(R.string.estimated_degradation_message),
            onDismiss = { showInfo = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Caption
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.battery_health_estimated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            InfoIcon(onClick = { showInfo = true })
        }

        // Dominant figure: estimated health
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%.1f".format(stats.healthPercent),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
            Text(
                text = " %",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }

        HealthBar(
            fraction = (stats.healthPercent / 100f).toFloat().coerceIn(0f, 1f),
            palette = palette
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroStat(
                label = stringResource(R.string.battery_usable_now),
                value = "%.1f kWh".format(stats.currentCapacity),
                modifier = Modifier.weight(1f)
            )
            HeroStat(
                label = stringResource(R.string.battery_when_new),
                value = "%.1f kWh".format(stats.originalCapacity),
                modifier = Modifier.weight(1f)
            )
            HeroStat(
                label = stringResource(R.string.battery_lost),
                value = "%.1f kWh".format(stats.lossKwh),
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Footer: rated efficiency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.battery_rated_efficiency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = UnitFormatter.formatEfficiency(stats.ratedEfficiencyWhPerUnit, units, 0),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Slim 8dp bar: accent fill over the palette's progress track. */
@Composable
private fun HealthBar(fraction: Float, palette: CarColorPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.progressTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.accent)
        )
    }
}

/** Range at 100% now vs. when new, and the difference, as accent tiles. */
@Composable
private fun RangeTiles(
    stats: BatteryStats,
    units: Units?,
    palette: CarColorPalette
) {
    val tiles = listOf(
        stringResource(R.string.battery_range_now) to UnitFormatter.formatDistance(stats.maxRangeNow, units, 0),
        stringResource(R.string.battery_when_new) to UnitFormatter.formatDistance(stats.maxRangeNew, units, 0),
        stringResource(R.string.battery_lost) to UnitFormatter.formatDistance(stats.rangeLoss, units, 0)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { (label, value) ->
            AccentStatTile(
                label = label,
                value = value,
                accent = palette.accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Current charge level and the three range estimates the car reports right now. */
@Composable
private fun RightNowCard(
    stats: BatteryStats,
    units: Units?,
    palette: CarColorPalette
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = stringResource(R.string.range_information_title),
            message = stringResource(R.string.range_information_message),
            onDismiss = { showInfo = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.battery_right_now),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                InfoIcon(onClick = { showInfo = true })
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stats.batteryLevel}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    if (stats.usableBatteryLevel != stats.batteryLevel) {
                        Text(
                            text = stringResource(R.string.usable_percent, stats.usableBatteryLevel),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceVariant
                        )
                    }
                }
            }

            RangeRow(
                label = stringResource(R.string.estimated_range),
                subtitle = stringResource(R.string.estimated_range_subtitle),
                value = UnitFormatter.formatDistance(stats.estimatedRange, units, 0),
                palette = palette
            )
            RangeRow(
                label = stringResource(R.string.rated_range),
                subtitle = stringResource(R.string.rated_range_subtitle),
                value = UnitFormatter.formatDistance(stats.ratedRange, units, 0),
                palette = palette
            )
            RangeRow(
                label = stringResource(R.string.ideal_range),
                subtitle = stringResource(R.string.ideal_range_subtitle),
                value = UnitFormatter.formatDistance(stats.idealRange, units, 0),
                palette = palette
            )
        }
    }
}

@Composable
private fun RangeRow(
    label: String,
    subtitle: String,
    value: String,
    palette: CarColorPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.onSurface
        )
    }
}

@Composable
private fun InfoIcon(onClick: () -> Unit) {
    Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = stringResource(R.string.info),
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.got_it))
            }
        }
    )
}
