package com.matedroid.data.repository

import com.matedroid.data.local.SentryState
import com.matedroid.data.local.SentryStateDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of processing a sentry status update.
 */
sealed class SentryEvent {
    /**
     * A sentry alert was detected. [count] is the running total for this session.
     * [shouldNotify] is true for the first event in a debounce window (triggers sound/heads-up);
     * false for subsequent events within the window (counter still increments, notification
     * updates silently).
     */
    data class AlertDetected(val count: Int, val shouldNotify: Boolean) : SentryEvent()

    /** Sentry mode turned off; session counters have been reset. */
    data object SessionEnded : SentryEvent()
}

/**
 * Business logic layer over [SentryStateDataStore].
 *
 * Every poll where center_display_state == "7" increments the event counter.
 * A 65-second debounce window (just over the 1-minute screen-on duration per
 * sentry event) controls whether the notification should alert (sound + heads-up)
 * or just update silently.
 */
@Singleton
class SentryStateRepository @Inject constructor(
    private val dataStore: SentryStateDataStore
) {
    companion object {
        /** Debounce window for notification alerting (not counting).
         *  Slightly over 60s because the car screen stays on for exactly 1 minute per event. */
        private const val NOTIFY_DEBOUNCE_MS = 65_000L
    }

    /**
     * Process a status update for a car.
     *
     * @param carId The car ID
     * @param sentryMode Whether sentry mode is currently active
     * @param isSentryAlerted Whether the center display is showing the sentry warning (state "7")
     * @return A [SentryEvent] if something noteworthy happened, null otherwise
     */
    suspend fun processStatus(
        carId: Int,
        sentryMode: Boolean,
        isSentryAlerted: Boolean
    ): SentryEvent? {
        val state = dataStore.getState(carId)

        // Sentry mode just turned off — reset session
        if (!sentryMode && state.sentryActive) {
            dataStore.resetSession(carId)
            return SentryEvent.SessionEnded
        }

        // Sentry mode is off and was already off — nothing to do
        if (!sentryMode) return null

        // Mark sentry as active if not already
        if (!state.sentryActive) {
            dataStore.saveState(carId, state.copy(sentryActive = true))
        }

        // Check for a sentry alert event
        if (isSentryAlerted) {
            // Always increment the counter
            val updated = dataStore.incrementEventCount(carId)

            // Debounce only controls whether the notification should alert with sound
            val now = System.currentTimeMillis()
            val timeSinceLastEvent = now - state.lastEventAt
            val shouldNotify = timeSinceLastEvent >= NOTIFY_DEBOUNCE_MS

            return SentryEvent.AlertDetected(updated.eventCount, shouldNotify)
        }

        return null
    }

    /**
     * Get the current event count for a car's sentry session.
     */
    suspend fun getEventCount(carId: Int): Int {
        return dataStore.getState(carId).eventCount
    }

    /**
     * Force-increment the event count, bypassing debounce.
     * Used for debug simulation only.
     */
    suspend fun forceIncrementEventCount(carId: Int): Int {
        // Ensure sentry is marked active
        val state = dataStore.getState(carId)
        if (!state.sentryActive) {
            dataStore.saveState(carId, state.copy(sentryActive = true))
        }
        return dataStore.incrementEventCount(carId).eventCount
    }
}
