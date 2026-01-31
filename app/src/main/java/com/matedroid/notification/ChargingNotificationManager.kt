package com.matedroid.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.matedroid.MainActivity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.matedroid.R
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.ui.theme.CarColorPalettes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for charging session notifications.
 *
 * On Android 16+, displays Live Update notifications with a visual progress bar
 * showing battery level and charge progress.
 *
 * On older Android versions, displays standard persistent (dismissable) notifications.
 */
@Singleton
class ChargingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ChargingNotificationManager"
        const val CHANNEL_ID = "charging_session_channel"
        const val NOTIFICATION_ID_BASE = 3000
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    /**
     * Show or update the charging notification for a car.
     *
     * @param car The car data (for name and image)
     * @param status The current car status with charging details
     */
    fun showChargingNotification(car: CarData, status: CarStatus) {
        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + car.carId
        val notification = buildNotification(car, status)
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showed charging notification for car ${car.carId}: ${status.batteryLevel}% -> ${status.chargeLimitSoc}%")
    }

    /**
     * Build a charging notification for a car (without showing it).
     * Used by ChargingMonitorService for foreground notification.
     */
    fun buildNotification(car: CarData, status: CarStatus): Notification {
        createNotificationChannel()

        val carName = car.displayName
        val batteryLevel = status.batteryLevel ?: 0
        val chargeLimit = status.chargeLimitSoc ?: 80
        val chargerPower = status.chargerPower ?: 0
        val isDcCharging = status.isDcCharging
        val timeToFullCharge = status.timeToFullCharge

        val title = buildTitle(carName, chargerPower, isDcCharging)
        val contentText = buildContentText(
            batteryLevel = batteryLevel,
            chargeLimit = chargeLimit,
            timeToFullCharge = timeToFullCharge
        )

        return if (Build.VERSION.SDK_INT >= 36) {
            buildProgressStyleNotification(
                car = car,
                title = title,
                contentText = contentText,
                batteryLevel = batteryLevel,
                chargeLimit = chargeLimit
            )
        } else {
            buildFallbackNotification(
                title = title,
                contentText = contentText,
                batteryLevel = batteryLevel
            )
        }
    }

    /**
     * Cancel the charging notification for a car.
     */
    fun cancelNotification(carId: Int) {
        val notificationId = NOTIFICATION_ID_BASE + carId
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled charging notification for car $carId")
    }

    /**
     * Ensure the notification channel exists.
     * Called by ChargingMonitorService before creating its foreground notification.
     */
    fun ensureChannelExists() {
        createNotificationChannel()
    }

    /**
     * Build title for the notification (e.g., "Elysa ⚡ 5kW AC").
     */
    private fun buildTitle(
        carName: String,
        chargerPower: Int,
        isDcCharging: Boolean
    ): String {
        val chargeType = if (isDcCharging) "DC" else "AC"
        return "$carName \u26A1 $chargerPower kW $chargeType"
    }

    /**
     * Build content text for the notification.
     */
    private fun buildContentText(
        batteryLevel: Int,
        chargeLimit: Int,
        timeToFullCharge: Double?
    ): String {
        val parts = mutableListOf<String>()
        parts.add("$batteryLevel% \u2192 $chargeLimit%")

        // Add estimated finish time with relative day (e.g., "today at 15:30")
        timeToFullCharge?.let { hours ->
            if (hours > 0) {
                val finishTime = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MINUTE, (hours * 60).toInt())
                }
                val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
                val formattedTime = timeFormat.format(finishTime.time)

                // Determine relative day using DateUtils
                val relativeDateTime = android.text.format.DateUtils.getRelativeDateTimeString(
                    context,
                    finishTime.timeInMillis,
                    android.text.format.DateUtils.DAY_IN_MILLIS,
                    android.text.format.DateUtils.DAY_IN_MILLIS,
                    0
                ).toString()

                parts.add("\uD83D\uDD52 $relativeDateTime")
            }
        }

        return parts.joinToString(" \u2022 ")
    }

    /**
     * Build Android 16+ ProgressStyle notification with visual battery progress bar.
     */
    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        car: CarData,
        title: String,
        contentText: String,
        batteryLevel: Int,
        chargeLimit: Int
    ): Notification {
        // Get car palette accent color for target marker
        val palette = CarColorPalettes.forExteriorColor(
            car.carExterior?.exteriorColor,
            darkTheme = false  // Use light theme colors for notification
        )

        // Load car image (semi-transparent for background)
        val carBitmap = loadCarImage(car)

        // Use simple progress without custom segments - let Android handle the fill
        // Custom segments cause minimum size issues at low battery levels
        val progressStyle = Notification.ProgressStyle()
            .setProgress(batteryLevel)
            .setStyledByProgress(true)
            .setProgressTrackerIcon(
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_bolt)
            )
            .setProgressPoints(
                listOf(
                    Notification.ProgressStyle.Point(chargeLimit)
                        .setColor(palette.accent.toArgb())
                )
            )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)  // Show on lock screen
            .setContentIntent(createContentIntent())

        // Add car image as large icon if available
        carBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        // Request promoted ongoing status (Live Update)
        // Use literal string since the constant is only available in API 36+
        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        return builder.build()
    }

    /**
     * Build fallback notification for Android < 16.
     */
    private fun buildFallbackNotification(
        title: String,
        contentText: String,
        batteryLevel: Int
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, batteryLevel, false)
            .setOngoing(false)  // Dismissable on older Android
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .setContentIntent(createContentIntent())
            .build()
    }

    /**
     * Create a PendingIntent that opens the app when notification is tapped.
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Load car image from assets.
     */
    private fun loadCarImage(car: CarData): Bitmap? {
        return try {
            val assetPath = CarImageResolver.getAssetPath(
                model = car.carDetails?.model,
                exteriorColor = car.carExterior?.exteriorColor,
                wheelType = car.carExterior?.wheelType,
                trimBadging = car.carDetails?.trimBadging
            )

            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load car image", e)
            null
        }
    }

    /**
     * Create the notification channel for charging notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.charging_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT  // Default importance for lock screen visibility
            ).apply {
                description = context.getString(R.string.charging_channel_description)
                setShowBadge(false)  // Don't show app badge for ongoing charging
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)  // No sound
                enableVibration(false)  // No vibration
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Convert Compose Color to Android ARGB int.
     */
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
