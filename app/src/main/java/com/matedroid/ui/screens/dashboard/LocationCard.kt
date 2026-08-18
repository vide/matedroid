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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matedroid.R
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.Units
import com.matedroid.domain.model.UnitFormatter
import com.matedroid.ui.components.MapGestureMode
import com.matedroid.ui.components.RouteMapView
import com.matedroid.ui.theme.CarColorPalette
import org.osmdroid.util.GeoPoint

// Map card geometry. The pin overlay sits in the upper third (clear of the place-name text),
// and the map's rendered center is shifted to the SAME point via setMapCenterOffset so the
// dot marks the car's true position — centering the map on the car while drawing the dot
// higher up made the car appear ~30 m north of reality (always just off the road).
// The height is shared with every other page of the dashboard carousel.
private val MAP_HEIGHT = DASHBOARD_CAROUSEL_HEIGHT
private val PIN_GLOW_SIZE = 46.dp

/** Vertical center of the pin overlay, measured from the top of the map box. */
private val PIN_CENTER_Y = 64.dp

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

