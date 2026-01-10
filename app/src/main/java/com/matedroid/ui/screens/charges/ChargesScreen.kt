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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                title = { Text("Charges") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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

        // Charges chart (daily/weekly/monthly based on date range)
        if (chartData.isNotEmpty()) {
            item {
                ChargesChart(chartData = chartData, granularity = chartGranularity, palette = palette)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Charge History",
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
                            text = "No charges found for selected period",
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
                label = { Text(filter.label) },
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
                label = { Text(filter.label) },
                colors = chipColors
            )
        }
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
                text = "Summary",
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
                    label = "Total Sessions",
                    value = summary.totalCharges.toString(),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Total Energy",
                    value = "%.1f kWh".format(summary.totalEnergyAdded),
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
                    label = "Total Cost",
                    value = "$currencySymbol%.2f".format(summary.totalCost),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Default.Paid,
                    label = "Avg Cost/Session",
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
                            text = charge.address ?: "Unknown location",
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
                    label = "Added",
                    modifier = Modifier.weight(1f)
                )

                // Duration
                ChargeStatCard(
                    icon = Icons.Default.Schedule,
                    value = charge.durationStr ?: "${charge.durationMin ?: 0}m",
                    unit = "",
                    label = "Duration",
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
                    label = "Cost",
                    modifier = Modifier.weight(1f),
                    trailingIcon = if (onEditCost != null) Icons.AutoMirrored.Filled.OpenInNew else null,
                    onClick = onEditCost
                )

                // Battery levels
                val startLevel = charge.startBatteryLevel
                val endLevel = charge.endBatteryLevel
                ChargeStatCard(
                    icon = Icons.Default.ElectricBolt,
                    value = if (startLevel != null && endLevel != null) "$startLevel% → $endLevel%" else "--",
                    unit = "",
                    label = "Battery",
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
                    contentDescription = "Edit",
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
    val text = if (isDcCharge) "DC" else "AC"

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

@Composable
private fun ChargesChart(
    chartData: List<ChargeChartData>,
    granularity: ChartGranularity,
    palette: CarColorPalette
) {
    val title = when (granularity) {
        ChartGranularity.DAILY -> "Energy per Day"
        ChartGranularity.WEEKLY -> "Energy per Week"
        ChartGranularity.MONTHLY -> "Energy per Month"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
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

            val barData = chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.totalEnergy,
                    displayValue = "%.1f kWh".format(data.totalEnergy)
                )
            }

            // Show max ~6 labels to avoid crowding
            val labelInterval = ((barData.size + 5) / 6).coerceAtLeast(1)

            InteractiveBarChart(
                data = barData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                showEveryNthLabel = labelInterval,
                valueFormatter = { "%.1f kWh".format(it) }
            )
        }
    }
}
