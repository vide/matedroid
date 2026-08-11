package com.matedroid.ui.screens.charges

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt
import com.matedroid.R
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargePoint
import com.matedroid.data.api.models.Units
import com.matedroid.domain.ChargeComparison
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.ChargeTypeBadge
import com.matedroid.ui.components.FullscreenLineChart
import com.matedroid.ui.components.extractTimeLabels
import com.matedroid.ui.components.MateDroidLoadingPlaceholder
import com.matedroid.ui.components.RouteMapView
import com.matedroid.ui.components.createPinMarkerDrawable
import com.matedroid.ui.screens.trips.displayName
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.ui.theme.CarColorPalettes
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import com.matedroid.util.formatDurationCompact
import com.matedroid.util.formatMedium
import com.matedroid.util.formatTime
import com.matedroid.util.parseIsoDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeDetailScreen(
    carId: Int,
    chargeId: Int,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTripDetail: (tripStartDate: String) -> Unit = {},
    onNavigateToCompare: (baseChargeId: Int) -> Unit = {},
    viewModel: ChargeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(carId, chargeId) {
        viewModel.loadChargeDetail(carId, chargeId)
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
                title = { Text(stringResource(R.string.charge_details_title)) },
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
        if (uiState.isLoading) {
            MateDroidLoadingPlaceholder(modifier = Modifier.padding(padding))
        } else {
            val context = LocalContext.current
            val teslamateBaseUrl = uiState.teslamateBaseUrl
            uiState.chargeDetail?.let { detail ->
                ChargeDetailContent(
                    detail = detail,
                    stats = uiState.stats,
                    units = uiState.units,
                    currencySymbol = uiState.currencySymbol,
                    isDcCharge = uiState.isDcCharge,
                    exteriorColor = exteriorColor,
                    containingTrip = uiState.containingTrip,
                    comparison = uiState.comparison,
                    onCompareClick = { onNavigateToCompare(chargeId) },
                    onNavigateToTripDetail = onNavigateToTripDetail,
                    onRemoveFromTrip = viewModel::removeFromTrip,
                    onEditCost = if (teslamateBaseUrl.isNotBlank()) {
                        {
                            val url = "$teslamateBaseUrl/charge-cost/$chargeId"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    } else null,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ChargeDetailContent(
    detail: ChargeDetail,
    stats: ChargeDetailStats?,
    units: Units?,
    currencySymbol: String,
    isDcCharge: Boolean,
    exteriorColor: String?,
    containingTrip: Pair<Long, com.matedroid.domain.model.Trip>?,
    comparison: ChargeComparison?,
    onCompareClick: () -> Unit,
    onNavigateToTripDetail: (String) -> Unit,
    onRemoveFromTrip: () -> Unit,
    onEditCost: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isSystemInDarkTheme())
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val scrollState = rememberScrollState()
    var sharedXFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { isScrolling -> if (isScrolling) sharedXFraction = null }
    }

    // Chart time labels are shared by the primary power chart and the secondary charts.
    // Remembered so crosshair-drag recompositions don't re-parse every point's date.
    val chargePoints = detail.chargePoints
    val timeLabels = remember(chargePoints, is24Hour) {
        chargePoints?.takeIf { it.size > 2 }
            ?.let { pts -> extractTimeLabels(pts.map { it.date }, is24Hour) } ?: emptyList()
    }
    val fractionToTimeLabel: (Float) -> String = label@{ fraction ->
        val cp = chargePoints
        if (cp == null || cp.size <= 2) return@label ""
        val index = (fraction * cp.lastIndex).roundToInt().coerceIn(0, cp.lastIndex)
        cp[index].date?.let { dateStr ->
            parseIsoDateTime(dateStr)?.formatTime(java.util.Locale.getDefault(), is24Hour) ?: ""
        } ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { sharedXFraction = null } }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero: location, headline energy/power, key meta, AC/DC badge
        ChargeHeroSection(
            detail = detail,
            stats = stats,
            isDcCharge = isDcCharge,
            currencySymbol = currencySymbol,
            palette = palette,
            is24Hour = is24Hour,
            onEditCost = onEditCost
        )

        // Accent stat tiles — the headline secondary figures
        stats?.let { s ->
            ChargeStatTiles(stats = s, units = units, palette = palette)
        }

        // Compare entry — appears for DC charges with comparable sessions nearby
        comparison?.let { cmp ->
            ChargeCompareCard(comparison = cmp, palette = palette, onClick = onCompareClick)
        }

        // Primary chart: power curve, accent-tinted by charge type (DC orange / AC green)
        val cp = chargePoints
        val hasPower = remember(cp) { cp?.any { (it.chargerPower ?: 0) > 0 } == true }
        if (cp != null && cp.size > 2 && hasPower) {
            MetricChartCard(
                chargePoints = cp,
                metricValue = { it.chargerPower?.toFloat() },
                title = stringResource(R.string.power_profile),
                icon = Icons.Default.Bolt,
                color = if (isDcCharge) palette.dcColor else palette.acColor,
                unit = "kW",
                timeLabels = timeLabels,
                externalSelectedFraction = sharedXFraction,
                onXSelected = { sharedXFraction = it },
                fractionToTimeLabel = fractionToTimeLabel
            )
        }

        // Map showing charge location
        if (detail.latitude != null && detail.longitude != null) {
            ChargeMapCard(
                latitude = detail.latitude,
                longitude = detail.longitude,
                accent = palette.accent
            )
        }

        // Everything else, tucked away behind a tap by default
        stats?.let { s ->
            ChargeMoreDetails(
                stats = s,
                units = units,
                isDcCharge = isDcCharge,
                currencySymbol = currencySymbol,
                palette = palette,
                chargePoints = chargePoints?.takeIf { it.size > 2 },
                timeLabels = timeLabels,
                sharedXFraction = sharedXFraction,
                onXSelected = { sharedXFraction = it },
                fractionToTimeLabel = fractionToTimeLabel,
                onEditCost = onEditCost
            )
        }

        // Part-of-trip banner — kept at the very end, below the details
        if (containingTrip != null) {
            val (_, trip) = containingTrip
            com.matedroid.ui.components.PartOfTripCard(
                tripRoute = trip.displayName(),
                onNavigateToTrip = { onNavigateToTripDetail(trip.startDate) },
                onConfirmRemove = onRemoveFromTrip
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Compact hero: location, the dominant figure (energy added) in the car's accent colour, a
 * balanced row of labelled key figures (peak power, battery swing, duration), and a footer with
 * the start time and the (editable) cost. Replaces the old verbose header.
 */
@Composable
private fun ChargeHeroSection(
    detail: ChargeDetail,
    stats: ChargeDetailStats?,
    isDcCharge: Boolean,
    currencySymbol: String,
    palette: CarColorPalette,
    is24Hour: Boolean,
    onEditCost: (() -> Unit)? = null
) {
    val unknownLocationLabel = stringResource(R.string.unknown_location)
    val unknownLabel = stringResource(R.string.unknown)
    val freeLabel = stringResource(R.string.charge_free)
    val peakLabel = stringResource(R.string.peak)
    val batteryLabel = stringResource(R.string.battery)
    val durationLabel = stringResource(R.string.duration)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Location
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = detail.address ?: unknownLocationLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        // Dominant figure: energy added, with the AC/DC badge on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(detail.chargeEnergyAdded ?: 0.0),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
                Text(
                    text = " kWh",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            ChargeTypeBadge(isDc = isDcCharge)
        }

        // Balanced row of labelled key figures
        if (stats != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stats.powerMax > 0) {
                    HeroStat(
                        label = peakLabel,
                        value = "${stats.powerMax} kW",
                        modifier = Modifier.weight(1f)
                    )
                }
                HeroStat(
                    label = batteryLabel,
                    value = "${stats.batteryStart}% → ${stats.batteryEnd}%",
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    label = durationLabel,
                    value = formatDurationCompact(stats.durationMin),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Footer: start time (left) and cost (right, tappable to edit in TeslaMate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = formatDateTime(detail.startDate, unknownLabel, is24Hour),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val cost = detail.cost ?: 0.0
            if (cost > 0 || onEditCost != null) {
                val costText = if (cost > 0) "$currencySymbol%.2f".format(cost) else freeLabel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (onEditCost != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onEditCost)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else Modifier
                ) {
                    Text(
                        text = costText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (onEditCost != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** A single labelled figure: small uppercase label over a bold value. Left-aligned in a column. */
@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** A single row of accent tiles for the secondary "quality" figures (avg power, efficiency, temp). */
@Composable
private fun ChargeStatTiles(
    stats: ChargeDetailStats,
    units: Units?,
    palette: CarColorPalette
) {
    val avgLabel = stringResource(R.string.average)
    val efficiencyLabel = stringResource(R.string.efficiency)
    val temperatureLabel = stringResource(R.string.temperature)

    val tiles = buildList {
        if (stats.powerMax > 0) add(avgLabel to "${stats.powerAvg.roundToInt()} kW")
        add(efficiencyLabel to "${stats.efficiency.roundToInt()}%")
        if (stats.tempMax > -100) add(temperatureLabel to UnitFormatter.formatTemperature(stats.tempAvg, units))
    }
    if (tiles.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { (label, value) ->
            ChargeStatTile(
                label = label,
                value = value,
                accent = palette.accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChargeStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
    }
}

/** Entry point into the charge-comparison screen, shown only for DC charges with nearby siblings. */
@Composable
private fun ChargeCompareCard(
    comparison: ChargeComparison,
    palette: CarColorPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.compare_charges_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(
                    R.plurals.compare_sessions_nearby,
                    comparison.others.size,
                    comparison.others.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Collapsible section holding the detailed stat cards and the secondary charts. Collapsed by
 * default to keep the screen calm; the headline figures live in the hero and tiles above.
 */
@Composable
private fun ChargeMoreDetails(
    stats: ChargeDetailStats,
    units: Units?,
    isDcCharge: Boolean,
    currencySymbol: String,
    palette: CarColorPalette,
    chargePoints: List<ChargePoint>?,
    timeLabels: List<String>,
    sharedXFraction: Float?,
    onXSelected: (Float?) -> Unit,
    fractionToTimeLabel: (Float) -> String,
    onEditCost: (() -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "moreDetailsChevron")
    val temperatureLabel = stringResource(R.string.temperature)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (expanded) R.string.hide_details else R.string.more_details),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // Energy section
                StatsSectionCard(
                    title = stringResource(R.string.energy),
                    icon = Icons.Default.EnergySavingsLeaf,
                    stats = listOf(
                        StatItem(stringResource(R.string.energy_added), "%.2f kWh".format(stats.energyAdded)),
                        StatItem(stringResource(R.string.used), "%.2f kWh".format(stats.energyUsed)),
                        StatItem(stringResource(R.string.efficiency), "%.1f%%".format(stats.efficiency))
                    )
                )

                // Battery section
                StatsSectionCard(
                    title = stringResource(R.string.battery),
                    icon = Icons.Default.BatteryChargingFull,
                    stats = listOf(
                        StatItem(stringResource(R.string.start), "${stats.batteryStart}%"),
                        StatItem(stringResource(R.string.end), "${stats.batteryEnd}%"),
                        StatItem(stringResource(R.string.energy_added), "+${stats.batteryAdded}%"),
                        StatItem(stringResource(R.string.duration), formatDurationCompact(stats.durationMin))
                    )
                )

                // Power section
                if (stats.powerMax > 0) {
                    StatsSectionCard(
                        title = stringResource(R.string.power),
                        icon = Icons.Default.Bolt,
                        stats = listOf(
                            StatItem(stringResource(R.string.maximum), "${stats.powerMax} kW"),
                            StatItem(stringResource(R.string.minimum), "${stats.powerMin} kW"),
                            StatItem(stringResource(R.string.average), "%.1f kW".format(stats.powerAvg))
                        )
                    )
                }

                // Voltage & current — AC only
                if (!isDcCharge) {
                    StatsSectionCard(
                        title = stringResource(R.string.charger),
                        icon = Icons.Default.ElectricalServices,
                        stats = listOf(
                            StatItem(stringResource(R.string.voltage_max), "${stats.voltageMax} V"),
                            StatItem(stringResource(R.string.voltage_min), "${stats.voltageMin} V"),
                            StatItem(stringResource(R.string.voltage_avg), "%.0f V".format(stats.voltageAvg)),
                            StatItem(stringResource(R.string.current_max), "${stats.currentMax} A"),
                            StatItem(stringResource(R.string.current_min), "${stats.currentMin} A"),
                            StatItem(stringResource(R.string.current_avg), "%.1f A".format(stats.currentAvg))
                        )
                    )
                }

                // Temperature section
                if (stats.tempMax > -100) {
                    StatsSectionCard(
                        title = temperatureLabel,
                        icon = Icons.Default.DeviceThermostat,
                        stats = listOf(
                            StatItem(stringResource(R.string.maximum), UnitFormatter.formatTemperature(stats.tempMax, units)),
                            StatItem(stringResource(R.string.minimum), UnitFormatter.formatTemperature(stats.tempMin, units)),
                            StatItem(stringResource(R.string.average), UnitFormatter.formatTemperature(stats.tempAvg, units))
                        )
                    )
                }

                // Cost section — tappable to edit in TeslaMate when configured.
                val cost = stats.cost ?: 0.0
                if (cost > 0) {
                    StatsSectionCard(
                        title = stringResource(R.string.cost),
                        icon = Icons.Default.Paid,
                        stats = listOf(
                            StatItem(stringResource(R.string.total), "$currencySymbol%.2f".format(cost)),
                            StatItem(stringResource(R.string.per_kwh), "$currencySymbol%.3f".format(cost / stats.energyAdded.coerceAtLeast(0.001)))
                        ),
                        onClick = onEditCost
                    )
                } else if (onEditCost != null) {
                    StatsSectionCard(
                        title = stringResource(R.string.cost),
                        icon = Icons.Default.Paid,
                        stats = listOf(StatItem(stringResource(R.string.total), stringResource(R.string.charge_free))),
                        onClick = onEditCost
                    )
                }

                // Secondary charts
                if (chargePoints != null) {
                    if (!isDcCharge) {
                        if (chargePoints.any { (it.chargerVoltage ?: 0) > 0 }) {
                            MetricChartCard(
                                chargePoints = chargePoints,
                                metricValue = { it.chargerVoltage?.toFloat() },
                                title = stringResource(R.string.voltage_profile),
                                icon = Icons.Default.ElectricalServices,
                                color = MaterialTheme.colorScheme.tertiary,
                                unit = "V",
                                timeLabels = timeLabels,
                                externalSelectedFraction = sharedXFraction,
                                onXSelected = onXSelected,
                                fractionToTimeLabel = fractionToTimeLabel
                            )
                        }
                        if (chargePoints.any { (it.chargerCurrent ?: 0) > 0 }) {
                            MetricChartCard(
                                chargePoints = chargePoints,
                                metricValue = { it.chargerCurrent?.toFloat() },
                                title = stringResource(R.string.current_profile),
                                icon = Icons.Default.Power,
                                color = MaterialTheme.colorScheme.secondary,
                                unit = "A",
                                timeLabels = timeLabels,
                                externalSelectedFraction = sharedXFraction,
                                onXSelected = onXSelected,
                                fractionToTimeLabel = fractionToTimeLabel
                            )
                        }
                    }
                    if (chargePoints.any { it.outsideTemp != null }) {
                        MetricChartCard(
                            chargePoints = chargePoints,
                            metricValue = { it.outsideTemp?.toFloat() },
                            title = temperatureLabel,
                            icon = Icons.Default.DeviceThermostat,
                            color = Color(0xFFFF9800),
                            unit = UnitFormatter.getTemperatureUnit(units),
                            timeLabels = timeLabels,
                            fixedMinMax = ::temperatureMinMax,
                            externalSelectedFraction = sharedXFraction,
                            onXSelected = onXSelected,
                            fractionToTimeLabel = fractionToTimeLabel
                        )
                    }
                    if (chargePoints.any { it.batteryLevel != null }) {
                        MetricChartCard(
                            chargePoints = chargePoints,
                            metricValue = { it.batteryLevel?.toFloat() },
                            title = stringResource(R.string.battery_level),
                            icon = Icons.Default.BatteryChargingFull,
                            color = palette.accent,
                            unit = "%",
                            timeLabels = timeLabels,
                            fixedMinMax = ::batteryMinMax,
                            externalSelectedFraction = sharedXFraction,
                            onXSelected = onXSelected,
                            fractionToTimeLabel = fractionToTimeLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeMapCard(latitude: Double, longitude: Double, accent: Color) {
    val context = LocalContext.current
    val locationTitle = stringResource(R.string.location)
    val chargeLocationMarker = stringResource(R.string.charge_location)

    fun openInMaps() {
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        context.startActivity(intent)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openInMaps() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = locationTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val primaryColor = accent.toArgb()

                RouteMapView(
                    onMapReady = { mapView ->
                        val geoPoint = GeoPoint(latitude, longitude)

                        // Add marker at charge location
                        val marker = Marker(mapView).apply {
                            position = geoPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = chargeLocationMarker
                            icon = createPinMarkerDrawable(mapView.context.resources, primaryColor)
                        }
                        mapView.overlays.add(marker)

                        // Center on the location
                        mapView.controller.setZoom(16.0)
                        mapView.controller.setCenter(geoPoint)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

data class StatItem(val label: String, val value: String)

@Composable
private fun StatsSectionCard(
    title: String,
    icon: ImageVector,
    stats: List<StatItem>,
    onClick: (() -> Unit)? = null
) {
    // Get the current screen settings
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    // Define how many columns we want according to the available screen width
    val columnCount = when {
        screenWidth > 600 -> 4 // Big screen or landscape orientation
        screenWidth > 340 -> 3 // Standard screen
        else -> 2              // Small screen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // External-link affordance: signals the card opens TeslaMate's cost editor.
                if (onClick != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Divide the list of statistics according to the calculated number of columns
            val chunked = stats.chunked(columnCount)
            chunked.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { stat ->
                        StatItemView(
                            label = stat.label,
                            value = stat.value,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Fill the leftover space if the last row is not complete.
                    // This prevents a single item from stretching too much
                    val emptySlots = columnCount - row.size
                    if (emptySlots > 0) {
                        repeat(emptySlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (index < chunked.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}


@Composable
private fun StatItemView(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * One card per charge metric: extracts the metric's values from the charge points,
 * optionally derives a fixed Y range from them, and renders the shared [ChartCard].
 */
@Composable
private fun MetricChartCard(
    chargePoints: List<ChargePoint>,
    metricValue: (ChargePoint) -> Float?,
    title: String,
    icon: ImageVector,
    color: Color,
    unit: String,
    timeLabels: List<String>,
    fixedMinMax: ((List<Float>) -> Pair<Float, Float>)? = null,
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    val values = remember(chargePoints) { chargePoints.mapNotNull(metricValue) }
    if (values.size < 2) return
    val minMax = remember(values) { fixedMinMax?.invoke(values) }

    ChartCard(
        title = title,
        icon = icon,
        data = values,
        color = color,
        unit = unit,
        fixedMinMax = minMax,
        timeLabels = timeLabels,
        externalSelectedFraction = externalSelectedFraction,
        onXSelected = onXSelected,
        fractionToTimeLabel = fractionToTimeLabel
    )
}

/** Y range for the temperature chart: floor/ceil to whole degrees, padded when flat. */
private fun temperatureMinMax(temps: List<Float>): Pair<Float, Float> {
    var yMin = kotlin.math.floor(temps.min())
    var yMax = kotlin.math.ceil(temps.max())
    if (yMin == yMax) {
        yMin -= 1
        yMax += 1
    }
    return Pair(yMin, yMax)
}

/** Y range for the battery chart: rounded out to the nearest 10%, padded when flat. */
private fun batteryMinMax(batteryLevels: List<Float>): Pair<Float, Float> {
    var yMin = (kotlin.math.floor(batteryLevels.min() / 10.0) * 10).toFloat()
    var yMax = (kotlin.math.ceil(batteryLevels.max() / 10.0) * 10).toFloat()
    if (yMin == yMax) {
        yMin -= 1
        yMax += 1
    }
    return Pair(yMin, yMax)
}

@Composable
private fun ChartCard(
    title: String,
    icon: ImageVector,
    data: List<Float>,
    color: Color,
    unit: String,
    showZeroLine: Boolean = false,
    fixedMinMax: Pair<Float, Float>? = null,
    timeLabels: List<String> = emptyList(),
    externalSelectedFraction: Float? = null,
    onXSelected: ((Float?) -> Unit)? = null,
    fractionToTimeLabel: ((Float) -> String)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FullscreenLineChart(
                data = data,
                color = color,
                unit = unit,
                showZeroLine = showZeroLine,
                fixedMinMax = fixedMinMax,
                timeLabels = timeLabels,
                externalSelectedFraction = externalSelectedFraction,
                onXSelected = onXSelected,
                fractionToTimeLabel = fractionToTimeLabel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatDateTime(dateStr: String?, unknownLabel: String = "Unknown", is24Hour: Boolean? = null): String {
    if (dateStr.isNullOrBlank()) return unknownLabel
    val dt = parseIsoDateTime(dateStr) ?: return dateStr
    val locale = java.util.Locale.getDefault()
    return "${dt.toLocalDate().formatMedium(locale)} ${dt.formatTime(locale, is24Hour)}"
}
