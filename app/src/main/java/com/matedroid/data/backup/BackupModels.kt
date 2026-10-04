package com.matedroid.data.backup

import com.squareup.moshi.JsonClass

/**
 * Layout version of the backup file.
 *
 * Bump it only for a change an older release could not make sense of. Adding a field or a
 * whole new section does not need one: unknown names are skipped on the way in, and
 * missing ones fall back to the defaults declared here.
 */
const val BACKUP_FORMAT_VERSION = 1

/**
 * What sits at the top of the file, before any of the data.
 *
 * [cars] is the bridge between two installations. Teslamate numbers cars per database, so
 * the ids in a backup only mean something against the server it came from; matching on the
 * VIN lets a restore onto a rebuilt Teslamate land the trips on the right car. It is
 * best-effort — the server may be unreachable at export time, in which case the ids are
 * taken at face value.
 *
 * [databaseVersion] guards the one section whose shape follows the local database schema.
 */
@JsonClass(generateAdapter = true)
data class BackupHeader(
    val format: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val databaseVersion: Int = 0,
    val sections: List<String> = emptyList(),
    val cars: List<BackupCar> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupCar(
    val carId: Int,
    val vin: String? = null,
    val name: String? = null
)

/**
 * Settings worth carrying between installations.
 *
 * The API token and the HTTP Basic Auth username and password are deliberately absent: a
 * backup is shared through whatever app the user picks, and a file that opens a Teslamate
 * server should not be sitting in a chat thread. The server URLs stay, so a restore leaves
 * only the token to type back in.
 *
 * Every field is nullable and means "the file did not carry this" — a restore then leaves
 * the current value alone.
 */
@JsonClass(generateAdapter = true)
data class BackupSettings(
    val serverUrl: String? = null,
    val secondaryServerUrl: String? = null,
    val teslamateBaseUrl: String? = null,
    val acceptInvalidCerts: Boolean? = null,
    val connectTimeoutSeconds: Int? = null,
    val currencyCode: String? = null,
    val costPerKwhBasis: String? = null,
    val isImperial: Boolean? = null,
    val showShortDrivesCharges: Boolean? = null,
    val shortDriveMinDurationMin: Int? = null,
    val shortDriveMinDistance: Double? = null,
    val shortChargeMinEnergyKwh: Double? = null,
    val highSocWarningThreshold: Int? = null,
    val lowSocWarningThreshold: Int? = null,
    val lastSelectedCarId: Int? = null,
    val carImageOverrides: Map<String, BackupCarImage> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class BackupCarImage(
    val variant: String,
    val wheelCode: String
)

/**
 * A saved trip with everything needed to rebuild it, minus its row id — the importer
 * always allocates a fresh one rather than trusting the numbering of another install.
 */
@JsonClass(generateAdapter = true)
data class BackupTrip(
    val carId: Int,
    val name: String? = null,
    val source: String = "AUTO_DETECTED",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val legs: List<BackupTripLeg> = emptyList(),
    val consumedFingerprints: List<String> = emptyList()
)

/**
 * One drive or charge inside a trip.
 *
 * [startDate] is the Teslamate start timestamp of that drive or charge as the exporting
 * phone had it. It is the natural key behind [id]: if a restore lands on a Teslamate whose
 * numbering has moved, the timestamp still identifies the drive, and the importer can put
 * the trip back together instead of pointing it at a stranger's drive.
 */
@JsonClass(generateAdapter = true)
data class BackupTripLeg(
    val type: String,
    val id: Int,
    val position: Int,
    val startDate: String? = null
)

@JsonClass(generateAdapter = true)
data class BackupSentryEvent(
    val carId: Int,
    val detectedAt: Long,
    val sessionStartedAt: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)

/** One cell of the reverse-geocoding grid: a place name the app paid a lookup for. */
@JsonClass(generateAdapter = true)
data class BackupPlace(
    val gridLat: Int,
    val gridLon: Int,
    val countryCode: String? = null,
    val countryName: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val cachedAt: Long = 0L
)

/**
 * One cached route segment. [points] is the packed (latitude, longitude) blob Room stores,
 * Base64-encoded so it survives a JSON round trip untouched.
 */
@JsonClass(generateAdapter = true)
data class BackupTripRoute(
    val tripKey: String,
    val segmentIndex: Int,
    val points: String,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class BackupTripCountries(
    val tripKey: String,
    val countries: String,
    val createdAt: Long = 0L
)
