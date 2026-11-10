package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotMemoryCodecTest {
    @Test fun normalizesOcrAndRejectsEmptyOrOversizedContent() {
        assertEquals("Account\nBalance: 1200", ScreenshotMemoryCodec.normalize(" Account  \n Balance:   1200 "))
        assertEquals(null, ScreenshotMemoryCodec.normalize("  "))
        assertEquals(null, ScreenshotMemoryCodec.normalize("x".repeat(2_001)))
    }

    @Test fun keepsNewestEightUniqueScreens() {
        val entries = (1..10).fold(emptyList<String>()) { acc, n ->
            ScreenshotMemoryCodec.remember(acc, "Screen content number $n")
        }
        assertEquals(8, entries.size)
        assertEquals("Screen content number 10", entries.first())
        assertFalse(entries.contains("Screen content number 1"))
    }

    @Test fun retrievesOnlyRelevantSavedScreenText() {
        val entries = listOf("Settings page has battery optimization enabled", "WhatsApp chat with Rahul")
        assertEquals(
            listOf("WhatsApp chat with Rahul"),
            ScreenshotMemoryCodec.retrieve(entries, "WhatsApp Rahul")
        )
        assertTrue(ScreenshotMemoryCodec.promptContext(entries, "weather forecast").isEmpty())
    }

    @Test fun encryptedPreferencePayloadCodecIsBoundedAndRecoversInvalidJsonSafely() {
        val entries = listOf("Screen one", "Screen two")
        assertEquals(entries, ScreenshotMemoryCodec.decode(ScreenshotMemoryCodec.encode(entries)))
        assertTrue(ScreenshotMemoryCodec.decode("{broken").isEmpty())
        assertTrue(ScreenshotMemoryCodec.decode(null).isEmpty())
    }

    @Test fun promptContextHasStrictSizeLimit() {
        val large = listOf("important item ".repeat(130))
        assertTrue(ScreenshotMemoryCodec.promptContext(large, "important item").length <= 1_300)
    }
}
