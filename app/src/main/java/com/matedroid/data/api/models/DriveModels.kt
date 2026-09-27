package com.matedroid.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DrivesResponse(
    @Json(name = "data") val data: DrivesData? = null,
    // TeslamateAPI answers HTTP 200 with this field (and no data) when the query fails,
    // e.g. a single row with a NULL column makes the whole list "Unable to load drives."
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class DrivesData(
    @Json(name = "drives") val drives: List<DriveData>? = null
)

@JsonClass(generateAdapter = true)
data class DriveData(
    @Json(name = "drive_id") val driveId: Int,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "start_address") val startAddress: String? = null,
    @Json(name = "end_address") val endAddress: String? = null,
    @Json(name = "odometer_details") val odometerDetails: DriveOdometerDetails? = null,
    @Json(name = "duration_min") val durationMin: Int? = null,
    @Json(name = "duration_str") val durationStr: String? = null,
    @Json(name = "speed_max") val speedMax: Int? = null,
    @Json(name = "speed_avg") val speedAvg: Double? = null,
    @Json(name = "power_max") val powerMax: Int? = null,
    @Json(name = "power_min") val powerMin: Int? = null,
    @Json(name = "battery_details") val batteryDetails: DriveBatteryDetails? = null,
    @Json(name = "range_ideal") val rangeIdeal: DriveRange? = null,
    @Json(name = "range_rated") val rangeRated: DriveRange? = null,
    @Json(name = "outside_temp_avg") val outsideTempAvg: Double? = null,
    @Json(name = "inside_temp_avg") val insideTempAvg: Double? = null,
    @Json(name = "energy_consumed_net") val energyConsumedNet: Double? = null,
    @Json(name = "consumption_net") val consumptionNet: Double? = null
) {
    // Convenience accessors
    val id: Int get() = driveId
    val distance: Double? get() = odometerDetails?.distance
    val startBatteryLevel: Int? get() = batteryDetails?.startBatteryLevel
    val endBatteryLevel: Int? get() = batteryDetails?.endBatteryLevel
    val startRatedRangeKm: Double? get() = rangeRated?.startRange
    val endRatedRangeKm: Double? get() = rangeRated?.endRange

    val efficiencyWhKm: Double?
        get() {
            val dist = distance ?: return null
            if (dist <= 0) return null
            val consumed = energyConsumedNet ?: return null
            return (consumed * 1000) / dist // Convert kWh to Wh per km
        }
}

@JsonClass(generateAdapter = true)
data class DriveOdometerDetails(
    @Json(name = "odometer_start") val odometerStart: Double? = null,
    @Json(name = "odometer_end") val odometerEnd: Double? = null,
    @Json(name = "odometer_distance") val distance: Double? = null
)

@JsonClass(generateAdapter = true)
data class DriveBatteryDetails(
    @Json(name = "start_battery_level") val startBatteryLevel: Int? = null,
    @Json(name = "end_battery_level") val endBatteryLevel: Int? = null,
    @Json(name = "is_range_ideal") val isRangeIdeal: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class DriveRange(
    @Json(name = "start_range") val startRange: Double? = null,
    @Json(name = "end_range") val endRange: Double? = null,
    @Json(name = "range_diff") val rangeDiff: Double? = null
)

@JsonClass(generateAdapter = true)
data class DriveDetailResponse(
    @Json(name = "data") val data: DriveDetailData? = null,
    // HTTP 200 + error when the query fails, e.g. one position along the drive has a NULL.
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveDetailData(
    @Json(name = "car") val car: DriveDetailCar? = null,
    @Json(name = "drive") val drive: DriveDetail? = null
)

@JsonClass(generateAdapter = true)
data class DriveDetailCar(
    @Json(name = "car_id") val carId: Int? = null,
    @Json(name = "car_name") val carName: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveDetail(
    @Json(name = "drive_id") val driveId: Int,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "start_address") val startAddress: String? = null,
    @Json(name = "end_address") val endAddress: String? = null,
    @Json(name = "odometer_details") val odometerDetails: DriveOdometerDetails? = null,
    @Json(name = "duration_min") val durationMin: Int? = null,
    @Json(name = "duration_str") val durationStr: String? = null,
    @Json(name = "speed_max") val speedMax: Int? = null,
    @Json(name = "speed_avg") val speedAvg: Double? = null,
    @Json(name = "power_max") val powerMax: Int? = null,
    @Json(name = "power_min") val powerMin: Int? = null,
    @Json(name = "battery_details") val batteryDetails: DriveBatteryDetails? = null,
    @Json(name = "range_ideal") val rangeIdeal: DriveRange? = null,
    @Json(name = "range_rated") val rangeRated: DriveRange? = null,
    @Json(name = "outside_temp_avg") val outsideTempAvg: Double? = null,
    @Json(name = "inside_temp_avg") val insideTempAvg: Double? = null,
    @Json(name = "energy_consumed_net") val energyConsumedNet: Double? = null,
    @Json(name = "consumption_net") val consumptionNet: Double? = null,
    @Json(name = "drive_details") val positions: List<DrivePosition>? = null
) {
    val id: Int get() = driveId
    val distance: Double? get() = odometerDetails?.distance
    val startBatteryLevel: Int? get() = batteryDetails?.startBatteryLevel
    val endBatteryLevel: Int? get() = batteryDetails?.endBatteryLevel
}

@JsonClass(generateAdapter = true)
data class DrivePosition(
    @Json(name = "date") val date: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "speed") val speed: Int? = null,
    @Json(name = "power") val power: Int? = null,
    @Json(name = "battery_level") val batteryLevel: Int? = null,
    @Json(name = "elevation") val elevation: Int? = null,
    @Json(name = "climate_info") val climateInfo: DriveClimateInfo? = null,
    @Json(name = "battery_info") val batteryInfo: DriveBatteryInfo? = null
) {
    // Convenience accessors
    val insideTemp: Double? get() = climateInfo?.insideTemp
    val outsideTemp: Double? get() = climateInfo?.outsideTemp
    val isClimateOn: Boolean get() = climateInfo?.isClimateOn == true
    val isBatteryHeaterOn: Boolean get() = batteryInfo?.batteryHeater == true
}

@JsonClass(generateAdapter = true)
data class DriveBatteryInfo(
    @Json(name = "battery_heater") val batteryHeater: Boolean? = null,
    @Json(name = "battery_heater_on") val batteryHeaterOn: Boolean? = null,
    @Json(name = "battery_heater_no_power") val batteryHeaterNoPower: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class DriveClimateInfo(
    @Json(name = "inside_temp") val insideTemp: Double? = null,
    @Json(name = "outside_temp") val outsideTemp: Double? = null,
    @Json(name = "is_climate_on") val isClimateOn: Boolean? = null,
    @Json(name = "fan_status") val fanStatus: Int? = null,
    @Json(name = "driver_temp_setting") val driverTempSetting: Double? = null,
    @Json(name = "passenger_temp_setting") val passengerTempSetting: Double? = null
)
