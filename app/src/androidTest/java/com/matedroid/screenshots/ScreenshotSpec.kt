package com.matedroid.screenshots

import android.content.Context
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import com.matedroid.R
import com.matedroid.data.demo.DemoMode

/**
 * One screen the screenshot suite captures.
 *
 * @property id Stable identifier. It is the output file name (`<id>.jpg`), the value the
 *   `screenshotScreens` runner argument filters on, and the test name in the report.
 * @property alt Alt text for the README gallery.
 * @property group README gallery row. Consecutive specs with the same group share a `<p>`.
 * @property route `EXTRA_NAVIGATE_TO` value the activity is launched with; null for the dashboard.
 * @property exteriorColor `EXTRA_EXTERIOR_COLOR` value, which picks the palette. The old
 *   hand-run script varied this per screen for visual variety in the docs, and the gallery keeps
 *   that look.
 * @property ready The screen counts as loaded once any of these exists.
 * @property tapFirst When set, the first clickable node matching it is tapped before waiting
 *   for [ready]: how the list screens hand over to their detail screens, whose ids change daily.
 * @property gone When set, the capture also waits for this to disappear (sync banners).
 * @property required When set and absent after loading, the screen is skipped rather than
 *   captured: it is in a state the gallery does not want to show.
 * @property waitStable When true, the capture waits until the number of [ready] matches stops
 *   changing between polls, for screens that fill in row by row.
 * @property timeoutMs Upper bound on every wait in this spec.
 * @property settleMs Quiet time before the capture, for maps and images still painting.
 */
data class ScreenshotSpec(
    val id: String,
    val alt: String,
    val group: Int,
    val route: String?,
    val exteriorColor: String,
    val ready: List<BySelector>,
    val tapFirst: BySelector? = null,
    val gone: BySelector? = null,
    val required: BySelector? = null,
    val waitStable: Boolean = false,
    val timeoutMs: Long = 30_000L,
    val settleMs: Long = 1_500L,
) {
    override fun toString(): String = id
}

/**
 * The gallery, in README order. Visited Countries goes last on purpose: it is filled by the
 * geocoding worker, which has the whole run to finish while the other screens are captured.
 *
 * Markers are resolved from resources at runtime, so the suite works whatever locale the
 * device is in; the run script pins the device to en-US for the committed pictures.
 */
object ScreenshotSpecs {

    /** The demo car's own colour, as reported by the dataset. */
    private const val CAR_COLOR = "UltraRed"

    fun all(context: Context): List<ScreenshotSpec> {
        fun text(id: Int) = By.textContains(context.getString(id))
        fun desc(id: Int) = By.descContains(context.getString(id))

        return listOf(
            ScreenshotSpec(
                id = "main-dashboard",
                alt = "Main dashboard",
                group = 1,
                route = null,
                exteriorColor = CAR_COLOR,
                ready = listOf(desc(R.string.car_image_tap_for_stats)),
                // The location card carries a mini map.
                settleMs = 3_000L,
            ),
            ScreenshotSpec(
                id = "battery-health",
                alt = "Battery health",
                group = 1,
                route = "battery",
                exteriorColor = CAR_COLOR,
                ready = listOf(text(R.string.estimated_degradation)),
            ),
            ScreenshotSpec(
                id = "mileage",
                alt = "Mileage",
                group = 1,
                route = "mileage",
                exteriorColor = "DeepBlue",
                ready = listOf(text(R.string.mileage_by_year)),
            ),
            ScreenshotSpec(
                id = "software-versions",
                alt = "Software versions",
                group = 1,
                route = "updates",
                exteriorColor = "MidnightSilver",
                ready = listOf(text(R.string.software_update_history)),
            ),
            ScreenshotSpec(
                id = "drives",
                alt = "Drives",
                group = 2,
                route = "drives",
                exteriorColor = CAR_COLOR,
                ready = listOf(text(R.string.drive_history)),
            ),
            ScreenshotSpec(
                id = "drive-details",
                alt = "Drive details",
                group = 2,
                route = "drives",
                exteriorColor = CAR_COLOR,
                // Every drive row is "<from> → <to>"; the first one is the newest drive. Compose
                // exposes the texts as children of a text-less clickable card, hence hasDescendant.
                tapFirst = By.clickable(true).hasDescendant(By.textContains("→")),
                ready = listOf(text(R.string.route_map)),
                // Route map tiles plus the weather card.
                settleMs = 4_000L,
            ),
            ScreenshotSpec(
                id = "charges",
                alt = "Charges",
                group = 2,
                route = "charges",
                exteriorColor = "PearlWhite",
                ready = listOf(text(R.string.charge_history)),
            ),
            ScreenshotSpec(
                id = "charge-details",
                alt = "Charge details",
                group = 2,
                route = "charges",
                exteriorColor = "PearlWhite",
                // Every charge row carries a bare "kWh" unit label; the summary card's figures
                // are single "<n> kWh" strings, so the exact match only hits rows.
                tapFirst = By.clickable(true).hasDescendant(By.text("kWh")),
                ready = listOf(text(R.string.power_profile)),
                settleMs = 3_000L,
            ),
            ScreenshotSpec(
                id = "current-charge",
                alt = "Current charge",
                group = 3,
                route = "current_charge",
                exteriorColor = CAR_COLOR,
                ready = listOf(
                    text(R.string.current_charge_live),
                    text(R.string.current_charge_not_charging),
                ),
                // A "not charging" page is not worth a gallery slot; the run script pins the
                // demo clock inside a charging phase so this is only hit on unpinned runs.
                required = text(R.string.current_charge_live),
                settleMs = 3_000L,
            ),
            ScreenshotSpec(
                id = "stats-for-nerds",
                alt = "Stats for nerds",
                group = 3,
                route = "stats",
                exteriorColor = "DeepBlue",
                ready = listOf(text(R.string.stats_drives_overview)),
                // Stats are computed from the local database, which the sync worker fills on
                // first launch; the banner goes once it has caught up.
                gone = text(R.string.stats_syncing),
                timeoutMs = 120_000L,
            ),
            ScreenshotSpec(
                id = "visited-countries",
                alt = "Visited countries",
                group = 3,
                route = "countries_visited",
                exteriorColor = CAR_COLOR,
                // One chevron per country row; the rows appear as Nominatim answers.
                ready = listOf(desc(R.string.view_details)),
                waitStable = true,
                timeoutMs = 180_000L,
                settleMs = 3_000L,
            ),
        )
    }

    /** The car id every intent carries: the demo dataset has exactly one car. */
    const val CAR_ID: Int = DemoMode.CAR_ID
}
