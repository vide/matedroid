package com.matedroid.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GlobalSettingsResponse(
    @Json(name = "data") val data: GlobalSettingsData? = null
)

@JsonClass(generateAdapter = true)
data class GlobalSettingsData(
    @Json(name = "settings") val settings: GlobalSettings? = null
)

@JsonClass(generateAdapter = true)
data class GlobalSettings(
    @Json(name = "teslamate_urls") val teslamateUrls: TeslamateUrls? = null,
    @Json(name = "teslamate_units") val teslamateUnits: TeslamateUnits? = null
)

@JsonClass(generateAdapter = true)
data class TeslamateUrls(
    @Json(name = "base_url") val baseUrl: String? = null,
    @Json(name = "grafana_url") val grafanaUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class TeslamateUnits(
    @Json(name = "unit_of_length") val unitOfLength: String? = null,
    @Json(name = "unit_of_temperature") val unitOfTemperature: String? = null
)
