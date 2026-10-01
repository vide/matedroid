package com.matedroid.ui.screens.charges

import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.ChargeSessionStateDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.CarStatusWithUnits
import com.matedroid.data.repository.CurrentChargeOutcome
import com.matedroid.data.repository.TeslamateRepository
import androidx.lifecycle.viewModelScope
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentChargeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TeslamateRepository
    private lateinit var sessionStore: ChargeSessionStateDataStore
    private var vm: CurrentChargeViewModel? = null

    private val chargingStatus = CarStatus(
        displayName = "Test Tesla",
        state = "charging",
        chargingDetails = ChargingDetails(
            pluggedIn = true,
            chargingState = "Charging"
        )
    )

    private val idleStatus = CarStatus(
        displayName = "Test Tesla",
        state = "online",
        chargingDetails = ChargingDetails(
            pluggedIn = false,
            chargingState = null
        )
    )

    private val activeDetail = ChargeDetail(
        chargeId = 42,
        isCharging = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        sessionStore = mockk()
        coEvery { sessionStore.wasLastSessionDc(any()) } returns false
        coEvery { sessionStore.setLastSessionDc(any(), any()) } returns Unit
        coEvery { sessionStore.clear(any()) } returns Unit
    }

    @After
    fun teardown() {
        vm?.viewModelScope?.coroutineContext?.cancelChildren()
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun viewModel() = CurrentChargeViewModel(repository, sessionStore).also { vm = it }

    /**
     * Cancel the ViewModel's endless refresh loop. Must run inside the test body:
     * runTest's cleanup advances the scheduler until idle, and a still-armed
     * periodic loop never goes idle (it OOMs accumulating mock recordings).
     */
    private fun stopRefreshLoop() {
        vm?.viewModelScope?.coroutineContext?.cancelChildren()
    }

    private fun statusResult(status: CarStatus) =
        ApiResult.Success(CarStatusWithUnits(status, Units()))

    @Test
    fun `charge starting - no active charge but status says charging shows waiting state`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getCurrentCharge(1) } returns ApiResult.Success(CurrentChargeOutcome.NoActiveCharge)
        coEvery { repository.getCarStatus(1) } returns statusResult(chargingStatus)

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()

        val state = vm.uiState.value
        assertTrue("expected isChargeStarting", state.isChargeStarting)
        assertFalse("must not bounce out", state.isNotCharging)
        assertFalse(state.isLoading)
        stopRefreshLoop()
    }

    @Test
    fun `charge starting - transitions to live data when the charge materializes`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getCurrentCharge(1) } returns ApiResult.Success(CurrentChargeOutcome.NoActiveCharge)
        coEvery { repository.getCarStatus(1) } returns statusResult(chargingStatus)

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()
        assertTrue(vm.uiState.value.isChargeStarting)

        // Charge appears in the API; the fast poll (4s) should pick it up well before 30s
        coEvery { repository.getCurrentCharge(1) } returns ApiResult.Success(CurrentChargeOutcome.Active(activeDetail))
        advanceTimeBy(5_000)
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.isChargeStarting)
        assertNotNull(state.chargeDetail)
        assertEquals(42, state.chargeDetail?.chargeId)
        stopRefreshLoop()
    }

    @Test
    fun `not charging - no active charge and status agrees bounces out`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getCurrentCharge(1) } returns ApiResult.Success(CurrentChargeOutcome.NoActiveCharge)
        coEvery { repository.getCarStatus(1) } returns statusResult(idleStatus)

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state.isNotCharging)
        assertFalse(state.isChargeStarting)
        stopRefreshLoop()
    }

    @Test
    fun `network error - never interpreted as not charging`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getCurrentCharge(1) } returns ApiResult.Error("Connection failed")
        coEvery { repository.getCarStatus(1) } returns ApiResult.Error("Connection failed")

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()

        val state = vm.uiState.value
        assertFalse("network error must not bounce the user out", state.isNotCharging)
        assertEquals("Connection failed", state.error)
        stopRefreshLoop()
    }

    @Test
    fun `status fetch failure with no active charge keeps polling without bouncing`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getCurrentCharge(1) } returns ApiResult.Success(CurrentChargeOutcome.NoActiveCharge)
        coEvery { repository.getCarStatus(1) } returns ApiResult.Error("Connection failed")

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()

        assertFalse(vm.uiState.value.isNotCharging)
        stopRefreshLoop()
    }

    /** Counts live-charge fetches so the tests below can assert on polling cadence. */
    private fun countFetches(outcome: CurrentChargeOutcome): () -> Int {
        var fetches = 0
        coEvery { repository.getCurrentCharge(1) } coAnswers {
            fetches++
            ApiResult.Success(outcome)
        }
        return { fetches }
    }

    @Test
    fun `charge starting - fast polling gives up after the window and falls back to 30s`() = runTest(testDispatcher.scheduler) {
        val fetches = countFetches(CurrentChargeOutcome.NoActiveCharge)
        coEvery { repository.getCarStatus(1) } returns statusResult(chargingStatus)
        val maxFast = CurrentChargeViewModel.CHARGE_STARTING_MAX_FAST_POLLS

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()
        assertEquals(1, fetches())

        // One fetch every 4 s for the whole window
        advanceTimeBy(4_000L * maxFast)
        runCurrent()
        assertEquals(1 + maxFast, fetches())
        assertTrue("still waiting for the charge", vm.uiState.value.isChargeStarting)

        // Past the window the next fetch is 30 s away, not 4 s
        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals("no fast poll past the window", 1 + maxFast, fetches())
        advanceTimeBy(26_000L)
        runCurrent()
        assertEquals(2 + maxFast, fetches())
        stopRefreshLoop()
    }

    @Test
    fun `pause stops polling and resume restarts it with an immediate fetch`() = runTest(testDispatcher.scheduler) {
        val fetches = countFetches(CurrentChargeOutcome.Active(activeDetail))
        coEvery { repository.getCarStatus(1) } returns statusResult(chargingStatus)

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()
        assertEquals(1, fetches())

        vm.pauseRefresh()
        advanceTimeBy(90_000L)
        runCurrent()
        assertEquals("nothing fetched while paused", 1, fetches())

        vm.resumeRefresh()
        runCurrent()
        assertEquals("resume fetches right away", 2, fetches())
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals("and then keeps the normal cadence", 3, fetches())
        stopRefreshLoop()
    }

    @Test
    fun `resume is a no-op before load and while the loop is already running`() = runTest(testDispatcher.scheduler) {
        val fetches = countFetches(CurrentChargeOutcome.Active(activeDetail))
        coEvery { repository.getCarStatus(1) } returns statusResult(chargingStatus)

        val vm = viewModel()
        vm.resumeRefresh() // the screen's lifecycle effect fires before loadCurrentCharge
        runCurrent()
        assertEquals(0, fetches())

        vm.loadCurrentCharge(1)
        runCurrent()
        assertEquals(1, fetches())

        vm.resumeRefresh()
        runCurrent()
        assertEquals("no second loop", 1, fetches())
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals("one fetch per interval, not two", 2, fetches())
        stopRefreshLoop()
    }

    @Test
    fun `resume after the charge ended does not restart polling`() = runTest(testDispatcher.scheduler) {
        val fetches = countFetches(CurrentChargeOutcome.NoActiveCharge)
        coEvery { repository.getCarStatus(1) } returns statusResult(idleStatus)

        val vm = viewModel()
        vm.loadCurrentCharge(1)
        runCurrent()
        assertTrue(vm.uiState.value.isNotCharging)
        assertEquals(1, fetches())

        vm.pauseRefresh()
        vm.resumeRefresh()
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals("the loop stays finished", 1, fetches())
        stopRefreshLoop()
    }
}
