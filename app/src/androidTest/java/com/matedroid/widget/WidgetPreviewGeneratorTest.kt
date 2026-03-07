package com.matedroid.widget

import android.graphics.Bitmap
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the widget background bitmap with mock data and saves it as a PNG.
 *
 * Run with:
 *   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matedroid.widget.WidgetPreviewGeneratorTest
 *
 * Then pull the result:
 *   adb pull /sdcard/Android/data/com.matedroid/files/widget_preview.png
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewGeneratorTest {

    @Test
    fun generatePreviewBitmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Mock preferences: Legacy Model 3, Midnight Silver, Aero 18", AC charging at 72%
        // "MidnightSilver" → PMNG, "Pinwheel18CapKit" → W38B → car_images/m3_PMNG_W38B.png
        val prefs: MutablePreferences = mutablePreferencesOf()
        prefs[CarWidget.CAR_ID_KEY]              = 1
        prefs[CarWidget.HAS_DATA_KEY]            = true
        prefs[CarWidget.CAR_NAME_KEY]            = "Model 3"
        prefs[CarWidget.EXTERIOR_COLOR_KEY]      = "MidnightSilver"
        prefs[CarWidget.MODEL_KEY]               = "3"
        prefs[CarWidget.TRIM_BADGING_KEY]        = "74D"
        prefs[CarWidget.WHEEL_TYPE_KEY]          = "Pinwheel18CapKit"
        prefs[CarWidget.STATE_KEY]               = "charging"
        prefs[CarWidget.IS_LOCKED_KEY]           = true
        prefs[CarWidget.SENTRY_MODE_KEY]         = false
        prefs[CarWidget.PLUGGED_IN_KEY]          = true
        prefs[CarWidget.OUTSIDE_TEMP_KEY]        = 18f
        prefs[CarWidget.INSIDE_TEMP_KEY]         = 20f
        prefs[CarWidget.IS_CLIMATE_ON_KEY]       = false
        prefs[CarWidget.BATTERY_LEVEL_KEY]       = 72
        prefs[CarWidget.RATED_RANGE_KEY]         = 340f
        prefs[CarWidget.CHARGE_LIMIT_KEY]        = 80
        prefs[CarWidget.IS_CHARGING_KEY]         = true
        prefs[CarWidget.IS_DC_CHARGING_KEY]      = false
        prefs[CarWidget.CHARGER_POWER_KEY]       = 11
        prefs[CarWidget.CHARGE_ENERGY_ADDED_KEY] = 8.4f
        prefs[CarWidget.TIME_TO_FULL_KEY]        = 0.5f
        prefs[CarWidget.CHARGER_VOLTAGE_KEY]     = 230
        prefs[CarWidget.CHARGER_CURRENT_KEY]     = 16
        prefs[CarWidget.AC_PHASES_KEY]           = 3

        // buildBackgroundBitmap is private — access it via reflection
        val method = CarWidget::class.java.getDeclaredMethod(
            "buildBackgroundBitmap",
            android.content.Context::class.java,
            Preferences::class.java
        )
        method.isAccessible = true

        val bitmap = method.invoke(CarWidget(), context, prefs) as Bitmap

        val outFile = File(context.getExternalFilesDir(null), "widget_preview.png")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("Widget preview saved to: ${outFile.absolutePath}")
    }
}
