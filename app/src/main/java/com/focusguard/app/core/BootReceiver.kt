package com.focusguard.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.SessionStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        SessionStore.init(context)
        if (SessionStore.activeEndMillis > System.currentTimeMillis()) {
            val i = Intent(context, FocusSessionService::class.java).apply {
                action = FocusSessionService.ACTION_RESUME_SAVED
            }
            context.startForegroundService(i)
        }
    }
}
