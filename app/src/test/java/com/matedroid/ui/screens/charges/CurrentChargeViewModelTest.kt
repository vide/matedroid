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
}
