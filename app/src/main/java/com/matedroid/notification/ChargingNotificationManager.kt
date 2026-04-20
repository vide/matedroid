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
    fun showChargingNotification(
        car: CarData,
        status: CarStatus,
        liveChargeAvailable: Boolean = false,
        dcFinishedPluggedIn: Boolean = false,
        chronometerBaseMs: Long? = null
    ) {
        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + car.carId
        val notification = buildNotification(car, status, liveChargeAvailable, dcFinishedPluggedIn, chronometerBaseMs)
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showed charging notification for car ${car.carId}: ${status.batteryLevel}% -> ${status.chargeLimitSoc}%")
    }

    /**
     * Build a charging notification for a car (without showing it).
     * Used by ChargingMonitorService for foreground notification.
     */
    fun buildNotification(
        car: CarData,
        status: CarStatus,
        liveChargeAvailable: Boolean = false,
        dcFinishedPluggedIn: Boolean = false,
        chronometerBaseMs: Long? = null
    ): Notification {
        createNotificationChannel()

        val carName = car.displayName
        val batteryLevel = status.batteryLevel ?: 0
        val chargeLimit = status.chargeLimitSoc ?: 80
        val chargerPower = status.chargerPower ?: 0
        val isDcCharging = status.isDcCharging
        val timeToFullCharge = status.timeToFullCharge

        val title: String
        val contentText: String
        val smallIconRes: Int
        val effectiveLiveCharge: Boolean

        if (dcFinishedPluggedIn) {
            title = context.getString(R.string.charging_notification_dc_finished_title, carName)
            contentText = context.getString(R.string.charging_notification_dc_finished_content)
            smallIconRes = R.drawable.ic_notification_warning
            effectiveLiveCharge = false // Navigate to main dashboard, not charge screen
        } else {
            title = buildTitle(carName, chargerPower, isDcCharging)
            contentText = buildContentText(
                batteryLevel = batteryLevel,
                chargeLimit = chargeLimit,
                timeToFullCharge = timeToFullCharge
            )
            smallIconRes = R.drawable.ic_notification
            effectiveLiveCharge = liveChargeAvailable
        }

        return if (Build.VERSION.SDK_INT >= 36) {
            buildProgressStyleNotification(
                car = car,
                title = title,
                contentText = contentText,
                batteryLevel = batteryLevel,
                chargeLimit = chargeLimit,
                liveChargeAvailable = effectiveLiveCharge,
                smallIconRes = smallIconRes,
                chronometerBaseMs = chronometerBaseMs,
                dcFinishedPluggedIn = dcFinishedPluggedIn
            )
        } else {
            buildFallbackNotification(
                carId = car.carId,
                title = title,
                contentText = contentText,
                batteryLevel = batteryLevel,
                liveChargeAvailable = effectiveLiveCharge,
                smallIconRes = smallIconRes,
                chronometerBaseMs = chronometerBaseMs
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
     * Uses NotificationCompat APIs (matching official Android sample).
     */
    @RequiresApi(36)
    private fun buildProgressStyleNotification(
        car: CarData,
        title: String,
        contentText: String,
        batteryLevel: Int,
        chargeLimit: Int,
        liveChargeAvailable: Boolean,
        smallIconRes: Int = R.drawable.ic_notification,
        chronometerBaseMs: Long? = null,
        dcFinishedPluggedIn: Boolean = false
    ): Notification {
        // Get car palette accent color
        val palette = CarColorPalettes.forExteriorColor(
            car.carExterior?.exteriorColor,
            darkTheme = false  // Use light theme colors for notification
        )

        // Load car image
        val carBitmap = loadCarImage(car)

        val accentArgb = palette.accent.toArgb()
        val grayArgb = android.graphics.Color.argb(80, 128, 128, 128)

        // Clamp values to safe ranges
        val soc = batteryLevel.coerceIn(0, 100)
        val limit = chargeLimit.coerceIn(soc, 100)

        Log.d(TAG, "ProgressStyle: soc=$soc, limit=$limit (segments: $soc, ${limit - soc}, ${100 - limit})")

        // 3 segments: charged (accent, bright) | charging-to-limit (accent, dimmed) | beyond limit (gray, dimmed)
        val segments = listOfNotNull(
            if (soc > 0) Notification.ProgressStyle.Segment(soc).setColor(accentArgb) else null,
            if (limit - soc > 0) Notification.ProgressStyle.Segment(limit - soc).setColor(accentArgb) else null,
            if (100 - limit > 0) Notification.ProgressStyle.Segment(100 - limit).setColor(grayArgb) else null,
        )

        val progressStyle = Notification.ProgressStyle()
            .setProgress(soc)
            .setStyledByProgress(true)
            .setProgressTrackerIcon(
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_bolt)
            )
            .setProgressSegments(segments)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(smallIconRes)
            .setProgress(100, soc, false)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(createContentIntent(car.carId, liveChargeAvailable))

        // Show live elapsed timer in the status bar
        if (chronometerBaseMs != null) {
            builder.setUsesChronometer(true)
            builder.setShowWhen(true)
            builder.setWhen(chronometerBaseMs)
        }

        // Add car image as large icon if available
        carBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        // Request promoted ongoing status (Live Update)
        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        return builder.build()
    }

    /**
     * Build fallback notification for Android < 16.
     */
    private fun buildFallbackNotification(
        carId: Int,
        title: String,
        contentText: String,
        batteryLevel: Int,
        liveChargeAvailable: Boolean,
        smallIconRes: Int = R.drawable.ic_notification,
        chronometerBaseMs: Long? = null
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(smallIconRes)
            .setProgress(100, batteryLevel, false)
            .setOngoing(false)  // Dismissable on older Android
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .setContentIntent(createContentIntent(carId, liveChargeAvailable))

        // Show live elapsed timer in the status bar
        if (chronometerBaseMs != null) {
            builder.setUsesChronometer(true)
            builder.setShowWhen(true)
            builder.setWhen(chronometerBaseMs)
        }

        return builder.build()
    }

    /**
     * Create a PendingIntent that opens the app.
     * When [liveChargeAvailable] is true, navigates to the current charge screen;
     * otherwise just opens the main activity.
     */
    private fun createContentIntent(carId: Int, liveChargeAvailable: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (liveChargeAvailable) {
                putExtra("EXTRA_NAVIGATE_TO", "current_charge")
                putExtra("EXTRA_CAR_ID", carId)
            }
        }
        return PendingIntent.getActivity(
            context,
            carId, // Use carId as requestCode for multi-car uniqueness
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
