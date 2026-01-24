package com.matedroid.ui.screens.stats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.repository.CountryBoundary
import com.matedroid.domain.model.ChargeLocation
import com.matedroid.domain.model.CountryRecord
import com.matedroid.domain.model.DriveLocation
import com.matedroid.domain.model.RegionRecord
import com.matedroid.domain.model.YearFilter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionsVisitedScreen(
    carId: Int,
    countryCode: String,
    countryName: String,
    yearFilter: YearFilter,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: RegionsVisitedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val palette = remember(exteriorColor, isDarkTheme) {
        CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)
    }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(carId, countryCode, yearFilter) {
        viewModel.loadRegions(carId, countryCode, yearFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.regions_visited_title, countryName)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_first_visit)) },
                                onClick = {
                                    viewModel.setSortOrder(RegionSortOrder.FIRST_VISIT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_alphabetically)) },
                                onClick = {
                                    viewModel.setSortOrder(RegionSortOrder.ALPHABETICAL)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_drive_count)) },
                                onClick = {
                                    viewModel.setSortOrder(RegionSortOrder.DRIVE_COUNT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_distance)) },
                                onClick = {
                                    viewModel.setSortOrder(RegionSortOrder.DISTANCE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_energy)) },
                                onClick = {
                                    viewModel.setSortOrder(RegionSortOrder.ENERGY)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_charges)) },
                                onClick = {
                                    viewModel.setSortOrder(RegionSortOrder.CHARGES)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.accent
                    )
                }
                uiState.regions.isEmpty() && uiState.countryRecord == null -> {
                    EmptyState(palette = palette)
                }
                else -> {
                    RegionsContent(
                        countryRecord = uiState.countryRecord,
                        regions = uiState.regions,
                        chargeLocations = uiState.filteredChargeLocations,
                        driveLocations = uiState.filteredDriveLocations,
                        allChargeLocations = uiState.chargeLocations,
                        allDriveLocations = uiState.driveLocations,
                        countryBoundary = uiState.countryBoundary,
                        mapViewMode = uiState.mapViewMode,
                        chargeTypeFilter = uiState.chargeTypeFilter,
                        availableYears = uiState.availableYears,
                        selectedMapYear = uiState.selectedMapYear,
                        onMapViewModeChange = { viewModel.setMapViewMode(it) },
                        onChargeTypeFilterToggle = { viewModel.toggleChargeTypeFilter(it) },
                        onMapYearChange = { viewModel.setMapYearFilter(it) },
                        palette = palette
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionsContent(
    countryRecord: CountryRecord?,
    regions: List<RegionRecord>,
    chargeLocations: List<ChargeLocation>,
    driveLocations: List<DriveLocation>,
    allChargeLocations: List<ChargeLocation>,
    allDriveLocations: List<DriveLocation>,
    countryBoundary: CountryBoundary?,
    mapViewMode: MapViewMode,
    chargeTypeFilter: ChargeTypeFilter,
    availableYears: List<Int>,
    selectedMapYear: Int?,
    onMapViewModeChange: (MapViewMode) -> Unit,
    onChargeTypeFilterToggle: (ChargeTypeFilter) -> Unit,
    onMapYearChange: (Int?) -> Unit,
    palette: CarColorPalette
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header card with country summary
        countryRecord?.let { country ->
            item(key = "header") {
                CountrySummaryCard(
                    country = country,
                    localizedName = getLocalizedCountryName(country.countryCode),
                    palette = palette
                )
            }
        }

        // Year filter chips (only show if there are multiple years)
        if (availableYears.size > 1) {
            item(key = "year_filter") {
                YearFilterRow(
                    availableYears = availableYears,
                    selectedYear = selectedMapYear,
                    onYearSelected = onMapYearChange,
                    palette = palette
                )
            }
        }

        // Map with charge/drive locations (only show if there's data)
        if (allChargeLocations.isNotEmpty() || allDriveLocations.isNotEmpty()) {
            item(key = "map") {
                CountryMapCard(
                    chargeLocations = chargeLocations,
                    driveLocations = driveLocations,
                    countryBoundary = countryBoundary,
                    mapViewMode = mapViewMode,
                    chargeTypeFilter = chargeTypeFilter,
                    onMapViewModeChange = onMapViewModeChange,
                    onChargeTypeFilterToggle = onChargeTypeFilterToggle,
                    palette = palette
                )
            }
        }

        // Region cards
        items(regions, key = { it.regionName }) { region ->
            RegionCard(region = region, palette = palette)
        }
    }
}

@Composable
private fun CountrySummaryCard(
    country: CountryRecord,
    localizedName: String,
    palette: CarColorPalette
) {
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                spotColor = palette.onSurface.copy(alpha = 0.1f)
            )
            .clip(cardShape)
            .background(palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with flag and country name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flag emoji
                Text(
                    text = country.flagEmoji,
                    fontSize = 40.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Country name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                }

                // Drive count
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = country.driveCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.drives_count,
                            country.driveCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }
            }

            // First and last visit dates
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.country_first_visit, formatDate(country.firstVisitDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.country_last_visit, formatDate(country.lastVisitDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant
                )
            }

            // Stats row in chips
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    icon = Icons.Default.Route,
                    value = "%.0f km".format(country.totalDistanceKm),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )

                StatChip(
                    icon = Icons.Default.ElectricBolt,
                    value = if (country.totalChargeEnergyKwh > 999) {
                        "%.1f MWh".format(country.totalChargeEnergyKwh / 1000)
                    } else {
                        "%.0f kWh".format(country.totalChargeEnergyKwh)
                    },
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )

                StatChip(
                    icon = Icons.Default.EvStation,
                    value = pluralStringResource(
                        R.plurals.charges_count,
                        country.chargeCount,
                        country.chargeCount
                    ),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Year filter chips for the map.
 */
@Composable
private fun YearFilterRow(
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
    palette: CarColorPalette
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        item {
            FilterChip(
                selected = selectedYear == null,
                onClick = { onYearSelected(null) },
                label = { Text(stringResource(R.string.all_years)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }

        // Year chips
        items(availableYears) { year ->
            FilterChip(
                selected = selectedYear == year,
                onClick = { onYearSelected(year) },
                label = { Text(year.toString()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.surface,
                    selectedLabelColor = palette.onSurface
                )
            )
        }
    }
}

/**
 * Map card showing charge or drive locations in a country with a toggle to switch between views.
 * Uses OSM tiles with custom styled markers.
 * Optionally highlights the country when boundary data is available.
 */
@Composable
private fun CountryMapCard(
    chargeLocations: List<ChargeLocation>,
    driveLocations: List<DriveLocation>,
    countryBoundary: CountryBoundary?,
    mapViewMode: MapViewMode,
    chargeTypeFilter: ChargeTypeFilter,
    onMapViewModeChange: (MapViewMode) -> Unit,
    onChargeTypeFilterToggle: (ChargeTypeFilter) -> Unit,
    palette: CarColorPalette
) {
    val cardShape = RoundedCornerShape(20.dp)
    val chargeCount = chargeLocations.size
    val driveCount = driveLocations.size

    // Colors for markers - green for AC, yellow/orange for DC (matching status colors used elsewhere)
    val acColor = StatusSuccess
    val dcColor = StatusWarning
    val driveColor = palette.accent
    val acColorArgb = acColor.toArgb()
    val dcColorArgb = dcColor.toArgb()
    val driveColorArgb = driveColor.toArgb()

    // Track if initial zoom has been done (only zoom once when page first opens)
    var hasInitialZoom by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                spotColor = palette.onSurface.copy(alpha = 0.15f)
            )
            .clip(cardShape)
            .background(palette.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header with toggle and count badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle selector with dots
                MapModeToggle(
                    selectedMode = mapViewMode,
                    onModeChange = onMapViewModeChange,
                    chargesEnabled = chargeLocations.isNotEmpty(),
                    drivesEnabled = driveLocations.isNotEmpty(),
                    palette = palette
                )

                // Count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when (mapViewMode) {
                            MapViewMode.CHARGES -> pluralStringResource(
                                R.plurals.format_charges_on_map,
                                chargeCount,
                                chargeCount
                            )
                            MapViewMode.DRIVES -> pluralStringResource(
                                R.plurals.format_drives_on_map,
                                driveCount,
                                driveCount
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent
                    )
                }
            }

            // Map view
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                DisposableEffect(Unit) {
                    Configuration.getInstance().userAgentValue = "MateDroid/1.0"
                    onDispose { }
                }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                        }
                    },
                    update = { mapView ->
                        // Clear all overlays except keep any info windows
                        mapView.overlays.clear()

                        // Add country highlight if boundary is available
                        countryBoundary?.let { boundary ->
                            val highlights = createCountryHighlightOverlays(boundary, acColorArgb)
                            mapView.overlays.addAll(highlights)
                        }

                        // Add markers based on current mode
                        when (mapViewMode) {
                            MapViewMode.CHARGES -> {
                                chargeLocations.forEach { charge ->
                                    val geoPoint = GeoPoint(charge.latitude, charge.longitude)
                                    val markerColor = if (charge.isDcCharge) dcColorArgb else acColorArgb

                                    val marker = Marker(mapView).apply {
                                        position = geoPoint
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        title = charge.address
                                        snippet = "%.1f kWh".format(charge.energyAddedKwh)

                                        val dotDrawable = GradientDrawable().apply {
                                            shape = GradientDrawable.OVAL
                                            setSize(32, 32)
                                            setColor(markerColor)
                                            setStroke(4, android.graphics.Color.WHITE)
                                        }
                                        icon = dotDrawable
                                    }
                                    mapView.overlays.add(marker)
                                }

                                // Only zoom on initial load
                                if (!hasInitialZoom && chargeLocations.isNotEmpty()) {
                                    val boundingBox = calculateChargeBoundingBox(chargeLocations)
                                    mapView.post {
                                        mapView.zoomToBoundingBox(boundingBox, false, 60)
                                        hasInitialZoom = true
                                    }
                                }
                            }
                            MapViewMode.DRIVES -> {
                                driveLocations.forEach { drive ->
                                    val geoPoint = GeoPoint(drive.latitude, drive.longitude)

                                    val marker = Marker(mapView).apply {
                                        position = geoPoint
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        title = drive.address
                                        snippet = "%.1f km".format(drive.distanceKm)

                                        val dotDrawable = GradientDrawable().apply {
                                            shape = GradientDrawable.OVAL
                                            setSize(28, 28)
                                            setColor(driveColorArgb)
                                            setStroke(3, android.graphics.Color.WHITE)
                                        }
                                        icon = dotDrawable
                                    }
                                    mapView.overlays.add(marker)
                                }

                                // Only zoom on initial load
                                if (!hasInitialZoom && driveLocations.isNotEmpty()) {
                                    val boundingBox = calculateDriveBoundingBox(driveLocations)
                                    mapView.post {
                                        mapView.zoomToBoundingBox(boundingBox, false, 60)
                                        hasInitialZoom = true
                                    }
                                }
                            }
                        }

                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Legend overlay at bottom-left
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (mapViewMode) {
                        MapViewMode.CHARGES -> {
                            // AC legend (tappable filter)
                            val acSelected = chargeTypeFilter == ChargeTypeFilter.AC_ONLY
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (acSelected) acColor.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { onChargeTypeFilterToggle(ChargeTypeFilter.AC_ONLY) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(acColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (acSelected) acColor else Color.DarkGray,
                                    fontWeight = if (acSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            // DC legend (tappable filter)
                            val dcSelected = chargeTypeFilter == ChargeTypeFilter.DC_ONLY
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (dcSelected) dcColor.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { onChargeTypeFilterToggle(ChargeTypeFilter.DC_ONLY) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dcColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "DC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dcSelected) dcColor else Color.DarkGray,
                                    fontWeight = if (dcSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                        MapViewMode.DRIVES -> {
                            // Drive legend with colored dot
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(driveColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.drive_start_legend),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.DarkGray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Toggle selector with dots for switching between Charges and Drives map views.
 */
@Composable
private fun MapModeToggle(
    selectedMode: MapViewMode,
    onModeChange: (MapViewMode) -> Unit,
    chargesEnabled: Boolean,
    drivesEnabled: Boolean,
    palette: CarColorPalette
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.onSurface.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Charges option
        val chargesSelected = selectedMode == MapViewMode.CHARGES
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (chargesSelected) palette.accent else Color.Transparent
                )
                .then(
                    if (chargesEnabled) {
                        Modifier.clickable { onModeChange(MapViewMode.CHARGES) }
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EvStation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        chargesSelected -> Color.White
                        chargesEnabled -> palette.onSurface
                        else -> palette.onSurface.copy(alpha = 0.4f)
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.map_mode_charges),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (chargesSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        chargesSelected -> Color.White
                        chargesEnabled -> palette.onSurface
                        else -> palette.onSurface.copy(alpha = 0.4f)
                    }
                )
            }
        }

        // Dot separator
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(palette.onSurface.copy(alpha = 0.3f))
        )

        // Drives option
        val drivesSelected = selectedMode == MapViewMode.DRIVES
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (drivesSelected) palette.accent else Color.Transparent
                )
                .then(
                    if (drivesEnabled) {
                        Modifier.clickable { onModeChange(MapViewMode.DRIVES) }
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CustomIcons.SteeringWheel,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        drivesSelected -> Color.White
                        drivesEnabled -> palette.onSurface
                        else -> palette.onSurface.copy(alpha = 0.4f)
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.map_mode_drives),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (drivesSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        drivesSelected -> Color.White
                        drivesEnabled -> palette.onSurface
                        else -> palette.onSurface.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}

/**
 * Calculate bounding box that contains all charge locations with some padding.
 */
private fun calculateChargeBoundingBox(chargeLocations: List<ChargeLocation>): BoundingBox {
    if (chargeLocations.isEmpty()) {
        // Default to Europe if no locations
        return BoundingBox(55.0, 15.0, 35.0, -10.0)
    }

    var minLat = Double.MAX_VALUE
    var maxLat = Double.MIN_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = Double.MIN_VALUE

    chargeLocations.forEach { location ->
        minLat = minOf(minLat, location.latitude)
        maxLat = maxOf(maxLat, location.latitude)
        minLon = minOf(minLon, location.longitude)
        maxLon = maxOf(maxLon, location.longitude)
    }

    // Add some padding (about 10% on each side)
    val latPadding = (maxLat - minLat) * 0.15
    val lonPadding = (maxLon - minLon) * 0.15

    // Ensure minimum padding for single point
    val minPadding = 0.01
    val effectiveLatPadding = maxOf(latPadding, minPadding)
    val effectiveLonPadding = maxOf(lonPadding, minPadding)

    return BoundingBox(
        maxLat + effectiveLatPadding,  // north
        maxLon + effectiveLonPadding,  // east
        minLat - effectiveLatPadding,  // south
        minLon - effectiveLonPadding   // west
    )
}

/**
 * Calculate bounding box that contains all drive locations with some padding.
 */
private fun calculateDriveBoundingBox(driveLocations: List<DriveLocation>): BoundingBox {
    if (driveLocations.isEmpty()) {
        // Default to Europe if no locations
        return BoundingBox(55.0, 15.0, 35.0, -10.0)
    }

    var minLat = Double.MAX_VALUE
    var maxLat = Double.MIN_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = Double.MIN_VALUE

    driveLocations.forEach { location ->
        minLat = minOf(minLat, location.latitude)
        maxLat = maxOf(maxLat, location.latitude)
        minLon = minOf(minLon, location.longitude)
        maxLon = maxOf(maxLon, location.longitude)
    }

    // Add some padding (about 10% on each side)
    val latPadding = (maxLat - minLat) * 0.15
    val lonPadding = (maxLon - minLon) * 0.15

    // Ensure minimum padding for single point
    val minPadding = 0.01
    val effectiveLatPadding = maxOf(latPadding, minPadding)
    val effectiveLonPadding = maxOf(lonPadding, minPadding)

    return BoundingBox(
        maxLat + effectiveLatPadding,  // north
        maxLon + effectiveLonPadding,  // east
        minLat - effectiveLatPadding,  // south
        minLon - effectiveLonPadding   // west
    )
}

/**
 * Create country boundary overlays that highlight the selected country.
 * Returns a list of polygons - one for each part of the country (mainland + islands).
 */
private fun createCountryHighlightOverlays(boundary: CountryBoundary, accentColor: Int): List<Polygon> {
    return boundary.polygons.mapIndexed { index, ring ->
        Polygon().apply {
            id = "country_boundary_$index"
            points = ring.map { (lat, lon) -> GeoPoint(lat, lon) }

            // Light fill with the accent color (very subtle)
            fillColor = android.graphics.Color.argb(25,
                android.graphics.Color.red(accentColor),
                android.graphics.Color.green(accentColor),
                android.graphics.Color.blue(accentColor)
            )

            // Visible stroke in accent color
            strokeColor = accentColor
            strokeWidth = 3f
        }
    }
}

@Composable
private fun RegionCard(
    region: RegionRecord,
    palette: CarColorPalette
) {
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = cardShape,
                spotColor = palette.onSurface.copy(alpha = 0.08f)
            )
            .clip(cardShape)
            .background(palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Region name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = region.regionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface
                    )
                }

                // Drive count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = region.driveCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.drives_count,
                            region.driveCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant
                    )
                }
            }

            // Stats row
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    icon = Icons.Default.Route,
                    value = "%.0f km".format(region.totalDistanceKm),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )

                StatChip(
                    icon = Icons.Default.ElectricBolt,
                    value = if (region.totalChargeEnergyKwh > 999) {
                        "%.1f MWh".format(region.totalChargeEnergyKwh / 1000)
                    } else {
                        "%.0f kWh".format(region.totalChargeEnergyKwh)
                    },
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )

                StatChip(
                    icon = Icons.Default.EvStation,
                    value = pluralStringResource(
                        R.plurals.charges_count,
                        region.chargeCount,
                        region.chargeCount
                    ),
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    value: String,
    palette: CarColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = palette.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyState(palette: CarColorPalette) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = stringResource(R.string.no_regions_found),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val inputFormatter = DateTimeFormatter.ISO_DATE_TIME
        val outputFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val dateTime = LocalDateTime.parse(dateStr, inputFormatter)
        dateTime.format(outputFormatter)
    } catch (e: Exception) {
        // Fallback: try parsing just the date portion
        try {
            dateStr.take(10)
        } catch (e2: Exception) {
            dateStr
        }
    }
}

/**
 * Get the localized country name for a given ISO country code.
 * Falls back to the country code if localization fails.
 */
private fun getLocalizedCountryName(countryCode: String): String {
    return try {
        Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() && it != countryCode } ?: countryCode
    } catch (e: Exception) {
        countryCode
    }
}

/**
 * Create a steering wheel drawable for drive markers on the map.
 * Draws a circular background with a simple steering wheel shape.
 */
private fun createSteeringWheelDrawable(
    context: android.content.Context,
    color: Int,
    size: Int = 36
): android.graphics.drawable.Drawable {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val center = size / 2f
    val radius = size / 2f - 2f

    // Draw white circle background with border
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, radius, paint)

    // Draw colored border
    paint.color = color
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(center, center, radius - 1.5f, paint)

    // Draw steering wheel shape
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    paint.strokeCap = Paint.Cap.ROUND

    // Outer ring
    val wheelRadius = radius * 0.6f
    canvas.drawCircle(center, center, wheelRadius, paint)

    // Center hub
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, radius * 0.15f, paint)

    // Three spokes at 90°, 210°, 330°
    paint.style = Paint.Style.STROKE
    val spokeLength = wheelRadius - radius * 0.15f
    for (angle in listOf(270.0, 150.0, 30.0)) {
        val rad = Math.toRadians(angle)
        val startX = center + (radius * 0.15f * kotlin.math.cos(rad)).toFloat()
        val startY = center + (radius * 0.15f * kotlin.math.sin(rad)).toFloat()
        val endX = center + (wheelRadius * kotlin.math.cos(rad)).toFloat()
        val endY = center + (wheelRadius * kotlin.math.sin(rad)).toFloat()
        canvas.drawLine(startX, startY, endX, endY, paint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
