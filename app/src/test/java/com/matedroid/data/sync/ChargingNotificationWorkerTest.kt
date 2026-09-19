package com.matedroid.data.sync

import com.matedroid.data.sync.ChargingNotificationWorker.Cadence
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one decision the charging/sentry chain makes: how long until the next check. */
class ChargingNotificationWorkerTest {

    private fun cadence(
        serviceRunning: Boolean = false,
        frequent: Boolean = false,
        failed: Boolean = false,
        previousFailures: Int = 0
    ) = ChargingNotificationWorker.cadenceAfter(serviceRunning, frequent, failed, previousFailures)

    @Test
    fun `an active car keeps the 30 s cadence`() {
        assertEquals(Cadence(30, 0), cadence(frequent = true))
    }

    @Test
    fun `nothing to watch backs off to the idle cadence`() {
        assertEquals(Cadence(300, 0), cadence())
    }

    @Test
    fun `a running monitor service turns the chain into an idle watchdog`() {
        // Whatever else is going on: the service runs the 30 s loop itself.
        assertEquals(Cadence(300, 0), cadence(serviceRunning = true))
        assertEquals(Cadence(300, 0), cadence(serviceRunning = true, frequent = true, failed = true, previousFailures = 3))
    }

    @Test
    fun `consecutive failures double the delay up to the idle cadence`() {
        var failures = 0
        val delays = mutableListOf<Long>()
        repeat(7) {
            val next = cadence(failed = true, previousFailures = failures)
            delays += next.delaySeconds
            failures = next.consecutiveFailures
        }
        assertEquals(listOf(30L, 60L, 120L, 240L, 300L, 300L, 300L), delays)
        assertEquals(7, failures)
    }

    @Test
    fun `a successful check resets the failure count`() {
        assertEquals(Cadence(300, 0), cadence(previousFailures = 5))
        assertEquals(Cadence(30, 0), cadence(frequent = true, previousFailures = 5))
    }

    @Test
    fun `an active car outranks another car's failed check`() {
        // Two cars: one plugged in, the other's status fetch failed — keep watching the first.
        assertEquals(Cadence(30, 0), cadence(frequent = true, failed = true, previousFailures = 2))
    }
}
