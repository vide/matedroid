package com.matedroid.domain

/**
 * Process-wide mirror of TeslamateAPI's unit-system setting.
 *
 * TeslamateAPI pre-converts every value to the user's preferred unit system, so distances
 * stored in Room are already km OR mi. Our own physical threshold constants (defined in km,
 * e.g. "hide drives under 1 km", "a trip is at least 300 km") must therefore be scaled into
 * the user's unit before comparing — otherwise imperial users get 1 mi / 300 mi thresholds.
 *
 * The flag is restored from preferences at app start ([com.matedroid.MateDroidApp]) and kept
 * fresh by [com.matedroid.data.repository.TeslamateRepository] whenever a car status arrives.
 *
 * This is ONLY for scaling our own constants — never use it to convert API values.
 */
object UnitSystem {
    private const val KM_TO_MI = 0.621371

    @Volatile
    var isImperial: Boolean = false

    /** Scale a threshold defined in km into the user's distance unit. */
    fun thresholdKmToUserUnits(km: Double): Double = if (isImperial) km * KM_TO_MI else km
}
