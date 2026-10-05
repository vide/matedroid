package com.matedroid.ui.screens.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.HeroStat
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.util.Locale

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
        CellCard(stats = stats, units = units, palette = palette)
        RightNowCard(stats = stats, units = units, palette = palette)
    }
}

/** The battery-cell pictogram with its leader-line figures, and the rated efficiency below. */
@Composable
private fun CellCard(
    stats: BatteryStats,
    units: Units?,
    palette: CarColorPalette
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = stringResource(R.string.battery_health_estimated),
            message = stringResource(R.string.estimated_degradation_message),
            onDismiss = { showInfo = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BatteryCellFigure(stats = stats, units = units, palette = palette)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )

            // Footer: rated efficiency
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = palette.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.battery_rated_efficiency),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = buildAnnotatedString {
                        append("%.0f".format(stats.ratedEfficiencyWhPerUnit))
                        withStyle(
                            SpanStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = palette.accent
                            )
                        ) { append(" " + UnitFormatter.getEfficiencyUnit(units)) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(10.dp))
                InfoIcon(onClick = { showInfo = true })
            }
        }
    }
}

/** Today's charge level, limit, plug state and the three range estimates the car reports. */
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

    val rightNow = stringResource(R.string.battery_right_now).uppercase(Locale.getDefault())
    val limit = stats.chargeLimitSoc?.let {
        stringResource(R.string.charge_limit_format, it).uppercase(Locale.getDefault())
    }
    val status = when {
        stats.isCharging -> stringResource(R.string.charging)
        stats.isPluggedIn -> stringResource(R.string.plugged_in)
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kicker with the info icon right after it; the pill keeps the far right
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(rightNow)
                            append(" · ")
                            withStyle(SpanStyle(color = palette.onSurface)) {
                                append("${stats.batteryLevel}%")
                            }
                            if (limit != null) {
                                append(" · ")
                                append(limit)
                            }
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = palette.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    InfoIcon(onClick = { showInfo = true })
                }
                if (status != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(text = status, palette = palette)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroStat(
                    label = stringResource(R.string.battery_range_estimated_short),
                    value = UnitFormatter.formatDistance(stats.estimatedRange, units, 0),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxHeight()
                )
                HeroStat(
                    label = stringResource(R.string.battery_range_rated_short),
                    value = UnitFormatter.formatDistance(stats.ratedRange, units, 0),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxHeight()
                )
                HeroStat(
                    label = stringResource(R.string.battery_range_ideal_short),
                    value = UnitFormatter.formatDistance(stats.idealRange, units, 0),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Plug/charging status: a pill, not a button. */
@Composable
private fun StatusPill(text: String, palette: CarColorPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = palette.accent
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.accent,
            maxLines = 1
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
