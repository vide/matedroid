package com.matedroid.widget

import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.local.CarImageOverride

/**
 * Data class encapsulating all fields shown on the dashboard battery card.
 * This is the single source of truth for what the home screen widget displays.
 *
 * Adding a field here forces an update to [from], enforcing parity with the
 * battery card: the compiler will fail if the factory method is not updated.
 */
data class CarWidgetDisplayData(
    val carId: Int,
    val carName: String,
    val exteriorColor: String?,
    val model: String?,
    val trimBadging: String?,
    val wheelType: String?,
    // --- Status indicators ---
    val state: String?,
    val stateSince: String?,
    val isLocked: Boolean,
    val sentryModeActive: Boolean,
    val pluggedIn: Boolean,
    val outsideTemp: Double?,
    val insideTemp: Double?,
    val isClimateOn: Boolean,
    // --- Battery info ---
    val batteryLevel: Int,
    val ratedBatteryRangeKm: Double?,
    val chargeLimitSoc: Int?,
    // --- Charging state ---
    val isCharging: Boolean,
    val isDcCharging: Boolean,
    val chargerPower: Int?,
    val chargeEnergyAdded: Double?,
    val timeToFullCharge: Double?,
    val chargerVoltage: Int?,
    val chargerActualCurrent: Int?,
    val acPhases: Int?,
    val sentryEventCount: Int = 0,
    // --- Image override (from car image picker) ---
    val imageOverride: CarImageOverride? = null,
    // --- Location (pre-resolved for widget display) ---
    val locationText: String? = null,
) {
    companion object {
        fun from(carData: CarData, status: CarStatus): CarWidgetDisplayData {
            return CarWidgetDisplayData(
                carId = carData.carId,
                carName = carData.displayName,
                exteriorColor = carData.carExterior?.exteriorColor,
                model = carData.carDetails?.model,
                trimBadging = carData.carDetails?.trimBadging,
                wheelType = carData.carExterior?.wheelType,
                state = status.state,
                stateSince = status.stateSince,
                isLocked = status.locked ?: false,
                sentryModeActive = status.sentryMode ?: false,
                pluggedIn = status.pluggedIn ?: false,
                outsideTemp = status.outsideTemp,
                insideTemp = status.insideTemp,
                isClimateOn = status.isClimateOn ?: false,
                batteryLevel = status.batteryLevel ?: 0,
                ratedBatteryRangeKm = status.ratedBatteryRangeKm,
                chargeLimitSoc = status.chargeLimitSoc,
                isCharging = status.isCharging,
                isDcCharging = status.isDcCharging,
                chargerPower = status.chargerPower,
                chargeEnergyAdded = status.chargeEnergyAdded,
                timeToFullCharge = status.timeToFullCharge,
                chargerVoltage = status.chargingDetails?.chargerVoltage,
                chargerActualCurrent = status.chargerActualCurrent,
                acPhases = status.acPhases,
                sentryEventCount = 0,  // Populated separately by worker from SentryStateRepository
            )
        }
    }
}
