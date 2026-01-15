package com.matedroid.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.data.sync.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val secondaryServerUrl: String = "",
    val apiToken: String = "",
    val acceptInvalidCerts: Boolean = false,
    val currencyCode: String = "EUR",
    val showShortDrivesCharges: Boolean = false,
    val teslamateBaseUrl: String = "",
    val isLoading: Boolean = true,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val isResyncing: Boolean = false,
    val testResult: TestResult? = null,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * Represents the result of testing a single server connection.
 */
sealed class ServerTestResult {
    data object Success : ServerTestResult()
    data class Failure(val message: String) : ServerTestResult()
}

/**
 * Represents the combined results of testing primary and optionally secondary server connections.
 */
data class TestResult(
    val primaryResult: ServerTestResult,
    val secondaryResult: ServerTestResult? = null // null if no secondary URL configured
) {
    val isFullySuccessful: Boolean
        get() = primaryResult is ServerTestResult.Success &&
                (secondaryResult == null || secondaryResult is ServerTestResult.Success)

    val hasAnySuccess: Boolean
        get() = primaryResult is ServerTestResult.Success ||
                secondaryResult is ServerTestResult.Success
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val repository: TeslamateRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            _uiState.value = _uiState.value.copy(
                serverUrl = settings.serverUrl,
                secondaryServerUrl = settings.secondaryServerUrl,
                apiToken = settings.apiToken,
                acceptInvalidCerts = settings.acceptInvalidCerts,
                currencyCode = settings.currencyCode,
                showShortDrivesCharges = settings.showShortDrivesCharges,
                teslamateBaseUrl = settings.teslamateBaseUrl,
                isLoading = false
            )
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            testResult = null,
            error = null
        )
    }

    fun updateSecondaryServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            secondaryServerUrl = url,
            testResult = null,
            error = null
        )
    }

    fun updateApiToken(token: String) {
        _uiState.value = _uiState.value.copy(
            apiToken = token,
            testResult = null,
            error = null
        )
    }

    fun updateAcceptInvalidCerts(accept: Boolean) {
        _uiState.value = _uiState.value.copy(
            acceptInvalidCerts = accept,
            testResult = null,
            error = null
        )
    }

    fun updateCurrency(currencyCode: String) {
        _uiState.value = _uiState.value.copy(currencyCode = currencyCode)
        viewModelScope.launch {
            settingsDataStore.saveCurrency(currencyCode)
        }
    }

    fun updateShowShortDrivesCharges(show: Boolean) {
        _uiState.value = _uiState.value.copy(showShortDrivesCharges = show)
        viewModelScope.launch {
            settingsDataStore.saveShowShortDrivesCharges(show)
        }
    }

    fun updateTeslamateBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(teslamateBaseUrl = url)
        viewModelScope.launch {
            settingsDataStore.saveTeslamateBaseUrl(url.trimEnd('/'))
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null, error = null)

            val primaryUrl = _uiState.value.serverUrl.trimEnd('/')
            val secondaryUrl = _uiState.value.secondaryServerUrl.trimEnd('/')

            // Validate primary URL
            if (primaryUrl.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = TestResult(
                        primaryResult = ServerTestResult.Failure("Server URL is required")
                    )
                )
                return@launch
            }

            if (!primaryUrl.startsWith("http://") && !primaryUrl.startsWith("https://")) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = TestResult(
                        primaryResult = ServerTestResult.Failure("URL must start with http:// or https://")
                    )
                )
                return@launch
            }

            // Validate secondary URL format if provided
            if (secondaryUrl.isNotBlank() &&
                !secondaryUrl.startsWith("http://") && !secondaryUrl.startsWith("https://")) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = TestResult(
                        primaryResult = ServerTestResult.Failure("Primary URL not tested"),
                        secondaryResult = ServerTestResult.Failure("Secondary URL must start with http:// or https://")
                    )
                )
                return@launch
            }

            // Test primary server
            val primaryResult = when (val result = repository.testConnection(primaryUrl, _uiState.value.acceptInvalidCerts)) {
                is ApiResult.Success -> ServerTestResult.Success
                is ApiResult.Error -> ServerTestResult.Failure(result.message)
            }

            // Test secondary server if configured
            val secondaryResult = if (secondaryUrl.isNotBlank()) {
                when (val result = repository.testConnection(secondaryUrl, _uiState.value.acceptInvalidCerts)) {
                    is ApiResult.Success -> ServerTestResult.Success
                    is ApiResult.Error -> ServerTestResult.Failure(result.message)
                }
            } else {
                null
            }

            _uiState.value = _uiState.value.copy(
                isTesting = false,
                testResult = TestResult(
                    primaryResult = primaryResult,
                    secondaryResult = secondaryResult
                )
            )
        }
    }

    fun saveSettings(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val url = _uiState.value.serverUrl.trimEnd('/')
                if (url.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Server URL is required"
                    )
                    return@launch
                }

                val secondaryUrl = _uiState.value.secondaryServerUrl.trimEnd('/')

                settingsDataStore.saveSettings(
                    serverUrl = url,
                    secondaryServerUrl = secondaryUrl,
                    apiToken = _uiState.value.apiToken,
                    acceptInvalidCerts = _uiState.value.acceptInvalidCerts,
                    currencyCode = _uiState.value.currencyCode
                )

                // Trigger sync after settings are saved (handles first-time setup)
                triggerImmediateSync()

                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save settings"
                )
            }
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun forceResync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResyncing = true, error = null)
            try {
                // Get all cars and do a full reset (delete all cached data) for each
                when (val result = repository.getCars()) {
                    is ApiResult.Success -> {
                        for (car in result.data) {
                            syncManager.fullResetSync(car.carId)
                        }
                        // Trigger immediate sync via WorkManager
                        triggerImmediateSync()
                        _uiState.value = _uiState.value.copy(
                            isResyncing = false,
                            successMessage = "Full resync started. All cached data cleared. Check the Stats screen for progress."
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isResyncing = false,
                            error = "Failed to start resync: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResyncing = false,
                    error = "Failed to start resync: ${e.message}"
                )
            }
        }
    }

    private fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(DataSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DataSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
