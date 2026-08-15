package com.matedroid.domain

import java.time.ZoneId

/**
 * Which clock the app renders timestamps in.
 *
 * TeslamateAPI returns every timestamp as server-local time *with* its UTC offset attached
 * (e.g. `2026-08-15T11:29:19+02:00`). That offset makes the instant unambiguous, so we can
 * render it in whichever zone the user prefers:
 *
 * - [MODE_SERVER] (default) keeps the wall clock exactly as TeslaMate sent it, i.e. the time
 *   where the car and the TeslaMate server are. This matches TeslaMate's own web UI, and for a
 *   car log it is usually what you want — a drive reads at the time it actually happened,
 *   even if you check the app from another country.
 * - [MODE_DEVICE] converts to the phone's current zone.
 * - Any other value is treated as an explicit [ZoneId] id (e.g. `Europe/Madrid`), for when
 *   neither the server nor the phone is on the clock the user wants — most commonly a
 *   TeslaMate instance left on the Docker default of UTC.
 *
 * Like [UnitSystem] and [ShortEntryFilter], this is a process-wide mirror of the stored
 * preference rather than something threaded through call sites: restored at app start by
 * [com.matedroid.MateDroidApp] and written through by
 * [com.matedroid.ui.screens.settings.SettingsViewModel]. That is what lets
 * [com.matedroid.util.parseIsoDateTime] stay a plain function — it has 26 call sites, and
 * passing a zone to each would be a far bigger change than the behaviour warrants.
 *
 * This affects displayed wall-clock times only. Elapsed-time math (e.g. "10 minutes ago") works
 * off the absolute instant and is correct under every mode.
 */
object AppTimeZone {
    /** Render timestamps in the zone TeslamateAPI sent them in. The default. */
    const val MODE_SERVER = "server"

    /** Render timestamps in the phone's current zone. */
    const val MODE_DEVICE = "device"

    @Volatile
    var mode: String = MODE_SERVER

    /**
     * The zone to convert timestamps into, or `null` to keep the timestamp's own offset.
     *
     * An unrecognised zone id falls back to `null` (server time) rather than throwing, so a
     * stale preference — a zone id dropped by a future tzdb update — degrades to the default
     * instead of breaking every date in the app.
     */
    fun displayZone(): ZoneId? = when (val current = mode) {
        MODE_SERVER -> null
        MODE_DEVICE -> ZoneId.systemDefault()
        else -> runCatching { ZoneId.of(current) }.getOrNull()
    }

    /** True when [mode] names an explicit zone rather than one of the automatic modes. */
    fun isFixedZone(): Boolean = mode != MODE_SERVER && mode != MODE_DEVICE
}
