package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordConfigTest {
    @Test fun configuredPhrasesMatchVoicuteDemoModel() {
        assertEquals("Jarvis / Hey Jarvis / Hi Jarvis", WakeWordConfig.DISPLAY_PHRASE)
        VoicuteModelLoader.validateDemoKeywords(listOf("Jarvis", "Hey Jarvis", "Hi Jarvis"))
    }

    @Test fun sensitivityThresholdsBecomeMoreSensitive() {
        assertEquals(0.50f, WakeWordConfig.Sensitivity.LOW.threshold, 0.0001f)
        assertEquals(0.35f, WakeWordConfig.Sensitivity.MEDIUM.threshold, 0.0001f)
        assertEquals(0.25f, WakeWordConfig.Sensitivity.HIGH.threshold, 0.0001f)
        assertTrue(WakeWordConfig.Sensitivity.HIGH.threshold < WakeWordConfig.Sensitivity.MEDIUM.threshold)
        assertEquals(WakeWordConfig.Sensitivity.HIGH, WakeWordConfig.Sensitivity.fromPreference("unknown"))
    }

    @Test fun voicuteRejectsPhrasesNotPresentInTheBundledModel() {
        val result = runCatching {
            VoicuteModelLoader.validateDemoKeywords(listOf("Jarvis", "Hey Mitra", "Hey Dost"))
        }
        assertTrue(result.isFailure)
    }
}
