package com.matedroid.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class NominatimAddress(
    val road: String? = null,
    val house_number: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
    @Json(name = "country_code") val countryCode: String? = null
)

@JsonClass(generateAdapter = true)
data class NominatimResponse(
    @Json(name = "display_name") val displayName: String? = null,
    val address: NominatimAddress? = null
)

/**
 * GeoJSON geometry for country boundaries.
 */
@JsonClass(generateAdapter = true)
data class GeoJsonGeometry(
    val type: String,
    // Coordinates can be deeply nested arrays for MultiPolygon
    // We'll parse this manually due to varying depth
    val coordinates: Any? = null
)

/**
 * Search result with optional GeoJSON polygon.
 */
@JsonClass(generateAdapter = true)
data class NominatimSearchResult(
    @Json(name = "place_id") val placeId: Long? = null,
    @Json(name = "osm_type") val osmType: String? = null,
    @Json(name = "osm_id") val osmId: Long? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val geojson: GeoJsonGeometry? = null
)

interface NominatimApi {
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Header("User-Agent") userAgent: String = "MateDroid/1.0 Android"
    ): Response<NominatimResponse>

    /**
     * Search for a country by ISO code and get its boundary polygon.
     */
    @GET("search")
    suspend fun searchCountryBoundary(
        @Query("country") countryCode: String,
        @Query("format") format: String = "json",
        @Query("polygon_geojson") polygonGeoJson: Int = 1,
        @Query("limit") limit: Int = 1,
        @Header("User-Agent") userAgent: String = "MateDroid/1.0 Android"
    ): Response<List<NominatimSearchResult>>
}
