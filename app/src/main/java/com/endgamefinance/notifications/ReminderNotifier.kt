package com.endgamefinance.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.endgamefinance.MainActivity
import com.endgamefinance.R

/** Local-only notifications; nothing here touches the network. */
object ReminderNotifier {

    private const val CHANNEL_ID = "bills"
    private const val DUE_NOTIFICATION_ID = 1001
    private const val AUTOPOST_NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Bills & reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Due bills and auto-posted transactions"
            },
        )
    }

    fun notifyDue(context: Context, names: List<String>) {
        if (names.isEmpty()) return
        notify(
            context,
            DUE_NOTIFICATION_ID,
            title = if (names.size == 1) "Bill due: ${names.first()}"
            else "${names.size} bills need attention",
            text = names.joinToString(", "),
        )
    }

    fun notifyAutoPosted(context: Context, descriptions: List<String>) {
        if (descriptions.isEmpty()) return
        notify(
            context,
            AUTOPOST_NOTIFICATION_ID,
            title = if (descriptions.size == 1) "Auto-posted: ${descriptions.first()}"
            else "${descriptions.size} bills auto-posted",
            text = descriptions.joinToString(", "),
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and notify — nothing to do
        }
    }
}
