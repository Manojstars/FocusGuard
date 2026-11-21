package com.focusguard.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.MainActivity
import com.focusguard.app.ui.appLabel

/** Full-screen block window drawn directly over the blocked app.
 *  Uses TYPE_APPLICATION_OVERLAY (the "display over other apps" permission),
 *  so it does not depend on starting an Activity from the background -
 *  which many phones (Xiaomi/Oppo/Vivo/Samsung) silently block. */
object OverlayBlocker {
    private var view: LinearLayout? = null
    private var currentPkg: String? = null
    private var labelView: TextView? = null
    private var timeView: TextView? = null

    fun isShowing(): Boolean = view != null
    fun shownPkg(): String? = currentPkg

    fun show(context: Context, pkg: String) {
        if (view != null && currentPkg == pkg) { refreshTime(); return }
        hide(context)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val label = appLabel(context, pkg)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#07130D"))
            setPadding(64, 64, 64, 64)
        }
        fun tv(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
            this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(tv("🛡️", 56f, Color.WHITE))
        labelView = tv("$label is blocked", 26f, Color.WHITE, true)
        root.addView(labelView)
        root.addView(tv("You are in a focus session.", 16f, Color.parseColor("#B7E4C7")))
        timeView = tv("", 34f, Color.parseColor("#7CFC9A"), true)
        root.addView(timeView)

        val homeBtn = Button(context).apply {
            text = "Go Home"
            setOnClickListener {
                hide(context)
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(home)
            }
        }
        root.addView(homeBtn)
        val fgBtn = Button(context).apply {
            text = "Back to FocusGuard"
            setOnClickListener {
                hide(context)
                val i = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(i)
            }
        }
        root.addView(fgBtn)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.windowAnimations = 0
        try {
            wm.addView(root, params)
            view = root; currentPkg = pkg
            refreshTime()
        } catch (e: Exception) {
            // Overlay permission missing - leave view null; activity fallback path handles it
            view = null; currentPkg = null
        }
    }

    fun refreshTime() {
        val s = SessionState.flow.value
        timeView?.text = if (s.mode == "Stopwatch") "Focus session running"
            else FocusSessionService.format(s.remainingSeconds) + " left"
    }

    fun hide(context: Context) {
        val v = view ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) { }
        view = null; currentPkg = null; labelView = null; timeView = null
    }
}
