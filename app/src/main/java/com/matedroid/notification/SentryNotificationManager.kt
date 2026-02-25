package com.matedroid.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.matedroid.MainActivity
import com.matedroid.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for sentry alert notifications.
 *
 * Uses a dedicated IMPORTANCE_HIGH channel so alerts produce sound and heads-up display,
 * since sentry events are security-relevant.
 */
@Singleton
class SentryNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SentryNotificationManager"
        const val CHANNEL_ID = "sentry_alerts_channel"
        const val NOTIFICATION_ID_BASE = 4000
        private const val TESLA_PACKAGE_NAME = "com.teslamotors.tesla"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    /**
     * Show or update a sentry alert notification for a car.
     *
     * @param shouldAlert If true, the notification alerts with sound/heads-up (first event in
     *   a debounce window). If false, the notification content is updated silently (counter
     *   changed but no new sound).
     */
    fun showSentryAlert(carName: String, carId: Int, eventCount: Int, shouldAlert: Boolean = true) {
        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + carId

        val title = context.getString(R.string.sentry_notification_title, carName)
        val body = context.resources.getQuantityString(
            R.plurals.sentry_notification_body, eventCount, eventCount
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(createContentIntent(carId))

        // Add "Open Tesla" action button if the Tesla app is installed
        createTeslaAppIntent()?.let { teslaIntent ->
            val teslaPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_BASE + carId + 1000,
                teslaIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.sentry_action_open_tesla),
                teslaPendingIntent
            )
        }

        if (!shouldAlert) {
            // Suppress sound/vibration/heads-up for counter-only updates
            builder.setSilent(true)
        }

        val notification = builder.build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Sentry alert for car $carId: $eventCount events (alert=$shouldAlert)")
    }

    /**
     * Cancel the sentry notification for a car.
     */
    fun cancelNotification(carId: Int) {
        val notificationId = NOTIFICATION_ID_BASE + carId
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled sentry notification for car $carId")
    }

    /**
     * Ensure the notification channel exists.
     */
    fun ensureChannelExists() {
        createNotificationChannel()
    }

    private fun createContentIntent(carId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_CAR_ID", carId)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + carId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createTeslaAppIntent(): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage(TESLA_PACKAGE_NAME)
        return intent?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sentry_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.sentry_channel_description)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
