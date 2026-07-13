package com.endgamefinance

import android.app.Application
import com.endgamefinance.notifications.ReminderNotifier
import com.endgamefinance.work.ReminderWork

class EndgameApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
        ReminderWork.schedule(this)
        ReminderWork.runNow(this)
    }
}
