package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class SessionRecord(val date: String, val mode: String, val subject: String, val minutes: Int)

object SessionStore {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences("focusguard", Context.MODE_PRIVATE)
        }
    }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply() }

    var exam: String
        get() = prefs.getString("exam", "") ?: ""
        set(v) { prefs.edit().putString("exam", v).apply() }

    var dailyGoalMinutes: Int
        get() = prefs.getInt("daily_goal", 240)
        set(v) { prefs.edit().putInt("daily_goal", v).apply() }

    var blockedPackages: Set<String>
        get() = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("blocked_packages", v).apply() }

    // Persisted running-session snapshot (survives reboot / service restart)
    var activeEndMillis: Long
        get() = prefs.getLong("active_end_millis", 0L)
        set(v) { prefs.edit().putLong("active_end_millis", v).apply() }
    var activeMode: String
        get() = prefs.getString("active_mode", "") ?: ""
        set(v) { prefs.edit().putString("active_mode", v).apply() }
    var activeSubject: String
        get() = prefs.getString("active_subject", "") ?: ""
        set(v) { prefs.edit().putString("active_subject", v).apply() }
    var activeTotalSeconds: Long
        get() = prefs.getLong("active_total_seconds", 0L)
        set(v) { prefs.edit().putLong("active_total_seconds", v).apply() }

    fun clearActive() {
        prefs.edit().remove("active_end_millis").remove("active_mode")
            .remove("active_subject").remove("active_total_seconds").apply()
    }

    fun addRecord(mode: String, subject: String, minutes: Int) {
        val arr = JSONArray(prefs.getString("sessions_json", "[]"))
        val obj = JSONObject()
        obj.put("date", LocalDate.now().toString())
        obj.put("mode", mode)
        obj.put("subject", subject)
        obj.put("minutes", minutes)
        arr.put(obj)
        prefs.edit().putString("sessions_json", arr.toString()).apply()
    }

    fun records(): List<SessionRecord> {
        val arr = JSONArray(prefs.getString("sessions_json", "[]"))
        val out = mutableListOf<SessionRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(SessionRecord(o.optString("date"), o.optString("mode"), o.optString("subject"), o.optInt("minutes")))
        }
        return out
    }

    fun focusMinutesOn(date: LocalDate): Int =
        records().filter { it.date == date.toString() }.sumOf { it.minutes }

    fun currentStreak(): Int {
        val days = records().map { it.date }.toSet()
        var streak = 0
        var day = LocalDate.now()
        if (!days.contains(day.toString())) day = day.minusDays(1)
        while (days.contains(day.toString())) { streak++; day = day.minusDays(1) }
        return streak
    }
}
