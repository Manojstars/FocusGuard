package com.focusguard.app.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.MainActivity
import com.focusguard.app.SessionStore
import java.util.concurrent.TimeUnit

class FocusSessionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var endMillis: Long = 0
    private var pausedRemaining: Long = 0
    private var pomodoroBreaks: Boolean = false
    private var inBreak: Boolean = false
    private var startMillis: Long = 0
    private var pausedElapsed: Long = 0

    private val tick = object : Runnable {
        override fun run() {
            val s = SessionState.flow.value
            if (!s.active) return
            if (s.paused) { handler.postDelayed(this, 1000); return }
            if (s.mode == "Stopwatch") {
                val elapsed = (System.currentTimeMillis() - startMillis) / 1000
                SessionState.flow.value = s.copy(elapsedSeconds = elapsed)
                updateNotification(elapsed)
                Blocker.checkForeground(this@FocusSessionService)
                handler.postDelayed(this, 1000)
                return
            }
            val remaining = ((endMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            SessionState.flow.value = s.copy(remainingSeconds = remaining)
            updateNotification(remaining)
            if (remaining <= 0) {
                if (pomodoroBreaks && !inBreak) {
                    // Start a 5-minute break, then finish
                    inBreak = true
                    endMillis = System.currentTimeMillis() + 5 * 60 * 1000
                    SessionState.flow.value = SessionState.flow.value.copy(
                        mode = SessionState.flow.value.mode + " (Break)", remainingSeconds = 300
                    )
                    handler.postDelayed(this, 1000)
                    return
                }
                completeSession()
                return
            }
            // Fallback foreground-app check (works even if Accessibility is off)
            Blocker.checkForeground(this@FocusSessionService)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: "Timer"
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "Study"
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 25)
                pomodoroBreaks = mode == "Pomodoro"
                inBreak = false
                startMillis = System.currentTimeMillis()
                pausedElapsed = 0
                val totalSec = if (mode == "Stopwatch") 0L else minutes * 60L
                endMillis = if (mode == "Stopwatch") Long.MAX_VALUE / 2 else System.currentTimeMillis() + totalSec * 1000
                val blocked = SessionStore.blockedPackages
                SessionState.flow.value = RunningSession(
                    active = true, mode = mode, subject = subject,
                    totalSeconds = totalSec, remainingSeconds = totalSec, blocked = blocked
                )
                if (mode != "Stopwatch") {
                    SessionStore.activeEndMillis = endMillis
                    SessionStore.activeMode = mode
                    SessionStore.activeSubject = subject
                    SessionStore.activeTotalSeconds = totalSec
                }
                startForeground(NOTIF_ID, buildNotification(if (mode == "Stopwatch") 0 else totalSec))
                handler.removeCallbacks(tick); handler.post(tick)
            }
            ACTION_PAUSE -> {
                val s = SessionState.flow.value
                if (s.active && !s.paused) {
                    if (s.mode == "Stopwatch") {
                        pausedElapsed = (System.currentTimeMillis() - startMillis) / 1000
                        SessionState.flow.value = s.copy(paused = true, elapsedSeconds = pausedElapsed)
                    } else {
                        pausedRemaining = ((endMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                        SessionState.flow.value = s.copy(paused = true, remainingSeconds = pausedRemaining)
                    }
                }
            }
            ACTION_RESUME -> {
                val s = SessionState.flow.value
                if (s.active && s.paused) {
                    if (s.mode == "Stopwatch") {
                        startMillis = System.currentTimeMillis() - pausedElapsed * 1000
                    } else {
                        endMillis = System.currentTimeMillis() + pausedRemaining * 1000
                        SessionStore.activeEndMillis = endMillis
                    }
                    SessionState.flow.value = s.copy(paused = false)
                }
            }
            ACTION_STOP -> {
                val s = SessionState.flow.value
                if (s.active) {
                    val elapsedSec = if (s.mode == "Stopwatch") s.elapsedSeconds else s.totalSeconds - s.remainingSeconds
                    val elapsedMin = (elapsedSec / 60).toInt()
                    if (elapsedMin >= 1) SessionStore.addRecord(s.mode, s.subject, elapsedMin)
                }
                endSession()
            }
            ACTION_RESUME_SAVED -> {
                val end = SessionStore.activeEndMillis
                if (end > System.currentTimeMillis()) {
                    endMillis = end
                    val remaining = (end - System.currentTimeMillis()) / 1000
                    SessionState.flow.value = RunningSession(
                        active = true, mode = SessionStore.activeMode, subject = SessionStore.activeSubject,
                        totalSeconds = SessionStore.activeTotalSeconds, remainingSeconds = remaining,
                        blocked = SessionStore.blockedPackages
                    )
                    startForeground(NOTIF_ID, buildNotification(remaining))
                    handler.removeCallbacks(tick); handler.post(tick)
                } else {
                    SessionStore.clearActive(); stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun completeSession() {
        val s = SessionState.flow.value
        val mins = (s.totalSeconds / 60).toInt()
        if (mins >= 1) SessionStore.addRecord(s.mode.replace(" (Break)", ""), s.subject, mins)
        endSession()
    }

    private fun endSession() {
        handler.removeCallbacks(tick)
        SessionState.flow.value = RunningSession()
        SessionStore.clearActive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(seconds: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, FocusGuardApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus session running")
            .setContentText("Time left: " + format(seconds))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(seconds: Long) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(seconds))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.focusguard.app.START"
        const val ACTION_PAUSE = "com.focusguard.app.PAUSE"
        const val ACTION_RESUME = "com.focusguard.app.RESUME"
        const val ACTION_STOP = "com.focusguard.app.STOP"
        const val ACTION_RESUME_SAVED = "com.focusguard.app.RESUME_SAVED"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_MINUTES = "minutes"
        private const val NOTIF_ID = 42

        fun format(sec: Long): String {
            val h = TimeUnit.SECONDS.toHours(sec)
            val m = TimeUnit.SECONDS.toMinutes(sec) % 60
            val s = sec % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }

        fun start(context: Context, mode: String, subject: String, minutes: Int) {
            val i = Intent(context, FocusSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode); putExtra(EXTRA_SUBJECT, subject); putExtra(EXTRA_MINUTES, minutes)
            }
            context.startForegroundService(i)
        }
        fun send(context: Context, action: String) {
            context.startService(Intent(context, FocusSessionService::class.java).apply { this.action = action })
        }
    }
}
