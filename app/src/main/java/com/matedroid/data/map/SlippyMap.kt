package com.matedroid.data.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator ("slippy map") geometry: turning coordinates into the tiles that cover them
 * and the pixel positions to draw on top.
 *
 * Deliberately free of Android types so the framing decisions — which is where the bugs
 * live — can be unit-tested without a device.
 */
object SlippyMap {

    /** Edge length of an OpenStreetMap raster tile, in pixels. */
    const val TILE_SIZE = 256

    /** Latitudes beyond this are not representable in Web Mercator. */
    private const val MAX_LATITUDE = 85.05112878

    /** A point to frame, in degrees. */
    data class Point(val latitude: Double, val longitude: Double)

    /**
     * Everything needed to paint one static map: which tiles to fetch and where each one
     * lands on a [widthPx] × [heightPx] canvas.
     *
     * [originPxX]/[originPxY] are the world-pixel coordinates of the canvas's top-left
     * corner at [zoom], which is what turns a world position into a canvas position.
     */
    data class Frame(
        val zoom: Int,
        val widthPx: Int,
        val heightPx: Int,
        val originPxX: Double,
        val originPxY: Double
    ) {
        /** Tiles covering the canvas, in the order they should be drawn. */
        val tiles: List<TilePlacement>
            get() {
                val worldTiles = 1 shl zoom
                val firstX = floor(originPxX / TILE_SIZE).toInt()
                val firstY = floor(originPxY / TILE_SIZE).toInt()
                val lastX = floor((originPxX + widthPx - 1) / TILE_SIZE).toInt()
                val lastY = floor((originPxY + heightPx - 1) / TILE_SIZE).toInt()

                val placements = mutableListOf<TilePlacement>()
                for (ty in firstY..lastY) {
                    // Above the north pole or below the south pole there is no tile to ask
                    // for; the canvas keeps its background there.
                    if (ty < 0 || ty >= worldTiles) continue
                    for (tx in firstX..lastX) {
                        // The world wraps east-west, so a frame straddling the antimeridian
                        // asks for tiles from the other side rather than nothing at all.
                        val wrappedX = ((tx % worldTiles) + worldTiles) % worldTiles
                        placements.add(
                            TilePlacement(
                                zoom = zoom,
                                x = wrappedX,
                                y = ty,
                                leftPx = (tx * TILE_SIZE - originPxX).toFloat(),
                                topPx = (ty * TILE_SIZE - originPxY).toFloat()
                            )
                        )
                    }
                }
                return placements
            }

        /** Where [point] falls on the canvas, in pixels from its top-left corner. */
        fun pixelOf(point: Point): Pair<Float, Float> {
            val x = lonToWorldPx(point.longitude, zoom) - originPxX
            val y = latToWorldPx(point.latitude, zoom) - originPxY
            return x.toFloat() to y.toFloat()
        }
    }

    /** One tile and the canvas position of its top-left corner. */
    data class TilePlacement(
        val zoom: Int,
        val x: Int,
        val y: Int,
        val leftPx: Float,
        val topPx: Float
    )

    /** Horizontal world-pixel position of [longitude] at [zoom]. */
    fun lonToWorldPx(longitude: Double, zoom: Int): Double =
        (longitude + 180.0) / 360.0 * (1 shl zoom) * TILE_SIZE

    /** Vertical world-pixel position of [latitude] at [zoom]. */
    fun latToWorldPx(latitude: Double, zoom: Int): Double {
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val rad = clamped * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0
        return y * (1 shl zoom) * TILE_SIZE
    }

    /** Longitude of a horizontal world-pixel position — the inverse of [lonToWorldPx]. */
    fun worldPxToLon(px: Double, zoom: Int): Double =
        px / ((1 shl zoom) * TILE_SIZE) * 360.0 - 180.0

    /** Latitude of a vertical world-pixel position — the inverse of [latToWorldPx]. */
    fun worldPxToLat(px: Double, zoom: Int): Double {
        val y = px / ((1 shl zoom) * TILE_SIZE)
        return atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI
    }

    /**
     * Build the frame that fits every one of [points] onto a [widthPx] × [heightPx] canvas.
     *
     * Picks the closest zoom in, so the map is as detailed as it can be while still holding
     * everything, then centres the canvas on the middle of what it is showing. [paddingPx]
     * is kept clear on each edge so a marker sitting at the extreme of the span is not drawn
     * half off the canvas.
     *
     * With a single point — or several so close together the span rounds to nothing —
     * there is no span to fit, so [maxZoom] is used.
     */
    fun frameFor(
        points: List<Point>,
        widthPx: Int,
        heightPx: Int,
        paddingPx: Int = 0,
        minZoom: Int = 3,
        maxZoom: Int = 16
    ): Frame {
        require(points.isNotEmpty()) { "A frame needs at least one point" }
        require(widthPx > 0 && heightPx > 0) { "A frame needs a positive size" }

        val usableWidth = max(1, widthPx - 2 * paddingPx)
        val usableHeight = max(1, heightPx - 2 * paddingPx)

        // Spans are measured at zoom 0 and scaled, since world pixels double per zoom level.
        val xs = points.map { lonToWorldPx(it.longitude, 0) }
        val ys = points.map { latToWorldPx(it.latitude, 0) }
        val spanX = xs.max() - xs.min()
        val spanY = ys.max() - ys.min()

        val zoom = when {
            spanX <= 0.0 && spanY <= 0.0 -> maxZoom
            else -> {
                val fitX = if (spanX > 0.0) log2(usableWidth / spanX) else Double.MAX_VALUE
                val fitY = if (spanY > 0.0) log2(usableHeight / spanY) else Double.MAX_VALUE
                floor(min(fitX, fitY)).toInt().coerceIn(minZoom, maxZoom)
            }
        }

        val centreX0 = (xs.min() + xs.max()) / 2.0
        val centreY0 = (ys.min() + ys.max()) / 2.0
        val scale = (1 shl zoom).toDouble()

        return Frame(
            zoom = zoom,
            widthPx = widthPx,
            heightPx = heightPx,
            originPxX = centreX0 * scale - widthPx / 2.0,
            originPxY = centreY0 * scale - heightPx / 2.0
        )
    }

    /**
     * The OpenStreetMap URL for one tile.
     *
     * Same tile server the in-app maps already read through osmdroid's MAPNIK source, so a
     * tile pulled for a notification is one the map screens would have pulled anyway.
     */
    fun tileUrl(tile: TilePlacement): String =
        "https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png"
}
