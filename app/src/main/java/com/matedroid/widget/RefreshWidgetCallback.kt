package com.matedroid.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.matedroid.MainActivity

/**
 * Glance ActionCallback triggered when the user taps the widget.
 * Schedules an immediate data refresh then opens the app, selecting the
 * car that belongs to this specific widget instance.
 */
class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        CarWidgetUpdateWorker.scheduleImmediateUpdate(context)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val carId = prefs[CarWidget.CAR_ID_KEY]
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (carId != null) {
            intent.putExtra("EXTRA_CAR_ID", carId)
        }
        context.startActivity(intent)
    }
}
