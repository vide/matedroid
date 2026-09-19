package com.matedroid.data.backup

import com.matedroid.data.local.entity.SavedTripLeg

/**
 * Translates the car ids in a backup into the car ids this phone's server uses.
 *
 * Teslamate numbers cars per database, so a backup restored against a rebuilt server can
 * arrive with ids that now belong to a different car — or to no car at all. The VIN is the
 * one identifier both sides agree on, so it decides the mapping; anything without a VIN on
 * either side keeps its original id, which is the right answer for the ordinary case of
 * restoring onto the same server.
 */
class BackupCarMap private constructor(private val byExportedId: Map<Int, Int>) {

    /** True when at least one car actually changed id. */
    val isRemapping: Boolean = byExportedId.any { (from, to) -> from != to }

    operator fun get(exportedCarId: Int): Int = byExportedId[exportedCarId] ?: exportedCarId

    companion object {
        val Identity = BackupCarMap(emptyMap())

        fun of(backupCars: List<BackupCar>, localCars: List<BackupCar>): BackupCarMap {
            if (backupCars.isEmpty() || localCars.isEmpty()) return Identity
            val localByVin = localCars
                .filter { !it.vin.isNullOrBlank() }
                .associateBy { it.vin!!.uppercase() }
            if (localByVin.isEmpty()) return Identity

            val mapping = buildMap {
                backupCars.forEach { car ->
                    val vin = car.vin?.takeIf { it.isNotBlank() }?.uppercase() ?: return@forEach
                    val local = localByVin[vin] ?: return@forEach
                    put(car.carId, local.carId)
                }
            }
            return if (mapping.isEmpty()) Identity else BackupCarMap(mapping)
        }
    }
}

/** What a restore can ask about the drives and charges already on this phone. */
interface LegIndex {
    /** Teslamate start timestamp of the drive or charge with this id, or null if unknown here. */
    suspend fun startDateOf(carId: Int, legType: String, legId: Int): String?

    /** Id of the drive or charge that starts at this timestamp, or null if there is none. */
    suspend fun idAtStartDate(carId: Int, legType: String, startDate: String): Int?

    /** How many drives or charges this phone holds for the car. Zero means "not synced yet". */
    suspend fun count(carId: Int, legType: String): Int
}

/** What became of one leg of a restored trip. */
sealed interface LegResolution {
    /** The id in the file is right, either verified against the local copy or taken on trust. */
    data class Kept(val legId: Int) : LegResolution

    /** The drive moved to a new id; found again by its start timestamp. */
    data class Remapped(val legId: Int) : LegResolution

    /** Not on this phone. Kept as-is: a later sync may still bring it in. */
    data class Pending(val legId: Int) : LegResolution

    /** That id belongs to a different drive here, and the original is nowhere to be found. */
    data object Dropped : LegResolution
}

/**
 * Decides which drive or charge each leg of a restored trip should point at.
 *
 * The happy path — same phone, same server — verifies the id against the local copy and
 * keeps it. The rest of the policy exists for the two ways a restore can land somewhere
 * else: a Teslamate rebuilt from scratch, where the ids have shifted under the same
 * drives, and a fresh install that has not synced yet, where nothing can be checked at all.
 *
 * The one case it refuses to guess at is an id that exists here but starts at a different
 * moment: that is somebody else's drive, and a trip is better off short than wrong.
 */
class BackupLegResolver(private val index: LegIndex) {

    suspend fun resolve(carId: Int, leg: BackupTripLeg): LegResolution {
        val startDate = leg.startDate?.takeIf { it.isNotBlank() }
            ?: return LegResolution.Kept(leg.id)

        val localStartDate = index.startDateOf(carId, leg.type, leg.id)
        if (localStartDate == startDate) return LegResolution.Kept(leg.id)

        if (localStartDate == null && index.count(carId, leg.type) == 0) {
            return LegResolution.Kept(leg.id)
        }

        val byStartDate = index.idAtStartDate(carId, leg.type, startDate)
        return when {
            byStartDate != null && byStartDate == leg.id -> LegResolution.Kept(leg.id)
            byStartDate != null -> LegResolution.Remapped(byStartDate)
            localStartDate == null -> LegResolution.Pending(leg.id)
            else -> LegResolution.Dropped
        }
    }
}

/**
 * Identity of a trip for the purpose of "is this one already here?".
 *
 * Two trips are the same when they cover the same drives and charges for the same car,
 * whatever they happen to be called — a merge the user made on the old phone and the same
 * merge already remade on the new one should not end up listed twice.
 */
fun tripSignature(carId: Int, legs: List<SavedTripLeg>): String =
    legs.map { "${it.legType}:${it.legId}" }.sorted().joinToString(",", prefix = "$carId|")

/** Identity of a sentry alert: the same car flagging the same moment. */
fun sentryKey(carId: Int, detectedAt: Long): String = "$carId|$detectedAt"
