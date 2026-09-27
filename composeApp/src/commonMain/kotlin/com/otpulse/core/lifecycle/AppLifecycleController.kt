package com.otpulse.core.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLifecycleState(
    val isForeground: Boolean = false,
    val refreshRevision: Long = 0L,
)

class AppLifecycleController {
    private val mutableState = MutableStateFlow(AppLifecycleState())
    val state: StateFlow<AppLifecycleState> = mutableState.asStateFlow()

    fun onForeground() {
        val current = mutableState.value
        if (current.isForeground) return
        mutableState.value = current.copy(isForeground = true, refreshRevision = current.refreshRevision + 1L)
    }

    fun onBackground() {
        mutableState.value = mutableState.value.copy(isForeground = false)
    }

    fun onSystemTimeChanged() {
        val current = mutableState.value
        mutableState.value = current.copy(refreshRevision = current.refreshRevision + 1L)
    }
}
