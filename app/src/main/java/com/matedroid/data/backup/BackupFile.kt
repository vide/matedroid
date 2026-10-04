package com.matedroid.data.backup

import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate
import com.matedroid.data.local.entity.DriveSummary
import com.matedroid.data.local.entity.SyncState
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import okio.BufferedSink
import okio.BufferedSource

/**
 * The backup file itself: one JSON object, written and read a section at a time.
 *
 * Neither half ever holds the whole file in memory. Writing asks [BackupSource] for one
 * section, streams it to the sink and lets it go before asking for the next; reading hands
 * [BackupVisitor] batches of rows as they come off the wire. That matters because the two
 * optional heavyweight sections — cached route lines and a fully synced set of drives and
 * charges — can together be tens of megabytes on a phone that has been collecting for years.
 *
 * Unknown names are skipped on the way in and missing ones fall back to the defaults on the
 * data classes, so a file from a newer release still restores everything this one understands.
 */
object BackupFile {

    /** Rows handed to a visitor at a time while reading. */
    const val BATCH_SIZE = 500

    private const val NAME_HEADER = "header"
    private const val NAME_SETTINGS = "settings"
    private const val NAME_TRIPS = "trips"
    private const val NAME_SENTRY = "sentryEvents"
    private const val NAME_PLACES = "places"
    private const val NAME_TRIP_ROUTES = "tripRoutes"
    private const val NAME_TRIP_COUNTRIES = "tripCountries"
    private const val NAME_STATS = "stats"
    private const val NAME_SYNC_STATES = "syncStates"
    private const val NAME_DRIVES = "drives"
    private const val NAME_CHARGES = "charges"
    private const val NAME_DRIVE_AGGREGATES = "driveAggregates"
    private const val NAME_CHARGE_AGGREGATES = "chargeAggregates"

    suspend fun write(sink: BufferedSink, moshi: Moshi, source: BackupSource) {
        val writer = JsonWriter.of(sink)
        writer.setIndent("  ")
        writer.beginObject()

        writer.name(NAME_HEADER)
        moshi.adapter(BackupHeader::class.java).toJson(writer, source.header())

        source.settings()?.let {
            writer.name(NAME_SETTINGS)
            moshi.adapter(BackupSettings::class.java).toJson(writer, it)
        }

        writer.array(moshi, NAME_TRIPS, BackupTrip::class.java, source.trips())
        writer.array(moshi, NAME_SENTRY, BackupSentryEvent::class.java, source.sentryEvents())
        writer.array(moshi, NAME_PLACES, BackupPlace::class.java, source.places())
        writer.array(moshi, NAME_TRIP_ROUTES, BackupTripRoute::class.java, source.tripRoutes())
        writer.array(
            moshi, NAME_TRIP_COUNTRIES, BackupTripCountries::class.java, source.tripCountries()
        )

        writer.name(NAME_STATS)
        writer.beginObject()
        writer.array(moshi, NAME_SYNC_STATES, SyncState::class.java, source.syncStates())
        writer.array(moshi, NAME_DRIVES, DriveSummary::class.java, source.drives())
        writer.array(moshi, NAME_CHARGES, ChargeSummary::class.java, source.charges())
        writer.array(
            moshi, NAME_DRIVE_AGGREGATES, DriveDetailAggregate::class.java, source.driveAggregates()
        )
        writer.array(
            moshi,
            NAME_CHARGE_AGGREGATES,
            ChargeDetailAggregate::class.java,
            source.chargeAggregates()
        )
        writer.endObject()

        writer.endObject()
        writer.flush()
    }

    /**
     * Read a backup, handing each section to [visitor] as it is parsed.
     *
     * Throws [okio.IOException] or [com.squareup.moshi.JsonDataException] on anything that is
     * not a backup file — callers turn that into "this file can't be read".
     */
    suspend fun read(source: BufferedSource, moshi: Moshi, visitor: BackupVisitor) {
        val reader = JsonReader.of(source)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                NAME_HEADER ->
                    moshi.adapter(BackupHeader::class.java).fromJson(reader)
                        ?.let { visitor.onHeader(it) }

                NAME_SETTINGS ->
                    moshi.adapter(BackupSettings::class.java).fromJson(reader)
                        ?.let { visitor.onSettings(it) }

                NAME_TRIPS ->
                    reader.array(moshi, BackupTrip::class.java) { visitor.onTrips(it) }

                NAME_SENTRY ->
                    reader.array(moshi, BackupSentryEvent::class.java) { visitor.onSentryEvents(it) }

                NAME_PLACES ->
                    reader.array(moshi, BackupPlace::class.java) { visitor.onPlaces(it) }

                NAME_TRIP_ROUTES ->
                    reader.array(moshi, BackupTripRoute::class.java) { visitor.onTripRoutes(it) }

                NAME_TRIP_COUNTRIES ->
                    reader.array(moshi, BackupTripCountries::class.java) {
                        visitor.onTripCountries(it)
                    }

                NAME_STATS -> reader.readStats(moshi, visitor)

                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private suspend fun JsonReader.readStats(moshi: Moshi, visitor: BackupVisitor) {
        if (peek() == JsonReader.Token.NULL) {
            nextNull<Unit>()
            return
        }
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                NAME_SYNC_STATES ->
                    array(moshi, SyncState::class.java) { visitor.onSyncStates(it) }

                NAME_DRIVES ->
                    array(moshi, DriveSummary::class.java) { visitor.onDrives(it) }

                NAME_CHARGES ->
                    array(moshi, ChargeSummary::class.java) { visitor.onCharges(it) }

                NAME_DRIVE_AGGREGATES ->
                    array(moshi, DriveDetailAggregate::class.java) { visitor.onDriveAggregates(it) }

                NAME_CHARGE_AGGREGATES ->
                    array(moshi, ChargeDetailAggregate::class.java) {
                        visitor.onChargeAggregates(it)
                    }

                else -> skipValue()
            }
        }
        endObject()
    }

    private fun <T : Any> JsonWriter.array(
        moshi: Moshi,
        name: String,
        type: Class<T>,
        items: List<T>
    ) {
        val adapter: JsonAdapter<T> = moshi.adapter(type)
        name(name)
        beginArray()
        items.forEach { adapter.toJson(this, it) }
        endArray()
    }

    private suspend fun <T : Any> JsonReader.array(
        moshi: Moshi,
        type: Class<T>,
        onBatch: suspend (List<T>) -> Unit
    ) {
        if (peek() == JsonReader.Token.NULL) {
            nextNull<Unit>()
            return
        }
        val adapter: JsonAdapter<T> = moshi.adapter(type)
        beginArray()
        val batch = ArrayList<T>(BATCH_SIZE)
        while (hasNext()) {
            adapter.fromJson(this)?.let { batch.add(it) }
            if (batch.size >= BATCH_SIZE) {
                onBatch(batch.toList())
                batch.clear()
            }
        }
        endArray()
        if (batch.isNotEmpty()) onBatch(batch.toList())
    }
}

/**
 * Where [BackupFile.write] pulls each section from.
 *
 * Every method is called exactly once, in file order, and a section the user left out
 * simply returns an empty list — the array is still written, so the shape of the file never
 * depends on the choices made on the export screen.
 */
interface BackupSource {
    suspend fun header(): BackupHeader
    suspend fun settings(): BackupSettings?
    suspend fun trips(): List<BackupTrip>
    suspend fun sentryEvents(): List<BackupSentryEvent>
    suspend fun places(): List<BackupPlace>
    suspend fun tripRoutes(): List<BackupTripRoute>
    suspend fun tripCountries(): List<BackupTripCountries>
    suspend fun syncStates(): List<SyncState>
    suspend fun drives(): List<DriveSummary>
    suspend fun charges(): List<ChargeSummary>
    suspend fun driveAggregates(): List<DriveDetailAggregate>
    suspend fun chargeAggregates(): List<ChargeDetailAggregate>
}

/**
 * Receives a backup as it is read. Batch methods are called repeatedly, in file order,
 * with at most [BackupFile.BATCH_SIZE] rows each.
 */
interface BackupVisitor {
    suspend fun onHeader(header: BackupHeader) = Unit
    suspend fun onSettings(settings: BackupSettings) = Unit
    suspend fun onTrips(batch: List<BackupTrip>) = Unit
    suspend fun onSentryEvents(batch: List<BackupSentryEvent>) = Unit
    suspend fun onPlaces(batch: List<BackupPlace>) = Unit
    suspend fun onTripRoutes(batch: List<BackupTripRoute>) = Unit
    suspend fun onTripCountries(batch: List<BackupTripCountries>) = Unit
    suspend fun onSyncStates(batch: List<SyncState>) = Unit
    suspend fun onDrives(batch: List<DriveSummary>) = Unit
    suspend fun onCharges(batch: List<ChargeSummary>) = Unit
    suspend fun onDriveAggregates(batch: List<DriveDetailAggregate>) = Unit
    suspend fun onChargeAggregates(batch: List<ChargeDetailAggregate>) = Unit
}
