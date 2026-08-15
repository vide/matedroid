package com.matedroid.util

import com.matedroid.domain.AppTimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [parseIsoDateTime] resolves the offset through the process-wide [AppTimeZone] mirror, so each
 * test restores the default afterwards rather than leaking a mode into the rest of the suite.
 *
 * All fixtures use explicit zones (never the machine's default) so the suite behaves the same on
 * a developer laptop and on CI.
 */
class DateTimeParseTimeZoneTest {

    /** 15:39 UTC — i.e. 17:39 in Madrid, 00:39 the next day in Tokyo. */
    private val utcTimestamp = "2026-05-10T15:39:00Z"

    /** The same instant, as TeslaMate would send it from a server in Spain. */
    private val madridTimestamp = "2026-05-10T17:39:00+02:00"

    @Before
    @After
    fun resetMode() {
        AppTimeZone.mode = AppTimeZone.MODE_SERVER
    }

    // ==================== Default (server) mode ====================

    @Test
    fun `server mode keeps the wall clock TeslaMate sent, ignoring the offset`() {
        assertEquals("2026-05-10T15:39", parseIsoDateTime(utcTimestamp).toString())
        assertEquals("2026-05-10T17:39", parseIsoDateTime(madridTimestamp).toString())
    }

    @Test
    fun `server mode is the default so upgrading changes nothing`() {
        assertEquals(AppTimeZone.MODE_SERVER, AppTimeZone.mode)
        assertNull(AppTimeZone.displayZone())
    }

    // ==================== Fixed zone ====================

    @Test
    fun `a fixed zone converts both encodings of the same instant identically`() {
        AppTimeZone.mode = "Europe/Madrid"

        assertEquals("2026-05-10T17:39", parseIsoDateTime(utcTimestamp).toString())
        assertEquals("2026-05-10T17:39", parseIsoDateTime(madridTimestamp).toString())
    }

    @Test
    fun `a fixed zone can roll the date over`() {
        AppTimeZone.mode = "Asia/Tokyo"

        // 15:39Z is 00:39 the following day in JST (UTC+9).
        assertEquals("2026-05-11T00:39", parseIsoDateTime(utcTimestamp).toString())
    }

    @Test
    fun `this is what fixes a TeslaMate server left on the Docker default of UTC`() {
        // The reported bug: server emits UTC, user is in Madrid, times read two hours early.
        assertEquals("2026-05-10T15:39", parseIsoDateTime(utcTimestamp).toString())

        AppTimeZone.mode = "Europe/Madrid"
        assertEquals("2026-05-10T17:39", parseIsoDateTime(utcTimestamp).toString())
    }

    // ==================== Robustness ====================

    @Test
    fun `an unknown zone id falls back to server time instead of throwing`() {
        AppTimeZone.mode = "Mars/Olympus_Mons"

        assertNull(AppTimeZone.displayZone())
        assertEquals("2026-05-10T15:39", parseIsoDateTime(utcTimestamp).toString())
    }

    @Test
    fun `a timestamp without an offset is taken at face value in every mode`() {
        val noOffset = "2026-05-10T15:39:00"

        assertEquals("2026-05-10T15:39", parseIsoDateTime(noOffset).toString())

        AppTimeZone.mode = "Asia/Tokyo"
        assertEquals("2026-05-10T15:39", parseIsoDateTime(noOffset).toString())
    }

    @Test
    fun `blank and malformed input stays null in every mode`() {
        AppTimeZone.mode = "Europe/Madrid"

        assertNull(parseIsoDateTime(null))
        assertNull(parseIsoDateTime(""))
        assertNull(parseIsoDateTime("   "))
        assertNull(parseIsoDateTime("not a date"))
    }

    @Test
    fun `parseIsoDate follows the converted instant across a date boundary`() {
        // 23:30 in Madrid is already the next day in Tokyo.
        val lateEvening = "2026-05-10T23:30:00+02:00"

        assertEquals("2026-05-10", parseIsoDate(lateEvening).toString())

        AppTimeZone.mode = "Asia/Tokyo"
        assertEquals("2026-05-11", parseIsoDate(lateEvening).toString())
    }

    // ==================== Mode helpers ====================

    @Test
    fun `isFixedZone distinguishes explicit zones from the automatic modes`() {
        AppTimeZone.mode = AppTimeZone.MODE_SERVER
        assert(!AppTimeZone.isFixedZone())

        AppTimeZone.mode = AppTimeZone.MODE_DEVICE
        assert(!AppTimeZone.isFixedZone())

        AppTimeZone.mode = "Europe/Madrid"
        assert(AppTimeZone.isFixedZone())
    }

    @Test
    fun `device mode resolves to the JVM default zone`() {
        AppTimeZone.mode = AppTimeZone.MODE_DEVICE

        assertEquals(java.time.ZoneId.systemDefault(), AppTimeZone.displayZone())
    }
}
