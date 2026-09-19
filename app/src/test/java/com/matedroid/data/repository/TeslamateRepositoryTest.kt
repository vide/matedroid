package com.matedroid.data.repository

import com.matedroid.data.api.TeslamateApi
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargeDetailData
import com.matedroid.data.api.models.ChargeDetailResponse
import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.di.TeslamateApiFactory
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * The live-charge availability probe. While a car is charging it is asked for every 5 s by
 * the dashboard and every 30 s by the monitor service, so anything short of "remember the
 * answer" turns into a request storm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeslamateRepositoryTest {

    private lateinit var api: TeslamateApi
    private lateinit var apiFactory: TeslamateApiFactory
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: TeslamateRepository

    @Before
    fun setup() {
        api = mockk()
        apiFactory = mockk()
        settingsDataStore = mockk()
        every { settingsDataStore.settings } returns flowOf(AppSettings(serverUrl = "https://tm.example"))
        coEvery { apiFactory.create(any(), any(), any()) } returns api
        repository = TeslamateRepository(apiFactory, settingsDataStore)
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    private fun httpError(code: Int): Response<ChargeDetailResponse> =
        Response.error(code, "".toResponseBody(null))

    private val activeChargeBody = ChargeDetailResponse(
        data = ChargeDetailData(charge = ChargeDetail(chargeId = 7, isCharging = true))
    )

    @Test
    fun `a 200 is remembered as available and never probed again`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns Response.success(activeChargeBody)

        assertTrue(repository.isCurrentChargeAvailable(1))
        assertTrue(repository.isCurrentChargeAvailable(1))

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `a 204 proves the endpoint exists`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns Response.success<ChargeDetailResponse>(204, null)

        assertTrue(repository.isCurrentChargeAvailable(1))
        assertTrue(repository.isCurrentChargeAvailable(1))

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `a 404 is remembered as unavailable and never probed again`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns httpError(404)

        assertFalse(repository.isCurrentChargeAvailable(1))
        assertFalse(repository.isCurrentChargeAvailable(1))

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `a server error is not re-probed on every call`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns httpError(503)

        repeat(5) { assertFalse(repository.isCurrentChargeAvailable(1)) }

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `a network failure is not re-probed on every call`() = runTest {
        coEvery { api.getCurrentCharge(1) } throws IOException("timeout")

        repeat(5) { assertFalse(repository.isCurrentChargeAvailable(1)) }

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `the failure memory is per car`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns httpError(503)
        coEvery { api.getCurrentCharge(2) } returns Response.success(activeChargeBody)

        assertFalse(repository.isCurrentChargeAvailable(1))
        assertTrue(repository.isCurrentChargeAvailable(2))
        assertFalse(repository.isCurrentChargeAvailable(1))

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
        coVerify(exactly = 1) { api.getCurrentCharge(2) }
    }

    @Test
    fun `concurrent callers share a single probe`() = runTest {
        coEvery { api.getCurrentCharge(1) } coAnswers {
            delay(1_000)
            httpError(503)
        }

        val answers = List(4) { async { repository.isCurrentChargeAvailable(1) } }.awaitAll()

        assertTrue(answers.none { it })
        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `a successful live charge fetch counts as the probe`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns Response.success(activeChargeBody)

        val fetched = repository.getCurrentCharge(1)
        assertTrue(fetched is ApiResult.Success)
        assertTrue(repository.isCurrentChargeAvailable(1))

        // The screen's fetch answered the question; no dedicated probe was made.
        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }

    @Test
    fun `a 404 on the live charge fetch marks the endpoint unavailable`() = runTest {
        coEvery { api.getCurrentCharge(1) } returns httpError(404)

        repository.getCurrentCharge(1)
        assertFalse(repository.isCurrentChargeAvailable(1))

        coVerify(exactly = 1) { api.getCurrentCharge(1) }
    }
}
