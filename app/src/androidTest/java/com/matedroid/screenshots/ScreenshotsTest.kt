package com.matedroid.screenshots

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.matedroid.MainActivity
import com.matedroid.data.demo.DemoMode
import com.matedroid.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

/**
 * Captures the README gallery from demo mode, one screen per test case.
 *
 * The app is put into demo mode by writing the settings directly, so no server, mock or
 * onboarding tap is involved: every screen renders the generated sample dataset. Each spec in
 * [ScreenshotSpecs] launches [MainActivity] with the same intent extras the notification and
 * widget deep links use, waits for a marker that only exists once the data is on screen, and
 * saves a cropped, scaled JPEG. The crop uses the window's real system-bar insets, so the same
 * code works on any device or emulator profile.
 *
 * Output goes to the directory AGP pulls back after `connectedAndroidTest`
 * (`app/build/outputs/connected_android_test_additional_output/`), together with a
 * `manifest.tsv` the run script turns into the README gallery. Run it through
 * `scripts/screenshots.sh`, which also prepares the device; or directly with:
 *
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.matedroid.screenshots.ScreenshotsTest
 * ```
 *
 * Runner arguments (all optional):
 * - `screenshotClock`: ISO-8601 instant the demo clock is pinned to, so the live session is in
 *   a known phase. Only pass it when the device clock was set to the same instant, or the
 *   dashboard's "since" durations disagree with the demo data.
 * - `screenshotScreens`: comma-separated spec ids; the rest are skipped.
 * - `screenshotWidth`: width in pixels of the saved images (default 576, as in the README).
 */
@RunWith(Parameterized::class)
class ScreenshotsTest(private val spec: ScreenshotSpec) {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext get() = instrumentation.targetContext
    private val device: UiDevice by lazy { UiDevice.getInstance(instrumentation) }
    private val args get() = InstrumentationRegistry.getArguments()

    @Before
    fun prepare() {
        val requested = args.getString(ARG_SCREENS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        assumeTrue("$spec not in $ARG_SCREENS", requested == null || spec.id in requested)

        args.getString(ARG_CLOCK)?.let { iso ->
            DemoMode.clock = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)
        }

        // Same process as the app, same DataStore file: the activity launched below starts
        // configured for demo mode, with the one-time notification prompt already answered.
        runBlocking {
            val settings = SettingsDataStore(targetContext)
            settings.enterDemoMode()
            settings.saveNotificationPermissionAsked()
        }

        // Without this the activity launches under the keyguard: the semantic tree is there,
        // so every wait succeeds, but the pixels are the lock screen.
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("svc power stayon true")
        device.waitForIdle()
        dismissSystemDialogs()
    }

    @Test
    fun capture() {
        val intent = Intent(targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("EXTRA_CAR_ID", ScreenshotSpecs.CAR_ID)
            putExtra("EXTRA_EXTERIOR_COLOR", spec.exteriorColor)
            spec.route?.let { putExtra("EXTRA_NAVIGATE_TO", it) }
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            try {
                spec.tapFirst?.let { tapFirstRow(it) }
                waitForAny(spec.ready, spec.timeoutMs)
                spec.gone?.let { waitGone(it, spec.timeoutMs) }
            } catch (e: IllegalStateException) {
                dumpFailure()
                throw e
            }
            spec.required?.let { required ->
                assumeTrue("$spec: required state not on screen, skipped", device.hasObject(required))
            }
            if (spec.waitStable) waitForStableCount(spec.ready, spec.timeoutMs)

            device.waitForIdle()
            SystemClock.sleep(spec.settleMs)

            val insets = systemBarInsets(scenario)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
                ?: error("$spec: UiAutomation returned no screenshot")
            val file = save(screenshot, insets)
            Log.i(TAG, "$spec -> ${file.absolutePath} (${file.length() / 1024} KiB)")
        }
    }

    // --- Waiting -------------------------------------------------------------------------

    private fun waitForAny(selectors: List<BySelector>, timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (selectors.any { device.hasObject(it) }) return
            dismissSystemDialogs()
            SystemClock.sleep(POLL_MS)
        }
        error("$spec: none of ${selectors.size} ready marker(s) appeared within ${timeoutMs / 1000}s")
    }

    private fun waitGone(selector: BySelector, timeoutMs: Long) {
        check(device.wait(Until.gone(selector), timeoutMs)) {
            "$spec: '$selector' still on screen after ${timeoutMs / 1000}s"
        }
    }

    /** Waits until the number of matches stays put across [STABLE_INTERVAL_MS]. */
    private fun waitForStableCount(selectors: List<BySelector>, timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var previous = -1
        while (SystemClock.uptimeMillis() < deadline) {
            val count = selectors.sumOf { device.findObjects(it).size }
            if (count > 0 && count == previous) return
            previous = count
            SystemClock.sleep(STABLE_INTERVAL_MS)
        }
        Log.w(TAG, "$spec: row count still changing at timeout, capturing anyway")
    }

    /**
     * Taps the first row matching [selector], scrolling down a few times if the list starts
     * below the fold. The row is a merged Compose clickable, so its node carries every text in
     * the card.
     */
    private fun tapFirstRow(selector: BySelector) {
        var row: UiObject2? = null
        for (attempt in 0..MAX_SCROLLS) {
            dismissSystemDialogs()
            device.wait(Until.hasObject(selector), spec.timeoutMs / (MAX_SCROLLS + 1))
            row = device.findObjects(selector).firstOrNull()
            if (row != null) break
            if (attempt < MAX_SCROLLS) scrollDown()
        }
        val target = row ?: error("$spec: no row matching '$selector' to tap")
        target.click()
    }

    private fun scrollDown() {
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, SWIPE_STEPS)
        device.waitForIdle()
    }

    /**
     * Taps away an "isn't responding" or "keeps stopping" dialog if one is up. A software-rendered
     * emulator under load raises them for the launcher or System UI, and while such a dialog is
     * showing the app's window is not in the accessibility tree at all, so no marker can match.
     * Matched by the system's button ids, which do not depend on the device locale. "Wait" is
     * preferred so the stalled process is kept; "Close" is the fallback for crash dialogs.
     */
    private fun dismissSystemDialogs() {
        for (id in SYSTEM_DIALOG_BUTTONS) {
            val button = device.findObject(By.res(id)) ?: continue
            Log.w(TAG, "$spec: dismissing system error dialog via $id")
            button.click()
            device.waitForIdle()
            return
        }
    }

    /**
     * What the accessibility tree and the screen looked like when a wait gave up. Pulled to
     * the host with the rest of the output, and the one thing that tells a wrong selector
     * from a screen that never loaded.
     */
    private fun dumpFailure() {
        runCatching {
            device.dumpWindowHierarchy(File(outputDir(), "${spec.id}-failure.xml"))
            device.takeScreenshot(File(outputDir(), "${spec.id}-failure.png"))
        }.onFailure { Log.w(TAG, "$spec: could not dump failure state", it) }
    }

    // --- Capturing -----------------------------------------------------------------------

    private fun systemBarInsets(scenario: ActivityScenario<MainActivity>): Insets {
        val result = AtomicReference(Insets.NONE)
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val platform = view.rootWindowInsets ?: return@onActivity
            val compat = WindowInsetsCompat.toWindowInsetsCompat(platform, view)
            result.set(
                compat.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            )
        }
        return result.get()
    }

    private fun save(screenshot: Bitmap, insets: Insets): File {
        // Recent platforms hand back a hardware bitmap, which cannot be cropped directly.
        val software = if (screenshot.config == Bitmap.Config.HARDWARE) {
            screenshot.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            screenshot
        }
        val cropHeight = software.height - insets.top - insets.bottom
        check(cropHeight > 0) { "$spec: insets $insets swallow the whole ${software.width}x${software.height} frame" }
        val cropped = Bitmap.createBitmap(software, 0, insets.top, software.width, cropHeight)

        val width = args.getString(ARG_WIDTH)?.toIntOrNull() ?: DEFAULT_WIDTH
        val height = (cropped.height.toLong() * width / cropped.width).toInt()
        val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)

        val file = File(outputDir(), "${spec.id}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return file
    }

    companion object {
        private const val TAG = "Screenshots"

        private const val ARG_CLOCK = "screenshotClock"
        private const val ARG_SCREENS = "screenshotScreens"
        private const val ARG_WIDTH = "screenshotWidth"

        /** Set by AGP for connected tests; files written there are pulled to the host. */
        private const val ARG_OUTPUT_DIR = "additionalTestOutputDir"

        const val MANIFEST = "manifest.tsv"
        private const val DEFAULT_WIDTH = 576
        private const val JPEG_QUALITY = 85
        private const val POLL_MS = 500L
        private val SYSTEM_DIALOG_BUTTONS = listOf("android:id/aerr_wait", "android:id/aerr_close")
        private const val STABLE_INTERVAL_MS = 5_000L
        private const val MAX_SCROLLS = 3
        private const val SWIPE_STEPS = 20

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun specs(): List<ScreenshotSpec> =
            ScreenshotSpecs.all(InstrumentationRegistry.getInstrumentation().targetContext)

        /**
         * Clears leftovers from an earlier run, which would otherwise end up in the gallery, and
         * writes the manifest. The manifest lists every spec, not just the ones this run
         * captures: it describes the gallery layout, and a filtered or partly skipped run must
         * not shrink the README.
         */
        @JvmStatic
        @BeforeClass
        fun resetOutput() {
            val dir = outputDir()
            dir.listFiles()
                ?.filter { it.name == MANIFEST || it.extension == "jpg" || it.name.contains("-failure.") }
                ?.forEach { it.delete() }
            File(dir, MANIFEST).writeText(
                specs().joinToString(separator = "") { "${it.id}.jpg\t${it.alt}\t${it.group}\n" }
            )
        }

        private fun outputDir(): File {
            val args = InstrumentationRegistry.getArguments()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = args.getString(ARG_OUTPUT_DIR)?.let(::File)
                ?: File(context.getExternalFilesDir(null), "screenshots")
            dir.mkdirs()
            return dir
        }
    }
}
