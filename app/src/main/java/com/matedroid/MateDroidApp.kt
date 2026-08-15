package com.matedroid

import android.app.Application
import android.util.Log
import java.io.File
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.sync.ChargingNotificationWorker
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.data.sync.TpmsPressureWorker
import com.matedroid.domain.AppTimeZone
import com.matedroid.domain.ShortEntryFilter
import com.matedroid.domain.UnitSystem
import com.matedroid.notification.SentryNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class MateDroidApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sentryNotificationManager: SentryNotificationManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Restore the last known unit system before any trip detection / filtering runs.
        appScope.launch {
            UnitSystem.isImperial = settingsDataStore.isImperial.first()
        }

        // Restore the display preferences that live in process-wide mirrors, before any
        // list filtering or date formatting runs.
        appScope.launch {
            val settings = settingsDataStore.settings.first()
            ShortEntryFilter.minDriveDurationMin = settings.shortDriveMinDurationMin
            ShortEntryFilter.minDriveDistance = settings.shortDriveMinDistance
            ShortEntryFilter.minChargeEnergyKwh = settings.shortChargeMinEnergyKwh
            AppTimeZone.mode = settings.timeZoneMode
        }

        // Configure OSMDroid tile cache (shared across all map screens)
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = "MateDroid/${BuildConfig.VERSION_NAME}"
            osmdroidTileCache = File(cacheDir, "osmdroid")
            tileFileSystemCacheMaxBytes = 100L * 1024 * 1024  // 100 MB
            tileFileSystemCacheTrimBytes = 80L * 1024 * 1024  // trim to 80 MB
            expirationOverrideDuration = 7L * 24 * 60 * 60 * 1000  // 7 days
        }

        // Start background sync on app launch
        enqueueSyncWork()

        // Schedule periodic TPMS pressure monitoring
        TpmsPressureWorker.schedulePeriodicWork(this)

        // Schedule periodic charging notification monitoring
        ChargingNotificationWorker.schedulePeriodicWork(this)

        // Also run an immediate check to cancel stale notifications
        ChargingNotificationWorker.runNow(this)

        // Create sentry notification channel eagerly so it appears in Android settings
        sentryNotificationManager.ensureChannelExists()
    }

    /**
     * Enqueue background sync work.
     * Uses REPLACE so a stuck/backoff-waiting worker gets a fresh start on every app open;
     * an interrupted sync loses little (unprocessed-ID queries resume where it left off).
     */
    private fun enqueueSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, // Start with 30 seconds
                TimeUnit.SECONDS
            )
            .addTag(DataSyncWorker.TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            DataSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,  // Replace stuck/waiting work with fresh start
            syncRequest
        )

        Log.d("MateDroidApp", "Enqueued sync work")
    }
}
