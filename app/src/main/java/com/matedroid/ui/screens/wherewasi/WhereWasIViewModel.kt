package com.matedroid.ui.screens.wherewasi

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.api.OpenMeteoApi
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.Units
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.GeocodedLocation
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.TeslamateRepository
import com.matedroid.data.repository.WeatherCondition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

enum class CarActivityState { DRIVING, CHARGING, PARKED }

data class WhereWasIUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val carState: CarActivityState? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: GeocodedLocation? = null,
    val weatherCondition: WeatherCondition? = null,
    val weatherTemperature: Double? = null,
    val odometer: Double? = null,
    val outsideTemp: Double? = null,
    val units: Units? = null,
    // Driving-specific
    val driveId: Int? = null,
    val speed: Int? = null,
    val driveDistance: Double? = null,
    // Charging-specific
    val chargeId: Int? = null,
    val batteryLevel: Int? = null,
    val chargerPower: Int? = null,
    // Parked-specific
    val parkedDurationMinutes: Long? = null,
    val parkedSince: String? = null, // Formatted localized datetime of last activity end
    val lastActivityDriveId: Int? = null,
    val lastActivityChargeId: Int? = null,
    // Display
    val targetDateTime: String? = null,
    val geofenceName: String? = null
)

@HiltViewModel
class WhereWasIViewModel @Inject constructor(
    private val repository: TeslamateRepository,
    private val geocodingRepository: GeocodingRepository,
    private val openMeteoApi: OpenMeteoApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhereWasIUiState())
    val uiState: StateFlow<WhereWasIUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun load(carId: Int, timestamp: String) {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            try {
                val targetTime = parseDateTime(timestamp) ?: run {
                    _uiState.value = WhereWasIUiState(isLoading = false, error = "Invalid date")
                    return@launch
                }

                val displayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                _uiState.value = _uiState.value.copy(targetDateTime = targetTime.format(displayFormatter))

                val dateStr = targetTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val dayStart = "${dateStr}T00:00:00Z"
                val dayEnd = "${dateStr}T23:59:59Z"

                // Fetch drives, charges, and units in parallel
                val drivesDeferred = async { repository.getDrives(carId, dayStart, dayEnd) }
                val chargesDeferred = async { repository.getCharges(carId, dayStart, dayEnd) }
                val statusDeferred = async { repository.getCarStatus(carId) }

                val drives = when (val r = drivesDeferred.await()) {
                    is ApiResult.Success -> r.data
                    is ApiResult.Error -> emptyList()
                }
                val charges = when (val r = chargesDeferred.await()) {
                    is ApiResult.Success -> r.data
                    is ApiResult.Error -> emptyList()
                }
                val units = when (val r = statusDeferred.await()) {
                    is ApiResult.Success -> r.data.units
                    is ApiResult.Error -> null
                }

                // Determine state
                val activeDrive = findActiveDrive(drives, targetTime)
                val activeCharge = findActiveCharge(charges, targetTime)

                when {
                    activeDrive != null -> handleDriving(carId, activeDrive, targetTime, units)
                    activeCharge != null -> handleCharging(carId, activeCharge, targetTime, units)
                    else -> handleParked(carId, drives, charges, targetTime, units, dayStart)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading where-was-i data", e)
                _uiState.value = WhereWasIUiState(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun handleDriving(carId: Int, drive: DriveData, targetTime: LocalDateTime, units: Units?) {
        // Get drive detail for position interpolation
        val detail = when (val r = repository.getDriveDetail(carId, drive.driveId)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> null
        }

        val positions = detail?.positions
        val nearest = positions?.minByOrNull { pos ->
            val posTime = parseDateTime(pos.date ?: "") ?: LocalDateTime.MAX
            kotlin.math.abs(java.time.Duration.between(posTime, targetTime).seconds)
        }

        val lat = nearest?.latitude ?: parseEndLatFromAddress(drive)
        val lon = nearest?.longitude ?: parseEndLonFromAddress(drive)

        _uiState.value = WhereWasIUiState(
            isLoading = false,
            carState = CarActivityState.DRIVING,
            latitude = lat,
            longitude = lon,
            odometer = drive.odometerDetails?.odometerEnd,
            outsideTemp = nearest?.outsideTemp ?: drive.outsideTempAvg,
            speed = nearest?.speed,
            driveId = drive.driveId,
            driveDistance = drive.distance,
            units = units,
            targetDateTime = _uiState.value.targetDateTime,
            // Destination only — falling back to startAddress would mislead, since the screen
            // frames this as "Heading to X" and X must be where the car is going.
            geofenceName = drive.endAddress
        )

        // Fetch geocoding and weather in background
        fetchGeocodingAndWeather(lat, lon, targetTime)
    }

    private suspend fun handleCharging(carId: Int, charge: ChargeData, targetTime: LocalDateTime, units: Units?) {
        val lat = charge.latitude
        val lon = charge.longitude

        // Get charge detail for instant power and battery level
        val detail = when (val r = repository.getChargeDetail(carId, charge.chargeId)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> null
        }

        val nearestPoint = detail?.chargePoints?.minByOrNull { point ->
            val pointTime = parseDateTime(point.date ?: "") ?: LocalDateTime.MAX
            kotlin.math.abs(java.time.Duration.between(pointTime, targetTime).seconds)
        }

        _uiState.value = WhereWasIUiState(
            isLoading = false,
            carState = CarActivityState.CHARGING,
            latitude = lat,
            longitude = lon,
            odometer = charge.odometer,
            outsideTemp = nearestPoint?.outsideTemp ?: charge.outsideTempAvg,
            batteryLevel = nearestPoint?.batteryLevel ?: charge.startBatteryLevel,
            chargerPower = nearestPoint?.chargerPower,
            chargeId = charge.chargeId,
            units = units,
            targetDateTime = _uiState.value.targetDateTime,
            geofenceName = charge.address
        )

        fetchGeocodingAndWeather(lat, lon, targetTime)
    }

    private data class ActivityEnd(
        val lat: Double?,
        val lon: Double?,
        val odometer: Double?,
        val temp: Double?,
        val endTime: LocalDateTime,
        val geofenceName: String? = null,
        val driveId: Int? = null,
        val chargeId: Int? = null
    )

    private suspend fun handleParked(
        carId: Int,
        drives: List<DriveData>,
        charges: List<ChargeData>,
        targetTime: LocalDateTime,
        units: Units?,
        dayStart: String
    ) {
        var result = findLastActivityBefore(carId, drives, charges, targetTime)

        // If nothing before target time on this day, try the first activity after
        if (result == null) {
            result = findFirstActivityAfter(carId, drives, charges, targetTime)
        }

        // If still nothing, search up to 1 year back (API returns most recent first, show=1 is cheap)
        if (result == null) {
            result = findLastActivityInHistory(carId, targetTime)
        }

        if (result?.lat == null || result.lon == null) {
            _uiState.value = WhereWasIUiState(
                isLoading = false,
                error = "no_data",
                units = units,
                targetDateTime = _uiState.value.targetDateTime
            )
            return
        }

        val parkedMinutes = java.time.Duration.between(result.endTime, targetTime).toMinutes()
        val sinceFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

        _uiState.value = WhereWasIUiState(
            isLoading = false,
            carState = CarActivityState.PARKED,
            latitude = result.lat,
            longitude = result.lon,
            odometer = result.odometer,
            outsideTemp = result.temp,
            units = units,
            parkedDurationMinutes = parkedMinutes,
            parkedSince = result.endTime.format(sinceFormatter),
            lastActivityDriveId = result.driveId,
            lastActivityChargeId = result.chargeId,
            targetDateTime = _uiState.value.targetDateTime,
            geofenceName = result.geofenceName
        )

        fetchGeocodingAndWeather(result.lat, result.lon, targetTime)
    }

    private suspend fun findLastActivityBefore(
        carId: Int,
        drives: List<DriveData>,
        charges: List<ChargeData>,
        targetTime: LocalDateTime
    ): ActivityEnd? {
        val activityEnds = mutableListOf<ActivityEnd>()

        for (drive in drives) {
            val endTime = parseDateTime(drive.endDate ?: "") ?: continue
            if (endTime <= targetTime) {
                val detail = when (val r = repository.getDriveDetail(carId, drive.driveId)) {
                    is ApiResult.Success -> r.data
                    is ApiResult.Error -> null
                }
                val lastPos = detail?.positions?.lastOrNull()
                activityEnds.add(ActivityEnd(
                    lat = lastPos?.latitude,
                    lon = lastPos?.longitude,
                    odometer = drive.odometerDetails?.odometerEnd,
                    temp = drive.outsideTempAvg,
                    endTime = endTime,
                    geofenceName = drive.endAddress,
                    driveId = drive.driveId
                ))
            }
        }

        for (charge in charges) {
            val endTime = parseDateTime(charge.endDate ?: "") ?: continue
            if (endTime <= targetTime) {
                activityEnds.add(ActivityEnd(
                    lat = charge.latitude,
                    lon = charge.longitude,
                    odometer = charge.odometer,
                    temp = charge.outsideTempAvg,
                    endTime = endTime,
                    geofenceName = charge.address,
                    chargeId = charge.chargeId
                ))
            }
        }

        return activityEnds.maxByOrNull { it.endTime }
    }

    private suspend fun findFirstActivityAfter(
        carId: Int,
        drives: List<DriveData>,
        charges: List<ChargeData>,
        targetTime: LocalDateTime
    ): ActivityEnd? {
        val firstDriveAfter = drives.firstOrNull { d ->
            val st = parseDateTime(d.startDate ?: "")
            st != null && st > targetTime
        }
        if (firstDriveAfter != null) {
            val detail = when (val r = repository.getDriveDetail(carId, firstDriveAfter.driveId)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> null
            }
            val firstPos = detail?.positions?.firstOrNull()
            return ActivityEnd(firstPos?.latitude, firstPos?.longitude, firstDriveAfter.odometerDetails?.odometerStart, firstDriveAfter.outsideTempAvg, targetTime, firstDriveAfter.startAddress, driveId = firstDriveAfter.driveId)
        }

        val firstChargeAfter = charges.firstOrNull { c ->
            val st = parseDateTime(c.startDate ?: "")
            st != null && st > targetTime
        }
        return firstChargeAfter?.let {
            ActivityEnd(it.latitude, it.longitude, it.odometer, it.outsideTempAvg, targetTime, it.address, chargeId = it.chargeId)
        }
    }

    /**
     * Searches up to 1 year back for the most recent drive or charge that ended before targetTime.
     * The API returns results most-recent-first, so show=1 is a cheap query.
     */
    private suspend fun findLastActivityInHistory(carId: Int, targetTime: LocalDateTime): ActivityEnd? = coroutineScope {
        val searchEnd = targetTime.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"
        val searchStart = targetTime.minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"

        val drivesDeferred = async {
            repository.getDrives(carId, searchStart, searchEnd, page = 1, show = 1)
        }
        val chargesDeferred = async {
            repository.getCharges(carId, searchStart, searchEnd, page = 1, show = 1)
        }

        val lastDrive = when (val r = drivesDeferred.await()) {
            is ApiResult.Success -> r.data.firstOrNull()
            is ApiResult.Error -> null
        }
        val lastCharge = when (val r = chargesDeferred.await()) {
            is ApiResult.Success -> r.data.firstOrNull()
            is ApiResult.Error -> null
        }

        val driveEndTime = lastDrive?.let { parseDateTime(it.endDate ?: "") }
        val chargeEndTime = lastCharge?.let { parseDateTime(it.endDate ?: "") }

        // Pick whichever ended later
        val useDrive = when {
            driveEndTime != null && chargeEndTime != null -> driveEndTime.isAfter(chargeEndTime)
            driveEndTime != null -> true
            else -> false
        }

        return@coroutineScope if (useDrive && lastDrive != null && driveEndTime != null) {
            val detail = when (val r = repository.getDriveDetail(carId, lastDrive.driveId)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> null
            }
            val lastPos = detail?.positions?.lastOrNull()
            ActivityEnd(
                lat = lastPos?.latitude,
                lon = lastPos?.longitude,
                odometer = lastDrive.odometerDetails?.odometerEnd,
                temp = lastDrive.outsideTempAvg,
                endTime = driveEndTime,
                geofenceName = lastDrive.endAddress,
                driveId = lastDrive.driveId
            )
        } else if (lastCharge != null && chargeEndTime != null) {
            ActivityEnd(
                lat = lastCharge.latitude,
                lon = lastCharge.longitude,
                odometer = lastCharge.odometer,
                temp = lastCharge.outsideTempAvg,
                endTime = chargeEndTime,
                geofenceName = lastCharge.address,
                chargeId = lastCharge.chargeId
            )
        } else null
    }

    private suspend fun fetchGeocodingAndWeather(lat: Double?, lon: Double?, targetTime: LocalDateTime) {
        if (lat == null || lon == null) return

        // Reverse geocode
        try {
            val location = geocodingRepository.reverseGeocodeWithCountry(lat, lon)
            _uiState.value = _uiState.value.copy(location = location)
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed", e)
        }

        // Fetch weather
        try {
            val dateStr = targetTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val response = openMeteoApi.getHistoricalWeather(
                latitude = lat,
                longitude = lon,
                startDate = dateStr,
                endDate = dateStr
            )
            if (response.isSuccessful) {
                val hourly = response.body()?.hourly
                val hour = targetTime.hour
                val timeIndex = hourly?.time?.indexOfFirst { timeStr ->
                    try {
                        LocalDateTime.parse(timeStr).hour == hour
                    } catch (e: Exception) { false }
                } ?: -1

                if (timeIndex >= 0) {
                    val temp = hourly?.temperature2m?.getOrNull(timeIndex)
                    val code = hourly?.weatherCode?.getOrNull(timeIndex) ?: 0
                    _uiState.value = _uiState.value.copy(
                        weatherTemperature = temp,
                        weatherCondition = WeatherCondition.fromWmoCode(code)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
        }
    }

    private fun findActiveDrive(drives: List<DriveData>, targetTime: LocalDateTime): DriveData? {
        return drives.firstOrNull { drive ->
            val start = parseDateTime(drive.startDate ?: "") ?: return@firstOrNull false
            val end = parseDateTime(drive.endDate ?: "") ?: return@firstOrNull false
            !targetTime.isBefore(start) && !targetTime.isAfter(end)
        }
    }

    private fun findActiveCharge(charges: List<ChargeData>, targetTime: LocalDateTime): ChargeData? {
        return charges.firstOrNull { charge ->
            val start = parseDateTime(charge.startDate ?: "") ?: return@firstOrNull false
            val end = parseDateTime(charge.endDate ?: "") ?: return@firstOrNull false
            !targetTime.isBefore(start) && !targetTime.isAfter(end)
        }
    }

    private fun parseDateTime(dateStr: String): LocalDateTime? {
        if (dateStr.isBlank()) return null
        return try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(dateStr.replace("Z", ""))
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun parseEndLatFromAddress(drive: DriveData): Double? = null
    private fun parseEndLonFromAddress(drive: DriveData): Double? = null

    companion object {
        private const val TAG = "WhereWasIViewModel"
    }
}
