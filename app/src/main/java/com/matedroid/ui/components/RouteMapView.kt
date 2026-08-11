package com.matedroid.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** How an embedded osmdroid map responds to touch. */
enum class MapGestureMode {
    /** One finger scrolls the surrounding page; two fingers pan/zoom the map. */
    TWO_FINGER_PAN,

    /** Fully inert — no pan, no zoom. Taps are handled by Compose overlays above the map. */
    INERT,

    /** Regular osmdroid gestures, single-finger pan included (fullscreen maps). */
    FULL
}

/**
 * Shared osmdroid [MapView] wrapper: MAPNIK tiles, gesture handling per [gestureMode], optional
 * tile dimming, optional deferred mount, and the mandatory `onDetach()` on release (without it
 * every visit to the screen leaks a tile provider).
 *
 * Screen-specific content stays at the call site: [onMapReady] runs once inside the view factory
 * (markers, polylines, zoom/center), [update] runs on every recomposition-driven update pass.
 */
@Composable
fun RouteMapView(
    modifier: Modifier = Modifier,
    gestureMode: MapGestureMode = MapGestureMode.TWO_FINGER_PAN,
    dimTiles: Boolean = false,
    deferMount: Boolean = false,
    onMapReady: ((MapView) -> Unit)? = null,
    update: ((MapView) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()

    // Defer MapView instantiation by a short delay so the first frame paints before osmdroid's
    // synchronous MapView constructor runs on the main thread.
    var mapMounted by remember { mutableStateOf(!deferMount) }
    if (deferMount) {
        LaunchedEffect(Unit) {
            delay(120)
            mapMounted = true
        }
    }
    if (!mapMounted) return

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                when (gestureMode) {
                    MapGestureMode.TWO_FINGER_PAN -> {
                        setMultiTouchControls(true)
                        setOnTouchListener { v, event ->
                            v.parent?.requestDisallowInterceptTouchEvent(event.pointerCount >= 2)
                            false
                        }
                    }
                    MapGestureMode.INERT -> {
                        setMultiTouchControls(false)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        isClickable = false
                        isFocusable = false
                        setOnTouchListener { _, _ -> true }
                    }
                    MapGestureMode.FULL -> setMultiTouchControls(true)
                }
                if (dimTiles) {
                    overlayManager.tilesOverlay.setColorFilter(mapDimFilter(isDark))
                }
                onMapReady?.invoke(this)
            }
        },
        update = { mapView -> update?.invoke(mapView) },
        // onDetach() shuts down osmdroid's tile-loader threads and cache.
        onRelease = { it.onDetach() },
        modifier = modifier
    )
}

// Dim + desaturate map tiles so overlaid data stays legible. Route polylines and markers
// are separate overlays and are unaffected by this tile-only filter.
fun mapDimFilter(isDark: Boolean): ColorMatrixColorFilter {
    val matrix = ColorMatrix().apply { setSaturation(if (isDark) 0.35f else 0.55f) }
    val toneScale = if (isDark) 0.60f else 0.92f
    val toneLift = if (isDark) 0f else 18f
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                toneScale, 0f, 0f, 0f, toneLift,
                0f, toneScale, 0f, 0f, toneLift,
                0f, 0f, toneScale, 0f, toneLift,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return ColorMatrixColorFilter(matrix)
}

/**
 * Bounding box around [points] with fractional padding on each side, and a minimum absolute
 * padding so single points (or degenerate routes) still get a sensible viewport.
 * [points] must be non-empty.
 */
fun boundingBoxOf(
    points: List<GeoPoint>,
    paddingFraction: Double = 0.15,
    minPadding: Double = 0.01
): BoundingBox {
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }

    val latPadding = maxOf((maxLat - minLat) * paddingFraction, minPadding)
    val lonPadding = maxOf((maxLon - minLon) * paddingFraction, minPadding)

    return BoundingBox(
        maxLat + latPadding,  // north
        maxLon + lonPadding,  // east
        minLat - latPadding,  // south
        minLon - lonPadding   // west
    )
}
