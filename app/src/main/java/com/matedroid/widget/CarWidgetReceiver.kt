package com.matedroid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver entry point for the car home screen widget.
 * Registered in AndroidManifest.xml with the APPWIDGET_UPDATE action.
 */
class CarWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Schedule background updates when the first widget instance is added
        CarWidgetUpdateWorker.scheduleWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel background updates when the last widget instance is removed
        CarWidgetUpdateWorker.cancelWork(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Re-fetch data immediately so the resized widget shows fresh content
        CarWidgetUpdateWorker.scheduleImmediateUpdate(context)
    }
}
