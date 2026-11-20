package com.focusguard.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val pkg: String, val label: String)

fun installedLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    val seen = mutableSetOf<String>()
    val out = mutableListOf<AppEntry>()
    for (ri in resolved) {
        val pkg = ri.activityInfo.packageName
        if (pkg == context.packageName) continue
        if (!seen.add(pkg)) continue
        val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { pkg }
        out.add(AppEntry(pkg, label))
    }
    return out.sortedBy { it.label.lowercase() }
}

fun appLabel(context: Context, pkg: String): String = try {
    val info = context.packageManager.getApplicationInfo(pkg, 0)
    context.packageManager.getApplicationLabel(info).toString()
} catch (e: Exception) { pkg }
