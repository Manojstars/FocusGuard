package com.focusguard.app.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

data class AppUsage(val pkg: String, val minutes: Int)

object UsageStatsHelper {
    fun lastForegroundPackage(context: Context): String? {
        if (!Permissions.hasUsageAccess(context)) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - 120_000, end)
            var last: String? = null
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
            }
            last
        } catch (ex: Exception) { null }
    }

    fun todayUsage(context: Context): List<AppUsage> {
        if (!Permissions.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()
        ) ?: return emptyList()
        return stats
            .filter { it.totalTimeInForeground > 60_000 }
            .map { AppUsage(it.packageName, (it.totalTimeInForeground / 60_000).toInt()) }
            .sortedByDescending { it.minutes }
            .take(10)
    }
}
