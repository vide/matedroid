package com.matedroid.di

import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.domain.ConnectionTimeout
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Covers the two halves of the configurable connect timeout that ConnectionTimeoutTest
 * cannot see: that the resolved value reaches OkHttp in the unit it expects, and that the
 * API cache does not hand back a client built with the old timeout after the setting changes.
 * Also checks that custom headers reach the request, and that invalid ones are dropped
 * rather than crashing OkHttp.
 */
class TeslamateApiFactoryTest {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var factory: TeslamateApiFactory

    /** Mutable so a test can change the settings between two [TeslamateApiFactory.create] calls. */
    private var currentSettings = AppSettings(serverUrl = PRIMARY)

    @Before
    fun setup() {
        settingsDataStore = mockk()
        every { settingsDataStore.settings } answers { flowOf(currentSettings) }
        factory = TeslamateApiFactory(settingsDataStore, Moshi.Builder().build())
    }

    @Test
    fun `the configured timeout reaches OkHttp as seconds`() {
        val client = factory.createOkHttpClient(
            apiToken = "",
            acceptInvalidCerts = false,
            connectTimeoutSeconds = 5
        )

        assertEquals(5_000, client.connectTimeoutMillis)
    }

    @Test
    fun `the timeout setting does not disturb reads and writes`() {
        val client = factory.createOkHttpClient(
            apiToken = "",
            acceptInvalidCerts = false,
            connectTimeoutSeconds = 1
        )

        assertEquals(1_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }

    @Test
    fun `an unchanged configuration reuses the cached api`() = runTest {
        val first = factory.create(PRIMARY)
        val second = factory.create(PRIMARY)

        assertSame(first, second)
    }

    @Test
    fun `changing the timeout builds a new api instead of reusing the cached one`() = runTest {
        currentSettings = AppSettings(serverUrl = PRIMARY, connectTimeoutSeconds = 1)
        val before = factory.create(PRIMARY)

        currentSettings = AppSettings(serverUrl = PRIMARY, connectTimeoutSeconds = 10)
        val after = factory.create(PRIMARY)

        assertNotSame(before, after)
    }

    @Test
    fun `adding a fallback server re-resolves the automatic timeout`() = runTest {
        currentSettings = AppSettings(serverUrl = PRIMARY, connectTimeoutSeconds = ConnectionTimeout.AUTO)
        val singleServer = factory.create(PRIMARY)

        currentSettings = currentSettings.copy(secondaryServerUrl = SECONDARY)
        val withFallback = factory.create(PRIMARY)

        // Automatic goes from 5s to 1s, so the cached client must not be reused.
        assertNotSame(singleServer, withFallback)
    }

    @Test
    fun `an explicit timeout is unaffected by adding a fallback server`() = runTest {
        currentSettings = AppSettings(serverUrl = PRIMARY, connectTimeoutSeconds = 5)
        val singleServer = factory.create(PRIMARY)

        currentSettings = currentSettings.copy(secondaryServerUrl = SECONDARY)
        val withFallback = factory.create(PRIMARY)

        assertSame(singleServer, withFallback)
    }

    @Test
    fun `an explicit override wins over the stored timeout`() = runTest {
        currentSettings = AppSettings(serverUrl = PRIMARY, connectTimeoutSeconds = 1)
        val fromSettings = factory.create(PRIMARY)
        val fromOverride = factory.create(PRIMARY, connectTimeoutSeconds = 15)

        assertNotSame(fromSettings, fromOverride)
        // The override is part of the cache key like every other setting, so it still caches.
        assertSame(fromOverride, factory.create(PRIMARY, connectTimeoutSeconds = 15))
    }

    @Test
    fun `valid custom headers are sent and invalid ones are skipped instead of crashing`() {
        val client = factory.createOkHttpClient(
            apiToken = "",
            acceptInvalidCerts = false,
            customHeaders = mapOf(
                "X-API-Key" to "secret",
                "X Bad Name" to "value",
                "X-Bad-Value" to "contraseña"
            )
        )

        val sent = sendThrough(client)

        assertEquals("secret", sent.header("X-API-Key"))
        assertNull(sent.header("X Bad Name"))
        assertNull(sent.header("X-Bad-Value"))
    }

    @Test
    fun `changing custom headers builds a new api instead of reusing the cached one`() = runTest {
        currentSettings = AppSettings(serverUrl = PRIMARY, customHeaders = mapOf("X-API-Key" to "old"))
        val before = factory.create(PRIMARY)

        currentSettings = currentSettings.copy(customHeaders = mapOf("X-API-Key" to "new"))
        val after = factory.create(PRIMARY)

        assertNotSame(before, after)
    }

    @Test
    fun `invalidateCache forces the next api to be rebuilt`() = runTest {
        val before = factory.create(PRIMARY)
        factory.invalidateCache()

        assertNotSame(before, factory.create(PRIMARY))
    }

    /**
     * Runs one request through [client]'s own interceptors and returns it as it would go on
     * the wire. A trailing interceptor answers locally, so no server or network is needed.
     */
    private fun sendThrough(client: OkHttpClient): Request {
        var captured: Request? = null
        val probe = client.newBuilder()
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        probe.newCall(Request.Builder().url("$PRIMARY/api/ping").build()).execute().close()
        return checkNotNull(captured)
    }

    private companion object {
        const val PRIMARY = "https://primary.example.com"
        const val SECONDARY = "https://secondary.example.com"
    }
}
