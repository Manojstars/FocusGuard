package com.focusguard.app.core

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import com.focusguard.app.BlockActivity
import java.util.SortedMap
import java.util.TreeMap

object Blocker {
    private var lastBlockAt: Long = 0
    private var lastBlockedPkg: String = ""

    fun maybeBlock(context: Context, pkg: String?) {
        if (pkg.isNullOrBlank()) return
        val s = SessionState.flow.value
        if (!s.active || s.paused) return
        if (pkg == context.packageName) return
        if (!s.blocked.contains(pkg)) return
        val now = System.currentTimeMillis()
        if (pkg == lastBlockedPkg && now - lastBlockAt < 1500) return
        lastBlockAt = now; lastBlockedPkg = pkg
        val i = Intent(context, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockActivity.EXTRA_PKG, pkg)
        }
        context.startActivity(i)
    }

    fun checkForeground(context: Context) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 10_000
            val events = usm.queryEvents(begin, end)
            var lastPkg: String? = null
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            maybeBlock(context, lastPkg)
        } catch (e: Exception) {
            // Usage access not granted yet - Accessibility path still covers blocking
        }
    }
}
