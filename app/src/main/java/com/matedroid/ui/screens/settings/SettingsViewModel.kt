package com.matedroid.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.matedroid.R
import com.matedroid.data.demo.DemoMode
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.local.TirePosition
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.domain.ConnectionTimeout
import com.matedroid.domain.CostPerKwhBasis
import com.matedroid.domain.HighSocWarning
import com.matedroid.domain.LowSocWarning
import com.matedroid.domain.ShortEntryFilter
import com.matedroid.data.repository.SentryStateRepository
import com.matedroid.data.repository.TpmsStateRepository
import com.matedroid.notification.SentryNotificationManager
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.data.sync.SyncManager
import com.matedroid.data.sync.TpmsPressureWorker
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
    val httpBasicAuthUsername: String = "",
    val httpBasicAuthPassword: String = "",
    val acceptInvalidCerts: Boolean = false,
    val connectTimeoutSeconds: Int = ConnectionTimeout.AUTO,
    val currencyCode: String = "EUR",
    val costPerKwhBasis: CostPerKwhBasis = CostPerKwhBasis.DEFAULT,
    val showShortDrivesCharges: Boolean = false,
    val shortDriveMinDurationMin: Int = ShortEntryFilter.DEFAULT_MIN_DRIVE_DURATION_MIN,
    val shortDriveMinDistance: Double = ShortEntryFilter.DEFAULT_MIN_DRIVE_DISTANCE,
    val shortChargeMinEnergyKwh: Double = ShortEntryFilter.DEFAULT_MIN_CHARGE_ENERGY_KWH,
    val highSocWarningThreshold: Int = HighSocWarning.DEFAULT_THRESHOLD,
    val lowSocWarningThreshold: Int = LowSocWarning.DEFAULT_THRESHOLD,
    val isDemoMode: Boolean = false,
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
    private val syncManager: SyncManager,
    private val tpmsStateRepository: TpmsStateRepository,
    private val sentryStateRepository: SentryStateRepository,
    private val sentryNotificationManager: SentryNotificationManager
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
                httpBasicAuthUsername = settings.httpBasicAuthUsername,
                httpBasicAuthPassword = settings.httpBasicAuthPassword,
                acceptInvalidCerts = settings.acceptInvalidCerts,
                connectTimeoutSeconds = settings.connectTimeoutSeconds,
                currencyCode = settings.currencyCode,
                costPerKwhBasis = settings.costPerKwhBasis,
                showShortDrivesCharges = settings.showShortDrivesCharges,
                shortDriveMinDurationMin = settings.shortDriveMinDurationMin,
                shortDriveMinDistance = settings.shortDriveMinDistance,
                shortChargeMinEnergyKwh = settings.shortChargeMinEnergyKwh,
                highSocWarningThreshold = settings.highSocWarningThreshold,
                lowSocWarningThreshold = settings.lowSocWarningThreshold,
                isDemoMode = settings.isDemoMode,
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

    fun updateHttpBasicAuthUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            httpBasicAuthUsername = username,
            testResult = null,
            error = null
        )
        // Save eagerly so testConnection() picks up the unsaved value
        viewModelScope.launch {
            settingsDataStore.saveHttpBasicAuth(username, _uiState.value.httpBasicAuthPassword)
        }
    }

    fun updateHttpBasicAuthPassword(password: String) {
        _uiState.value = _uiState.value.copy(
            httpBasicAuthPassword = password,
            testResult = null,
            error = null
        )
        // Save eagerly so testConnection() picks up the unsaved value
        viewModelScope.launch {
            settingsDataStore.saveHttpBasicAuth(_uiState.value.httpBasicAuthUsername, password)
        }
    }

    fun updateAcceptInvalidCerts(accept: Boolean) {
        _uiState.value = _uiState.value.copy(
            acceptInvalidCerts = accept,
            testResult = null,
            error = null
        )
    }

    /**
     * Saved eagerly, unlike the rest of the connection form: Test Connection builds its client
     * from the stored settings, so the timeout has to be on disk for the test to exercise it.
     */
    fun updateConnectTimeoutSeconds(seconds: Int) {
        _uiState.value = _uiState.value.copy(
            connectTimeoutSeconds = seconds,
            testResult = null,
            error = null
        )
        viewModelScope.launch {
            settingsDataStore.saveConnectTimeoutSeconds(seconds)
        }
    }

    fun updateCurrency(currencyCode: String) {
        _uiState.value = _uiState.value.copy(currencyCode = currencyCode)
        viewModelScope.launch {
            settingsDataStore.saveCurrency(currencyCode)
        }
    }

    /**
     * The process-wide mirror is updated synchronously so per-kWh figures pick up the new
     * basis on the way back from Settings rather than on the next app start.
     */
    fun updateCostPerKwhBasis(basis: CostPerKwhBasis) {
        _uiState.value = _uiState.value.copy(costPerKwhBasis = basis)
        CostPerKwhBasis.current = basis
        viewModelScope.launch {
            settingsDataStore.saveCostPerKwhBasis(basis)
        }
    }

    fun updateShowShortDrivesCharges(show: Boolean) {
        _uiState.value = _uiState.value.copy(showShortDrivesCharges = show)
        viewModelScope.launch {
            settingsDataStore.saveShowShortDrivesCharges(show)
        }
    }

    fun updateShortDriveMinDuration(minutes: Int) {
        _uiState.value = _uiState.value.copy(shortDriveMinDurationMin = minutes)
        persistShortEntryThresholds()
    }

    fun updateShortDriveMinDistance(distance: Double) {
        _uiState.value = _uiState.value.copy(shortDriveMinDistance = distance)
        persistShortEntryThresholds()
    }

    fun updateShortChargeMinEnergy(energyKwh: Double) {
        _uiState.value = _uiState.value.copy(shortChargeMinEnergyKwh = energyKwh)
        persistShortEntryThresholds()
    }

    fun updateHighSocWarningThreshold(threshold: Int) {
        _uiState.value = _uiState.value.copy(highSocWarningThreshold = threshold)
        viewModelScope.launch {
            settingsDataStore.saveHighSocWarningThreshold(threshold)
        }
    }

    fun updateLowSocWarningThreshold(threshold: Int) {
        _uiState.value = _uiState.value.copy(lowSocWarningThreshold = threshold)
        viewModelScope.launch {
            settingsDataStore.saveLowSocWarningThreshold(threshold)
        }
    }

    /**
     * Writes the thresholds to disk and to the [ShortEntryFilter] mirror the lists read from.
     * The mirror is updated synchronously so a list re-filters on the way back from Settings
     * rather than on the next app start.
     */
    private fun persistShortEntryThresholds() {
        val state = _uiState.value
        ShortEntryFilter.minDriveDurationMin = state.shortDriveMinDurationMin
        ShortEntryFilter.minDriveDistance = state.shortDriveMinDistance
        ShortEntryFilter.minChargeEnergyKwh = state.shortChargeMinEnergyKwh
        viewModelScope.launch {
            settingsDataStore.saveShortEntryThresholds(
                driveMinDurationMin = state.shortDriveMinDurationMin,
                driveMinDistance = state.shortDriveMinDistance,
                chargeMinEnergyKwh = state.shortChargeMinEnergyKwh
            )
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
                        primaryResult = ServerTestResult.Failure(context.getString(R.string.settings_error_server_url_required))
                    )
                )
                return@launch
            }

            if (!primaryUrl.startsWith("http://") && !primaryUrl.startsWith("https://")) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = TestResult(
                        primaryResult = ServerTestResult.Failure(context.getString(R.string.settings_error_url_scheme))
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
                        primaryResult = ServerTestResult.Failure(context.getString(R.string.settings_error_primary_not_tested)),
                        secondaryResult = ServerTestResult.Failure(context.getString(R.string.settings_error_secondary_url_scheme))
                    )
                )
                return@launch
            }

            // Test what the form says, not what is on disk: neither the timeout picker's
            // "Automatic" resolution nor the secondary URL it depends on are saved yet.
            val timeoutSeconds = ConnectionTimeout.resolveSeconds(
                setting = _uiState.value.connectTimeoutSeconds,
                hasFallbackServer = secondaryUrl.isNotBlank()
            )

            // Test primary server
            val primaryResult = when (
                val result = repository.testConnection(
                    primaryUrl,
                    _uiState.value.acceptInvalidCerts,
                    timeoutSeconds
                )
            ) {
                is ApiResult.Success -> ServerTestResult.Success
                is ApiResult.Error -> ServerTestResult.Failure(result.message)
            }

            // Test secondary server if configured
            val secondaryResult = if (secondaryUrl.isNotBlank()) {
                when (
                    val result = repository.testConnection(
                        secondaryUrl,
                        _uiState.value.acceptInvalidCerts,
                        timeoutSeconds
                    )
                ) {
                    is ApiResult.Success -> ServerTestResult.Success
                    is ApiResult.Error -> ServerTestResult.Failure(result.message)
                }
            } else {
                null
            }

            // If primary connection succeeded, fetch and cache global settings
            if (primaryResult is ServerTestResult.Success) {
                fetchAndCacheGlobalSettings()
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

    /**
     * Fetches global settings from the API and caches the base_url.
     * This runs silently - failures don't affect the user experience.
     */
    private suspend fun fetchAndCacheGlobalSettings() {
        when (val result = repository.getGlobalSettings()) {
            is ApiResult.Success -> {
                result.data.settings?.teslamateUrls?.baseUrl?.let { url ->
                    settingsDataStore.saveTeslamateBaseUrl(url.trimEnd('/'))
                }
            }
            is ApiResult.Error -> {
                // Silent fail - this is optional functionality
                // Older Teslamate API versions may not have this endpoint
            }
        }
    }

    /**
     * Switch to the built-in sample dataset and go straight to the dashboard.
     *
     * Offered only from first-run onboarding, so there is no configured server to trample
     * and no cached data to clear on the way in — the reset below is there for the case
     * where someone reaches this from a half-finished setup.
     */
    fun enterDemoMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                syncManager.fullResetSync(DemoMode.CAR_ID)
                settingsDataStore.enterDemoMode()
                loadSettings()
                triggerImmediateSync()
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: context.getString(R.string.settings_error_save_failed)
                )
            }
        }
    }

    /**
     * Leave the demo and return to onboarding.
     *
     * The sample drives and charges are deleted rather than left in place: they are keyed by
     * the same car id a real TeslaMate would use, so anything left behind would be merged
     * into the real car's history the moment a server is configured.
     */
    fun exitDemoMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                syncManager.fullResetSync(DemoMode.CAR_ID)
                settingsDataStore.exitDemoMode()
                loadSettings()
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: context.getString(R.string.settings_error_save_failed)
                )
            }
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
                        error = context.getString(R.string.settings_error_server_url_required)
                    )
                    return@launch
                }

                val secondaryUrl = _uiState.value.secondaryServerUrl.trimEnd('/')

                settingsDataStore.saveSettings(
                    serverUrl = url,
                    secondaryServerUrl = secondaryUrl,
                    apiToken = _uiState.value.apiToken,
                    httpBasicAuthUsername = _uiState.value.httpBasicAuthUsername,
                    httpBasicAuthPassword = _uiState.value.httpBasicAuthPassword,
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
                    error = e.message ?: context.getString(R.string.settings_error_save_failed)
                )
            }
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    /** Surfaces a snackbar from the UI layer (e.g. the "saved" confirmation). */
    fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(successMessage = message)
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
                            successMessage = context.getString(R.string.settings_resync_started)
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isResyncing = false,
                            error = context.getString(R.string.settings_error_resync_failed, result.message)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResyncing = false,
                    error = context.getString(R.string.settings_error_resync_failed, e.message ?: "")
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

    // ==================== Debug Functions ====================

    /**
     * Simulate a TPMS warning for testing purposes.
     * Shows a test notification immediately.
     * Only use in debug builds.
     */
    fun simulateTpmsWarning(tire: TirePosition) {
        viewModelScope.launch {
            // Save state for consistency
            tpmsStateRepository.simulateWarning(1, tire)

            // Show notification immediately for testing
            createNotificationChannel()
            val tireName = getTireFullName(tire)
            showTpmsNotification(
                title = context.getString(R.string.tpms_notification_title),
                body = context.getString(R.string.tpms_notification_body, "Test Car", tireName)
            )

            _uiState.value = _uiState.value.copy(
                successMessage = "Simulated TPMS warning for ${tire.name}"
            )
        }
    }

    /**
     * Clear the TPMS warning state for testing purposes.
     * Shows a "cleared" notification immediately.
     * Only use in debug builds.
     */
    fun clearTpmsWarning() {
        viewModelScope.launch {
            // Clear state
            tpmsStateRepository.clearWarning(1)

            // Show notification immediately for testing
            createNotificationChannel()
            showTpmsNotification(
                title = context.getString(R.string.tpms_notification_title),
                body = context.getString(R.string.tpms_notification_cleared, "Test Car")
            )

            _uiState.value = _uiState.value.copy(
                successMessage = "TPMS state cleared"
            )
        }
    }

    /**
     * Simulate a sentry alert event for testing purposes.
     * Increments the event counter and fires a notification immediately.
     * Only use in debug builds.
     */
    fun simulateSentryEvent() {
        viewModelScope.launch {
            val carId = 1
            // Fetch current position so simulated events get an address
            val statusResult = repository.getCarStatus(carId)
            val status = (statusResult as? ApiResult.Success)?.data?.status
            val count = sentryStateRepository.forceIncrementEventCount(
                carId,
                latitude = status?.latitude,
                longitude = status?.longitude,
                geofence = status?.geofence
            )

            sentryNotificationManager.showSentryAlert(
                carName = "Test Car",
                carId = carId,
                eventCount = count
            )

            _uiState.value = _uiState.value.copy(
                successMessage = "Simulated sentry event #$count"
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TpmsPressureWorker.CHANNEL_ID,
                context.getString(R.string.tpms_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.tpms_channel_description)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showTpmsNotification(title: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val notification = NotificationCompat.Builder(context, TpmsPressureWorker.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }

    private fun getTireFullName(tire: TirePosition): String {
        return when (tire) {
            TirePosition.FL -> context.getString(R.string.tire_fl_full)
            TirePosition.FR -> context.getString(R.string.tire_fr_full)
            TirePosition.RL -> context.getString(R.string.tire_rl_full)
            TirePosition.RR -> context.getString(R.string.tire_rr_full)
        }
    }

    /**
     * Run TPMS check immediately (for debugging).
     */
    fun runTpmsCheckNow() {
        TpmsPressureWorker.runNow(context)
        _uiState.value = _uiState.value.copy(
            successMessage = "TPMS check triggered - check logcat for TpmsPressureWorker"
        )
    }
}
