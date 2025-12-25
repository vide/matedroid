package com.matedroid.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CarsResponse(
    @Json(name = "data") val data: CarsData? = null
)

@JsonClass(generateAdapter = true)
data class CarsData(
    @Json(name = "cars") val cars: List<CarData>? = null
)

@JsonClass(generateAdapter = true)
data class CarData(
    @Json(name = "car_id") val carId: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "car_details") val carDetails: CarDetails? = null,
    @Json(name = "car_exterior") val carExterior: CarExterior? = null,
    @Json(name = "teslamate_stats") val teslamateStats: TeslamateStats? = null
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: carDetails?.model?.let { "Model $it" }
            ?: "Tesla"
}

@JsonClass(generateAdapter = true)
data class TeslamateStats(
    @Json(name = "total_charges") val totalCharges: Int? = null,
    @Json(name = "total_drives") val totalDrives: Int? = null
)

@JsonClass(generateAdapter = true)
data class CarExterior(
    @Json(name = "exterior_color") val exteriorColor: String? = null,
    @Json(name = "spoiler_type") val spoilerType: String? = null,
    @Json(name = "wheel_type") val wheelType: String? = null
)

@JsonClass(generateAdapter = true)
data class CarDetails(
    @Json(name = "model") val model: String? = null,
    @Json(name = "trim_badging") val trimBadging: String? = null,
    @Json(name = "vin") val vin: String? = null,
    @Json(name = "efficiency") val efficiency: Double? = null
)

@JsonClass(generateAdapter = true)
data class CarStatusResponse(
    @Json(name = "data") val data: CarStatusData? = null
)

@JsonClass(generateAdapter = true)
data class CarStatusData(
    @Json(name = "status") val status: CarStatus? = null,
    @Json(name = "units") val units: Units? = null
)

@JsonClass(generateAdapter = true)
data class Units(
    @Json(name = "unit_of_length") val unitOfLength: String? = null,
    @Json(name = "unit_of_pressure") val unitOfPressure: String? = null,
    @Json(name = "unit_of_temperature") val unitOfTemperature: String? = null
) {
    val isMetric: Boolean get() = unitOfLength == "km"
    val isImperial: Boolean get() = unitOfLength == "mi"
}

@JsonClass(generateAdapter = true)
data class CarStatus(
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "state_since") val stateSince: String? = null,
    @Json(name = "odometer") val odometer: Double? = null,
    @Json(name = "car_status") val carStatus: CarStatusDetails? = null,
    @Json(name = "car_geodata") val carGeodata: CarGeodata? = null,
    @Json(name = "car_versions") val carVersions: CarVersions? = null,
    @Json(name = "driving_details") val drivingDetails: DrivingDetails? = null,
    @Json(name = "climate_details") val climateDetails: ClimateDetails? = null,
    @Json(name = "battery_details") val batteryDetails: BatteryDetails? = null,
    @Json(name = "charging_details") val chargingDetails: ChargingDetails? = null,
    @Json(name = "tpms_details") val tpmsDetails: TpmsDetails? = null
) {
    // Convenience accessors that flatten the nested structure
    val batteryLevel: Int? get() = batteryDetails?.batteryLevel
    val usableBatteryLevel: Int? get() = batteryDetails?.usableBatteryLevel
    val ratedBatteryRangeKm: Double? get() = batteryDetails?.ratedBatteryRange
    val estBatteryRangeKm: Double? get() = batteryDetails?.estBatteryRange
    val idealBatteryRangeKm: Double? get() = batteryDetails?.idealBatteryRange

    val pluggedIn: Boolean? get() = chargingDetails?.pluggedIn
    val chargingState: String? get() = chargingDetails?.chargingState
    val isCharging: Boolean get() = chargingState?.lowercase() == "charging"
    val chargeEnergyAdded: Double? get() = chargingDetails?.chargeEnergyAdded
    val chargeLimitSoc: Int? get() = chargingDetails?.chargeLimitSoc
    val chargerPower: Int? get() = chargingDetails?.chargerPower
    val timeToFullCharge: Double? get() = chargingDetails?.timeToFullCharge

    val isClimateOn: Boolean? get() = climateDetails?.isClimateOn
    val insideTemp: Double? get() = climateDetails?.insideTemp
    val outsideTemp: Double? get() = climateDetails?.outsideTemp

    val geofence: String? get() = carGeodata?.geofence
    val latitude: Double? get() = carGeodata?.latitude
    val longitude: Double? get() = carGeodata?.longitude

    val locked: Boolean? get() = carStatus?.locked
    val sentryMode: Boolean? get() = carStatus?.sentryMode

    val version: String? get() = carVersions?.version
    val updateAvailable: Boolean? get() = carVersions?.updateAvailable

    val speed: Int? get() = drivingDetails?.speed
    val power: Int? get() = drivingDetails?.power
    val heading: Int? get() = drivingDetails?.heading
    val elevation: Int? get() = drivingDetails?.elevation
}

@JsonClass(generateAdapter = true)
data class CarStatusDetails(
    @Json(name = "healthy") val healthy: Boolean? = null,
    @Json(name = "locked") val locked: Boolean? = null,
    @Json(name = "sentry_mode") val sentryMode: Boolean? = null,
    @Json(name = "windows_open") val windowsOpen: Boolean? = null,
    @Json(name = "doors_open") val doorsOpen: Boolean? = null,
    @Json(name = "trunk_open") val trunkOpen: Boolean? = null,
    @Json(name = "frunk_open") val frunkOpen: Boolean? = null,
    @Json(name = "is_user_present") val isUserPresent: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CarGeodata(
    @Json(name = "geofence") val geofence: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null
)

@JsonClass(generateAdapter = true)
data class CarVersions(
    @Json(name = "version") val version: String? = null,
    @Json(name = "update_available") val updateAvailable: Boolean? = null,
    @Json(name = "update_version") val updateVersion: String? = null
)

@JsonClass(generateAdapter = true)
data class DrivingDetails(
    @Json(name = "shift_state") val shiftState: String? = null,
    @Json(name = "power") val power: Int? = null,
    @Json(name = "speed") val speed: Int? = null,
    @Json(name = "heading") val heading: Int? = null,
    @Json(name = "elevation") val elevation: Int? = null
)

@JsonClass(generateAdapter = true)
data class ClimateDetails(
    @Json(name = "is_climate_on") val isClimateOn: Boolean? = null,
    @Json(name = "inside_temp") val insideTemp: Double? = null,
    @Json(name = "outside_temp") val outsideTemp: Double? = null,
    @Json(name = "is_preconditioning") val isPreconditioning: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class BatteryDetails(
    @Json(name = "battery_level") val batteryLevel: Int? = null,
    @Json(name = "usable_battery_level") val usableBatteryLevel: Int? = null,
    @Json(name = "est_battery_range") val estBatteryRange: Double? = null,
    @Json(name = "rated_battery_range") val ratedBatteryRange: Double? = null,
    @Json(name = "ideal_battery_range") val idealBatteryRange: Double? = null
)

@JsonClass(generateAdapter = true)
data class ChargingDetails(
    @Json(name = "plugged_in") val pluggedIn: Boolean? = null,
    @Json(name = "charging_state") val chargingState: String? = null,
    @Json(name = "charge_energy_added") val chargeEnergyAdded: Double? = null,
    @Json(name = "charge_limit_soc") val chargeLimitSoc: Int? = null,
    @Json(name = "charge_port_door_open") val chargePortDoorOpen: Boolean? = null,
    @Json(name = "charger_actual_current") val chargerActualCurrent: Int? = null,
    @Json(name = "charger_power") val chargerPower: Int? = null,
    @Json(name = "charger_voltage") val chargerVoltage: Int? = null,
    @Json(name = "time_to_full_charge") val timeToFullCharge: Double? = null
)

@JsonClass(generateAdapter = true)
data class TpmsDetails(
    @Json(name = "tpms_pressure_fl") val pressureFl: Double? = null,
    @Json(name = "tpms_pressure_fr") val pressureFr: Double? = null,
    @Json(name = "tpms_pressure_rl") val pressureRl: Double? = null,
    @Json(name = "tpms_pressure_rr") val pressureRr: Double? = null,
    @Json(name = "tpms_soft_warning_fl") val warningFl: Boolean? = null,
    @Json(name = "tpms_soft_warning_fr") val warningFr: Boolean? = null,
    @Json(name = "tpms_soft_warning_rl") val warningRl: Boolean? = null,
    @Json(name = "tpms_soft_warning_rr") val warningRr: Boolean? = null
)
