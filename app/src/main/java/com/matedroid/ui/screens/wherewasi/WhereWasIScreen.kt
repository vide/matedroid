package com.matedroid.ui.screens.wherewasi

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.matedroid.R
import com.matedroid.data.repository.WeatherCondition
import com.matedroid.data.repository.countryCodeToFlag
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.icons.CustomIcons
import com.matedroid.ui.components.createPinMarkerDrawable
import com.matedroid.ui.theme.CarColorPalettes
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhereWasIScreen(
    carId: Int,
    targetTimestamp: String,
    exteriorColor: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDriveDetail: (driveId: Int) -> Unit,
    onNavigateToChargeDetail: (chargeId: Int) -> Unit,
    onNavigateToCountriesVisited: () -> Unit,
    viewModel: WhereWasIViewModel = hiltViewModel()
) {
    val isDarkTheme = isSystemInDarkTheme()
    val palette = CarColorPalettes.forExteriorColor(exteriorColor, isDarkTheme)
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(carId, targetTimestamp) {
        viewModel.load(carId, targetTimestamp)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.where_was_i_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error == "no_data" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.where_was_i_no_data),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.targetDateTime?.let { dt ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // DateTime header
                    state.targetDateTime?.let { dt ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = palette.accent
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = dt,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface
                                )
                            }
                        }
                    }

                    // Map
                    val lat = state.latitude
                    val lon = state.longitude
                    if (lat != null && lon != null) {
                        val context = LocalContext.current
                        val accentArgb = palette.accent.toArgb()
                        val youWereHere = stringResource(R.string.you_were_here)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface),
                            modifier = Modifier.clickable {
                                val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                            }
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(false)
                                        controller.setZoom(15.0)
                                        controller.setCenter(GeoPoint(lat, lon))
                                        val marker = org.osmdroid.views.overlay.Marker(this)
                                        marker.position = GeoPoint(lat, lon)
                                        marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                                        marker.icon = createPinMarkerDrawable(ctx.resources, accentArgb)
                                        marker.title = youWereHere
                                        overlays.add(marker)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    // Location breadcrumb + address
                    state.location?.let { loc ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Breadcrumb line: flag Country > Region > City
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    loc.countryCode?.let { code ->
                                        val flag = countryCodeToFlag(code)
                                        Text(text = flag, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    val localizedCountryName = loc.countryCode?.let { code ->
                                        java.util.Locale.Builder().setRegion(code).build().getDisplayCountry(java.util.Locale.getDefault())
                                            .takeIf { it.isNotBlank() && it != code }
                                    } ?: loc.countryName ?: loc.countryCode
                                    val breadcrumbParts = listOfNotNull(
                                        localizedCountryName,
                                        loc.regionName,
                                        loc.city
                                    )
                                    breadcrumbParts.forEachIndexed { index, part ->
                                        if (index == 0 && loc.countryCode != null) {
                                            // Country is tappable
                                            Text(
                                                text = part,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.accent,
                                                modifier = Modifier.clickable { onNavigateToCountriesVisited() }
                                            )
                                        } else {
                                            Text(
                                                text = part,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.onSurfaceVariant
                                            )
                                        }
                                        if (index < breadcrumbParts.lastIndex) {
                                            Text(
                                                text = " > ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }

                                // Geofence name or full address (bigger)
                                val displayAddress = state.geofenceName
                                    ?: loc.address
                                if (displayAddress != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = displayAddress,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // State card
                    state.carState?.let { carState ->
                        val hasLinkedActivity = when (carState) {
                            CarActivityState.DRIVING -> state.driveId != null
                            CarActivityState.CHARGING -> state.chargeId != null
                            CarActivityState.PARKED -> state.lastActivityDriveId != null || state.lastActivityChargeId != null
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface),
                            modifier = if (hasLinkedActivity) {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when (carState) {
                                            CarActivityState.DRIVING -> state.driveId?.let { onNavigateToDriveDetail(it) }
                                            CarActivityState.CHARGING -> state.chargeId?.let { onNavigateToChargeDetail(it) }
                                            CarActivityState.PARKED -> {
                                                state.lastActivityDriveId?.let { onNavigateToDriveDetail(it) }
                                                    ?: state.lastActivityChargeId?.let { onNavigateToChargeDetail(it) }
                                            }
                                        }
                                    }
                            } else Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // State icon + label (centered)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = stateIcon(carState),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stateLabel(carState),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onSurface
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = palette.onSurfaceVariant.copy(alpha = 0.2f)
                                )

                                // Info rows — shared info first, then state-specific
                                // Row 1: Odometer | Outside Temp
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    InfoItem(
                                        label = stringResource(R.string.mileage_title),
                                        value = state.odometer?.let {
                                            UnitFormatter.formatDistance(it, state.units)
                                        } ?: "—",
                                        palette = palette,
                                        modifier = Modifier.weight(1f)
                                    )
                                    InfoItem(
                                        label = stringResource(R.string.outside_temp),
                                        value = state.outsideTemp?.let {
                                            UnitFormatter.formatTemperature(it, state.units)
                                        } ?: "—",
                                        palette = palette,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Row 2-3: State-specific
                                when (carState) {
                                    CarActivityState.DRIVING -> {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            InfoItem(
                                                label = stringResource(R.string.speed_profile).split(" ").first(),
                                                value = state.speed?.let { "$it ${UnitFormatter.getSpeedUnit(state.units)}" } ?: "—",
                                                palette = palette,
                                                modifier = Modifier.weight(1f)
                                            )
                                            InfoItem(
                                                label = stringResource(R.string.distance),
                                                value = state.driveDistance?.let {
                                                    UnitFormatter.formatDistance(it, state.units)
                                                } ?: "—",
                                                palette = palette,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    CarActivityState.CHARGING -> {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            InfoItem(
                                                label = stringResource(R.string.battery_level),
                                                value = state.batteryLevel?.let { "$it%" } ?: "—",
                                                palette = palette,
                                                modifier = Modifier.weight(1f)
                                            )
                                            InfoItem(
                                                label = stringResource(R.string.power_profile).split(" ").first(),
                                                value = state.chargerPower?.let { "$it kW" } ?: "—",
                                                palette = palette,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    CarActivityState.PARKED -> {
                                        state.parkedDurationMinutes?.takeIf { it > 0 }?.let { totalMin ->
                                            val days = totalMin / (24 * 60)
                                            val hours = (totalMin % (24 * 60)) / 60
                                            val minutes = totalMin % 60
                                            val durationStr = buildString {
                                                if (days > 0) append("${days}d ")
                                                if (hours > 0) append("${hours}h ")
                                                if (days == 0L && minutes > 0) append("${minutes}m")
                                            }.trim()
                                            val parkedForPrefix = stringResource(R.string.parked_for, "").trimEnd()
                                            val sinceStr = state.parkedSince
                                            val sincePrefix = sinceStr?.let { stringResource(R.string.parked_since, "").trimEnd() }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = buildAnnotatedString {
                                                            append("$parkedForPrefix ")
                                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                                append(durationStr)
                                                            }
                                                        },
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = palette.onSurface
                                                    )
                                                    if (sinceStr != null && sincePrefix != null) {
                                                        Text(
                                                            text = buildAnnotatedString {
                                                                append("$sincePrefix ")
                                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                                    append(sinceStr)
                                                                }
                                                            },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = palette.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                if (hasLinkedActivity) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = palette.onSurfaceVariant,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Chevron hint for tappable cards (driving/charging)
                                if (hasLinkedActivity && carState != CarActivityState.PARKED) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = palette.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }

                    // Weather card
                    if (state.weatherCondition != null && state.weatherTemperature != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = weatherIcon(state.weatherCondition!!),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = weatherIconColor(state.weatherCondition!!)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "%.1f\u00B0C".format(state.weatherTemperature),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = weatherDescription(state.weatherCondition!!),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    palette: com.matedroid.ui.theme.CarColorPalette,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = palette.onSurface
        )
    }
}

@Composable
private fun stateLabel(state: CarActivityState): String = when (state) {
    CarActivityState.DRIVING -> stringResource(R.string.where_was_i_driving)
    CarActivityState.CHARGING -> stringResource(R.string.where_was_i_charging)
    CarActivityState.PARKED -> stringResource(R.string.where_was_i_parked)
}

private fun stateIcon(state: CarActivityState): ImageVector = when (state) {
    CarActivityState.DRIVING -> Icons.Default.DirectionsCar
    CarActivityState.CHARGING -> Icons.Default.EvStation
    CarActivityState.PARKED -> Icons.Default.LocalParking
}

private fun weatherIcon(condition: WeatherCondition): ImageVector = when (condition) {
    WeatherCondition.CLEAR -> CustomIcons.WeatherSunny
    WeatherCondition.PARTLY_CLOUDY -> CustomIcons.WeatherPartlyCloudy
    WeatherCondition.FOG -> CustomIcons.WeatherFog
    WeatherCondition.DRIZZLE -> CustomIcons.WeatherDrizzle
    WeatherCondition.RAIN -> CustomIcons.WeatherRain
    WeatherCondition.SNOW -> CustomIcons.WeatherSnow
    WeatherCondition.THUNDERSTORM -> CustomIcons.WeatherThunderstorm
}

private fun weatherIconColor(condition: WeatherCondition): Color = when (condition) {
    WeatherCondition.CLEAR -> Color(0xFFFFC107)
    WeatherCondition.PARTLY_CLOUDY -> Color(0xFF78909C)
    WeatherCondition.FOG -> Color(0xFF90A4AE)
    WeatherCondition.DRIZZLE -> Color(0xFF64B5F6)
    WeatherCondition.RAIN -> Color(0xFF1E88E5)
    WeatherCondition.SNOW -> Color(0xFF42A5F5)
    WeatherCondition.THUNDERSTORM -> Color(0xFF7E57C2)
}

@Composable
private fun weatherDescription(condition: WeatherCondition): String = when (condition) {
    WeatherCondition.CLEAR -> stringResource(R.string.weather_clear)
    WeatherCondition.PARTLY_CLOUDY -> stringResource(R.string.weather_partly_cloudy)
    WeatherCondition.FOG -> stringResource(R.string.weather_fog)
    WeatherCondition.DRIZZLE -> stringResource(R.string.weather_drizzle)
    WeatherCondition.RAIN -> stringResource(R.string.weather_rain)
    WeatherCondition.SNOW -> stringResource(R.string.weather_snow)
    WeatherCondition.THUNDERSTORM -> stringResource(R.string.weather_thunderstorm)
}
