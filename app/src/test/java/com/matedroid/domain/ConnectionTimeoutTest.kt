package com.matedroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTimeoutTest {

    @Test
    fun `automatic keeps the fast failover when a secondary server is configured`() {
        assertEquals(
            ConnectionTimeout.WITH_FALLBACK_SECONDS,
            ConnectionTimeout.resolveSeconds(ConnectionTimeout.AUTO, hasFallbackServer = true)
        )
    }

    @Test
    fun `automatic allows for a slow TLS handshake with a single server`() {
        assertEquals(
            ConnectionTimeout.WITHOUT_FALLBACK_SECONDS,
            ConnectionTimeout.resolveSeconds(ConnectionTimeout.AUTO, hasFallbackServer = false)
        )
    }

    @Test
    fun `an explicit setting wins over the automatic rule`() {
        assertEquals(10, ConnectionTimeout.resolveSeconds(10, hasFallbackServer = true))
        assertEquals(10, ConnectionTimeout.resolveSeconds(10, hasFallbackServer = false))
    }

    @Test
    fun `automatic resolves to a value the picker also offers explicitly`() {
        assertTrue(ConnectionTimeout.PRESETS.contains(ConnectionTimeout.WITH_FALLBACK_SECONDS))
        assertTrue(ConnectionTimeout.PRESETS.contains(ConnectionTimeout.WITHOUT_FALLBACK_SECONDS))
    }

    @Test
    fun `presets offer automatic first then ascending seconds`() {
        assertEquals(ConnectionTimeout.AUTO, ConnectionTimeout.PRESETS.first())
        val seconds = ConnectionTimeout.PRESETS.drop(1)
        assertEquals(seconds.sorted(), seconds)
        assertTrue(seconds.all { it > 0 })
    }

    @Test
    fun `every preset resolves to a positive timeout`() {
        for (preset in ConnectionTimeout.PRESETS) {
            assertTrue(ConnectionTimeout.resolveSeconds(preset, hasFallbackServer = true) > 0)
            assertTrue(ConnectionTimeout.resolveSeconds(preset, hasFallbackServer = false) > 0)
        }
    }
}
