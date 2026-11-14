package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionLocaleRetryPolicyTest {
    @Test fun preferredLocalesAreHindiEnglishMarathiThenDeviceDefault() {
        assertEquals(listOf("hi-IN", "en-IN", "mr-IN", "ta-IN"),
            RecognitionLocaleRetryPolicy.localeAttempts("ta-IN"))
    }

    @Test fun duplicateDeviceLocaleIsNotRetriedTwice() {
        assertEquals(listOf("hi-IN", "en-IN", "mr-IN"),
            RecognitionLocaleRetryPolicy.localeAttempts("mr-IN"))
    }

    @Test fun atMostThreeRetriesAfterInitialLocale() {
        assertEquals(4, RecognitionLocaleRetryPolicy.localeAttempts("ta-IN").size)
    }

    @Test fun onlyLanguageUnsupportedAndUnavailableErrorsRetry() {
        assertTrue(RecognitionLocaleRetryPolicy.shouldRetry(12))
        assertTrue(RecognitionLocaleRetryPolicy.shouldRetry(13))
        assertFalse(RecognitionLocaleRetryPolicy.shouldRetry(7))
    }

    @Test fun localeStatusDistinguishesInstalledPendingSupportedAndUnsupported() {
        assertEquals("Installed", RecognitionLocaleRetryPolicy.statusFor("hi-IN",
            setOf("hi-IN"), emptySet(), setOf("hi-IN", "en-IN")))
        assertEquals("Download pending", RecognitionLocaleRetryPolicy.statusFor("mr-IN",
            emptySet(), setOf("mr-IN"), setOf("hi-IN", "mr-IN")))
        assertEquals("Available", RecognitionLocaleRetryPolicy.statusFor("en-IN",
            emptySet(), emptySet(), setOf("en-IN")))
        assertEquals("Unsupported", RecognitionLocaleRetryPolicy.statusFor("ta-IN",
            emptySet(), emptySet(), setOf("hi-IN", "en-IN")))
    }
}
