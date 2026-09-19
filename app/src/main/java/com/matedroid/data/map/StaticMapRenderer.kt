package com.matedroid.data.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Paints a static OpenStreetMap image: the tiles covering a set of points, muted to match
 * the app's in-app maps, with a marker on each point.
 *
 * Built for notification artwork, so every failure is survivable — a tile that will not
 * download simply leaves its patch of the canvas empty, and a render that fetches nothing
 * at all returns null so the caller can post without a picture.
 */
@Singleton
class StaticMapRenderer @Inject constructor(
    @Named("mapTiles") private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "StaticMapRenderer"

        /**
         * A cap on tiles per render, as a backstop against a pathological frame. The sizes
         * used here need at most 15, so hitting this means the geometry is wrong and it is
         * better to draw a bare canvas than to hammer the public tile server.
         */
        private const val MAX_TILES = 24
    }

    /** A point to mark, and how to mark it. */
    data class Marker(
        val point: SlippyMap.Point,
        val style: Style,
        val colorArgb: Int
    ) {
        enum class Style {
            /** A pin with a needle, for a fixed place such as the destination. */
            PIN,

            /** A dot with a ring, for something that moves, such as the car. */
            DOT
        }
    }

    /**
     * Render [markers] onto the canvas described by [frame].
     *
     * The frame is passed in rather than derived here so the caller can decide, from the
     * frame alone, whether anything has moved enough to be worth redrawing.
     *
     * @param dark mute the tiles for a dark UI rather than a light one.
     * @param connectMarkers draw a dashed line between the first two markers. It is a
     *   straight line, not the driving route, which the API does not give us — dashes are
     *   what keep it from reading as a road.
     * @return the bitmap, or null when not one tile could be fetched.
     */
    suspend fun render(
        frame: SlippyMap.Frame,
        markers: List<Marker>,
        dark: Boolean,
        connectMarkers: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (markers.isEmpty()) return@withContext null

        val placements = frame.tiles
        if (placements.size > MAX_TILES) {
            Log.w(TAG, "Frame wants ${placements.size} tiles at zoom ${frame.zoom}, refusing")
            return@withContext null
        }

        val tiles = fetchTiles(placements)
        if (tiles.isEmpty()) {
            Log.w(TAG, "No tiles could be fetched for zoom ${frame.zoom}")
            return@withContext null
        }

        val bitmap = Bitmap.createBitmap(frame.widthPx, frame.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (dark) 0xFF12202A.toInt() else 0xFFE7ECF1.toInt())

        val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(muteMatrix(dark))
        }
        for ((placement, tile) in tiles) {
            canvas.drawBitmap(tile, placement.leftPx, placement.topPx, tilePaint)
            tile.recycle()
        }

        if (connectMarkers && markers.size >= 2) {
            drawConnector(canvas, frame, markers[0], markers[1], dark)
        }
        for (marker in markers) {
            val (x, y) = frame.pixelOf(marker.point)
            when (marker.style) {
                Marker.Style.PIN -> drawPin(canvas, x, y, marker.colorArgb, dark)
                Marker.Style.DOT -> drawDot(canvas, x, y, marker.colorArgb, dark)
            }
        }

        bitmap
    }

    /**
     * Download every tile of the frame at once, keeping the ones that arrive.
     *
     * The client has a disk cache, so on a drive most of these are served locally and only
     * the tiles newly scrolled into view reach the network.
     */
    private suspend fun fetchTiles(
        placements: List<SlippyMap.TilePlacement>
    ): List<Pair<SlippyMap.TilePlacement, Bitmap>> = coroutineScope {
        placements
            .map { placement -> async { placement to fetchTile(placement) } }
            .awaitAll()
            .mapNotNull { (placement, bitmap) -> bitmap?.let { placement to it } }
    }

    private fun fetchTile(placement: SlippyMap.TilePlacement): Bitmap? = try {
        val request = Request.Builder().url(SlippyMap.tileUrl(placement)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Tile ${placement.zoom}/${placement.x}/${placement.y}: HTTP ${response.code}")
                null
            } else {
                response.body.byteStream().use { BitmapFactory.decodeStream(it) }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Tile ${placement.zoom}/${placement.x}/${placement.y} failed: ${e.message}")
        null
    }

    /**
     * The same treatment the dashboard's location card gives its mini-map: drain the colour
     * out of the tiles, then darken or lighten them so markers and text stay the loudest
     * things in the frame.
     */
    private fun muteMatrix(dark: Boolean): ColorMatrix {
        val matrix = ColorMatrix().apply { setSaturation(0.15f) }
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
        return matrix
    }

    private fun drawConnector(
        canvas: Canvas,
        frame: SlippyMap.Frame,
        from: Marker,
        to: Marker,
        dark: Boolean
    ) {
        val (x1, y1) = frame.pixelOf(from.point)
        val (x2, y2) = frame.pixelOf(to.point)

        // A casing under the dashes keeps them readable over both dark tiles and pale ones.
        val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            color = if (dark) 0x99000000.toInt() else 0x66FFFFFF
        }
        val dashes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = from.colorArgb
            pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        }
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        canvas.drawPath(path, casing)
        canvas.drawPath(path, dashes)
    }

    /** A dot with a white ring and a soft glow — the car's own position. */
    private fun drawDot(canvas: Canvas, x: Float, y: Float, colorArgb: Int, dark: Boolean) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colorArgb and 0x00FFFFFF) or 0x55000000
        }
        canvas.drawCircle(x, y, 26f, glow)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = if (dark) Color.WHITE else Color.BLACK
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
        canvas.drawCircle(x, y, 13f, fill)
        canvas.drawCircle(x, y, 13f, ring)
    }

    /** A teardrop pin whose tip sits exactly on the point — the destination. */
    private fun drawPin(canvas: Canvas, x: Float, y: Float, colorArgb: Int, dark: Boolean) {
        val headRadius = 17f
        val height = 46f
        val headCy = y - height + headRadius

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = if (dark) Color.WHITE else Color.BLACK
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }

        val needle = Path().apply {
            moveTo(x - headRadius * 0.62f, headCy + headRadius * 0.62f)
            lineTo(x, y)
            lineTo(x + headRadius * 0.62f, headCy + headRadius * 0.62f)
            close()
        }
        canvas.drawPath(needle, fill)
        canvas.drawPath(needle, outline)
        canvas.drawCircle(x, headCy, headRadius, fill)
        canvas.drawCircle(x, headCy, headRadius, outline)

        // A hole in the middle, so the pin reads as a pin at notification size.
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFF101418.toInt() else Color.WHITE
        }
        canvas.drawCircle(x, headCy, headRadius * 0.38f, hole)
    }
}
