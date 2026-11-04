package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDiagnosticsTest {
    @Test fun modelResponseSummaryKeepsActionFlowButOmitsPayloads() {
        val raw = """[{"action":"open_app","label":"WhatsApp"},{"action":"type_text","value":"private message body"},{"action":"click","text":"Send"}]"""
        val summary = summarizeDiagnosticResponse(raw)
        assertTrue(summary.contains("open_app -> type_text -> click"))
        assertFalse(summary.contains("private message body"))
        assertFalse(summary.contains("WhatsApp"))
    }

    @Test fun reasonRedactsCommandAndTypedOrClickedText() {
        val command = "send secret text to Rahul"
        val reason = "Action failed for $command: Could not type: secret text"
        val safe = redactDiagnosticReason(reason, command)
        assertFalse(safe.contains(command))
        assertFalse(safe.contains("secret text"))
    }
    @Test fun localDebugReportIncludesRawModelResponseWithoutPuttingItInSummary() {
        val raw = """["type_text","value":"hello"]"""
        val summary = summarizeDiagnosticResponse(raw)
        assertFalse(summary.contains(raw))
        assertTrue(raw.contains("type_text"))
    }

}
