package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordConfigTest {
    @Test fun trimsAccessKeyWithoutChangingItsValue() {
        assertEquals("free-key-123", WakeWordConfig.normalizeAccessKey("  free-key-123  "))
    }

    @Test fun nullOrBlankAccessKeyStaysEmpty() {
        assertEquals("", WakeWordConfig.normalizeAccessKey(null))
        assertEquals("", WakeWordConfig.normalizeAccessKey("   "))
    }

    @Test fun wakePhraseAndEngineKeywordAreStable() {
        assertEquals("Hey Jarvis", WakeWordConfig.DISPLAY_PHRASE)
        assertEquals("JARVIS", WakeWordConfig.ENGINE_KEYWORD)
    }
}
