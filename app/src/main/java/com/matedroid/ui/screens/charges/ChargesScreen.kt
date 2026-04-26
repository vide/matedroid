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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.ChargeData
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.BarSegment
import com.matedroid.ui.components.DateRangePickerDialog
import com.matedroid.ui.components.EditorialListItem
import com.matedroid.ui.components.EditorialPill
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.components.formatEditorialDate
import com.matedroid.ui.components.formatShortDate
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.time.LocalDate

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
                    selectedCostFilter = uiState.costFilter,
                    freeSupercharging = uiState.freeSupercharging,
                    customStartDate = uiState.customStartDate,
                    customEndDate = uiState.customEndDate,
                    initialScrollPosition = uiState.scrollPosition,
                    initialScrollOffset = uiState.scrollOffset,
                    palette = palette,
                    onDateFilterSelected = { viewModel.setDateFilter(it) },
                    onCustomRangeSelected = { start, end -> viewModel.setCustomDateRange(start, end) },
                    availableLocations = uiState.availableLocations,
                    selectedLocations = uiState.selectedLocations,
                    onLocationFilterToggled = { viewModel.setLocationFilter(it) },
                    onLocationFilterCleared = { viewModel.clearLocationFilter() },
                    onChargeTypeFilterSelected = { viewModel.setChargeTypeFilter(it) },
                    onCostFilterSelected = { viewModel.setCostFilter(it) },
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
    selectedCostFilter: CostFilter,
    freeSupercharging: Boolean,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    availableLocations: List<String>,
    selectedLocations: Set<String>,
    onLocationFilterToggled: (String) -> Unit,
    onLocationFilterCleared: () -> Unit,
    initialScrollPosition: Int,
    initialScrollOffset: Int,
    palette: CarColorPalette,
    onDateFilterSelected: (DateFilter) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    onChargeTypeFilterSelected: (ChargeTypeFilter) -> Unit,
    onCostFilterSelected: (CostFilter) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DateFilterChips(
                selectedFilter = selectedDateFilter,
                customStartDate = customStartDate,
                customEndDate = customEndDate,
                palette = palette,
                onFilterSelected = onDateFilterSelected,
                onCustomRangeSelected = onCustomRangeSelected
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChargeTypeFilterDropdown(
                    selectedFilter = selectedChargeTypeFilter,
                    onFilterSelected = onChargeTypeFilterSelected,
                    palette = palette
                )
                CostFilterDropdown(
                    selectedFilter = selectedCostFilter,
                    onFilterSelected = onCostFilterSelected,
                    palette = palette
                )
                LocationFilterDropdown(
                    availableLocations = availableLocations,
                    selectedLocations = selectedLocations,
                    onLocationToggled = onLocationFilterToggled,
                    onClearAll = onLocationFilterCleared,
                    palette = palette
                )
            }
        }

        if (freeSupercharging && selectedCostFilter == CostFilter.NO_COST) {
            item {
                Text(
                    text = stringResource(R.string.charges_free_supercharging_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
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
                    palette = palette,
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
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    palette: CarColorPalette,
    onFilterSelected: (DateFilter) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DateFilter.entries.filter { it != DateFilter.CUSTOM }) { filter ->
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
        item {
            val label = if (selectedFilter == DateFilter.CUSTOM && customStartDate != null && customEndDate != null) {
                "${formatShortDate(customStartDate)} – ${formatShortDate(customEndDate)}"
            } else {
                stringResource(R.string.filter_custom)
            }
            FilterChip(
                selected = selectedFilter == DateFilter.CUSTOM,
                onClick = { showDatePicker = true },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onRangeSelected = { start, end ->
                showDatePicker = false
                onCustomRangeSelected(start, end)
            },
            initialStart = customStartDate,
            initialEnd = customEndDate
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeTypeFilterDropdown(
    selectedFilter: ChargeTypeFilter,
    palette: CarColorPalette,
    onFilterSelected: (ChargeTypeFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = selectedFilter != ChargeTypeFilter.ALL
    val themeColor = when (selectedFilter) {
        ChargeTypeFilter.ALL -> palette.onSurfaceVariant
        ChargeTypeFilter.AC -> palette.acColor
        ChargeTypeFilter.DC -> palette.dcColor
    }
    Box {
        FilterChip(
            selected = isActive,
            onClick = { expanded = true },
            label = { Text(getChargeTypeFilterLabel(selectedFilter)) },
            leadingIcon = { Icon(Icons.Default.ElectricBolt, null, Modifier.size(16.dp)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = themeColor,
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White,
                containerColor = Color.Transparent
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isActive,
                borderColor = themeColor.copy(alpha = 0.6f),
                borderWidth = 1.dp,
                selectedBorderWidth = 0.dp
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChargeTypeFilter.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(getChargeTypeFilterLabel(entry)) },
                    leadingIcon = {
                        if (entry == selectedFilter)
                            Icon(Icons.Default.Check, null, tint = palette.accent)
                        else
                            Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        onFilterSelected(entry)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostFilterDropdown(
    selectedFilter: CostFilter,
    palette: CarColorPalette,
    onFilterSelected: (CostFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = selectedFilter != CostFilter.ALL
    val themeColor = palette.onSurfaceVariant
    Box {
        FilterChip(
            selected = isActive,
            onClick = { expanded = true },
            label = { Text(stringResource(selectedFilter.labelRes)) },
            leadingIcon = { Icon(Icons.Default.Paid, null, Modifier.size(16.dp)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = themeColor,
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White,
                containerColor = Color.Transparent
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isActive,
                borderColor = themeColor.copy(alpha = 0.6f),
                borderWidth = 1.dp,
                selectedBorderWidth = 0.dp
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CostFilter.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.labelRes)) },
                    leadingIcon = {
                        if (entry == selectedFilter)
                            Icon(Icons.Default.Check, null, tint = palette.accent)
                        else
                            Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        onFilterSelected(entry)
                        expanded = false
                    }
                )
            }
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
    palette: CarColorPalette,
    onEditCost: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val unknownLocation = stringResource(R.string.unknown_location)
    val freeLabel = stringResource(R.string.charge_free)
    val accent = if (isDcCharge) palette.dcColor else palette.acColor

    val cost = charge.cost ?: 0.0
    // Existing behavior: a missing cost renders the same as an explicit zero. The user
    // can tell the two apart only via the edit-cost trailing affordance, which still
    // points at TeslaMate's cost editor.
    val isFree = cost == 0.0
    val costText = if (isFree) freeLabel else "$currencySymbol%.2f".format(cost)
    val costPillText = if (onEditCost != null) "$costText ↗" else costText

    EditorialListItem(
        accent = accent,
        dateline = formatEditorialDate(charge.startDate),
        title = charge.address ?: unknownLocation,
        heroValue = "%.1f".format(charge.chargeEnergyAdded ?: 0.0),
        heroUnit = "kWh",
        onClick = onClick,
        datelineTrailing = {
            ChargeTypeBadge(isDcCharge = isDcCharge, palette = palette)
        }
    ) {
        EditorialPill(charge.durationStr ?: "${charge.durationMin ?: 0}m")

        val costBg = if (isFree) palette.acColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f)
        val costColor = if (isFree) palette.acColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        val costFontWeight = if (isFree) FontWeight.Bold else FontWeight.SemiBold
        val costModifier = if (onEditCost != null) Modifier.clickable(onClick = onEditCost) else Modifier
        EditorialPill(
            text = costPillText,
            modifier = costModifier,
            background = costBg,
            color = costColor,
            fontWeight = costFontWeight
        )

        val start = charge.startBatteryLevel
        val end = charge.endBatteryLevel
        if (start != null && end != null) {
            EditorialPill(
                text = "$start→$end%",
                background = accent.copy(alpha = 0.12f),
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChargeTypeBadge(isDcCharge: Boolean, palette: CarColorPalette) {
    val backgroundColor = if (isDcCharge) palette.dcColor else palette.acColor
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
                //ChartLegend(palette = palette)
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
                    displayValue = "%.1f kWh".format(data.totalEnergy),
                    segments = listOf(
                        BarSegment(data.energyAc, palette.acColor, "AC"),
                        BarSegment(data.energyDc, palette.dcColor, "DC")
                    )
                )
            }
            ChargesChartType.COST -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.totalCost,
                    displayValue = "$currencySymbol%.2f".format(data.totalCost),
                    segments = listOf(
                        BarSegment(data.costAc, palette.acColor, "AC"),
                        BarSegment(data.costDc, palette.dcColor, "DC")
                    )
                )
            }
            ChargesChartType.COUNT -> chartData.map { data ->
                BarChartData(
                    label = data.label,
                    value = data.count.toDouble(),
                    displayValue = data.count.toString(),
                    segments = listOf(
                        BarSegment(data.countAc.toDouble(), palette.acColor, "AC"),
                        BarSegment(data.countDc.toDouble(), palette.dcColor, "DC")
                    )
                )
            }
        }

        val valueFormatter: (Double) -> String = when (chartType) {
            ChargesChartType.ENERGY -> { v -> "%.1f kWh".format(v) }
            ChargesChartType.COST -> { v -> "$currencySymbol%.2f".format(v) }
            ChargesChartType.COUNT -> { v -> v.toInt().toString() }
        }

        // Set number of labels to display
        val labelInterval = when {
            barData.size <= 7 -> 1  // Show all for Today and last 7 days
            barData.size <= 30 -> 3 // Show 1 label every 3 bars for last 30 days
            else -> ((barData.size + 5) / 6).coerceAtLeast(1)
        }

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

@Composable
private fun LocationFilterDropdown(
    availableLocations: List<String>,
    selectedLocations: Set<String>,
    palette: CarColorPalette,
    onLocationToggled: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val hasSelection = selectedLocations.isNotEmpty()

    Box {
        FilterChip(
            selected = hasSelection,
            onClick = { expanded = true },
            label = {
                Text(
                    if (hasSelection && selectedLocations.size == 1)
                        selectedLocations.first()
                    else if (hasSelection)
                        "${selectedLocations.size} ubicaciones"
                    else
                        stringResource(R.string.filter_location)
                )
            },
            leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp)) },
            trailingIcon = if (hasSelection) {
                {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).clickable { onClearAll() }
                    )
                }
            } else null
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; searchText = "" }
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(stringResource(R.string.search)) },
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                singleLine = true
            )
            val filtered = availableLocations.filter {
                it.contains(searchText, ignoreCase = true)
            }
            val allFilteredSelected = filtered.isNotEmpty() && filtered.all { it in selectedLocations }

            DropdownMenuItem(
                text = {
                    Text(
                        if (allFilteredSelected)
                            stringResource(R.string.deselect_all)
                        else
                            stringResource(R.string.select_all),
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        if (allFilteredSelected) Icons.Default.Clear
                        else Icons.Default.Check,
                        contentDescription = null,
                        tint = palette.accent
                    )
                },
                onClick = {
                    if (allFilteredSelected) {
                        filtered.forEach { onLocationToggled(it) }
                    } else {
                        filtered.filter { it !in selectedLocations }.forEach { onLocationToggled(it) }
                    }
                }
            )
            HorizontalDivider()
            filtered.forEach { location ->
                val isSelected = location in selectedLocations
                DropdownMenuItem(
                    text = { Text(location) },
                    leadingIcon = {
                        if (isSelected)
                            Icon(Icons.Default.Check, null, tint = palette.accent)
                        else
                            Spacer(Modifier.size(24.dp))
                    },
                    onClick = { onLocationToggled(location) }
                )
            }
        }
    }
}