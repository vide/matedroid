package com.matedroid.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The framing maths behind the navigation notification's map. Everything here is pure, so
 * the decisions that would otherwise only be visible on a phone — which zoom, which tiles,
 * where the markers land — are checked on the JVM.
 */
class SlippyMapTest {

    private val home = SlippyMap.Point(41.970389, 3.150913)
    private val carNearby = SlippyMap.Point(41.7857, 2.785037)

    @Test
    fun `world pixel positions match the reference slippy map projection`() {
        // Greenwich at zoom 0 is the middle of the single 256px world tile.
        assertEquals(128.0, SlippyMap.lonToWorldPx(0.0, 0), 1e-9)
        assertEquals(128.0, SlippyMap.latToWorldPx(0.0, 0), 1e-9)

        // The antimeridian is the right-hand edge, the prime meridian the middle.
        assertEquals(0.0, SlippyMap.lonToWorldPx(-180.0, 0), 1e-9)
        assertEquals(256.0, SlippyMap.lonToWorldPx(180.0, 0), 1e-9)

        // Every zoom level doubles the world.
        assertEquals(256.0, SlippyMap.lonToWorldPx(0.0, 1), 1e-9)
        assertEquals(1024.0, SlippyMap.lonToWorldPx(0.0, 3), 1e-9)
    }

    @Test
    fun `projecting a point and back returns the same coordinates`() {
        val zoom = 12
        val x = SlippyMap.lonToWorldPx(home.longitude, zoom)
        val y = SlippyMap.latToWorldPx(home.latitude, zoom)

        assertEquals(home.longitude, SlippyMap.worldPxToLon(x, zoom), 1e-9)
        assertEquals(home.latitude, SlippyMap.worldPxToLat(y, zoom), 1e-9)
    }

    @Test
    fun `latitudes past the Mercator limit are clamped instead of diverging`() {
        val northPole = SlippyMap.latToWorldPx(90.0, 4)
        val limit = SlippyMap.latToWorldPx(85.05112878, 4)

        assertTrue("the pole must stay finite", northPole.isFinite())
        assertEquals(limit, northPole, 1e-6)
    }

    @Test
    fun `both points land inside the canvas, clear of the padding`() {
        val padding = 72
        val frame = SlippyMap.frameFor(
            points = listOf(carNearby, home),
            widthPx = 1024,
            heightPx = 512,
            paddingPx = padding
        )

        for (point in listOf(carNearby, home)) {
            val (x, y) = frame.pixelOf(point)
            assertTrue("x=$x outside the padded canvas", x >= padding - 1 && x <= 1024 - padding + 1)
            assertTrue("y=$y outside the padded canvas", y >= padding - 1 && y <= 512 - padding + 1)
        }
    }

    @Test
    fun `the framed points are centred on the canvas`() {
        val frame = SlippyMap.frameFor(listOf(carNearby, home), 1024, 512, paddingPx = 72)

        val (x1, y1) = frame.pixelOf(carNearby)
        val (x2, y2) = frame.pixelOf(home)

        assertEquals(1024 / 2.0, ((x1 + x2) / 2).toDouble(), 0.5)
        assertEquals(512 / 2.0, ((y1 + y2) / 2).toDouble(), 0.5)
    }

    @Test
    fun `a closer pair of points is framed at a closer zoom`() {
        val far = SlippyMap.frameFor(
            listOf(SlippyMap.Point(41.0, 2.0), SlippyMap.Point(48.8, 2.3)),
            1024, 512, paddingPx = 72
        )
        val near = SlippyMap.frameFor(
            listOf(SlippyMap.Point(41.97, 3.15), SlippyMap.Point(41.99, 3.17)),
            1024, 512, paddingPx = 72
        )

        assertTrue("near=${near.zoom} should be closer in than far=${far.zoom}", near.zoom > far.zoom)
    }

    @Test
    fun `a single point is framed at the maximum zoom`() {
        val frame = SlippyMap.frameFor(listOf(home), 1024, 512, paddingPx = 72, maxZoom = 15)

        assertEquals(15, frame.zoom)
        val (x, y) = frame.pixelOf(home)
        assertEquals(512f, x, 0.5f)
        assertEquals(256f, y, 0.5f)
    }

    @Test
    fun `two points at the same place do not blow the zoom past the cap`() {
        // Arriving, the car marker converges on the destination and the span goes to zero.
        val frame = SlippyMap.frameFor(listOf(home, home), 1024, 512, paddingPx = 72, maxZoom = 15)

        assertEquals(15, frame.zoom)
    }

    @Test
    fun `zoom is clamped at the far end too`() {
        val frame = SlippyMap.frameFor(
            listOf(SlippyMap.Point(-33.9, 151.2), SlippyMap.Point(59.3, 18.1)),
            1024, 512, paddingPx = 72, minZoom = 3
        )

        assertTrue("zoom ${frame.zoom} below the floor", frame.zoom >= 3)
    }

    @Test
    fun `the tiles cover the whole canvas and no more`() {
        val frame = SlippyMap.frameFor(listOf(carNearby, home), 1024, 512, paddingPx = 72)
        val tiles = frame.tiles

        assertTrue("expected some tiles", tiles.isNotEmpty())
        // 1024x512 needs 4x2 whole tiles plus at most one partial column and row.
        assertTrue("too many tiles: ${tiles.size}", tiles.size <= 15)

        // Every pixel of the canvas must be covered by some tile.
        val coversLeft = tiles.any { it.leftPx <= 0f }
        val coversTop = tiles.any { it.topPx <= 0f }
        val coversRight = tiles.any { it.leftPx + SlippyMap.TILE_SIZE >= 1024f }
        val coversBottom = tiles.any { it.topPx + SlippyMap.TILE_SIZE >= 512f }
        assertTrue(coversLeft && coversTop && coversRight && coversBottom)
    }

    @Test
    fun `tile indices stay inside the world at every edge`() {
        val frame = SlippyMap.frameFor(
            listOf(SlippyMap.Point(84.0, 179.5), SlippyMap.Point(84.5, 179.9)),
            1024, 512, paddingPx = 72, maxZoom = 8
        )
        val worldTiles = 1 shl frame.zoom

        for (tile in frame.tiles) {
            assertTrue("x=${tile.x} out of range", tile.x in 0 until worldTiles)
            assertTrue("y=${tile.y} out of range", tile.y in 0 until worldTiles)
        }
    }

    @Test
    fun `a frame straddling the antimeridian wraps instead of asking for nothing`() {
        val frame = SlippyMap.frameFor(
            listOf(SlippyMap.Point(0.0, 179.9), SlippyMap.Point(0.0, -179.9)),
            1024, 512, paddingPx = 72
        )

        assertTrue("expected tiles across the wrap", frame.tiles.isNotEmpty())
        val worldTiles = 1 shl frame.zoom
        assertTrue(frame.tiles.all { it.x in 0 until worldTiles })
    }

    @Test
    fun `tile urls point at the OpenStreetMap tile server`() {
        val tile = SlippyMap.TilePlacement(zoom = 12, x = 2081, y = 1540, leftPx = 0f, topPx = 0f)

        assertEquals("https://tile.openstreetmap.org/12/2081/1540.png", SlippyMap.tileUrl(tile))
    }

    @Test
    fun `a tile is drawn where its own coordinates say it belongs`() {
        val frame = SlippyMap.frameFor(listOf(carNearby, home), 1024, 512, paddingPx = 72)

        val tile = frame.tiles.first()
        val expectedLeft = tile.x * SlippyMap.TILE_SIZE - frame.originPxX
        // The x may have been wrapped, so compare modulo a whole world of pixels.
        val worldPx = (1 shl frame.zoom) * SlippyMap.TILE_SIZE
        val delta = abs(tile.leftPx - expectedLeft) % worldPx

        assertTrue("tile drawn at ${tile.leftPx}, expected $expectedLeft", delta < 1.0)
        assertEquals(tile.y * SlippyMap.TILE_SIZE - frame.originPxY, tile.topPx.toDouble(), 1.0)
    }

    @Test
    fun `an empty point list is rejected rather than framing nothing`() {
        val thrown = runCatching { SlippyMap.frameFor(emptyList(), 1024, 512) }.exceptionOrNull()

        assertNotNull(thrown)
        assertTrue(thrown is IllegalArgumentException)
    }
}
