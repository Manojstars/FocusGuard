package com.focusguard.app.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.SessionStore

/** Small floating 🛡️ button shown during a focus session.
 *  Tap it to open a mini control panel: remaining time, Pause/Resume,
 *  End session - and when no session runs, quick-start timings.
 *  This is the always-reachable "session button" Manu asked for. */
object FloatingButton {
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelTime: TextView? = null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun roundBg(color: String, radius: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = radius
    }

    fun show(context: Context) {
        if (bubble != null) return
        if (!Settings.canDrawOverlays(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        val size = (58 * density).toInt()
        val tv = TextView(context).apply {
            text = "🛡️"
            textSize = 24f
            gravity = Gravity.CENTER
            background = roundBg("#0E241A", size / 2f)
            setTextColor(Color.WHITE)
        }
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt(); y = (160 * density).toInt()
            windowAnimations = 0
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        tv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = params.x; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt(); val dy = (ev.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 12) moved = true
                    params.x = startX - dx; params.y = startY + dy
                    try { wm.updateViewLayout(v, params) } catch (e: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel(context)
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(tv, params)
            bubble = tv; bubbleParams = params
        } catch (e: Exception) { }
    }

    fun hideAll(context: Context) {
        hidePanel(context)
        val b = bubble ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(b)
        } catch (e: Exception) { }
        bubble = null; bubbleParams = null
    }

    fun refresh() {
        val t = panelTime ?: return
        val s = SessionState.flow.value
        t.text = when {
            !s.active -> "No session running"
            s.mode == "Stopwatch" -> "⏱ " + FocusSessionService.format(s.elapsedSeconds) + if (s.paused) " (paused)" else ""
            else -> "⏱ " + FocusSessionService.format(s.remainingSeconds) + " left" + if (s.paused) " (paused)" else ""
        }
    }

    private fun togglePanel(context: Context) {
        if (panel != null) hidePanel(context) else showPanel(context)
    }

    private fun hidePanel(context: Context) {
        val p = panel ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(p)
        } catch (e: Exception) { }
        panel = null; panelTime = null
    }

    private fun showPanel(context: Context) {
        if (!Settings.canDrawOverlays(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        val s = SessionState.flow.value

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg("#0E241A", 24 * density)
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt())
        }
        fun label(text: String, size: Float, bold: Boolean = false) = TextView(context).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(label("🛡️ FocusGuard", 17f, true))
        panelTime = label("", 20f, true).apply { setTextColor(Color.parseColor("#7CFC9A")) }
        root.addView(panelTime)

        fun actionBtn(text: String, action: () -> Unit) = Button(context).apply {
            this.text = text
            setOnClickListener { action() }
        }

        if (s.active) {
            if (s.paused) root.addView(actionBtn("▶ Resume session") {
                FocusSessionService.send(context, FocusSessionService.ACTION_RESUME); hidePanel(context)
            }) else root.addView(actionBtn("⏸ Pause session") {
                FocusSessionService.send(context, FocusSessionService.ACTION_PAUSE); hidePanel(context)
            })
            root.addView(actionBtn("⏹ End session") {
                FocusSessionService.send(context, FocusSessionService.ACTION_STOP); hidePanel(context)
            })
        } else {
            root.addView(label("Start focus:", 13f))
            listOf(15, 25, 45, 60).forEach { mins ->
                root.addView(actionBtn("▶ $mins min") {
                    FocusSessionService.start(context, "Timer", SessionStore.exam.ifBlank { "Study" }, mins)
                    hidePanel(context)
                })
            }
        }
        root.addView(actionBtn("Close") { hidePanel(context) })

        val params = WindowManager.LayoutParams(
            (280 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            windowAnimations = 0
        }
        try {
            wm.addView(root, params)
            panel = root
            refresh()
        } catch (e: Exception) { }
    }
}
