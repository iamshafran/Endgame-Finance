package com.endgamefinance.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.notify.NotificationCaptureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the Confirm / Dismiss actions on a captured-transaction notification
 * (Milestone 8.1). Confirm posts the proposed transaction to the ledger; both
 * actions clear the notification. This is the only path that writes a captured
 * transaction after a user tap — the parse/propose step never writes.
 */
class NotificationConfirmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(CaptureNotifier.EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) NotificationManagerCompat.from(context).cancel(notifId)

        if (intent.action != CaptureNotifier.ACTION_CONFIRM) return

        val resolved = NotificationCaptureRepository.Resolved(
            payee = intent.getStringExtra(CaptureNotifier.EXTRA_PAYEE) ?: return,
            amountCents = intent.getLongExtra(CaptureNotifier.EXTRA_AMOUNT, 0L),
            type = intent.getStringExtra(CaptureNotifier.EXTRA_TYPE) ?: "expense",
            accountId = intent.getStringExtra(CaptureNotifier.EXTRA_ACCOUNT_ID) ?: return,
            accountName = intent.getStringExtra(CaptureNotifier.EXTRA_ACCOUNT_NAME),
            accountMatched = true,
            categoryId = intent.getStringExtra(CaptureNotifier.EXTRA_CATEGORY_ID),
            categoryName = intent.getStringExtra(CaptureNotifier.EXTRA_CATEGORY_NAME),
        )
        if (resolved.amountCents <= 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationCaptureRepository(DatabaseProvider.get(context), context)
                    .post(resolved)
            } finally {
                pending.finish()
            }
        }
    }
}
