package com.matedroid

import android.app.Application
import android.util.Log
import java.io.File
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.data.sync.ChargingNotificationWorker
import com.matedroid.data.sync.TpmsPressureWorker
import com.matedroid.domain.CostPerKwhBasis
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

        // Restore the user's short drive/charge thresholds and cost basis into their
        // process-wide mirrors.
        appScope.launch {
            val settings = settingsDataStore.settings.first()
            ShortEntryFilter.minDriveDurationMin = settings.shortDriveMinDurationMin
            ShortEntryFilter.minDriveDistance = settings.shortDriveMinDistance
            ShortEntryFilter.minChargeEnergyKwh = settings.shortChargeMinEnergyKwh
            CostPerKwhBasis.current = settings.costPerKwhBasis
        }

        // Configure OSMDroid tile cache (shared across all map screens)
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = "MateDroid/${BuildConfig.VERSION_NAME}"
            osmdroidTileCache = File(cacheDir, "osmdroid")
            tileFileSystemCacheMaxBytes = 100L * 1024 * 1024  // 100 MB
            tileFileSystemCacheTrimBytes = 80L * 1024 * 1024  // trim to 80 MB
            expirationOverrideDuration = 7L * 24 * 60 * 60 * 1000  // 7 days
        }

        // Application.onCreate runs on EVERY process start, and WorkManager starts the process
        // for each background job (widget refresh, TPMS, sync, the charging backstop itself),
        // so nothing here may assume the user opened the app. The launch sync and the immediate
        // charging check live in MainActivity.onCreate for that reason; here the schedules are
        // only made sure to exist, without resetting a chain that is already pending.
        TpmsPressureWorker.schedulePeriodicWork(this)
        ChargingNotificationWorker.ensureScheduled(this)

        // Create sentry notification channel eagerly so it appears in Android settings
        sentryNotificationManager.ensureChannelExists()
    }
}
