package com.matedroid.ui.screens.stats

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.StatsRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.sync.SyncLogCollector
import com.matedroid.data.sync.SyncManager
import com.matedroid.domain.model.CarStats
import com.matedroid.domain.model.OverallSyncStatus
import com.matedroid.domain.model.SyncPhase
import com.matedroid.domain.model.SyncProgress
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var statsRepository: StatsRepository
    private lateinit var syncManager: SyncManager
    private lateinit var syncStatus: MutableStateFlow<OverallSyncStatus>
    private var vm: StatsViewModel? = null
    private var hasData = false

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        hasData = false
        syncStatus = MutableStateFlow(OverallSyncStatus.IDLE)

        statsRepository = mockk(relaxed = true)
        coEvery { statsRepository.hasData(CAR) } answers { hasData }
        coEvery { statsRepository.getDeepSyncProgress(CAR) } returns 0f
        coEvery { statsRepository.getAvailableYears(CAR) } returns listOf(2026)
        coEvery { statsRepository.getStats(CAR, any()) } returns mockk<CarStats>(relaxed = true)
        every { statsRepository.observeDeepSyncProgress(CAR) } returns flowOf(0f)
        every { statsRepository.observeGeocodeProgress(CAR) } returns flowOf(null)

        syncManager = mockk(relaxed = true)
        every { syncManager.syncStatus } returns syncStatus
    }

    @After
    fun teardown() {
        vm?.viewModelScope?.coroutineContext?.cancelChildren()
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun TestScope.openScreen(): StatsViewModel {
        val teslamateRepository = mockk<TeslamateRepository>()
        coEvery { teslamateRepository.getCarStatus(CAR) } returns ApiResult.Error("offline")
        val settingsDataStore = mockk<SettingsDataStore>()
        every { settingsDataStore.settings } returns flowOf(AppSettings())

        return StatsViewModel(
            context = mockk<Context>(relaxed = true),
            statsRepository = statsRepository,
            teslamateRepository = teslamateRepository,
            syncManager = syncManager,
            syncLogCollector = mockk<SyncLogCollector>(relaxed = true),
            settingsDataStore = settingsDataStore
        ).also {
            vm = it
            it.setCarId(CAR)
            runCurrent()
        }
    }

    private fun setPhase(carId: Int, phase: SyncPhase, current: Int = 0) {
        val progresses = syncStatus.value.carProgresses + (carId to SyncProgress(carId, phase, current, 100))
        syncStatus.value = OverallSyncStatus(
            carProgresses = progresses,
            isAnySyncing = progresses.values.any { it.phase != SyncPhase.COMPLETE && it.phase != SyncPhase.ERROR },
            allComplete = progresses.values.all { it.phase == SyncPhase.COMPLETE }
        )
    }

    /** Stop the detail-sync ticker so runTest's cleanup can go idle. */
    private fun stopLoops() {
        vm?.viewModelScope?.coroutineContext?.cancelChildren()
    }

    @Test
    fun `stats appear once summaries sync, without waiting for details`() = runTest(testDispatcher) {
        setPhase(CAR, SyncPhase.SYNCING_SUMMARIES)
        val viewModel = openScreen()
        assertNull(viewModel.uiState.value.carStats)

        hasData = true
        setPhase(CAR, SyncPhase.SYNCING_DRIVE_DETAILS)
        runCurrent()

        assertNotNull(viewModel.uiState.value.carStats)
        stopLoops()
    }

    @Test
    fun `a sync that ends in error still reloads`() = runTest(testDispatcher) {
        setPhase(CAR, SyncPhase.SYNCING_CHARGE_DETAILS)
        val viewModel = openScreen()
        hasData = true

        setPhase(CAR, SyncPhase.ERROR)
        runCurrent()

        assertNotNull(viewModel.uiState.value.carStats)
    }

    @Test
    fun `opening after a finished sync loads once`() = runTest(testDispatcher) {
        hasData = true
        setPhase(CAR, SyncPhase.COMPLETE)
        openScreen()

        coVerify(exactly = 1) { statsRepository.getStats(CAR, any()) }
    }

    @Test
    fun `another car's sync progress doesn't reload this car`() = runTest(testDispatcher) {
        hasData = true
        setPhase(CAR, SyncPhase.COMPLETE)
        openScreen()

        setPhase(OTHER_CAR, SyncPhase.SYNCING_DRIVE_DETAILS)
        repeat(50) { item ->
            setPhase(OTHER_CAR, SyncPhase.SYNCING_DRIVE_DETAILS, current = item)
            runCurrent()
        }

        coVerify(exactly = 1) { statsRepository.getStats(CAR, any()) }
    }

    @Test
    fun `deep stats refresh periodically during a detail sync`() = runTest(testDispatcher) {
        hasData = true
        setPhase(CAR, SyncPhase.SYNCING_DRIVE_DETAILS)
        openScreen()
        coVerify(exactly = 1) { statsRepository.getStats(CAR, any()) }

        advanceTimeBy(30_001)
        coVerify(exactly = 2) { statsRepository.getStats(CAR, any()) }

        setPhase(CAR, SyncPhase.COMPLETE)
        runCurrent()
        coVerify(exactly = 3) { statsRepository.getStats(CAR, any()) }

        // Ticker stops once the sync is over
        advanceTimeBy(120_000)
        coVerify(exactly = 3) { statsRepository.getStats(CAR, any()) }
        stopLoops()
    }

    private companion object {
        const val CAR = 1
        const val OTHER_CAR = 2
    }
}
