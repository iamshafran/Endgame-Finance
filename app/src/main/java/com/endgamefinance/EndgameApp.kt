package com.endgamefinance

import android.app.Application
import com.endgamefinance.notifications.ReminderNotifier
import com.endgamefinance.work.ReminderWork

class EndgameApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Seed the display currency before any UI formats money
        com.endgamefinance.util.Money.currencyCode =
            com.endgamefinance.security.AppSettings.get(this).currencyCode.value
        // Reflect any already-downloaded AI model
        com.endgamefinance.data.ai.AiModel.refresh(this)
        ReminderNotifier.ensureChannel(this)
        ReminderWork.schedule(this)
        ReminderWork.runNow(this)
    }
}
