package com.matedroid.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matedroid.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Drives the app and captures Play Store / F-Droid screenshots via Fastlane screengrab.
 *
 * Run end-to-end (app must be pointed at the mock server first — see scripts/take-screenshots.sh):
 *   bundle exec fastlane screengrab
 *
 * PoC scope: just the dashboard. Add more @Test methods (or steps inside one) as
 * navigation gets wired up for the remaining 12 screens listed in docs/SCREENSHOTS.md.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {

    @get:Rule(order = 0)
    val localeTestRule = LocaleTestRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    init {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    @Test
    fun captureMainDashboard() {
        // Wait for the dashboard's car image to render — the most reliable signal
        // that the network call has resolved and the hero card is laid out.
        composeTestRule.waitUntil(timeoutMillis = 30_000) {
            composeTestRule
                .onAllNodes(hasContentDescription("Car image", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(
            "Car image - tap for stats",
            substring = true
        ).assertIsDisplayed()

        Screengrab.screenshot("01-main-dashboard")
    }
}
