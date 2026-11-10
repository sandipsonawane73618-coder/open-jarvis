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

    @Test fun pipelineTraceSummaryOmitsStepReplyAndSkippedContent() {
        val step = "send private message to Rahul"
        val reply = "Message body contains a private address"
        val skipped = "delete account confirmation"
        assertTrue(summarizePipelineTraceText(step).contains("${step.length} chars"))
        assertFalse(summarizePipelineTraceText(step).contains("Rahul"))
        assertFalse(summarizePipelineTraceText(reply).contains("private address"))
        assertFalse(summarizePipelineTraceText(skipped).contains("delete account"))
    }

    @Test fun reasonRedactsCommandAndTypedOrClickedText() {
        val command = "send secret text to Rahul"
        val reason = "Action failed for $command: Could not type: secret text"
        val safe = redactDiagnosticReason(reason, command)
        assertFalse(safe.contains(command))
        assertFalse(safe.contains("secret text"))
    }
}
