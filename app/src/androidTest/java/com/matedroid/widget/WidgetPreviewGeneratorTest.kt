package com.matedroid.widget

import android.graphics.Bitmap
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

        // Mock appearance: Legacy Model 3, Midnight Silver, Aero 18", AC charging
        // "MidnightSilver" → PMNG, "Pinwheel18CapKit" → W38B → car_images/m3_PMNG_W38B.png
        val key = WidgetBackgroundCache.Key(
            exteriorColor = "MidnightSilver",
            model = "3",
            trimBadging = "74D",
            wheelType = "Pinwheel18CapKit",
            overrideVariant = null,
            overrideWheel = null,
            isCharging = true,
            isDcCharging = false
        )

        // buildBackgroundBitmap is private — access it via reflection
        val method = CarWidget::class.java.getDeclaredMethod(
            "buildBackgroundBitmap",
            android.content.Context::class.java,
            WidgetBackgroundCache.Key::class.java
        )
        method.isAccessible = true

        val bitmap = method.invoke(CarWidget(), context, key) as Bitmap

        val outFile = File(context.getExternalFilesDir(null), "widget_preview.png")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("Widget preview saved to: ${outFile.absolutePath}")
    }
}
