package com.focusguard.app.core

import kotlinx.coroutines.flow.MutableStateFlow

data class RunningSession(
    val active: Boolean = false,
    val mode: String = "",
    val subject: String = "",
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val elapsedSeconds: Long = 0,
    val paused: Boolean = false,
    val blocked: Set<String> = emptySet()
)

object SessionState {
    val flow = MutableStateFlow(RunningSession())
}
