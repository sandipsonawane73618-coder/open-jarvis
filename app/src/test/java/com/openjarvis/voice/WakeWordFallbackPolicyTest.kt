package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordFallbackPolicyTest {
    @Test fun waitsBeforeRecognitionTimeout() {
        assertEquals(
            WakeWordFallbackPolicy.Decision.WAIT,
            WakeWordFallbackPolicy.evaluate(
                nowMs = 44_999L, startedAtMs = 1L, lastDetectionAtMs = null,
                framesProcessed = 100, silentFrames = 0, hasValidAccessKey = true
            )
        )
    }

    @Test fun silenceDoesNotTriggerFallback() {
        assertEquals(
            WakeWordFallbackPolicy.Decision.WAIT,
            WakeWordFallbackPolicy.evaluate(
                nowMs = 60_000L, startedAtMs = 1L, lastDetectionAtMs = null,
                framesProcessed = 500, silentFrames = 500, hasValidAccessKey = true
            )
        )
    }

    @Test fun recentSuccessfulDetectionPreventsFallback() {
        assertEquals(
            WakeWordFallbackPolicy.Decision.WAIT,
            WakeWordFallbackPolicy.evaluate(
                nowMs = 60_000L, startedAtMs = 1L, lastDetectionAtMs = 50_000L,
                framesProcessed = 500, silentFrames = 0, hasValidAccessKey = true
            )
        )
    }

    @Test fun staleDetectionDoesNotBlockFallbackForever() {
        assertEquals(
            WakeWordFallbackPolicy.Decision.FALLBACK_TO_PORCUPINE,
            WakeWordFallbackPolicy.evaluate(
                nowMs = 100_000L, startedAtMs = 1L, lastDetectionAtMs = 50_000L,
                framesProcessed = 500, silentFrames = 20, hasValidAccessKey = true
            )
        )
    }

    @Test fun activeSpeechFallsBackOnlyWhenAccessKeyIsAvailable() {
        assertEquals(
            WakeWordFallbackPolicy.Decision.FALLBACK_TO_PORCUPINE,
            WakeWordFallbackPolicy.evaluate(60_000L, 1L, null, 500L, 20L, true)
        )
        assertEquals(
            WakeWordFallbackPolicy.Decision.NO_FALLBACK_KEY,
            WakeWordFallbackPolicy.evaluate(60_000L, 1L, null, 500L, 20L, false)
        )
    }

    @Test fun insufficientActiveSpeechFramesWaits() {
        assertEquals(
            WakeWordFallbackPolicy.Decision.WAIT,
            WakeWordFallbackPolicy.evaluate(
                nowMs = 60_000L, startedAtMs = 1L, lastDetectionAtMs = null,
                framesProcessed = 100, silentFrames = 95, hasValidAccessKey = true
            )
        )
    }
}
