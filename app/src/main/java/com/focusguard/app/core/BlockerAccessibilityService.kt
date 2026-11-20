package com.focusguard.app.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class BlockerAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        Blocker.maybeBlock(this, event.packageName?.toString())
    }
    override fun onInterrupt() {}
}
