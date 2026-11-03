package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordConfigTest {
    @Test fun trimsAndRemovesWhitespaceAndQuotesFromAccessKey() {
        assertEquals("abcdefghijklmnopqrstuvwx1234567890",
            WakeWordConfig.normalizeAccessKey("  \"abcdefghijklmnop\nqrstuvwx1234567890\"  "))
    }

    @Test fun emptyKeyIsRejected() {
        assertEquals("Add your Picovoice AccessKey in Settings.", WakeWordConfig.validateAccessKey(null))
        assertEquals("Add your Picovoice AccessKey in Settings.", WakeWordConfig.validateAccessKey(""))
        assertEquals("Add your Picovoice AccessKey in Settings.", WakeWordConfig.validateAccessKey("  \n "))
    }

    @Test fun shortJarvisTextIsRejectedWithHelpfulMessage() {
        val message = WakeWordConfig.validateAccessKey("Jarvis")
        assertTrue(message.orEmpty().contains("console.picovoice.ai"))
        assertTrue(message.orEmpty().contains("chhota text"))
    }

    @Test fun keyWithSpacesAndNewlineIsNormalizedBeforeValidation() {
        val formatted = "abcd efgh ijkl mnop qrst uvwx 1234 5678"
        assertNull(WakeWordConfig.validateAccessKey(formatted))
        assertEquals("abcdefghijklmnopqrstuvwx12345678", WakeWordConfig.normalizeAccessKey(formatted))
    }

    @Test fun validLookingKeyPassesLengthValidation() {
        assertNull(WakeWordConfig.validateAccessKey("0123456789abcdef0123456789abcdef"))
    }

    @Test fun diagnosticsExposeOnlyPrefixAndLength() {
        assertEquals("keyPrefix=abcd, keyLength=30",
            WakeWordConfig.accessKeyDiagnostic("abcd0123456789abcdef0123456789"))
    }

    @Test fun wakePhraseAndEngineKeywordAreStable() {
        assertEquals("Jarvis", WakeWordConfig.DISPLAY_PHRASE)
        assertEquals("JARVIS", WakeWordConfig.ENGINE_KEYWORD)
    }
}
