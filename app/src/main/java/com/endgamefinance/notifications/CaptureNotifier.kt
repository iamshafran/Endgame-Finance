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
import com.endgamefinance.data.notify.NotificationCaptureRepository
import com.endgamefinance.util.Money

/**
 * Builds the "we spotted a transaction — confirm it?" notification for
 * captured bank/card notifications (Milestone 8.1). The confirm action posts
 * via [NotificationConfirmReceiver]; nothing is written until the user taps it
 * (unless auto-post is explicitly enabled for the source, in which case the
 * listener posts directly and we only show an informational notice).
 *
 * Local-only — no network, and the captured text never leaves the device.
 */
object CaptureNotifier {

    private const val CHANNEL_ID = "capture"

    const val ACTION_CONFIRM = "com.endgamefinance.CAPTURE_CONFIRM"
    const val ACTION_DISMISS = "com.endgamefinance.CAPTURE_DISMISS"

    const val EXTRA_NOTIF_ID = "notif_id"
    const val EXTRA_PAYEE = "payee"
    const val EXTRA_AMOUNT = "amount"
    const val EXTRA_TYPE = "type"
    const val EXTRA_ACCOUNT_ID = "account_id"
    const val EXTRA_ACCOUNT_NAME = "account_name"
    const val EXTRA_CATEGORY_ID = "category_id"
    const val EXTRA_CATEGORY_NAME = "category_name"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Captured transactions",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Confirm transactions detected from bank notifications" },
        )
    }

    private fun Intent.putResolved(r: NotificationCaptureRepository.Resolved, notifId: Int) = apply {
        putExtra(EXTRA_NOTIF_ID, notifId)
        putExtra(EXTRA_PAYEE, r.payee)
        putExtra(EXTRA_AMOUNT, r.amountCents)
        putExtra(EXTRA_TYPE, r.type)
        putExtra(EXTRA_ACCOUNT_ID, r.accountId)
        putExtra(EXTRA_ACCOUNT_NAME, r.accountName)
        putExtra(EXTRA_CATEGORY_ID, r.categoryId)
        putExtra(EXTRA_CATEGORY_NAME, r.categoryName)
    }

    /** Shows a confirm-to-post notification for [resolved]. */
    fun notifyConfirm(context: Context, resolved: NotificationCaptureRepository.Resolved) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val notifId = (System.nanoTime() and 0x3fffffff).toInt()
        val amount = Money.format(resolved.amountCents)
        val where = when {
            resolved.accountName == null -> "no account yet — add one first"
            resolved.accountMatched -> resolved.accountName
            else -> "${resolved.accountName} (default — check)"
        }
        val cat = resolved.categoryName?.let { " · $it" } ?: ""

        fun action(intentAction: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, NotificationConfirmReceiver::class.java)
                .setAction(intentAction)
                .putResolved(resolved, notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Tapping the body opens the app (to edit/pick account) rather than posting blind.
        val openApp = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${resolved.payee} · $amount")
            .setContentText("$where$cat")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, "Dismiss", action(ACTION_DISMISS, notifId * 2 + 1))

        // Only offer one-tap confirm when we actually know where it lands.
        if (resolved.accountId != null) {
            builder.addAction(0, "Confirm", action(ACTION_CONFIRM, notifId * 2))
        }

        try {
            manager.notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between check and notify — nothing to do.
        }
    }

    /** Informational notice after an auto-posted capture. */
    fun notifyAutoPosted(context: Context, resolved: NotificationCaptureRepository.Resolved) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val notifId = (System.nanoTime() and 0x3fffffff).toInt()
        val openApp = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Auto-posted: ${resolved.payee}")
            .setContentText("${Money.format(resolved.amountCents)} · ${resolved.accountName ?: ""}")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(notifId, notification)
        } catch (e: SecurityException) {
            // ignore
        }
    }
}
