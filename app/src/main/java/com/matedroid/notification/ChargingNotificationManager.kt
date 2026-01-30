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
import androidx.core.graphics.drawable.IconCompat
import com.matedroid.R
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarStatus
import com.matedroid.domain.model.CarImageResolver
import com.matedroid.ui.theme.CarColorPalettes
import com.matedroid.ui.theme.StatusError
import com.matedroid.ui.theme.StatusSuccess
import com.matedroid.ui.theme.StatusWarning
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

        val parts = mutableListOf<String>()
        parts.add("$batteryLevel% \u2192 $chargeLimit%")
        parts.add("$chargerPower kW $chargeType")

        // Add time remaining if available (clock icon + HH:MM format)
        timeToFullCharge?.let { hours ->
            if (hours > 0) {
                val totalMinutes = (hours * 60).toInt()
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                parts.add("\uD83D\uDD52 %d:%02d".format(h, m))
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
        carName: String,
        contentText: String,
        batteryLevel: Int,
        chargeLimit: Int,
        isDcCharging: Boolean
    ): Notification {
        // Get battery level color (same as dashboard)
        val batteryColor = when {
            batteryLevel < 20 -> StatusError
            batteryLevel < 40 -> StatusWarning
            else -> StatusSuccess
        }

        // Get car palette accent color for target marker
        val palette = CarColorPalettes.forExteriorColor(
            car.carExterior?.exteriorColor,
            darkTheme = false  // Use light theme colors for notification
        )

        // Load car image (semi-transparent for background)
        val carBitmap = loadCarImage(car)

        // Create progress segments:
        // - Filled segment: 0 to batteryLevel (battery color)
        // - Target segment: batteryLevel to chargeLimit (dimmed accent)
        val segments = mutableListOf<Notification.ProgressStyle.Segment>()

        // Filled battery segment
        segments.add(
            Notification.ProgressStyle.Segment(batteryLevel)
                .setColor(batteryColor.toArgb())
        )

        // Remaining to charge limit (if not yet at limit)
        if (chargeLimit > batteryLevel) {
            segments.add(
                Notification.ProgressStyle.Segment(chargeLimit - batteryLevel)
                    .setColor(palette.accentDim.toArgb())
            )
        }

        // Remaining after limit (empty/track color)
        if (chargeLimit < 100) {
            segments.add(
                Notification.ProgressStyle.Segment(100 - chargeLimit)
                    .setColor(palette.progressTrack.toArgb())
            )
        }

        val progressStyle = Notification.ProgressStyle()
            .setProgress(batteryLevel)
            .setProgressTrackerIcon(
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_bolt)
            )
            .setProgressSegments(segments)
            .setProgressPoints(
                listOf(
                    Notification.ProgressStyle.Point(chargeLimit)
                        .setColor(palette.accent.toArgb())
                )
            )

        val title = context.getString(R.string.charging_notification_title, carName)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)  // Show on lock screen

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
