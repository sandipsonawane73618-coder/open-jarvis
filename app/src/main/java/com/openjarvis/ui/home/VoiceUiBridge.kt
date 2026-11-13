package com.openjarvis.ui.home

import com.openjarvis.voice.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Narrow UI-only bridge to the one VoiceManager owned by OverlayService.
 * It publishes presentation state and delegates interruption to that same instance.
 */
object VoiceUiBridge {
    private val mutableState = MutableStateFlow(VoiceSessionState.Idle)
    val state: StateFlow<VoiceSessionState> = mutableState.asStateFlow()

    @Volatile private var stopSpeakingCallback: (() -> Unit)? = null

    fun publish(value: VoiceSessionState) {
        mutableState.value = value
    }

    @Synchronized
    fun registerStopCallback(callback: (() -> Unit)?) {
        stopSpeakingCallback = callback
    }

    fun stopSpeaking(): Boolean {
        val callback = stopSpeakingCallback ?: return false
        callback.invoke()
        return true
    }
}
