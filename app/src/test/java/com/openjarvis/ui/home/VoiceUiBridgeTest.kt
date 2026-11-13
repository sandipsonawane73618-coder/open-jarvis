package com.openjarvis.ui.home

import com.openjarvis.voice.VoiceSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceUiBridgeTest {
    @Test fun publishesVoiceStateToHomeObservers() {
        VoiceUiBridge.publish(VoiceSessionState.Speaking)
        assertEquals(VoiceSessionState.Speaking, VoiceUiBridge.state.value)
        VoiceUiBridge.publish(VoiceSessionState.Idle)
    }

    @Test fun stopSpeakingInvokesRegisteredOverlayCallback() {
        var stopped = 0
        VoiceUiBridge.registerStopCallback { stopped++ }
        assertTrue(VoiceUiBridge.stopSpeaking())
        assertEquals(1, stopped)
        VoiceUiBridge.registerStopCallback(null)
    }

    @Test fun stopSpeakingWithoutOverlayCallbackIsSafe() {
        VoiceUiBridge.registerStopCallback(null)
        assertFalse(VoiceUiBridge.stopSpeaking())
    }
}
