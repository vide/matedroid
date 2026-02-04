package com.matedroid.ui.screens.settings

import android.content.Context
import androidx.work.WorkManager
import com.matedroid.data.api.models.GlobalSettings
import com.matedroid.data.api.models.GlobalSettingsData
import com.matedroid.data.api.models.TeslamateUrls
import com.matedroid.data.local.AppSettings
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.repository.TpmsStateRepository
import com.matedroid.data.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: TeslamateRepository
    private lateinit var syncManager: SyncManager
    private lateinit var tpmsStateRepository: TpmsStateRepository
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        settingsDataStore = mockk()
        repository = mockk()
        syncManager = mockk()
        tpmsStateRepository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)

        every { settingsDataStore.settings } returns flowOf(AppSettings())

        // Mock WorkManager.getInstance()
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(context, settingsDataStore, repository, syncManager, tpmsStateRepository)
    }

    @Test
    fun `initial state loads settings from datastore`() = runTest {
        val savedSettings = AppSettings(
            serverUrl = "https://test.com",
            secondaryServerUrl = "https://backup.test.com",
            apiToken = "token123",
            acceptInvalidCerts = true
        )
        every { settingsDataStore.settings } returns flowOf(savedSettings)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://test.com", viewModel.uiState.value.serverUrl)
        assertEquals("https://backup.test.com", viewModel.uiState.value.secondaryServerUrl)
        assertEquals("token123", viewModel.uiState.value.apiToken)
        assertTrue(viewModel.uiState.value.acceptInvalidCerts)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `updateServerUrl updates state`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://new-server.com")

        assertEquals("https://new-server.com", viewModel.uiState.value.serverUrl)
    }

    @Test
    fun `updateSecondaryServerUrl updates state`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateSecondaryServerUrl("https://secondary.com")

        assertEquals("https://secondary.com", viewModel.uiState.value.secondaryServerUrl)
    }

    @Test
    fun `updateApiToken updates state`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateApiToken("new-token")

        assertEquals("new-token", viewModel.uiState.value.apiToken)
    }

    @Test
    fun `testConnection fails with blank url`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertTrue(result!!.primaryResult is ServerTestResult.Failure)
        assertEquals("Server URL is required", (result.primaryResult as ServerTestResult.Failure).message)
    }

    @Test
    fun `testConnection fails with invalid url scheme`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("ftp://invalid.com")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertTrue(result!!.primaryResult is ServerTestResult.Failure)
        assertEquals("URL must start with http:// or https://", (result.primaryResult as ServerTestResult.Failure).message)
    }

    @Test
    fun `testConnection succeeds with valid url`() = runTest {
        coEvery { repository.testConnection(any(), any()) } returns ApiResult.Success(Unit)
        // Mock global settings fetch (called after successful connection)
        coEvery { repository.getGlobalSettings() } returns ApiResult.Success(GlobalSettingsData(settings = GlobalSettings(teslamateUrls = TeslamateUrls(baseUrl = "https://teslamate.example.com"))))
        coEvery { settingsDataStore.saveTeslamateBaseUrl(any()) } returns Unit

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://valid.com")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertTrue(result!!.primaryResult is ServerTestResult.Success)
        assertNull(result.secondaryResult) // No secondary URL configured
    }

    @Test
    fun `testConnection tests both servers when secondary is configured`() = runTest {
        coEvery { repository.testConnection("https://primary.com", any()) } returns ApiResult.Success(Unit)
        coEvery { repository.testConnection("https://secondary.com", any()) } returns ApiResult.Success(Unit)
        // Mock global settings fetch (called after successful connection)
        coEvery { repository.getGlobalSettings() } returns ApiResult.Success(GlobalSettingsData(settings = GlobalSettings(teslamateUrls = TeslamateUrls(baseUrl = "https://teslamate.example.com"))))
        coEvery { settingsDataStore.saveTeslamateBaseUrl(any()) } returns Unit

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://primary.com")
        viewModel.updateSecondaryServerUrl("https://secondary.com")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertTrue(result!!.primaryResult is ServerTestResult.Success)
        assertNotNull(result.secondaryResult)
        assertTrue(result.secondaryResult is ServerTestResult.Success)
    }

    @Test
    fun `testConnection shows both results when primary fails and secondary succeeds`() = runTest {
        coEvery { repository.testConnection("https://primary.com", any()) } returns ApiResult.Error("Connection refused")
        coEvery { repository.testConnection("https://secondary.com", any()) } returns ApiResult.Success(Unit)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://primary.com")
        viewModel.updateSecondaryServerUrl("https://secondary.com")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertTrue(result!!.primaryResult is ServerTestResult.Failure)
        assertEquals("Connection refused", (result.primaryResult as ServerTestResult.Failure).message)
        assertNotNull(result.secondaryResult)
        assertTrue(result.secondaryResult is ServerTestResult.Success)
        assertTrue(result.hasAnySuccess)
        assertFalse(result.isFullySuccessful)
    }

    @Test
    fun `testConnection shows failure when api returns error`() = runTest {
        coEvery { repository.testConnection(any(), any()) } returns ApiResult.Error("Connection refused")

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://unreachable.com")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertTrue(result!!.primaryResult is ServerTestResult.Failure)
        assertEquals("Connection refused", (result.primaryResult as ServerTestResult.Failure).message)
    }

    @Test
    fun `saveSettings calls datastore with all fields and triggers callback`() = runTest {
        coEvery { settingsDataStore.saveSettings(any(), any(), any(), any(), any()) } returns Unit

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://saved.com")
        viewModel.updateSecondaryServerUrl("https://backup.com")
        viewModel.updateApiToken("saved-token")
        viewModel.updateAcceptInvalidCerts(true)

        var callbackCalled = false
        viewModel.saveSettings { callbackCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            settingsDataStore.saveSettings(
                "https://saved.com",
                "https://backup.com",
                "saved-token",
                true,
                "EUR"
            )
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun `updateAcceptInvalidCerts updates state`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.acceptInvalidCerts)

        viewModel.updateAcceptInvalidCerts(true)

        assertTrue(viewModel.uiState.value.acceptInvalidCerts)
    }

    @Test
    fun `saveSettings fails with blank url`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("")

        var callbackCalled = false
        viewModel.saveSettings { callbackCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackCalled)
        assertEquals("Server URL is required", viewModel.uiState.value.error)
    }

    @Test
    fun `clearTestResult clears test result`() = runTest {
        coEvery { repository.testConnection(any(), any()) } returns ApiResult.Success(Unit)
        // Mock global settings fetch (called after successful connection)
        coEvery { repository.getGlobalSettings() } returns ApiResult.Success(GlobalSettingsData(settings = GlobalSettings(teslamateUrls = TeslamateUrls(baseUrl = "https://teslamate.example.com"))))
        coEvery { settingsDataStore.saveTeslamateBaseUrl(any()) } returns Unit

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateServerUrl("https://test.com")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.testResult)
        assertTrue(viewModel.uiState.value.testResult!!.primaryResult is ServerTestResult.Success)

        viewModel.clearTestResult()

        assertNull(viewModel.uiState.value.testResult)
    }
}
