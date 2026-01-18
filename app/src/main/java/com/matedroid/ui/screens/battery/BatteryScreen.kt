package com.matedroid.ui.screens.battery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import com.matedroid.ui.icons.CustomIcons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning

// Colors matching the reference screenshots
private val CapacityGreen = Color(0xFF4CAF50)
private val CapacityYellow = Color(0xFFFFEB3B)
private val DegradationGreen = Color(0xFF4CAF50)
private val LossYellow = Color(0xFFFFD54F)
private val LossRed = Color(0xFFEF5350)
private val RangeBlue = Color(0xFF42A5F5)
private val RangeLossRed = Color(0xFFEF5350)
private val DetailCyan = Color(0xFF00BCD4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    carId: Int,
    efficiency: Double?,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: BatteryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val stats = viewModel.computeStats()
                    if (stats != null) {
                        BatteryHealthContent(
                            stats = stats,
                            palette = palette,
                            onCardClick = { viewModel.showDetail() }
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

        // Detail overlay
        AnimatedVisibility(
            visible = uiState.showDetail,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            val stats = viewModel.computeStats()
            if (stats != null) {
                BatteryDetailScreen(
                    stats = stats,
                    onClose = { viewModel.hideDetail() }
                )
            }
        }
    }
}

@Composable
private fun BatteryHealthContent(
    stats: BatteryStats,
    palette: CarColorPalette,
    onCardClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Capacity Section
        CapacityCard(stats = stats, palette = palette, onClick = onCardClick)

        // Degradation Section
        DegradationCard(stats = stats, palette = palette, onClick = onCardClick)

        // Range Section
        RangeCard(stats = stats, palette = palette, onClick = onCardClick)
    }
}

@Composable
private fun CapacityCard(stats: BatteryStats, palette: CarColorPalette, onClick: () -> Unit) {
    var showTooltip by remember { mutableStateOf(false) }
    val capacityTitle = stringResource(R.string.battery_capacity_title)
    val capacityMessage = stringResource(R.string.battery_capacity_message)
    val capacityLabel = stringResource(R.string.capacity)
    val usableNewLabel = stringResource(R.string.usable_new)
    val usableNowLabel = stringResource(R.string.usable_now)
    val ratedLabel = stringResource(R.string.rated)
    val infoLabel = stringResource(R.string.info)
    val gotItLabel = stringResource(R.string.got_it)

    if (showTooltip) {
        InfoDialog(
            title = capacityTitle,
            message = capacityMessage,
            confirmText = gotItLabel,
            onDismiss = { showTooltip = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = capacityLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = infoLabel,
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showTooltip = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capacity values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CapacityValueCard(
                    value = "%.1f kWh".format(stats.originalCapacity),
                    label = usableNewLabel,
                    iconColor = CapacityGreen,
                    modifier = Modifier.weight(1f)
                )
                CapacityValueCard(
                    value = "%.1f kWh".format(stats.currentCapacity),
                    label = usableNowLabel,
                    iconColor = CapacityYellow,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rated efficiency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f Wh/km".format(stats.ratedEfficiency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = ratedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CapacityValueCard(
    value: String,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.ElectricBolt,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DegradationCard(stats: BatteryStats, palette: CarColorPalette, onClick: () -> Unit) {
    var showTooltip by remember { mutableStateOf(false) }
    val degradationTitle = stringResource(R.string.estimated_degradation_title)
    val degradationMessage = stringResource(R.string.estimated_degradation_message)
    val degradationLabel = stringResource(R.string.estimated_degradation)
    val capacityLabel = stringResource(R.string.capacity)
    val lossKwhLabel = stringResource(R.string.loss_kwh)
    val lossPercentLabel = stringResource(R.string.loss_percent)
    val infoLabel = stringResource(R.string.info)
    val gotItLabel = stringResource(R.string.got_it)

    if (showTooltip) {
        InfoDialog(
            title = degradationTitle,
            message = degradationMessage,
            confirmText = gotItLabel,
            onDismiss = { showTooltip = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "*",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = degradationLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = infoLabel,
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showTooltip = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capacity percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = capacityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant
                )
                Text(
                    text = "%.1f%%".format(stats.healthPercent),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DegradationGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (stats.healthPercent / 100).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = DegradationGreen,
                trackColor = palette.progressTrack,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Loss values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LossValueCard(
                    value = "%.1f kWh".format(stats.lossKwh),
                    label = lossKwhLabel,
                    iconColor = LossYellow,
                    modifier = Modifier.weight(1f)
                )
                LossValueCard(
                    value = "%.1f%%".format(stats.lossPercent),
                    label = lossPercentLabel,
                    iconColor = LossRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LossValueCard(
    value: String,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RangeCard(stats: BatteryStats, palette: CarColorPalette, onClick: () -> Unit) {
    val rangeLabel = stringResource(R.string.range)
    val maxRangeNewLabel = stringResource(R.string.max_range_new)
    val maxRangeNowLabel = stringResource(R.string.max_range_now)
    val rangeLossLabel = stringResource(R.string.range_loss)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Text(
                text = rangeLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Range values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RangeValueCard(
                    value = "%.1f km".format(stats.maxRangeNew),
                    label = maxRangeNewLabel,
                    iconColor = CapacityGreen,
                    modifier = Modifier.weight(1f)
                )
                RangeValueCard(
                    value = "%.1f km".format(stats.maxRangeNow),
                    label = maxRangeNowLabel,
                    iconColor = CapacityYellow,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Range loss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = RangeLossRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f km".format(stats.rangeLoss),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = rangeLossLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeValueCard(
    value: String,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = CustomIcons.Road,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Battery Detail Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryDetailScreen(
    stats: BatteryStats,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Battery Status Section
            BatteryStatusCard(stats = stats)

            // Range Information Section
            RangeInformationCard(stats = stats)

            // Estimated Total Capacity Section
            EstimatedCapacityCard(stats = stats)
        }
    }
}

@Composable
private fun BatteryStatusCard(stats: BatteryStats) {
    var showTooltip by remember { mutableStateOf(false) }
    val currentChargeTitle = stringResource(R.string.current_charge_title)
    val currentChargeMessage = stringResource(R.string.current_charge_message)
    val currentChargeLabel = stringResource(R.string.current_charge)
    val usablePercentLabel = stringResource(R.string.usable_percent, stats.usableBatteryLevel)
    val infoLabel = stringResource(R.string.info)
    val gotItLabel = stringResource(R.string.got_it)

    if (showTooltip) {
        InfoDialog(
            title = currentChargeTitle,
            message = currentChargeMessage,
            confirmText = gotItLabel,
            onDismiss = { showTooltip = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentChargeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = infoLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showTooltip = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Battery percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${stats.batteryLevel}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = DetailCyan
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { stats.batteryLevel / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = DetailCyan,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Usable percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = usablePercentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DetailCyan
                )
            }
        }
    }
}

@Composable
private fun RangeInformationCard(stats: BatteryStats) {
    var showTooltip by remember { mutableStateOf(false) }
    val rangeInfoTitle = stringResource(R.string.range_information_title)
    val rangeInfoMessage = stringResource(R.string.range_information_message)
    val rangeInfoLabel = stringResource(R.string.range_information)
    val estimatedRangeLabel = stringResource(R.string.estimated_range)
    val estimatedRangeSubtitle = stringResource(R.string.estimated_range_subtitle)
    val ratedRangeLabel = stringResource(R.string.rated_range)
    val ratedRangeSubtitle = stringResource(R.string.rated_range_subtitle)
    val idealRangeLabel = stringResource(R.string.ideal_range)
    val idealRangeSubtitle = stringResource(R.string.ideal_range_subtitle)
    val infoLabel = stringResource(R.string.info)
    val gotItLabel = stringResource(R.string.got_it)

    if (showTooltip) {
        InfoDialog(
            title = rangeInfoTitle,
            message = rangeInfoMessage,
            confirmText = gotItLabel,
            onDismiss = { showTooltip = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rangeInfoLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = infoLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showTooltip = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Range rows
            RangeInfoRow(
                title = estimatedRangeLabel,
                subtitle = estimatedRangeSubtitle,
                value = "%.1f km".format(stats.estimatedRange),
                valueColor = RangeBlue
            )

            Spacer(modifier = Modifier.height(12.dp))

            RangeInfoRow(
                title = ratedRangeLabel,
                subtitle = ratedRangeSubtitle,
                value = "%.1f km".format(stats.ratedRange),
                valueColor = RangeBlue
            )

            Spacer(modifier = Modifier.height(12.dp))

            RangeInfoRow(
                title = idealRangeLabel,
                subtitle = idealRangeSubtitle,
                value = "%.1f km".format(stats.idealRange),
                valueColor = RangeBlue
            )
        }
    }
}

@Composable
private fun RangeInfoRow(
    title: String,
    subtitle: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circle icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(valueColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(valueColor, RoundedCornerShape(6.dp))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun EstimatedCapacityCard(stats: BatteryStats) {
    var showTooltip by remember { mutableStateOf(false) }
    val estimatedCapacityTitle = stringResource(R.string.estimated_total_capacity_title)
    val estimatedCapacityMessage = stringResource(R.string.estimated_total_capacity_message, stats.batteryLevel)
    val estimatedCapacityLabel = stringResource(R.string.estimated_total_capacity)
    val rangeAt100Label = stringResource(R.string.range_at_100)
    val estimatedRangeDescription = stringResource(R.string.estimated_range_description, stats.batteryLevel, stats.ratedRange)
    val infoLabel = stringResource(R.string.info)
    val gotItLabel = stringResource(R.string.got_it)

    if (showTooltip) {
        InfoDialog(
            title = estimatedCapacityTitle,
            message = estimatedCapacityMessage,
            confirmText = gotItLabel,
            onDismiss = { showTooltip = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = estimatedCapacityLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = infoLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showTooltip = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = StatusSuccess,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rangeAt100Label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = estimatedRangeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "%.1f km".format(stats.rangeAt100),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusSuccess
                )
            }
        }
    }
}

@Composable
private fun InfoDialog(
    title: String,
    message: String,
    confirmText: String,
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
                Text(confirmText)
            }
        }
    )
}
