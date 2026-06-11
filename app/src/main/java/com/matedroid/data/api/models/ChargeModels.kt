package com.matedroid.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChargesResponse(
    @Json(name = "data") val data: ChargesData? = null
)

@JsonClass(generateAdapter = true)
data class ChargesData(
    @Json(name = "charges") val charges: List<ChargeData>? = null
)

@JsonClass(generateAdapter = true)
data class ChargeData(
    @Json(name = "charge_id") val chargeId: Int,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "charge_energy_added") val chargeEnergyAdded: Double? = null,
    @Json(name = "charge_energy_used") val chargeEnergyUsed: Double? = null,
    @Json(name = "cost") val cost: Double? = null,
    @Json(name = "duration_min") val durationMin: Int? = null,
    @Json(name = "duration_str") val durationStr: String? = null,
    @Json(name = "battery_details") val batteryDetails: ChargeBatteryDetails? = null,
    @Json(name = "range_ideal") val rangeIdeal: ChargeRange? = null,
    @Json(name = "range_rated") val rangeRated: ChargeRange? = null,
    @Json(name = "outside_temp_avg") val outsideTempAvg: Double? = null,
    @Json(name = "odometer") val odometer: Double? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null
) {
    // Convenience accessors
    val startBatteryLevel: Int? get() = batteryDetails?.startBatteryLevel
    val endBatteryLevel: Int? get() = batteryDetails?.endBatteryLevel
    val startRatedRangeKm: Double? get() = rangeRated?.startRange
    val endRatedRangeKm: Double? get() = rangeRated?.endRange
}

@JsonClass(generateAdapter = true)
data class ChargeBatteryDetails(
    @Json(name = "start_battery_level") val startBatteryLevel: Int? = null,
    @Json(name = "end_battery_level") val endBatteryLevel: Int? = null,
    @Json(name = "current_battery_level") val currentBatteryLevel: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChargeRange(
    @Json(name = "start_range") val startRange: Double? = null,
    @Json(name = "end_range") val endRange: Double? = null
)

@JsonClass(generateAdapter = true)
data class ChargeDetailResponse(
    @Json(name = "data") val data: ChargeDetailData? = null,
    // TeslamateAPI returns HTTP 200 with this error field (and no data) when
    // there is no active charge, e.g. "No active charging in progress."
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ChargeDetailData(
    @Json(name = "car") val car: ChargeDetailCar? = null,
    @Json(name = "charge") val charge: ChargeDetail? = null
)

@JsonClass(generateAdapter = true)
data class ChargeDetailCar(
    @Json(name = "car_id") val carId: Int? = null,
    @Json(name = "car_name") val carName: String? = null
)

@JsonClass(generateAdapter = true)
data class ChargeDetail(
    @Json(name = "charge_id") val chargeId: Int,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "charge_energy_added") val chargeEnergyAdded: Double? = null,
    @Json(name = "charge_energy_used") val chargeEnergyUsed: Double? = null,
    @Json(name = "cost") val cost: Double? = null,
    @Json(name = "duration_min") val durationMin: Int? = null,
    @Json(name = "duration_str") val durationStr: String? = null,
    @Json(name = "battery_details") val batteryDetails: ChargeBatteryDetails? = null,
    @Json(name = "range_ideal") val rangeIdeal: ChargeRange? = null,
    @Json(name = "range_rated") val rangeRated: ChargeRange? = null,
    @Json(name = "outside_temp_avg") val outsideTempAvg: Double? = null,
    @Json(name = "odometer") val odometer: Double? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "charge_details") val chargePoints: List<ChargePoint>? = null,
    @Json(name = "is_charging") val isCharging: Boolean? = null
) {
    val startBatteryLevel: Int? get() = batteryDetails?.startBatteryLevel
    val endBatteryLevel: Int? get() = batteryDetails?.endBatteryLevel
    val currentBatteryLevel: Int? get() = batteryDetails?.currentBatteryLevel
    val currentOrEndBatteryLevel: Int? get() = currentBatteryLevel ?: endBatteryLevel
}

@JsonClass(generateAdapter = true)
data class ChargePoint(
    @Json(name = "date") val date: String? = null,
    @Json(name = "battery_level") val batteryLevel: Int? = null,
    @Json(name = "charge_energy_added") val chargeEnergyAdded: Double? = null,
    @Json(name = "charger_details") val chargerDetails: ChargerDetails? = null,
    @Json(name = "outside_temp") val outsideTemp: Double? = null,
    @Json(name = "battery_info") val batteryInfo: ChargeBatteryInfo? = null
) {
    // Convenience accessors
    val chargerPower: Int? get() = chargerDetails?.chargerPower
    val chargerVoltage: Int? get() = chargerDetails?.chargerVoltage
    val chargerCurrent: Int? get() = chargerDetails?.chargerActualCurrent
}

@JsonClass(generateAdapter = true)
data class ChargerDetails(
    @Json(name = "charger_power") val chargerPower: Int? = null,
    @Json(name = "charger_voltage") val chargerVoltage: Int? = null,
    @Json(name = "charger_actual_current") val chargerActualCurrent: Int? = null,
    @Json(name = "charger_phases") val chargerPhases: Int? = null,
    @Json(name = "fast_charger_present") val fastChargerPresent: Boolean? = null,
    @Json(name = "fast_charger_brand") val fastChargerBrand: String? = null,
    @Json(name = "fast_charger_type") val fastChargerType: String? = null
)

@JsonClass(generateAdapter = true)
data class ChargeBatteryInfo(
    @Json(name = "ideal_battery_range_km") val idealBatteryRangeKm: Double? = null,
    @Json(name = "rated_battery_range_km") val ratedBatteryRangeKm: Double? = null,
    @Json(name = "usable_battery_level") val usableBatteryLevel: Int? = null
)
