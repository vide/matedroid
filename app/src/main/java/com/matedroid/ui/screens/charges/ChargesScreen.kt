package com.matedroid.ui.screens.charges

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.ChargeData
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargesScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToChargeDetail: (Int) -> Unit = {},
    viewModel: ChargesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) {
        viewModel.setCarId(carId)
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
                title = { Text(stringResource(R.string.charges_title)) },
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
                ChargesContent(
                    charges = uiState.charges,
                    dcChargeIds = uiState.dcChargeIds,
                    chartData = uiState.chartData,
                    chartGranularity = uiState.chartGranularity,
                    summary = uiState.summary,
                    currencySymbol = uiState.currencySymbol,
                    teslamateBaseUrl = uiState.teslamateBaseUrl,
                    selectedDateFilter = uiState.selectedFilter,
                    selectedChargeTypeFilter = uiState.chargeTypeFilter,
                    initialScrollPosition = uiState.scrollPosition,
                    initialScrollOffset = uiState.scrollOffset,
                    palette = palette,
                    onDateFilterSelected = { viewModel.setDateFilter(it) },
                    onChargeTypeFilterSelected = { viewModel.setChargeTypeFilter(it) },
                    onChargeClick = { chargeId, scrollIndex, scrollOffset ->
                        viewModel.saveScrollPosition(scrollIndex, scrollOffset)
                        onNavigateToChargeDetail(chargeId)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargesContent(
    charges: List<ChargeData>,
    dcChargeIds: Set<Int>,
    chartData: List<ChargeChartData>,
    chartGranularity: ChartGranularity,
    summary: ChargesSummary,
    currencySymbol: String,
    teslamateBaseUrl: String,
    selectedDateFilter: DateFilter,
    selectedChargeTypeFilter: ChargeTypeFilter,
    initialScrollPosition: Int,
    initialScrollOffset: Int,
    palette: CarColorPalette,
    onDateFilterSelected: (DateFilter) -> Unit,
    onChargeTypeFilterSelected: (ChargeTypeFilter) -> Unit,
    onChargeClick: (chargeId: Int, scrollIndex: Int, scrollOffset: Int) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition,
        initialFirstVisibleItemScrollOffset = initialScrollOffset
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DateFilterChips(
                selectedFilter = selectedDateFilter,
                palette = palette,
                onFilterSelected = onDateFilterSelected
            )
        }

        item {
            ChargeTypeFilterChips(
                selectedFilter = selectedChargeTypeFilter,
                palette = palette,
                onFilterSelected = onChargeTypeFilterSelected
            )
        }

        item {
            SummaryCard(summary = summary, currencySymbol = currencySymbol, palette = palette)
        }

        // Charges charts (daily/weekly/monthly based on date range) - swipeable
        if (chartData.isNotEmpty()) {
            item {
                ChargesChartsPager(
                    chartData = chartData,
                    granularity = chartGranularity,
                    currencySymbol = currencySymbol,
                    palette = palette
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.charge_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (charges.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_charges_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(charges, key = { it.chargeId }) { charge ->
                ChargeItem(
                    charge = charge,
                    // Show DC badge if in dcChargeIds, AC otherwise
                    // Will be correct once sync has processed charge details
                    isDcCharge = charge.chargeId in dcChargeIds,
                    currencySymbol = currencySymbol,
                    onEditCost = if (teslamateBaseUrl.isNotBlank()) {
                        {
                            val url = "$teslamateBaseUrl/charge-cost/${charge.chargeId}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    } else null,
                    onClick = {
                        onChargeClick(
                            charge.chargeId,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChips(
    selectedFilter: DateFilter,
    palette: CarColorPalette,
    onFilterSelected: (DateFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DateFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeTypeFilterChips(
    selectedFilter: ChargeTypeFilter,
    palette: CarColorPalette,
    onFilterSelected: (ChargeTypeFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ChargeTypeFilter.entries.toList()) { filter ->
            val isSelected = filter == selectedFilter
            val chipColors = when (filter) {
                ChargeTypeFilter.ALL -> FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
                ChargeTypeFilter.AC -> FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF4CAF50),
                    selectedLabelColor = Color.White
                )
                ChargeTypeFilter.DC -> FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFF9800),
                    selectedLabelColor = Color.White
                )
            }
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = { Text(getChargeTypeFilterLabel(filter)) },
                colors = chipColors
            )
        }
    }
}

@Composable
private fun getChargeTypeFilterLabel(filter: ChargeTypeFilter): String {
    return when (filter) {
        ChargeTypeFilter.ALL -> stringResource(R.string.filter_all)
        ChargeTypeFilter.AC -> stringResource(R.string.charging_ac)
        ChargeTypeFilter.DC -> stringResource(R.string.charging_dc)
    }
}

@Composable
private fun SummaryCard(summary: ChargesSummary, currencySymbol: String, palette: CarColorPalette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryItem(
                    icon = Icons.Default.ElectricBolt,
                    label = stringResource(R.string.total_sessions),
                    value = summary.totalCharges.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Default.BatteryChargingFull,
                    label = stringResource(R.string.total_energy),
                    value = when {
                        summary.totalEnergyAdded > 999 -> "%.2f MWh".format(summary.totalEnergyAdded / 1000)
                        summary.totalEnergyAdded < 10 -> "%.1f kWh".format(summary.totalEnergyAdded)
                        else -> "%.0f kWh".format(summary.totalEnergyAdded)
                    },
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryItem(
                    icon = Icons.Default.Paid,
                    label = stringResource(R.string.total_cost),
                    value = when {
                        summary.totalCost < 100 -> "$currencySymbol%.2f".format(summary.totalCost)
                        summary.totalCost < 1000 -> "$currencySymbol%.1f".format(summary.totalCost)
                        else -> "$currencySymbol%.0f".format(summary.totalCost)
                    },
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Default.Paid,
                    label = stringResource(R.string.avg_cost_per_session),
                    value = "$currencySymbol%.2f".format(summary.avgCostPerCharge),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = palette.accent
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }
    }
}

@Composable
private fun ChargeItem(
    charge: ChargeData,
    isDcCharge: Boolean,
    currencySymbol: String,
    onEditCost: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val unknownLocation = stringResource(R.string.unknown_location)
    val energyAddedLabel = stringResource(R.string.energy_added)
    val durationLabel = stringResource(R.string.duration)
    val costLabel = stringResource(R.string.cost)
    val batteryLabel = stringResource(R.string.battery)
    val editLabel = stringResource(R.string.edit)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header card with location, date, and AC/DC badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = charge.address ?: unknownLocation,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        charge.startDate?.let { dateStr ->
                            Text(
                                text = formatDate(dateStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ChargeTypeBadge(isDcCharge = isDcCharge)
                }
            }

            // Stats row with individual cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Energy added
                ChargeStatCard(
                    icon = Icons.Default.BatteryChargingFull,
                    value = "%.1f".format(charge.chargeEnergyAdded ?: 0.0),
                    unit = "kWh",
                    label = energyAddedLabel,
                    modifier = Modifier.weight(1f)
                )

                // Duration
                ChargeStatCard(
                    icon = Icons.Default.Schedule,
                    value = charge.durationStr ?: "${charge.durationMin ?: 0}m",
                    unit = "",
                    label = durationLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cost (tappable if editable)
                ChargeStatCard(
                    icon = Icons.Default.Paid,
                    value = "$currencySymbol%.2f".format(charge.cost ?: 0.0),
                    unit = "",
                    label = costLabel,
                    modifier = Modifier.weight(1f),
                    trailingIcon = if (onEditCost != null) Icons.AutoMirrored.Filled.OpenInNew else null,
                    trailingContentDescription = editLabel,
                    onClick = onEditCost
                )

                // Battery levels
                val startLevel = charge.startBatteryLevel
                val endLevel = charge.endBatteryLevel
                ChargeStatCard(
                    icon = Icons.Default.ElectricBolt,
                    value = if (startLevel != null && endLevel != null) "$startLevel% → $endLevel%" else "--",
                    unit = "",
                    label = batteryLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChargeStatCard(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    trailingContentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (unit.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Trailing icon in top-right corner
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = trailingContentDescription,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChargeTypeBadge(isDcCharge: Boolean) {
    val backgroundColor = if (isDcCharge) Color(0xFFFF9800) else Color(0xFF4CAF50)
    val text = if (isDcCharge) stringResource(R.string.charging_dc) else stringResource(R.string.charging_ac)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val inputFormatter = DateTimeFormatter.ISO_DATE_TIME
        val outputFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        val dateTime = LocalDateTime.parse(dateStr, inputFormatter)
        dateTime.format(outputFormatter)
    } catch (e: Exception) {
        dateStr
    }
}

/**
 * Chart type enum for the swipeable pager
 */
private enum class ChargesChartType {
    ENERGY, COST, COUNT
}

/**
 * Swipeable pager containing Energy, Cost, and Count charts with page indicator dots
 */
@Composable
private fun ChargesChartsPager(
    chartData: List<ChargeChartData>,
    granularity: ChartGranularity,
    currencySymbol: String,
    palette: CarColorPalette
) {
    val pagerState = rememberPagerState(pageCount = { ChargesChartType.entries.size })

    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = palette.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val chartType = ChargesChartType.entries[page]
                    ChargesChartPage(
                        chartData = chartData,
                        granularity = granularity,
                        chartType = chartType,
                        currencySymbol = currencySymbol,
                        palette = palette
                    )
                }
            }
        }

        // Page indicator dots
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(ChargesChartType.entries.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) palette.accent
                            else palette.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

/**
 * Individual chart page showing Energy, Cost, or Count data
 */
@Composable
private fun ChargesChartPage(
    chartData: List<ChargeChartData>,
    granularity: ChartGranularity,
    chartType: ChargesChartType,
    currencySymbol: String,
    palette: CarColorPalette
) {
    val title = when (chartType) {
        ChargesChartType.ENERGY -> when (granularity) {
            ChartGranularity.DAILY -> stringResource(R.string.chart_energy_per_day)
            ChartGranularity.WEEKLY -> stringResource(R.string.chart_energy_per_week)
            ChartGranularity.MONTHLY -> stringResource(R.string.chart_energy_per_month)
        }
        ChargesChartType.COST -> when (granularity) {
            ChartGranularity.DAILY -> stringResource(R.string.chart_cost_per_day)
            ChartGranularity.WEEKLY -> stringResource(R.string.chart_cost_per_week)
            ChartGranularity.MONTHLY -> stringResource(R.string.chart_cost_per_month)
        }
        ChargesChartType.COUNT -> when (granularity) {
            ChartGranularity.DAILY -> stringResource(R.string.chart_charges_per_day)
            ChartGranularity.WEEKLY -> stringResource(R.string.chart_charges_per_week)
            ChartGranularity.MONTHLY -> stringResource(R.string.chart_charges_per_month)
        }
    }
    val icon = when (chartType) {
        ChargesChartType.ENERGY -> Icons.Default.BatteryChargingFull
        ChargesChartType.COST -> Icons.Default.Paid
        ChargesChartType.COUNT -> Icons.Default.ElectricBolt
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val barData = when (chartType) {
            ChargesChartType.ENERGY -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.totalEnergy,
                    displayValue = "%.1f kWh".format(data.totalEnergy)
                )
            }
            ChargesChartType.COST -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.totalCost,
                    displayValue = "$currencySymbol%.2f".format(data.totalCost)
                )
            }
            ChargesChartType.COUNT -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.count.toDouble(),
                    displayValue = data.count.toString()
                )
            }
        }

        val valueFormatter: (Double) -> String = when (chartType) {
            ChargesChartType.ENERGY -> { v -> "%.1f kWh".format(v) }
            ChargesChartType.COST -> { v -> "$currencySymbol%.2f".format(v) }
            ChargesChartType.COUNT -> { v -> v.toInt().toString() }
        }

        // Show max ~6 labels to avoid crowding
        val labelInterval = ((barData.size + 5) / 6).coerceAtLeast(1)

        InteractiveBarChart(
            data = barData,
            modifier = Modifier.fillMaxWidth(),
            barColor = palette.accent,
            labelColor = palette.onSurfaceVariant,
            showEveryNthLabel = labelInterval,
            valueFormatter = valueFormatter
        )
    }
}
