package com.matedroid.screenshots

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.matedroid.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Drives the app and captures Play Store / F-Droid screenshots via Fastlane screengrab.
 *
 * Run end-to-end (app must be pointed at the mock server first — see scripts/screengrab.sh):
 *   ./scripts/screengrab.sh
 *
 * PoC scope: just the dashboard. Add more @Test methods (or steps inside one) as
 * navigation gets wired up for the remaining 12 screens listed in docs/SCREENSHOTS.md.
 *
 * Uses ActivityScenario + UiAutomator instead of `createAndroidComposeRule` because
 * Compose's test rule auto-invokes `Espresso.onIdle()`, which crashes on Android 14+
 * with espresso-core 3.6.1: `InputManagerEventInjectionStrategy` does
 * `InputManager.getInstance()` via reflection, and that method was removed in API 34.
 * Screengrab's screenshot strategy is UiAutomator anyway, so leaning on that here
 * keeps the toolchain consistent and avoids a flaky Espresso bump.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {

    @get:Rule
    val localeTestRule = LocaleTestRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    init {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    /**
     * Wake the device and dismiss the keyguard before each test. Without this,
     * the activity launches *under* the lock screen — the accessibility tree
     * still contains the dashboard's nodes (so a semantic-only wait succeeds)
     * but the visible surface is whatever the lock screen / black-screen-saver
     * is showing, and the screenshot comes out black with just the nav bar.
     */
    @Before
    fun wakeAndUnlock() {
        device.wakeUp()
        // No-op when there's a PIN/pattern; on the screenshot device the lock
        // screen is just the swipe-up keyguard so this dismisses it.
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("svc power stayon true")
        device.waitForIdle()
    }

    @Test
    fun captureMainDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The dashboard's hero card carries this content description
            // (R.string.car_image_tap_for_stats). Waiting on the semantic node
            // tells us Compose has composed the screen — but composition happens
            // ahead of drawing, so the pixels may not be on the GPU yet.
            device.wait(Until.hasObject(By.descContains("Car image")), 30_000)
                ?: error("Dashboard car image did not appear within 30s — is the mock running and serving data?")

            // Force a layout/draw settle. waitForIdle() blocks until the AccessibilityEvent
            // queue drains; the extra short sleep is to let Compose's animations
            // (e.g. the loading spinner fade-out, hero card scale-in) finish so the
            // screenshot is the steady-state and not mid-transition.
            device.waitForIdle()
            Thread.sleep(1500)

            Screengrab.screenshot("01-main-dashboard")
        }
    }
}
