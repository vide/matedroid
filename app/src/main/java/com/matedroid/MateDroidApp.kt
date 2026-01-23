package com.matedroid

import android.app.Application
import android.util.Log
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
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.data.sync.TpmsPressureWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MateDroidApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Start background sync on app launch
        enqueueSyncWork()

        // Schedule periodic TPMS pressure monitoring
        TpmsPressureWorker.schedulePeriodicWork(this)
    }

    /**
     * Enqueue background sync work.
     * Uses KEEP policy to not restart if already running.
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
