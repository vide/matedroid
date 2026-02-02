package com.matedroid.ui.screens.dashboard

import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.CarStatusDetails
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.Units
import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.CarStatusWithUnits
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.TeslamateRepository
import kotlinx.coroutines.flow.flowOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TeslamateRepository
    private lateinit var geocodingRepository: GeocodingRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private var viewModel: DashboardViewModel? = null

    private val testCar = CarData(
        carId = 1,
        name = "Test Tesla"
    )

    private val testStatus = CarStatus(
        displayName = "Test Tesla",
        state = "online",
        batteryDetails = BatteryDetails(
            batteryLevel = 75,
            ratedBatteryRange = 300.0
        ),
        chargingDetails = ChargingDetails(
            pluggedIn = false,
            chargeLimitSoc = 80
        ),
        carStatus = CarStatusDetails(locked = true)
    )

    private val testStatusWithUnits = CarStatusWithUnits(
        status = testStatus,
        units = Units()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        geocodingRepository = mockk()
        settingsDataStore = mockk()
        // Default: no previously selected car
        every { settingsDataStore.settings } returns flowOf(AppSettings())
        every { settingsDataStore.carImageOverrides } returns flowOf(emptyMap())
        coEvery { settingsDataStore.saveLastSelectedCarId(any()) } returns Unit
    }

    @After
    fun tearDown() {
        cancelViewModelCoroutines()
        viewModel = null
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): DashboardViewModel {
        return DashboardViewModel(repository, geocodingRepository, settingsDataStore)
    }

    /**
     * Helper to cancel ViewModel coroutines before test assertions.
     * This prevents the auto-refresh infinite loop from running forever.
     */
    private fun cancelViewModelCoroutines() {
        viewModel?.viewModelScope?.coroutineContext?.cancelChildren()
    }

    @Test
    fun `loadCars fetches cars and selects first one`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Success(listOf(testCar))
        coEvery { repository.getCarStatus(1) } returns ApiResult.Success(testStatusWithUnits)

        viewModel = createViewModel()
        // Run only currently scheduled coroutines, not future delayed ones
        runCurrent()

        // Cancel auto-refresh before assertions
        cancelViewModelCoroutines()

        assertFalse(viewModel!!.uiState.value.isLoading)
        assertEquals(1, viewModel!!.uiState.value.cars.size)
        assertEquals(1, viewModel!!.uiState.value.selectedCarId)
        assertEquals(testStatus, viewModel!!.uiState.value.carStatus)
    }

    @Test
    fun `loadCars shows error when api fails`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Error("Network error")

        viewModel = createViewModel()
        runCurrent()
        cancelViewModelCoroutines()

        assertFalse(viewModel!!.uiState.value.isLoading)
        assertEquals("Network error", viewModel!!.uiState.value.error)
        assertTrue(viewModel!!.uiState.value.cars.isEmpty())
    }

    @Test
    fun `loadCars handles empty car list`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Success(emptyList())

        viewModel = createViewModel()
        runCurrent()
        cancelViewModelCoroutines()

        assertFalse(viewModel!!.uiState.value.isLoading)
        assertTrue(viewModel!!.uiState.value.cars.isEmpty())
        assertNull(viewModel!!.uiState.value.selectedCarId)
    }

    @Test
    fun `selectCar updates selected car and loads status`() = runTest {
        val car1 = CarData(carId = 1, name = "Car 1")
        val car2 = CarData(carId = 2, name = "Car 2")
        val status2 = testStatus.copy(displayName = "Car 2")
        val status2WithUnits = CarStatusWithUnits(status = status2, units = Units())

        coEvery { repository.getCars() } returns ApiResult.Success(listOf(car1, car2))
        coEvery { repository.getCarStatus(1) } returns ApiResult.Success(testStatusWithUnits)
        coEvery { repository.getCarStatus(2) } returns ApiResult.Success(status2WithUnits)

        viewModel = createViewModel()
        runCurrent()

        assertEquals(1, viewModel!!.uiState.value.selectedCarId)

        viewModel!!.selectCar(2)
        runCurrent()

        // Cancel auto-refresh before assertions
        cancelViewModelCoroutines()

        assertEquals(2, viewModel!!.uiState.value.selectedCarId)
        assertEquals("Car 2", viewModel!!.uiState.value.carStatus?.displayName)
    }

    @Test
    fun `refresh reloads car status`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Success(listOf(testCar))
        coEvery { repository.getCarStatus(1) } returns ApiResult.Success(testStatusWithUnits)

        viewModel = createViewModel()
        runCurrent()

        val updatedStatus = testStatus.copy(
            batteryDetails = BatteryDetails(batteryLevel = 80, ratedBatteryRange = 300.0)
        )
        val updatedStatusWithUnits = CarStatusWithUnits(status = updatedStatus, units = Units())
        coEvery { repository.getCarStatus(1) } returns ApiResult.Success(updatedStatusWithUnits)

        viewModel!!.refresh()
        runCurrent()

        // Cancel auto-refresh before assertions
        cancelViewModelCoroutines()

        assertFalse(viewModel!!.uiState.value.isRefreshing)
        assertEquals(80, viewModel!!.uiState.value.carStatus?.batteryLevel)

        // Using atLeast = 2 because the auto-refresh mechanism may trigger
        // additional getCarStatus calls before cancelViewModelCoroutines() runs.
        coVerify(atLeast = 2) { repository.getCarStatus(1) }
    }

    @Test
    fun `refresh does nothing when no car selected`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Success(emptyList())

        viewModel = createViewModel()
        runCurrent()

        viewModel!!.refresh()
        runCurrent()

        cancelViewModelCoroutines()

        coVerify(exactly = 0) { repository.getCarStatus(any()) }
    }

    @Test
    fun `clearError clears error state`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Error("Test error")

        viewModel = createViewModel()
        runCurrent()
        cancelViewModelCoroutines()

        assertEquals("Test error", viewModel!!.uiState.value.error)

        viewModel!!.clearError()

        assertNull(viewModel!!.uiState.value.error)
    }

    @Test
    fun `status error is shown when status fetch fails`() = runTest {
        coEvery { repository.getCars() } returns ApiResult.Success(listOf(testCar))
        coEvery { repository.getCarStatus(1) } returns ApiResult.Error("Status error")

        viewModel = createViewModel()
        runCurrent()
        cancelViewModelCoroutines()

        assertEquals("Status error", viewModel!!.uiState.value.error)
        assertNull(viewModel!!.uiState.value.carStatus)
    }
}
