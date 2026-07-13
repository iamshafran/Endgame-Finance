package com.endgamefinance.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.repo.ReminderRepository
import com.endgamefinance.notifications.ReminderNotifier
import com.endgamefinance.util.Money
import java.util.concurrent.TimeUnit

/**
 * Periodic due-bill check. Auto-posts fixed-amount reminders flagged for it
 * and raises a local notification for anything needing manual attention.
 * WorkManager (not AlarmManager) per the spec — survives doze via
 * maintenance windows; exact-minute delivery is deliberately not a goal.
 */
class ReminderCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = DatabaseProvider.get(applicationContext)
        val repo = ReminderRepository(db)
        val due = db.reminderDao().dueNow(System.currentTimeMillis())

        val autoPosted = mutableListOf<String>()
        val needAttention = mutableListOf<String>()
        due.forEach { reminder ->
            if (reminder.isAutoPost && reminder.amount != null) {
                try {
                    repo.post(reminder)
                    autoPosted += "${reminder.name} ${Money.format(reminder.amount)}"
                } catch (e: Exception) {
                    needAttention += reminder.name
                }
            } else {
                needAttention += reminder.name
            }
        }

        ReminderNotifier.notifyAutoPosted(applicationContext, autoPosted)
        ReminderNotifier.notifyDue(applicationContext, needAttention)
        return Result.success()
    }
}

object ReminderWork {

    private const val PERIODIC_NAME = "reminder_check_periodic"
    private const val IMMEDIATE_NAME = "reminder_check_now"

    /** Idempotent; called from Application.onCreate. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderCheckWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    /** Catch-up pass on app open so due bills never wait for the next window. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderCheckWorker>().build(),
        )
    }
}
