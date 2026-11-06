package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDiagnosticsTest {
    @Test fun tracksSignalLevelSilenceAndClipping() {
        WakeWordDiagnostics.started(true, listOf("JARVIS"), 0.1f, 2.5f)

        WakeWordDiagnostics.audioFrame(level = 0.001f, peak = 0.02f, clipped = 0)
        WakeWordDiagnostics.audioFrame(level = 0.12f, peak = 0.99f, clipped = 3)

        val snapshot = WakeWordDiagnostics.snapshot()
        assertEquals(2L, snapshot.framesProcessed)
        assertEquals(1L, snapshot.silentFrames)
        assertEquals(3L, snapshot.clippedSamples)
        assertEquals(0.12f, snapshot.rms, 0.0001f)
        assertEquals(0.99f, snapshot.peakLevel, 0.0001f)
    }

    @Test fun recordsAudioEffectAvailabilityAndEnabledState() {
        WakeWordDiagnostics.started(true, listOf("JARVIS"), 0.1f, 2.5f)
        WakeWordDiagnostics.audioConfiguration(
            agcAvailable = true,
            agcEnabled = true,
            noiseSuppressorAvailable = false,
            noiseSuppressorEnabled = false,
            audioSource = "MIC",
            sampleRate = 16_000
        )

        val snapshot = WakeWordDiagnostics.snapshot()
        assertTrue(snapshot.agcAvailable)
        assertTrue(snapshot.agcEnabled)
        assertFalse(snapshot.noiseSuppressorAvailable)
        assertFalse(snapshot.noiseSuppressorEnabled)
        assertEquals("MIC", snapshot.audioSource)
        assertEquals(16_000, snapshot.sampleRate)
    }
}
