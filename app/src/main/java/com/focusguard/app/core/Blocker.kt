package com.focusguard.app.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import com.focusguard.app.BlockActivity

object Blocker {

    /** Single entry point: given the current foreground package, show or hide the block. */
    fun handleForeground(context: Context, pkg: String?) {
        if (pkg.isNullOrBlank()) return
        val s = SessionState.flow.value
        if (!s.active || s.paused) { OverlayBlocker.hide(context); return }
        if (pkg == context.packageName) { OverlayBlocker.hide(context); return }
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
