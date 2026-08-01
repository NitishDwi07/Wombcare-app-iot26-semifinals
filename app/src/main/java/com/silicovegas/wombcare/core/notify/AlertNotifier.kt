package com.silicovegas.wombcare.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.device.AlertDecision
import com.silicovegas.wombcare.core.device.AlertEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local notifications for alert episodes. Two channels on purpose:
 *  - **Alerts** — high importance, for a confirmed Pathologic episode. This is the one
 *    interruption the app is allowed to make.
 *  - **Monitoring** — silent/low, reserved for the ongoing foreground-service notice a live
 *    session will carry (Phase 5b). Keeping them separate means a mother can't accidentally
 *    silence real alerts by muting the persistent "monitoring" notice.
 *
 * Every message points at seeking care and never states a diagnosis — the framing rule
 * applies to notifications too, which are the one surface a mother sees with the app closed.
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Important changes in your baby's wellness readings" },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, "Monitoring", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown while a monitoring session is running" },
        )
    }

    /** Returns false without throwing if the runtime notification permission isn't granted. */
    fun notifyAlert(event: AlertEvent): Boolean {
        if (!canPost()) return false

        val (title, text) = when (event.decision) {
            AlertDecision.ALERT ->
                "Please contact your doctor" to
                    "WombCare has seen a pattern that may need attention. This is not a " +
                    "diagnosis — please check in with your doctor."
            AlertDecision.RECHECK ->
                "Please sit still and re-check" to
                    "The signal quality was low. Rest for a moment so WombCare can read more " +
                    "reliably."
            else -> return false // ADVISORY / NONE are in-app only, never a push
        }

        post(NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build())
        return true
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun post(notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(ALERT_ID, notification)
    }

    private fun canPost(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_MONITORING = "monitoring"
        const val ALERT_ID = 1001
    }
}
