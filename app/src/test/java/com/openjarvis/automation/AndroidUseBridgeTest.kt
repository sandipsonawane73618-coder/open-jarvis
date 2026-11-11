package com.openjarvis.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUseBridgeTest {
    @Test fun reportsAccessibilitySetupWhenServiceUnavailable() {
        val bridge = AndroidUseBridge { null }
        val observation = bridge.observe()
        assertFalse(observation.available)
        assertTrue(observation.reason.orEmpty().contains("Accessibility"))
        assertTrue(bridge.tap("OK").isFailure)
    }
}
