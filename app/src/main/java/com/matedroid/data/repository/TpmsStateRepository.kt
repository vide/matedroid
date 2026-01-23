package com.matedroid.data.repository

import com.matedroid.data.api.models.TpmsDetails
import com.matedroid.data.local.TirePosition
import com.matedroid.data.local.TpmsState
import com.matedroid.data.local.TpmsStateDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a change in TPMS warning state.
 */
sealed class TpmsStateChange {
    /**
     * One or more tires have entered a warning state.
     */
    data class WarningStarted(val tires: List<TirePosition>) : TpmsStateChange()

    /**
     * All tires have returned to normal (no warnings).
     */
    data object WarningCleared : TpmsStateChange()
}

/**
 * Repository for managing TPMS state and detecting state changes.
 * Handles the business logic for determining when to notify users about tire pressure changes.
 */
@Singleton
class TpmsStateRepository @Inject constructor(
    private val tpmsStateDataStore: TpmsStateDataStore
) {
    /**
     * Detect if there's a state change between the stored state and current TPMS data.
     * Returns null if there's no meaningful state change to notify about.
     *
     * @param carId The car ID to check
     * @param currentTpms The current TPMS details from the API
     * @return A TpmsStateChange if a transition occurred, null otherwise
     */
    suspend fun detectStateChange(carId: Int, currentTpms: TpmsDetails?): TpmsStateChange? {
        val previousState = tpmsStateDataStore.getState(carId)
        val currentState = currentTpms?.toTpmsState() ?: TpmsState()

        val previousHadWarning = previousState.hasAnyWarning
        val currentHasWarning = currentState.hasAnyWarning

        return when {
            // Transition from no warning to warning
            !previousHadWarning && currentHasWarning -> {
                TpmsStateChange.WarningStarted(currentState.getWarningTires())
            }
            // Transition from warning to no warning
            previousHadWarning && !currentHasWarning -> {
                TpmsStateChange.WarningCleared
            }
            // Warning state changed (different tires now have warnings)
            previousHadWarning && currentHasWarning &&
                previousState.getWarningTires() != currentState.getWarningTires() -> {
                TpmsStateChange.WarningStarted(currentState.getWarningTires())
            }
            // No state change
            else -> null
        }
    }

    /**
     * Update the stored TPMS state for a car.
     *
     * @param carId The car ID to update
     * @param tpms The current TPMS details from the API
     */
    suspend fun updateState(carId: Int, tpms: TpmsDetails?) {
        val state = tpms?.toTpmsState() ?: TpmsState()
        tpmsStateDataStore.saveState(carId, state.copy(lastCheckedAt = System.currentTimeMillis()))
    }

    /**
     * Get the current stored TPMS state for a car.
     */
    suspend fun getState(carId: Int): TpmsState {
        return tpmsStateDataStore.getState(carId)
    }

    /**
     * Clear all stored TPMS states.
     */
    suspend fun clearAllStates() {
        tpmsStateDataStore.clearAllStates()
    }

    /**
     * Simulate a TPMS warning for testing purposes (debug builds only).
     * Sets a warning state for the specified tire.
     */
    suspend fun simulateWarning(carId: Int, tire: TirePosition) {
        val state = TpmsState(
            warningFl = tire == TirePosition.FL,
            warningFr = tire == TirePosition.FR,
            warningRl = tire == TirePosition.RL,
            warningRr = tire == TirePosition.RR,
            lastCheckedAt = System.currentTimeMillis()
        )
        tpmsStateDataStore.saveState(carId, state)
    }

    /**
     * Clear the TPMS warning state for a car (for testing).
     */
    suspend fun clearWarning(carId: Int) {
        tpmsStateDataStore.saveState(carId, TpmsState(lastCheckedAt = System.currentTimeMillis()))
    }
}

/**
 * Extension function to convert API TpmsDetails to local TpmsState.
 */
private fun TpmsDetails.toTpmsState(): TpmsState {
    return TpmsState(
        warningFl = warningFl ?: false,
        warningFr = warningFr ?: false,
        warningRl = warningRl ?: false,
        warningRr = warningRr ?: false,
        lastCheckedAt = System.currentTimeMillis()
    )
}
