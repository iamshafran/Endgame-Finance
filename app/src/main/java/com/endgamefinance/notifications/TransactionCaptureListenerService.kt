package com.endgamefinance.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.notify.NotificationCapturePrefs
import com.endgamefinance.data.notify.NotificationCaptureRepository
import com.endgamefinance.data.notify.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads notifications from user-whitelisted banking/card apps and proposes
 * transactions (Milestone 8.1). The OS grants this service *all* notifications,
 * so the very first thing every callback does is check the per-app whitelist
 * ([NotificationCapturePrefs.shouldCapture]) and drop anything not opted in —
 * non-whitelisted apps are never read, parsed, or logged.
 *
 * Everything runs on-device: the notification text goes only to the local model
 * and the local database. Nothing is auto-posted unless the user has explicitly
 * enabled auto-post for that specific source.
 */
class TransactionCaptureListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Small ring of recently-handled notification keys — bank apps re-post the
    // same notification on update, and we must not double-propose.
    private val recentKeys = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (!NotificationCapturePrefs.shouldCapture(applicationContext, pkg)) return

        synchronized(recentKeys) {
            val now = System.currentTimeMillis()
            val last = recentKeys[sbn.key]
            if (last != null && now - last < DEDUP_WINDOW_MS) return
            recentKeys[sbn.key] = now
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val context = applicationContext
        scope.launch {
            try {
                val parsed = NotificationParser.parse(context, title, text) ?: return@launch
                val repo = NotificationCaptureRepository(DatabaseProvider.get(context), context)
                val resolved = repo.resolve(parsed)
                if (NotificationCapturePrefs.isAutoPost(context, pkg) && resolved.accountId != null) {
                    if (repo.post(resolved)) CaptureNotifier.notifyAutoPosted(context, resolved)
                } else {
                    CaptureNotifier.notifyConfirm(context, resolved)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Capture failed for $pkg: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CaptureListener"
        private const val DEDUP_WINDOW_MS = 60_000L
    }
}
