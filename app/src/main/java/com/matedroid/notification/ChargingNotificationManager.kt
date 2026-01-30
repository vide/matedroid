package com.matedroid.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.matedroid.R
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.domain.model.CarImageResolver
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
        private const val NOTIFICATION_ID_BASE = 3000
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
        val carName = car.displayName

        val batteryLevel = status.batteryLevel ?: 0
        val chargeLimit = status.chargeLimitSoc ?: 80
        val chargerPower = status.chargerPower ?: 0
        val energyAdded = status.chargeEnergyAdded ?: 0.0
        val isDcCharging = status.isDcCharging
        val timeToFullCharge = status.timeToFullCharge

        // Build content text
        val contentText = buildContentText(
            batteryLevel = batteryLevel,
            chargeLimit = chargeLimit,
            chargerPower = chargerPower,
            energyAdded = energyAdded,
            isDcCharging = isDcCharging,
            timeToFullCharge = timeToFullCharge
        )

        val notification = if (Build.VERSION.SDK_INT >= 36) {
            buildProgressStyleNotification(
                car = car,
                carName = carName,
                contentText = contentText,
                batteryLevel = batteryLevel,
                chargeLimit = chargeLimit,
                isDcCharging = isDcCharging
            )
        } else {
            buildFallbackNotification(
                carName = carName,
                contentText = contentText,
                batteryLevel = batteryLevel
            )
        }

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showed charging notification for car ${car.carId}: $batteryLevel% -> $chargeLimit%")
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
     * Build content text for the notification.
     */
    private fun buildContentText(
        batteryLevel: Int,
        chargeLimit: Int,
        chargerPower: Int,
        energyAdded: Double,
        isDcCharging: Boolean,
        timeToFullCharge: Double?
    ): String {
        val chargeType = if (isDcCharging) "DC" else "AC"
        val energyText = String.format("%.1f", energyAdded)

        val parts = mutableListOf<String>()
        parts.add("$batteryLevel% \u2192 $chargeLimit%")
        parts.add("$chargerPower kW $chargeType")
        parts.add("+$energyText kWh")

        // Add time remaining if available
        timeToFullCharge?.let { hours ->
            if (hours > 0) {
                val minutes = (hours * 60).toInt()
                val timeText = if (minutes >= 60) {
                    val h = minutes / 60
                    val m = minutes % 60
                    if (m > 0) "${h}h ${m}min" else "${h}h"
                } else {
                    "${minutes}min"
                }
                parts.add("$timeText remaining")
            }
        }

        return parts.joinToString(" \u2022 ")
    }

    /**
     * Build Android 16+ BigPictureStyle notification with full car image background.
     */
    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        car: CarData,
        carName: String,
        contentText: String,
        batteryLevel: Int,
        chargeLimit: Int,
        isDcCharging: Boolean
    ): Notification {
        // Load car image for full background
        val carBitmap = loadCarImage(car, applyAlpha = false)

        val title = context.getString(R.string.charging_notification_title, carName)

        // Use BigPictureStyle for full background image
        val bigPictureStyle = Notification.BigPictureStyle()
            .setSummaryText(contentText)

        // Set the car image as the big picture (full background)
        carBitmap?.let { bitmap ->
            bigPictureStyle.bigPicture(bitmap)
            // Show the big picture in both collapsed and expanded states
            bigPictureStyle.showBigPictureWhenCollapsed(true)
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(bigPictureStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)  // Show on lock screen
            .setProgress(100, batteryLevel, false)  // Add progress bar

        // Request promoted ongoing status (Live Update)
        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        return builder.build()
    }

    /**
     * Build fallback notification for Android < 16.
     */
    private fun buildFallbackNotification(
        carName: String,
        contentText: String,
        batteryLevel: Int
    ): Notification {
        val title = context.getString(R.string.charging_notification_title, carName)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, batteryLevel, false)
            .setOngoing(false)  // Dismissable on older Android
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .build()
    }

    /**
     * Load car image from assets.
     *
     * @param car The car data
     * @param applyAlpha If true, applies 30% alpha for semi-transparent background
     */
    private fun loadCarImage(car: CarData, applyAlpha: Boolean = true): Bitmap? {
        return try {
            val assetPath = CarImageResolver.getAssetPath(
                model = car.carDetails?.model,
                exteriorColor = car.carExterior?.exteriorColor,
                wheelType = car.carExterior?.wheelType,
                trimBadging = car.carDetails?.trimBadging
            )

            context.assets.open(assetPath).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (applyAlpha) {
                    // Apply 30% alpha for semi-transparent background
                    applyAlpha(bitmap, 77)  // 77 = 30% of 255
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load car image", e)
            null
        }
    }

    /**
     * Apply alpha to a bitmap.
     */
    private fun applyAlpha(source: Bitmap, alpha: Int): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
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
}
