package com.matedroid.data.api

import com.matedroid.data.api.models.BatteryHealthResponse
import com.matedroid.data.api.models.CarsResponse
import com.matedroid.data.api.models.CarStatusResponse
import com.matedroid.data.api.models.ChargeDetailResponse
import com.matedroid.data.api.models.ChargesResponse
import com.matedroid.data.api.models.DriveDetailResponse
import com.matedroid.data.api.models.DrivesResponse
import com.matedroid.data.api.models.GlobalSettingsResponse
import com.matedroid.data.api.models.PingResponse
import com.matedroid.data.api.models.UpdatesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TeslamateApi {

    @GET("api/ping")
    suspend fun ping(): Response<PingResponse>

    @GET("api/v1/cars")
    suspend fun getCars(): Response<CarsResponse>

    @GET("api/v1/cars/{carId}")
    suspend fun getCar(
        @Path("carId") carId: Int
    ): Response<CarsResponse>

    @GET("api/v1/cars/{carId}/status")
    suspend fun getCarStatus(
        @Path("carId") carId: Int
    ): Response<CarStatusResponse>

    @GET("api/v1/cars/{carId}/charges")
    suspend fun getCharges(
        @Path("carId") carId: Int,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("page") page: Int? = null,
        @Query("show") show: Int? = null
    ): Response<ChargesResponse>

    @GET("api/v1/cars/{carId}/charges/{chargeId}")
    suspend fun getChargeDetail(
        @Path("carId") carId: Int,
        @Path("chargeId") chargeId: Int
    ): Response<ChargeDetailResponse>

    @GET("api/v1/cars/{carId}/drives")
    suspend fun getDrives(
        @Path("carId") carId: Int,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("page") page: Int? = null,
        @Query("show") show: Int? = null
    ): Response<DrivesResponse>

    @GET("api/v1/cars/{carId}/drives/{driveId}")
    suspend fun getDriveDetail(
        @Path("carId") carId: Int,
        @Path("driveId") driveId: Int
    ): Response<DriveDetailResponse>

    @GET("api/v1/cars/{carId}/battery-health")
    suspend fun getBatteryHealth(
        @Path("carId") carId: Int
    ): Response<BatteryHealthResponse>

    @GET("api/v1/cars/{carId}/updates")
    suspend fun getUpdates(
        @Path("carId") carId: Int,
        @Query("page") page: Int? = null,
        @Query("show") show: Int? = null
    ): Response<UpdatesResponse>

    @GET("api/v1/globalsettings")
    suspend fun getGlobalSettings(): Response<GlobalSettingsResponse>
}
