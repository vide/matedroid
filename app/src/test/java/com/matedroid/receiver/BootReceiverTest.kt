package com.matedroid.receiver

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.matedroid.BuildConfig
import com.matedroid.data.sync.ChargingNotificationWorker
import com.matedroid.data.sync.TpmsPressureWorker
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for BootReceiver to ensure all necessary workers are rescheduled on boot.
 *
 * This prevents regressions where a worker is added to the app but forgotten
 * in the BootReceiver, causing it to stop working after device reboot.
 */
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bootReceiver = BootReceiver()
        workManager = mockk(relaxed = true)

        // Mock Android Log
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0

        // Mock WorkManager.getInstance via the Companion object (WorkManager 2.10+
        // removed @JvmStatic, so mockkStatic(WorkManager::class) no longer intercepts).
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onReceive with ACTION_BOOT_COMPLETED schedules TpmsPressureWorker`() {
        // Given
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        // When
        bootReceiver.onReceive(context, intent)

        // Then
        verify {
            WorkManager.getInstance(context)
        }
        // TpmsPressureWorker uses OneTimeWorkRequest in debug, PeriodicWorkRequest in release
        if (BuildConfig.DEBUG) {
            verify(atLeast = 1) {
                workManager.enqueueUniqueWork(
                    eq(TpmsPressureWorker.WORK_NAME),
                    any<ExistingWorkPolicy>(),
                    any<OneTimeWorkRequest>()
                )
            }
        } else {
            verify(atLeast = 1) {
                workManager.enqueueUniquePeriodicWork(
                    eq(TpmsPressureWorker.WORK_NAME),
                    any<ExistingPeriodicWorkPolicy>(),
                    any<PeriodicWorkRequest>()
                )
            }
        }
    }

    @Test
    fun `onReceive with ACTION_BOOT_COMPLETED schedules ChargingNotificationWorker`() {
        // Given
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        // When
        bootReceiver.onReceive(context, intent)

        // Then
        verify {
            WorkManager.getInstance(context)
        }
        // Verify work was enqueued by checking WorkManager interactions
        verify(atLeast = 1) {
            workManager.enqueueUniqueWork(
                eq(ChargingNotificationWorker.WORK_NAME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `onReceive with ACTION_BOOT_COMPLETED schedules all critical workers`() {
        // Given
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        // When
        bootReceiver.onReceive(context, intent)

        // Then
        // This test documents which workers MUST be scheduled on boot.
        // If you add a new critical worker, add it here to prevent forgetting
        // to schedule it in BootReceiver.

        val criticalWorkers = listOf(
            TpmsPressureWorker.WORK_NAME,
            ChargingNotificationWorker.WORK_NAME
        )

        for (workerName in criticalWorkers) {
            // Workers may use enqueueUniqueWork or enqueueUniquePeriodicWork
            // depending on build variant, so check for either
            val oneTimeScheduled = runCatching {
                verify(atLeast = 1) {
                    workManager.enqueueUniqueWork(
                        eq(workerName),
                        any<ExistingWorkPolicy>(),
                        any<OneTimeWorkRequest>()
                    )
                }
            }.isSuccess

            val periodicScheduled = runCatching {
                verify(atLeast = 1) {
                    workManager.enqueueUniquePeriodicWork(
                        eq(workerName),
                        any<ExistingPeriodicWorkPolicy>(),
                        any<PeriodicWorkRequest>()
                    )
                }
            }.isSuccess

            assertTrue(
                "Worker $workerName was not scheduled on boot via either enqueue method",
                oneTimeScheduled || periodicScheduled
            )
        }
    }

    @Test
    fun `onReceive ignores non-boot intents`() {
        // Given
        val intent = mockk<Intent>()
        every { intent.action } returns "android.intent.action.SOME_OTHER_ACTION"

        // Clear any previous interactions
        clearMocks(workManager)

        // When
        bootReceiver.onReceive(context, intent)

        // Then - WorkManager should not be called
        verify(exactly = 0) {
            WorkManager.getInstance(any())
        }
    }
}
