package com.matedroid.data.demo

import com.matedroid.data.api.TeslamateApi
import com.matedroid.data.api.models.BatteryHealthData
import com.matedroid.data.api.models.BatteryHealthResponse
import com.matedroid.data.api.models.CarStatusData
import com.matedroid.data.api.models.CarStatusResponse
import com.matedroid.data.api.models.CarsData
import com.matedroid.data.api.models.CarsResponse
import com.matedroid.data.api.models.ChargeDetailCar
import com.matedroid.data.api.models.ChargeDetailData
import com.matedroid.data.api.models.ChargeDetailResponse
import com.matedroid.data.api.models.ChargesData
import com.matedroid.data.api.models.ChargesResponse
import com.matedroid.data.api.models.DriveDetailCar
import com.matedroid.data.api.models.DriveDetailData
import com.matedroid.data.api.models.DriveDetailResponse
import com.matedroid.data.api.models.DrivesData
import com.matedroid.data.api.models.DrivesResponse
import com.matedroid.data.api.models.GlobalSettings
import com.matedroid.data.api.models.GlobalSettingsData
import com.matedroid.data.api.models.GlobalSettingsResponse
import com.matedroid.data.api.models.PingResponse
import com.matedroid.data.api.models.TeslamateUnits
import com.matedroid.data.api.models.TeslamateUrls
import com.matedroid.data.api.models.UpdatesResponse
import com.matedroid.data.api.models.UpdatesResponseData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * A [TeslamateApi] that answers from [DemoDataSet] instead of the network.
 *
 * Implementing the Retrofit interface — rather than short-circuiting somewhere in the
 * repository — is what keeps demo mode honest: every screen, worker and notification path
 * runs exactly the code it runs against a real server, including the query parameters, the
 * pagination and the "no active charge" reply that is a 200 with an error field rather than
 * a failure. Nothing downstream knows or needs to know that the server isn't there.
 */
internal class DemoTeslamateApi : TeslamateApi {

    /**
     * Built once per process. The dataset is anchored to the moment it is created, and
     * rebuilding it mid-session would renumber every drive under the screens holding those
     * ids — a process that survives long enough for the anchor to drift is rarer than that
     * would be confusing.
     */
    private val data: DemoDataSet by lazy {
        DemoDataSet(Instant.now(), ZoneId.systemDefault())
    }

    private val carRef get() = DriveDetailCar(carId = data.car.carId, carName = data.car.name)

    override suspend fun ping(): Response<PingResponse> =
        Response.success(PingResponse(ping = "pong"))

    override suspend fun getCars(): Response<CarsResponse> =
        Response.success(CarsResponse(CarsData(cars = listOf(data.car))))

    override suspend fun getCar(carId: Int): Response<CarsResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        return Response.success(CarsResponse(CarsData(cars = listOf(data.car))))
    }

    override suspend fun getCarStatus(carId: Int): Response<CarStatusResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        return Response.success(
            CarStatusResponse(
                CarStatusData(status = data.status(Instant.now()), units = data.units)
            )
        )
    }

    override suspend fun getCharges(
        carId: Int,
        startDate: String?,
        endDate: String?,
        page: Int?,
        show: Int?
    ): Response<ChargesResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        val charges = data.charges(Instant.now())
            .filter { inRange(it.startDate, startDate, endDate) }
            .paginate(page, show)
        return Response.success(ChargesResponse(ChargesData(charges = charges)))
    }

    override suspend fun getChargeDetail(carId: Int, chargeId: Int): Response<ChargeDetailResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        val charge = data.chargeDetail(chargeId, Instant.now()) ?: return notFound()
        return Response.success(
            ChargeDetailResponse(
                ChargeDetailData(
                    car = ChargeDetailCar(carId = data.car.carId, carName = data.car.name),
                    charge = charge
                )
            )
        )
    }

    override suspend fun getCurrentCharge(carId: Int): Response<ChargeDetailResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        val charge = data.currentCharge(Instant.now())
            // TeslamateAPI answers 200 with an error field, not a 4xx, when nothing is
            // plugged in. The repository relies on that to tell "idle" from "unreachable".
            ?: return Response.success(
                ChargeDetailResponse(error = "No active charging in progress.")
            )
        return Response.success(
            ChargeDetailResponse(
                ChargeDetailData(
                    car = ChargeDetailCar(carId = data.car.carId, carName = data.car.name),
                    charge = charge
                )
            )
        )
    }

    override suspend fun getDrives(
        carId: Int,
        startDate: String?,
        endDate: String?,
        page: Int?,
        show: Int?
    ): Response<DrivesResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        val drives = data.drives
            .filter { inRange(it.startDate, startDate, endDate) }
            .paginate(page, show)
        return Response.success(DrivesResponse(DrivesData(drives = drives)))
    }

    override suspend fun getDriveDetail(carId: Int, driveId: Int): Response<DriveDetailResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        val drive = data.driveDetail(driveId) ?: return notFound()
        return Response.success(
            DriveDetailResponse(DriveDetailData(car = carRef, drive = drive))
        )
    }

    override suspend fun getBatteryHealth(carId: Int): Response<BatteryHealthResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        return Response.success(
            BatteryHealthResponse(BatteryHealthData(batteryHealth = data.batteryHealth))
        )
    }

    override suspend fun getUpdates(
        carId: Int,
        page: Int?,
        show: Int?
    ): Response<UpdatesResponse> {
        if (carId != DemoMode.CAR_ID) return notFound()
        return Response.success(
            UpdatesResponse(UpdatesResponseData(updates = data.updates.paginate(page, show)))
        )
    }

    override suspend fun getGlobalSettings(): Response<GlobalSettingsResponse> =
        Response.success(
            GlobalSettingsResponse(
                GlobalSettingsData(
                    GlobalSettings(
                        // Deliberately empty: there is no TeslaMate web UI behind the demo,
                        // and a base URL here would put "edit cost in TeslaMate" links on
                        // the charge screens that could only lead nowhere.
                        teslamateUrls = TeslamateUrls(baseUrl = null, grafanaUrl = null),
                        teslamateUnits = TeslamateUnits(unitOfLength = "km", unitOfTemperature = "C")
                    )
                )
            )
        )

    // ---------------------------------------------------------------- helpers

    private fun <T> notFound(): Response<T> =
        Response.error(404, """{"error":"not found"}""".toResponseBody(JSON))

    private fun <T> List<T>.paginate(page: Int?, show: Int?): List<T> {
        if (show == null || show <= 0) return this
        val index = (page ?: 1).coerceAtLeast(1) - 1
        return drop(index * show).take(show)
    }

    private fun inRange(date: String?, from: String?, to: String?): Boolean {
        val at = date?.let(::parseTimestamp) ?: return true
        val after = from?.let(::parseTimestamp)?.let { !at.isBefore(it) } ?: true
        val before = to?.let(::parseTimestamp)?.let { !at.isAfter(it) } ?: true
        return after && before
    }

    /**
     * Accepts what TeslamateAPI's own `parseDateParam` accepts: RFC3339 with an offset, and
     * the offset-less `2006-01-02 15:04:05` form, which is read as local time.
     */
    private fun parseTimestamp(value: String): Instant? = runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.recoverCatching {
        LocalDateTime.parse(value.trim().replace(' ', 'T'))
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }.getOrNull()

    private companion object {
        private val JSON = "application/json".toMediaType()
    }
}
