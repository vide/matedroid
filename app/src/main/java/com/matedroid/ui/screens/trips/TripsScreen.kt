package com.matedroid.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.Trip
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.DateRangePickerDialog
import com.matedroid.ui.components.MonthScrollIndicator
import com.matedroid.ui.components.TripFingerprintStrip
import com.matedroid.ui.components.formatShortDate
import com.matedroid.ui.components.parseListItemDate
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    carId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToTripDetail: (tripStartDate: String) -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)

    LaunchedEffect(carId) { viewModel.setCarId(carId) }

    // Reload trips when returning from a child screen — merges / edits in the trip detail
    // invalidate this screen's cached list. The VM ignores the first ON_RESUME to avoid
    // doubling up with setCarId's initial load.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title)) },
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.trips.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Route,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.trips_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.padding(padding)) {
                    TripsContent(
                        trips = uiState.trips,
                        totalDistance = uiState.totalDistance,
                        totalDrivingMin = uiState.totalDrivingMin,
                        totalEnergyCharged = uiState.totalEnergyCharged,
                        availableYears = uiState.availableYears,
                        selectedYear = uiState.selectedYear,
                        isCustomDateFilter = uiState.isCustomDateFilter,
                        customStartDate = uiState.customStartDate,
                        customEndDate = uiState.customEndDate,
                        onYearSelected = { viewModel.setYear(it) },
                        onCustomRangeSelected = { start, end -> viewModel.setCustomDateRange(start, end) },
                        units = uiState.units,
                        palette = palette,
                        dcChargeIds = uiState.dcChargeIds,
                        onTripClick = { trip ->
                            viewModel.cacheTrip(trip)
                            onNavigateToTripDetail(trip.startDate)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TripsContent(
    trips: List<Trip>,
    totalDistance: Double,
    totalDrivingMin: Int,
    totalEnergyCharged: Double,
    availableYears: List<Int>,
    selectedYear: Int?,
    isCustomDateFilter: Boolean,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    onYearSelected: (Int?) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    units: Units?,
    palette: CarColorPalette,
    dcChargeIds: Set<Int>,
    onTripClick: (Trip) -> Unit
) {
    val listState = rememberLazyListState()
    // Header items in render order: year chips (conditional), summary, section header.
    val headerCount = 2 + (if (availableYears.size > 1) 1 else 0)

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Year filter chips
        if (availableYears.size > 1) {
            item {
                YearFilterChips(
                    years = availableYears,
                    selectedYear = selectedYear,
                    isCustomDateFilter = isCustomDateFilter,
                    customStartDate = customStartDate,
                    customEndDate = customEndDate,
                    palette = palette,
                    onYearSelected = onYearSelected,
                    onCustomRangeSelected = onCustomRangeSelected
                )
            }
        }

        // Summary card
        item {
            SummaryCard(
                tripCount = trips.size,
                totalDistance = totalDistance,
                totalDrivingMin = totalDrivingMin,
                totalEnergyCharged = totalEnergyCharged,
                units = units,
                palette = palette
            )
        }

        // Section header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trips_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Trip cards
        // Bulletproof key: base is the composite of startDate/endDate/firstDriveId, which is
        // unique across any non-overlapping pair of trips. The list index is appended as a
        // final tiebreaker so that even a pre-fix DB still carrying literal duplicate saved
        // trips renders instead of crashing — the repository's cleanup-on-load will have
        // deleted them by the next launch anyway.
        itemsIndexed(
            trips,
            key = { index, trip ->
                "${trip.startDate}|${trip.endDate}|${trip.drives.firstOrNull()?.driveId ?: 0}|$index"
            }
        ) { index, trip ->
            TripItem(
                trip = trip,
                units = units,
                palette = palette,
                dcChargeIds = dcChargeIds,
                onClick = { onTripClick(trip) }
            )
        }
    }

    MonthScrollIndicator(
        state = listState,
        dateAt = { index ->
            if (index < headerCount) null
            else trips.getOrNull(index - headerCount)?.startDate.parseListItemDate()
        },
        accent = palette.accent,
        modifier = Modifier.align(Alignment.CenterEnd),
    )
    }
}

@Composable
private fun SummaryCard(
    tripCount: Int,
    totalDistance: Double,
    totalDrivingMin: Int,
    totalEnergyCharged: Double,
    units: Units?,
    palette: CarColorPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.trip_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Route,
                    label = stringResource(R.string.trips_title),
                    value = "%,d".format(tripCount),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.total_distance),
                    value = UnitFormatter.formatDistance(totalDistance, units, decimals = 0),
                    palette = palette,
                    modifier = Modifier.weight(0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.trip_driving_time),
                    value = formatDuration(totalDrivingMin),
                    palette = palette,
                    modifier = Modifier.weight(1.2f)
                )
                SummaryItem(
                    icon = Icons.Filled.ElectricBolt,
                    label = stringResource(R.string.trip_energy_charged),
                    value = "%,.0f kWh".format(totalEnergyCharged),
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
private fun TripItem(
    trip: Trip,
    units: Units?,
    palette: CarColorPalette,
    dcChargeIds: Set<Int>,
    onClick: () -> Unit
) {
    // Segments memoized per trip — pure computation on the in-memory Trip, cheap enough for
    // every visible list row (LazyColumn composes ~10 at a time). Keys include dcChargeIds so
    // the strip recolors if the DC set ever changes.
    val segments = remember(trip, dcChargeIds) { buildTimelineSegments(trip, dcChargeIds) }
    val chargeCount = trip.charges.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Top row: date chip + route + distance
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDateChip(trip.startDate),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 10.dp)
                )
                Text(
                    text = trip.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = UnitFormatter.formatDistance(trip.totalDistance, units, decimals = 0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fingerprint
            TripFingerprintStrip(
                segments = segments,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Meta row: duration · stops · kWh
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(trip.totalDurationMin),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (chargeCount == 0) {
                        stringResource(R.string.trip_no_stops)
                    } else {
                        pluralStopsLabel(chargeCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (trip.totalEnergyCharged > 0.0) {
                    Text(
                        text = " · %.1f kWh".format(trip.totalEnergyCharged),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun pluralStopsLabel(count: Int): String =
    if (count == 1) stringResource(R.string.trip_one_stop)
    else stringResource(R.string.trip_n_stops, count)

private fun formatDateChip(dateStr: String): String {
    return try {
        val dt = try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        }
        dt.format(DateTimeFormatter.ofPattern("d MMM yy")).uppercase()
    } catch (_: Exception) {
        dateStr
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearFilterChips(
    years: List<Int>,
    selectedYear: Int?,
    isCustomDateFilter: Boolean,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    palette: CarColorPalette,
    onYearSelected: (Int?) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedYear == null && !isCustomDateFilter,
                onClick = { onYearSelected(null) },
                label = { Text(stringResource(R.string.filter_all_time)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
        items(years) { year ->
            FilterChip(
                selected = year == selectedYear && !isCustomDateFilter,
                onClick = { onYearSelected(year) },
                label = { Text(year.toString()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
        item {
            val label = if (isCustomDateFilter && customStartDate != null && customEndDate != null) {
                "${formatShortDate(customStartDate)} – ${formatShortDate(customEndDate)}"
            } else {
                stringResource(R.string.filter_custom)
            }
            FilterChip(
                selected = isCustomDateFilter,
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

/** Extract city name from address like "Ionity Montpellier, Saint-Aunès" → "Saint-Aunès". */
internal fun extractCity(address: String): String {
    val parts = address.split(", ")
    return if (parts.size >= 2) parts.last() else address
}

/**
 * Canonical user-facing label for a trip.
 * If the user set a custom name (non-blank), that wins everywhere — title bar, trips list,
 * "Part of X" cards, merge sheet rows, etc. Falls back to "startCity → endCity" otherwise.
 */
internal fun Trip.displayName(): String {
    val custom = name?.takeIf { it.isNotBlank() }
    return custom ?: "${extractCity(startAddress)} → ${extractCity(endAddress)}"
}

/**
 * Format a minutes-granularity duration with unit cascading: as the duration grows into
 * a larger magnitude the smaller unit is dropped (rounded into the next-larger one),
 * so readers aren't distracted by precision that no longer matters.
 *  <1h → "Xm"
 *  1–24h → "Xh Ym" (minute precision)
 *  1–7d → "Xd Yh" (hour precision, minutes rolled into hours)
 *  1w–~1mo → "Xw Yd"
 *  ≥30d → "Xmo Yw"
 */
private fun formatDuration(minutes: Int): String {
    val totalMinutes = minutes.coerceAtLeast(0)
    val totalHours = totalMinutes / 60.0
    val totalDays = totalHours / 24.0

    return when {
        totalDays >= 30 -> {
            val weeks = (totalDays / 7.0).toInt().coerceAtLeast(1)
            val months = weeks / 4
            val remWeeks = weeks - months * 4
            if (remWeeks > 0) "${months}mo ${remWeeks}w" else "${months}mo"
        }
        totalDays >= 7 -> {
            val days = Math.round(totalDays).toInt()
            val weeks = days / 7
            val remDays = days - weeks * 7
            if (remDays > 0) "${weeks}w ${remDays}d" else "${weeks}w"
        }
        totalHours >= 24 -> {
            val hours = Math.round(totalHours).toInt()
            val days = hours / 24
            val remHours = hours - days * 24
            if (remHours > 0) "${days}d ${remHours}h" else "${days}d"
        }
        totalHours >= 1 -> {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${totalMinutes}m"
    }
}

