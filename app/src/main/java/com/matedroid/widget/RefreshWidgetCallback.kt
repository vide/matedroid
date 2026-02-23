package com.matedroid.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.matedroid.MainActivity

/**
 * Glance ActionCallback triggered when the user taps the widget.
 * Schedules an immediate data refresh then opens the app.
 */
class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        CarWidgetUpdateWorker.scheduleImmediateUpdate(context)
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
