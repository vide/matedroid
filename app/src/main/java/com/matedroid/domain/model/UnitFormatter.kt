package com.matedroid.domain.model

import com.matedroid.data.api.models.Units

/**
 * Utility object for formatting values based on unit preferences from TeslamateApi.
 * Supports metric (km, °C, bar) and imperial (mi, °F, psi) units.
 *
 * IMPORTANT: TeslamateAPI already returns all values pre-converted to the user's preferred
 * unit system. Do NOT apply any conversion here — just format the number and attach the
 * correct unit label.
 */
object UnitFormatter {

    /**
     * Format distance value with appropriate unit label.
     * Value is already in km (metric) or mi (imperial) as returned by the API.
     */
    fun formatDistance(value: Double, units: Units?, decimals: Int = 1): String {
        return if (units?.isImperial == true) {
            "%,.${decimals}f mi".format(value)
        } else {
            "%,.${decimals}f km".format(value)
        }
    }

    /**
     * Format distance value without unit label (just the number).
     * Value is already in km (metric) or mi (imperial) as returned by the API.
     */
    fun formatDistanceValue(value: Double, units: Units?, decimals: Int = 1): Double {
        return value
    }

    /**
     * Get the distance unit label
     */
    fun getDistanceUnit(units: Units?): String {
        return if (units?.isImperial == true) "mi" else "km"
    }

    /**
     * Format temperature value with appropriate unit label.
     * Value is already in °C (metric) or °F (imperial) as returned by the API.
     */
    fun formatTemperature(value: Double, units: Units?, decimals: Int = 0): String {
        return if (units?.unitOfTemperature == "F") {
            "%.${decimals}f°F".format(value)
        } else {
            "%.${decimals}f°C".format(value)
        }
    }

    /**
     * Format temperature value without unit label.
     * Value is already in the user's preferred unit as returned by the API.
     */
    fun formatTemperatureValue(value: Double, units: Units?): Double {
        return value
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
     * Format efficiency (Wh/km or Wh/mi).
     * Value is already in Wh/km (metric) or Wh/mi (imperial) as returned by the API.
     */
    fun formatEfficiency(value: Double, units: Units?, decimals: Int = 1): String {
        return if (units?.isImperial == true) {
            "%.${decimals}f Wh/mi".format(value)
        } else {
            "%.${decimals}f Wh/km".format(value)
        }
    }

    /**
     * Get the efficiency unit label
     */
    fun getEfficiencyUnit(units: Units?): String {
        return if (units?.isImperial == true) "Wh/mi" else "Wh/km"
    }

    /**
     * Format speed value with appropriate unit label.
     * Value is already in km/h (metric) or mph (imperial) as returned by the API.
     */
    fun formatSpeed(value: Double, units: Units?, decimals: Int = 0): String {
        return if (units?.isImperial == true) {
            "%.${decimals}f mph".format(value)
        } else {
            "%.${decimals}f km/h".format(value)
        }
    }

    /**
     * Get the speed unit label
     */
    fun getSpeedUnit(units: Units?): String {
        return if (units?.isImperial == true) "mph" else "km/h"
    }
}
