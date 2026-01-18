package com.matedroid.ui.screens.updates

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.ui.components.BarChartData
import com.matedroid.ui.components.InteractiveBarChart
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class UpdatesDateFilter(val months: Int?) {
    LAST_6_MONTHS(6),
    LAST_YEAR(12),
    ALL_TIME(null)
}

@Composable
private fun getFilterLabel(filter: UpdatesDateFilter): String {
    return when (filter) {
        UpdatesDateFilter.LAST_6_MONTHS -> stringResource(R.string.filter_last_6_months)
        UpdatesDateFilter.LAST_YEAR -> stringResource(R.string.filter_last_year)
        UpdatesDateFilter.ALL_TIME -> stringResource(R.string.filter_all_time)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareVersionsScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: SoftwareVersionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(UpdatesDateFilter.ALL_TIME) }
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

    fun applyDateFilter(filter: UpdatesDateFilter) {
        selectedFilter = filter
        viewModel.setFilter(filter.months)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.software_title)) },
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
                SoftwareVersionsContent(
                    uiState = uiState,
                    selectedFilter = selectedFilter,
                    palette = palette,
                    onFilterSelected = { applyDateFilter(it) }
                )
            }
        }
    }
}

@Composable
private fun SoftwareVersionsContent(
    uiState: SoftwareVersionsUiState,
    selectedFilter: UpdatesDateFilter,
    palette: CarColorPalette,
    onFilterSelected: (UpdatesDateFilter) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DateFilterChips(
                selectedFilter = selectedFilter,
                palette = palette,
                onFilterSelected = onFilterSelected
            )
        }

        // Overview Card
        item {
            OverviewCard(
                stats = uiState.stats,
                palette = palette
            )
        }

        // Bar Chart
        if (uiState.monthlyData.isNotEmpty()) {
            item {
                MonthlyUpdatesChart(
                    monthlyData = uiState.monthlyData,
                    palette = palette
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.software_update_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.updates.isEmpty()) {
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
                            text = stringResource(R.string.software_no_updates),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.updates, key = { it.id }) { update ->
                SoftwareVersionCard(
                    update = update,
                    isLongestInstalled = update.id == uiState.longestInstalledId,
                    palette = palette
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(
    stats: UpdatesStats,
    palette: CarColorPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = palette.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.software_overview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.software_total_updates),
                    value = stats.totalUpdates.toString(),
                    palette = palette
                )
                StatItem(
                    label = stringResource(R.string.software_avg_interval),
                    value = stringResource(R.string.format_interval_days, stats.meanDaysBetweenUpdates.roundToInt()),
                    palette = palette
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = palette.onSurfaceVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.software_newest),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                    Text(
                        text = stats.newestVersion ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.software_oldest),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                    Text(
                        text = stats.oldestVersion ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    palette: CarColorPalette
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = palette.accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthlyUpdatesChart(
    monthlyData: List<MonthlyUpdateCount>,
    palette: CarColorPalette
) {
    // Pre-compute localized format strings
    val updatesFormat = stringResource(R.string.format_updates_count)
    val updatesDecimalFormat = stringResource(R.string.format_updates_decimal)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.software_updates_per_month),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val chartData = monthlyData.map { data ->
                BarChartData(
                    label = data.yearMonth.format(DateTimeFormatter.ofPattern("MMM")),
                    value = data.count.toDouble(),
                    displayValue = updatesFormat.format(data.count)
                )
            }

            InteractiveBarChart(
                data = chartData,
                modifier = Modifier.fillMaxWidth(),
                barColor = palette.accent,
                labelColor = palette.onSurfaceVariant,
                showEveryNthLabel = if (chartData.size > 6) 3 else 1,
                valueFormatter = { updatesDecimalFormat.format(it) }
            )
        }
    }
}

@Composable
private fun DateFilterChips(
    selectedFilter: UpdatesDateFilter,
    palette: CarColorPalette,
    onFilterSelected: (UpdatesDateFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(UpdatesDateFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(getFilterLabel(filter)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

@Composable
private fun SoftwareVersionCard(
    update: SoftwareVersionItem,
    isLongestInstalled: Boolean,
    palette: CarColorPalette
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val uriHandler = LocalUriHandler.current
    val releaseNotesUrl = "https://www.notateslaapp.com/software-updates/version/${update.version}/release-notes"
    val unknownLabel = stringResource(R.string.unknown)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (update.isCurrent) {
                palette.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (update.isCurrent) palette.accent else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = update.version,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (update.isCurrent) palette.onSurface else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { uriHandler.openUri(releaseNotesUrl) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.software_view_release_notes),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (update.isCurrent) palette.accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (update.isCurrent) {
                            Text(
                                text = stringResource(R.string.software_current_version),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                        }
                    }
                }

                if (isLongestInstalled) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = stringResource(R.string.software_longest_installed),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.software_longest),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Install date
                Column {
                    Text(
                        text = stringResource(R.string.software_installed),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (update.isCurrent) palette.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = update.installDate?.format(dateFormatter) ?: unknownLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (update.isCurrent) palette.onSurface else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Days installed
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (update.isCurrent) palette.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.software_days),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (update.isCurrent) palette.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatDaysInstalled(update.daysInstalled),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (update.isCurrent) palette.onSurface else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Update duration (hh:mm)
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (update.isCurrent) palette.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.software_duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (update.isCurrent) palette.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatDurationHhMm(update.updateDurationMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (update.isCurrent) palette.onSurface else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatDaysInstalled(days: Long?): String {
    if (days == null || days < 0) return "--"
    return "$days"
}

private fun formatDurationHhMm(minutes: Long?): String {
    if (minutes == null || minutes < 0) return "--"
    val hours = minutes / 60
    val mins = minutes % 60
    return "%d:%02d".format(hours, mins)
}
