package com.matedroid.ui.screens.dashboard

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.ActiveRoute
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.MapGestureMode
import com.matedroid.ui.components.RouteMapView
import com.matedroid.ui.theme.CarColorPalette
import com.matedroid.util.formatDuration
import com.matedroid.util.formatTime
import org.osmdroid.util.GeoPoint
import java.time.LocalDateTime

// Map card geometry. The pin overlay sits in the upper third (clear of the place-name text),
// and the map's rendered center is shifted to the SAME point via setMapCenterOffset so the
// dot marks the car's true position — centering the map on the car while drawing the dot
// higher up made the car appear ~30 m north of reality (always just off the road).
// The height is shared with every other page of the dashboard carousel.
private val MAP_HEIGHT = DASHBOARD_CAROUSEL_HEIGHT
private val PIN_GLOW_SIZE = 46.dp

/** Vertical center of the pin overlay, measured from the top of the map box. */
private val PIN_CENTER_Y = 64.dp

// Navigation banner (shown only while the car has a destination set). It sits above the
// pin: 8dp of margin plus its own ~30dp of content ends at ~38dp, clear of the pin glow,
// which starts at PIN_CENTER_Y - PIN_GLOW_SIZE / 2 = 41dp.
private val ROUTE_BANNER_MARGIN = 8.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LocationCard(
    status: CarStatus,
    units: Units?,
    resolvedAddress: String? = null,
    palette: CarColorPalette
) {
    val context = LocalContext.current
    val latitude = status.latitude
    val longitude = status.longitude
    val geofence = status.geofence
    val elevation = status.elevation
    val route = status.activeRoute

    val headline = geofence?.takeIf { it.isNotBlank() }
        ?: resolvedAddress?.takeIf { it.isNotBlank() }
        ?: if (latitude != null && longitude != null) "%.5f, %.5f".format(latitude, longitude)
        else stringResource(R.string.unknown)
    // Show the street address as a subline only when the headline is a geofence name.
    val subAddress = resolvedAddress?.takeIf { it.isNotBlank() && it != headline }

    fun openInMaps() {
        if (latitude != null && longitude != null) {
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            val intent = Intent(Intent.ACTION_VIEW, geoUri)
            context.startActivity(intent)
        }
    }

    // The muted map follows the theme: dark map + light text in dark mode,
    // light map + dark text in light mode.
    val dark = isSystemInDarkTheme()
    val onMap = if (dark) Color.White else Color(0xFF0E1216)
    val onMapDim = onMap.copy(alpha = 0.80f)
    val baseColor = if (dark) Color(0xFF12202A) else Color(0xFFE7ECF1)
    val scrimColor = if (dark) Color(0xF00A0C10) else Color(0xF2F8F9FB)
    val tintColor = if (dark) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)
    val pinBorder = if (dark) Color.White else Color(0xFF0E1216)
    // Traffic delay is the one figure on this card that is bad news, so it gets its own
    // amber rather than the car palette's accent.
    val delayColor = if (dark) Color(0xFFFFA726) else Color(0xFFB4620A)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT)
        ) {
            // Base (shows while tiles load, or when there are no coordinates).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor)
            )

            if (latitude != null && longitude != null) {
                val centerOffsetYPx = with(LocalDensity.current) {
                    (PIN_CENTER_Y - MAP_HEIGHT / 2).roundToPx()
                }
                RouteMapView(
                    gestureMode = MapGestureMode.INERT,
                    onMapReady = { mapView ->
                        // Render the map center (the car) under the pin overlay instead of
                        // at the geometric middle of the card — see MAP_HEIGHT docs above.
                        mapView.setMapCenterOffset(0, centerOffsetYPx)
                        // Mute the basemap so roads/labels recede behind the scrim
                        // and pin. Dark theme: grayscale + darken. Light theme:
                        // grayscale + lift toward white to soften the detail.
                        // (Deliberately not the shared dim filter — this mini-map
                        // mutes harder than the detail-screen hero maps.)
                        val matrix = ColorMatrix().apply { setSaturation(0f) }
                        matrix.postConcat(
                            if (dark) {
                                ColorMatrix(
                                    floatArrayOf(
                                        0.55f, 0f, 0f, 0f, 0f,
                                        0f, 0.55f, 0f, 0f, 0f,
                                        0f, 0f, 0.60f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                )
                            } else {
                                ColorMatrix(
                                    floatArrayOf(
                                        0.92f, 0f, 0f, 0f, 18f,
                                        0f, 0.92f, 0f, 0f, 18f,
                                        0f, 0f, 0.92f, 0f, 18f,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                )
                            }
                        )
                        mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                        mapView.controller.setZoom(15.0)
                        mapView.controller.setCenter(GeoPoint(latitude, longitude))
                    },
                    // onMapReady only runs once — without this the map stays centered on
                    // wherever the car was at first composition while the status polls on.
                    update = { map ->
                        val center = GeoPoint(latitude, longitude)
                        if (map.mapCenter.latitude != center.latitude ||
                            map.mapCenter.longitude != center.longitude
                        ) {
                            map.controller.setCenter(center)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Translucent tint to knock back remaining tile clutter and unify the look.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tintColor)
            )
            // Bottom scrim for text legibility.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            1f to scrimColor
                        )
                    )
            )
            // Matching scrim at the top, so the navigation banner never has to sit on
            // bare map tiles. Only drawn when there is a banner to protect.
            if (route != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to scrimColor,
                                0.30f to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                )
            }

            // Glowing pin in the upper third so the place name below it never overlaps.
            // Its center must match PIN_CENTER_Y — the map's rendered center is shifted there.
            if (latitude != null && longitude != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = PIN_CENTER_Y - PIN_GLOW_SIZE / 2),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(PIN_GLOW_SIZE)
                            .background(
                                Brush.radialGradient(
                                    listOf(palette.accent.copy(alpha = 0.45f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .background(palette.accent, CircleShape)
                            .border(2.dp, pinBorder, CircleShape)
                    )
                }
            }

            // Navigation banner: where the car is heading and how long is left.
            if (route != null) {
                RouteBanner(
                    route = route,
                    palette = palette,
                    onMap = onMap,
                    delayColor = delayColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = ROUTE_BANNER_MARGIN)
                )
            }

            // Overlay: place name, address and detail chips.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (!geofence.isNullOrBlank()) {
                    // Geofence name — big; full address small beneath.
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onMap,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subAddress != null) {
                        Text(
                            text = subAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onMapDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (!resolvedAddress.isNullOrBlank()) {
                    // No geofence: the geocoder formats the address as "<street>, <city>".
                    // Lead with the city (big, bold); show the street smaller beneath so a
                    // long street doesn't force the whole headline to shrink.
                    val parts = resolvedAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val city = parts.lastOrNull() ?: resolvedAddress
                    val street = parts.dropLast(1).joinToString(", ").ifBlank { null }
                    Text(
                        text = city,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onMap,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (street != null) {
                        Text(
                            text = street,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = onMapDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Coordinates fallback (no geofence, no resolved address).
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = onMap,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (route != null) {
                        // While navigating the route figures earn the space: the raw
                        // coordinates and the elevation are the least useful things on
                        // the card, and four chips do not fit on a narrow phone.
                        val distance = route.distanceToArrival
                        if (distance != null) {
                            CarouselChip(
                                icon = Icons.Filled.Route,
                                text = UnitFormatter.formatDistance(distance, units),
                                contentDescription = stringResource(R.string.location_distance_remaining)
                            )
                        }
                        val arrival = rememberArrivalTime(route.minutesToArrivalRounded)
                        if (arrival != null) {
                            CarouselChip(
                                icon = Icons.Filled.Schedule,
                                text = arrival,
                                contentDescription = stringResource(R.string.location_arrival_time)
                            )
                        }
                        val energy = route.energyAtArrival
                        if (energy != null) {
                            CarouselChip(
                                icon = Icons.Filled.Battery5Bar,
                                text = "$energy%",
                                contentDescription = stringResource(R.string.location_battery_on_arrival)
                            )
                        }
                    } else {
                        if (elevation != null) {
                            CarouselChip(
                                icon = Icons.Filled.Terrain,
                                text = UnitFormatter.formatElevation(elevation, units)
                            )
                        }
                        if (latitude != null && longitude != null) {
                            CarouselChip(
                                icon = Icons.Filled.LocationOn,
                                text = "%.4f, %.4f".format(latitude, longitude)
                            )
                        }
                    }
                }
            }

            // Chevron affordance — signals the whole card is tappable.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .background(
                        if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.55f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = onMap,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Whole-card tap target, above the inert map, opens the default maps app.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { openInMaps() }
            )
        }
    }
}

/**
 * The strip across the top of the location card while the car is navigating:
 * destination, time left, and the slice of that time traffic is responsible for.
 */
@Composable
private fun RouteBanner(
    route: ActiveRoute,
    palette: CarColorPalette,
    onMap: Color,
    delayColor: Color,
    modifier: Modifier = Modifier
) {
    val destination = route.destination ?: return
    val dark = isSystemInDarkTheme()
    val bannerBg = if (dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
    val resources = LocalContext.current.resources
    val remaining = route.minutesToArrivalRounded
    val delay = route.trafficDelayMinutes

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bannerBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Navigation,
            contentDescription = stringResource(R.string.location_navigating_to, destination),
            tint = palette.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = destination,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = onMap,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (remaining != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDuration(resources, remaining),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                maxLines = 1
            )
        }
        if (delay != null) {
            val delayText = formatDuration(resources, delay)
            val delayLabel = stringResource(R.string.location_traffic_delay, delayText)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "+$delayText",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = delayColor,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = delayLabel }
            )
        }
    }
}

/**
 * Clock time the car is due to arrive, or null when the API gave no time left.
 *
 * Keyed on the minute count so the wall clock is only re-read when the estimate
 * itself moves — the dashboard polls every few seconds and the answer would
 * otherwise jitter by a minute for no reason.
 */
@Composable
private fun rememberArrivalTime(minutesToArrival: Int?): String? {
    if (minutesToArrival == null) return null
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    return remember(minutesToArrival, is24Hour) {
        LocalDateTime.now()
            .plusMinutes(minutesToArrival.toLong())
            .formatTime(java.util.Locale.getDefault(), is24Hour)
    }
}
