package com.openjarvis.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectionPolicyTest {
    @Test fun acceptsFirstDetection() {
        assertTrue(WakeWordDetectionPolicy.shouldAccept(1_000L, 0L))
    }

    @Test fun suppressesRapidRepeatedDetections() {
        assertFalse(WakeWordDetectionPolicy.shouldAccept(2_999L, 1_000L))
        assertTrue(WakeWordDetectionPolicy.shouldAccept(3_000L, 1_000L))
    }

    @Test fun rejectsClockRollbackAndInvalidCooldown() {
        assertFalse(WakeWordDetectionPolicy.shouldAccept(900L, 1_000L))
        assertFalse(WakeWordDetectionPolicy.shouldAccept(2_000L, 1_000L, -1L))
    }

    @Test fun zeroCooldownAllowsConsecutiveDetections() {
        assertTrue(WakeWordDetectionPolicy.shouldAccept(1_001L, 1_000L, 0L))
    }
}
