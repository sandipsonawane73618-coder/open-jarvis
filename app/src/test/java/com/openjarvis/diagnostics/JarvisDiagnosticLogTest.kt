package com.openjarvis.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisDiagnosticLogTest {
    @Test fun redactsCredentialsBearerTokensUrlsEmailAndIndianPhoneNumbers() {
        val raw = "api_key=secret-value Authorization: Bearer abc.def.ghi " +
            "https://example.test/path?access_token=private user@example.com +919876543210"
        val safe = DiagnosticRedactor.redact(raw)

        assertFalse(safe.contains("secret-value"))
        assertFalse(safe.contains("abc.def.ghi"))
        assertFalse(safe.contains("https://"))
        assertFalse(safe.contains("user@example.com"))
        assertFalse(safe.contains("9876543210"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test fun rejectedWakeCandidateKeepsConfidenceThresholdAndGateReason() {
        val message = JarvisDiagnosticLog.formatCandidateMessage(
            keyword = "Hey Jarvis",
            score = 0.2134f,
            threshold = 0.25f,
            accepted = false,
            rejectionReason = "consecutive_frames",
            consecutiveFrames = 1,
            gateCode = 1,
            peak = 0.31f,
            background = 0.04f,
            preRmsRaw = 182.5f
        )

        assertTrue(message.contains("keyword=Hey Jarvis"))
        assertTrue(message.contains("score=0.2134"))
        assertTrue(message.contains("threshold=0.2500"))
        assertTrue(message.contains("accepted=false"))
        assertTrue(message.contains("rejection=consecutive_frames"))
        assertTrue(message.contains("peak=0.3100"))
        assertTrue(message.contains("background=0.0400"))
        assertTrue(message.contains("preRmsRaw=182.5"))
    }

    @Test fun arbitraryModelLabelIsNotCopiedIntoDiagnosticMessage() {
        val message = JarvisDiagnosticLog.formatCandidateMessage(
            keyword = "recognized private speech",
            score = 0.4f,
            threshold = 0.25f,
            accepted = false,
            rejectionReason = "pipeline_rejected",
            consecutiveFrames = 0,
            gateCode = 0
        )

        assertTrue(message.contains("keyword=other"))
        assertFalse(message.contains("recognized private speech"))
    }

    @Test fun retentionKeepsOnlyNewestEvents() {
        assertEquals(listOf(3, 4, 5), JarvisDiagnosticLog.retainNewest(listOf(1, 2, 3, 4, 5), 3))
        assertEquals(emptyList<Int>(), JarvisDiagnosticLog.retainNewest(listOf(1, 2), 0))
    }
    @Test fun stackTraceKeepsFramesButOmitsExceptionMessage() {
        val error = IllegalStateException("user@example.com api_key=private-value")
        val safe = DiagnosticRedactor.stackTrace(error)

        assertTrue(safe.contains("java.lang.IllegalStateException"))
        assertTrue(safe.contains("JarvisDiagnosticLogTest"))
        assertFalse(safe.contains("user@example.com"))
        assertFalse(safe.contains("private-value"))
    }

}
