package com.matedroid.widget

import android.content.Intent
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.matedroid.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the widget tap — after AGP 9 / Kotlin 2.3 / targetSdk 36,
 * tapping a widget backed by `actionRunCallback { … startActivity() }` was
 * silently blocked by Background Activity Launch restrictions. The fix is to
 * use `actionStartActivity(intent)` so the launcher dispatches the PendingIntent
 * directly. This test locks that contract in place.
 */
@RunWith(AndroidJUnit4::class)
class CarWidgetClickActionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun widgetRoot_tap_opensMainActivityWithCarId() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setState(
            mutablePreferencesOf(
                CarWidget.CAR_ID_KEY to 42,
                CarWidget.HAS_DATA_KEY to false,
            )
        )
        provideComposable { CarWidget().WidgetContent(isOnLockScreen = false) }

        val expected = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("EXTRA_CAR_ID", 42)

        onNode(hasClickAction()).assertHasStartActivityClickAction(expected)
    }

    @Test
    fun widgetRoot_tap_withoutCarId_opensMainActivityWithoutExtra() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setState(mutablePreferencesOf())
        provideComposable { CarWidget().WidgetContent(isOnLockScreen = false) }

        val expected = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        onNode(hasClickAction()).assertHasStartActivityClickAction(expected)
    }
}
