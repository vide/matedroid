package com.matedroid.data.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.matedroid.R

/**
 * Builds the ongoing foreground-service notification (and its channel) shared by the
 * data-sync and geocode workers. Only the channel identity and content title differ
 * between them, so those are passed in; the rest of the plumbing lives here.
 */
class WorkerForegroundNotifier(
    private val context: Context,
    private val channelId: String,
    private val channelName: String,
    private val channelDescription: String,
    private val notificationId: Int,
    private val contentTitle: String,
) {
    fun createForegroundInfo(
        progress: String,
        current: Int = 0,
        total: Int = 0
    ): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification(progress, current, total)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /**
     * Refresh the same notification in place.
     *
     * Used while a long sync walks its thousands of drives: re-posting is far cheaper than
     * going back through setForeground, and the notification is the only thing watching.
     * Posting can be refused on Android 13+ when the user has not granted notifications,
     * which is not worth failing a sync over.
     */
    fun update(progress: String, current: Int = 0, total: Int = 0) {
        // Inline rather than behind a helper: lint only recognises the guard in the same
        // function as the call it protects.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createNotificationChannel()
        NotificationManagerCompat.from(context)
            .notify(notificationId, buildNotification(progress, current, total))
    }

    /** A total of zero means "no idea how much is left", which draws as indeterminate. */
    private fun buildNotification(progress: String, current: Int, total: Int): Notification =
        NotificationCompat.Builder(context, channelId)
            .setContentTitle(contentTitle)
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(total, current, total <= 0)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDescription
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
