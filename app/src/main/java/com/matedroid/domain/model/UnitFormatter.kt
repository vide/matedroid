package com.matedroid.domain.model

import com.matedroid.data.api.models.Units

/**
 * Utility object for formatting values based on unit preferences from TeslamateApi.
 * Supports metric (km, °C, bar) and imperial (mi, °F, psi) units.
 */
object UnitFormatter {

    // Conversion constants
    private const val KM_TO_MI = 0.621371
    private const val MI_TO_KM = 1.60934
    private const val WH_PER_KM_TO_WH_PER_MI = 1.60934

    /**
     * Format distance value with appropriate unit label
     */
    fun formatDistance(value: Double, units: Units?, decimals: Int = 1): String {
        return if (units?.isImperial == true) {
            val miles = value * KM_TO_MI
            "%.${decimals}f mi".format(miles)
        } else {
            "%.${decimals}f km".format(value)
        }
    }

    /**
     * Format distance value without unit label (just the number)
     */
    fun formatDistanceValue(value: Double, units: Units?, decimals: Int = 1): Double {
        return if (units?.isImperial == true) {
            value * KM_TO_MI
        } else {
            value
        }
    }

    /**
     * Get the distance unit label
     */
    fun getDistanceUnit(units: Units?): String {
        return if (units?.isImperial == true) "mi" else "km"
    }

    /**
     * Format temperature value with appropriate unit label
     */
    fun formatTemperature(celsius: Double, units: Units?, decimals: Int = 0): String {
        return if (units?.unitOfTemperature == "F") {
            val fahrenheit = celsius * 9 / 5 + 32
            "%.${decimals}f°F".format(fahrenheit)
        } else {
            "%.${decimals}f°C".format(celsius)
        }
    }

    /**
     * Format temperature value without unit label
     */
    fun formatTemperatureValue(celsius: Double, units: Units?): Double {
        return if (units?.unitOfTemperature == "F") {
            celsius * 9 / 5 + 32
        } else {
            celsius
        }
    }

    /**
     * Get the temperature unit label
     */
    fun getTemperatureUnit(units: Units?): String {
        return if (units?.unitOfTemperature == "F") "°F" else "°C"
    }

    /**
     * Format pressure value with appropriate unit label.
     * Note: TeslamateAPI returns pressure already in the user's preferred unit,
     * so no conversion is needed - just format and add the label.
     */
    fun formatPressure(value: Double, units: Units?, decimals: Int = 1): String {
        val unit = if (units?.unitOfPressure == "psi") "psi" else "bar"
        return "%.${decimals}f %s".format(value, unit)
    }

    /**
     * Get the pressure unit label
     */
    fun getPressureUnit(units: Units?): String {
        return if (units?.unitOfPressure == "psi") "psi" else "bar"
    }

    /**
     * Format efficiency (Wh/km or Wh/mi)
     */
    fun formatEfficiency(whPerKm: Double, units: Units?, decimals: Int = 1): String {
        return if (units?.isImperial == true) {
            val whPerMi = whPerKm * WH_PER_KM_TO_WH_PER_MI
            "%.${decimals}f Wh/mi".format(whPerMi)
        } else {
            "%.${decimals}f Wh/km".format(whPerKm)
        }
    }

    /**
     * Get the efficiency unit label
     */
    fun getEfficiencyUnit(units: Units?): String {
        return if (units?.isImperial == true) "Wh/mi" else "Wh/km"
    }

    /**
     * Format speed value with appropriate unit label
     */
    fun formatSpeed(kmh: Double, units: Units?, decimals: Int = 0): String {
        return if (units?.isImperial == true) {
            val mph = kmh * KM_TO_MI
            "%.${decimals}f mph".format(mph)
        } else {
            "%.${decimals}f km/h".format(kmh)
        }
    }

    /**
     * Get the speed unit label
     */
    fun getSpeedUnit(units: Units?): String {
        return if (units?.isImperial == true) "mph" else "km/h"
    }
}
