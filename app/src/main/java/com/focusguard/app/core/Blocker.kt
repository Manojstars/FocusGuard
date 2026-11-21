package com.focusguard.app.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.focusguard.app.BlockActivity

object Blocker {

    /** Packages whose window events must never change the block state:
     *  our own overlay window, system UI, keyboards, permission dialogs.
     *  (v0.1.x hid the block screen whenever our own overlay generated an
     *  event - that was the flicker Manu recorded.) */
    private fun isTransient(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return true
        when (pkg) {
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "android" -> return true
        }
        val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (!ime.isNullOrBlank() && ime.split("/").firstOrNull() == pkg) return true
        return false
    }

    /** Single entry point: given the current foreground package, show or hide the block. */
    fun handleForeground(context: Context, pkg: String?) {
        if (pkg.isNullOrBlank()) return
        if (isTransient(context, pkg)) return // not a real app switch - keep current state
        val s = SessionState.flow.value
        if (!s.active || s.paused) { OverlayBlocker.hide(context); return }
        if (s.blocked.contains(pkg)) {
            OverlayBlocker.show(context, pkg)
            if (!OverlayBlocker.isShowing()) {
                // Overlay permission not granted - fall back to the block activity
                val i = Intent(context, BlockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(BlockActivity.EXTRA_PKG, pkg)
                }
                try { context.startActivity(i) } catch (e: Exception) { }
            }
        } else {
            OverlayBlocker.hide(context)
        }
    }

    fun checkForeground(context: Context) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - 10_000, end)
            var lastPkg: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            if (lastPkg != null) handleForeground(context, lastPkg)
            if (OverlayBlocker.isShowing()) OverlayBlocker.refreshTime()
        } catch (e: Exception) {
            // Usage access not granted - Accessibility path still covers blocking
        }
    }
}
