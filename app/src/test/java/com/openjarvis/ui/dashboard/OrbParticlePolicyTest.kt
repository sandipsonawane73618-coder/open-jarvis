package com.openjarvis.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbParticlePolicyTest {
    @Test fun particleCountNeverExceedsFourHundred() {
        assertEquals(400, OrbParticlePolicy.PARTICLE_COUNT)
        assertTrue(OrbParticlePolicy.PARTICLE_COUNT <= 400)
    }

    @Test fun animationCadenceIsAtMostThirtyFramesPerSecond() {
        assertTrue(OrbParticlePolicy.FRAME_INTERVAL_MS >= 33L)
    }
}
