package com.focusguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class FocusGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Focus Session", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    companion object { const val CHANNEL_ID = "focus_session" }
}
