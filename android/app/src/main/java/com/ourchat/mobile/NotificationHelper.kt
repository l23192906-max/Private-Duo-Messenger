package com.ourchat.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_MONITORING = "our_chat_monitoring"
    const val CHANNEL_EVENTS = "our_chat_events"
    const val SERVICE_NOTIFICATION_ID = 1401

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITORING,
                "Connection sharing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Required ongoing notice while connection sharing is active."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                "OUR CHAT alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Important connection and sharing events."
            },
        )
    }

    fun monitoringNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("OUR CHAT connection sharing is active")
            .setContentText("Your consent is active. Tap OUR CHAT to manage sharing.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun showAdminEvent(context: Context, title: String, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            notification,
        )
    }

    fun canShowForegroundNotice(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.getNotificationChannel(CHANNEL_MONITORING)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }
}
