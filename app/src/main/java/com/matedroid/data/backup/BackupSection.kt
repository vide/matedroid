package com.matedroid.data.backup

/**
 * One switchable chunk of a backup file.
 *
 * [alwaysIncluded] sections are the reason the feature exists: they live only on this
 * phone and no Teslamate server can hand them back. The rest are offered as checkboxes on
 * every export, and [onByDefault] decides how each one starts out — pre-ticked when
 * rebuilding it is slow (place names are looked up one per second) and unticked when it is
 * merely another download away.
 *
 * [key] is what lands in the JSON file, so renaming one breaks older backups.
 */
enum class BackupSection(
    val key: String,
    val alwaysIncluded: Boolean = false,
    val onByDefault: Boolean = false
) {
    /** Saved trips: names, merges and edits. Nothing else knows about them. */
    TRIPS("trips", alwaysIncluded = true),

    /** App settings, minus the API token and HTTP Basic Auth credentials. */
    SETTINGS("settings", alwaysIncluded = true),

    /** Sentry alerts the app spotted while polling. Teslamate keeps no record of these. */
    SENTRY("sentry", onByDefault = true),

    /** Reverse-geocoded place names, rebuilt at one lookup per second when lost. */
    PLACES("places", onByDefault = true),

    /** Cached trip route lines and country sequences. Re-downloadable, and bulky. */
    TRIP_MAPS("tripMaps"),

    /** Everything already synced from Teslamate: drives, charges and their aggregates. */
    STATS("stats");

    companion object {
        fun fromKey(key: String): BackupSection? = entries.firstOrNull { it.key == key }

        /** The sections the export screen asks about, in display order. */
        val optional: List<BackupSection> = entries.filter { !it.alwaysIncluded }

        /** How the export screen starts out before the user touches anything. */
        val exportDefaults: Set<BackupSection> =
            entries.filter { it.alwaysIncluded || it.onByDefault }.toSet()
    }
}

/** Whether an import adds to what is already on the phone or wipes it first. */
enum class ImportMode {
    /** Keep what is here, add what is missing. Duplicates are skipped, not doubled. */
    MERGE,

    /** Clear each restored section first, so the phone ends up matching the file. */
    REPLACE
}
