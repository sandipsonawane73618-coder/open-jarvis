package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun newEngineSessionClearsStaleDetectionFromPreviousRun() {
        WakeWordDiagnostics.started(true, listOf("JARVIS"), 0.1f, 2.5f)
        WakeWordDiagnostics.detected("JARVIS")
        assertEquals("JARVIS", WakeWordDiagnostics.snapshot().lastDetectionKeyword)

        WakeWordDiagnostics.started(true, listOf("JARVIS"), 0.1f, 2.5f)

        val snapshot = WakeWordDiagnostics.snapshot()
        assertNull(snapshot.lastDetectionAtMs)
        assertNull(snapshot.lastDetectionKeyword)
        assertNull(snapshot.lastDetectionScore)
        assertNull(snapshot.lastCandidateAtMs)
        assertEquals(0L, snapshot.framesProcessed)
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
        assertEquals("MONO", snapshot.channelConfig)
        assertEquals("PCM_16BIT", snapshot.encoding)
    }

    @Test fun retainsRejectedCandidateConfidenceAndReason() {
        WakeWordDiagnostics.started(true, listOf("Jarvis", "Hey Jarvis", "Hi Jarvis"), 0.25f, 1f)
        WakeWordDiagnostics.candidate(
            keyword = "Hey Jarvis",
            score = 0.21f,
            threshold = 0.25f,
            accepted = false,
            rejectionReason = "consecutive_frames",
            atMs = 123_456L
        )

        val snapshot = WakeWordDiagnostics.snapshot()
        assertEquals(123_456L, snapshot.lastCandidateAtMs!!)
        assertEquals("Hey Jarvis", snapshot.lastCandidateKeyword)
        assertEquals(0.21f, snapshot.lastCandidateScore!!, 0.0001f)
        assertFalse(snapshot.lastCandidateAccepted)
        assertEquals("consecutive_frames", snapshot.lastCandidateRejectionReason)
    }

    @Test fun recordsServiceAndWatchdogRecoveryState() {
        WakeWordDiagnostics.serviceState("RUNNING", overlayAttached = true, wakeWordRunning = true, restartAttempt = 2)
        WakeWordDiagnostics.watchdogTick(55_000L, overlayAttached = false, wakeWordRunning = false, restartAttempt = 2)
        WakeWordDiagnostics.watchdogRecovery()

        val snapshot = WakeWordDiagnostics.snapshot()
        assertEquals("RUNNING", snapshot.serviceState)
        assertFalse(snapshot.overlayAttached)
        assertFalse(snapshot.wakeWordRunning)
        assertEquals(55_000L, snapshot.watchdogLastTickAtMs!!)
        assertEquals(1L, snapshot.watchdogTickCount)
        assertEquals(1L, snapshot.watchdogRecoveryCount)
        assertEquals(2, snapshot.wakeWordRestartAttempt)
    }
}
