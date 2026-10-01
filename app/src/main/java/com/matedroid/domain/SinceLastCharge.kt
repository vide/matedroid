package com.matedroid.domain

import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.TeslamateRepository
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregate consumption since the last charge that actually added energy —
 * the counterpart of the car's own "Since Last Charge" trip meter (issue #339).
 *
 * All distance values are already in the user's unit system (TeslamateAPI
 * pre-converts them), so [avgConsumptionWh] is Wh per that same unit.
 */
data class SinceLastChargeStats(
    /** End of the anchoring charge, RFC3339 as returned by the API. */
    val chargeEndDate: String,
    /** Battery level at the end of the anchoring charge, for the SoC-used delta. */
    val chargeEndBatteryLevel: Int?,
    val energyConsumedKwh: Double,
    val distance: Double,
    val driveCount: Int
) {
    val avgConsumptionWh: Double?
        get() = if (distance > 0 && energyConsumedKwh > 0) energyConsumedKwh * 1000 / distance else null
}

@Singleton
class SinceLastChargeRepository @Inject constructor(
    private val repository: TeslamateRepository
) {

    /**
     * Returns null when there is no completed energy-adding charge yet, or when
     * either API call fails — the dashboard simply hides the card in that case.
     */
    suspend fun getStats(carId: Int): SinceLastChargeStats? {
        val charges = (repository.getCharges(carId, page = 1, show = ANCHOR_SEARCH_WINDOW)
            as? ApiResult.Success)?.data ?: return null
        val anchor = findAnchorCharge(charges) ?: return null
        val startDate = toUtcRfc3339(anchor.endDate ?: return null) ?: return null
        val drives = (repository.getDrives(carId, startDate = startDate)
            as? ApiResult.Success)?.data ?: return null
        return aggregate(anchor, drives)
    }

    companion object {
        /** Recent charges scanned for the anchor; plug-in blips that added nothing are skipped. */
        private const val ANCHOR_SEARCH_WINDOW = 50

        /**
         * A charge must add at least this much to reset the cycle — brief plug-ins
         * are logged as 0 kWh charges and must not count as "the last charge".
         */
        private const val MIN_ENERGY_ADDED_KWH = 0.1

        /** Charges come back newest-first; pick the newest completed one that added energy. */
        fun findAnchorCharge(charges: List<ChargeData>): ChargeData? =
            charges.firstOrNull {
                it.endDate != null && (it.chargeEnergyAdded ?: 0.0) >= MIN_ENERGY_ADDED_KWH
            }

        fun aggregate(anchor: ChargeData, drives: List<DriveData>): SinceLastChargeStats =
            SinceLastChargeStats(
                chargeEndDate = anchor.endDate.orEmpty(),
                chargeEndBatteryLevel = anchor.endBatteryLevel,
                energyConsumedKwh = drives.sumOf { it.energyConsumedNet ?: 0.0 },
                distance = drives.sumOf { it.distance ?: 0.0 },
                driveCount = drives.size
            )

        /**
         * The API returns dates with a numeric offset (`+02:00`); a literal `+` in a
         * query parameter risks decoding as a space server-side, so convert to UTC `Z`
         * form (which parseDateParam accepts) before using it as a startDate filter.
         */
        fun toUtcRfc3339(rfc3339: String): String? = try {
            OffsetDateTime.parse(rfc3339).toInstant().truncatedTo(ChronoUnit.SECONDS).toString()
        } catch (_: Exception) {
            null
        }
    }
}
