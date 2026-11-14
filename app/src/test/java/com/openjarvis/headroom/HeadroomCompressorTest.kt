package com.openjarvis.headroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomCompressorTest {
    @Test
    fun removesOnlyImmediatelyRepeatedToolLines() {
        val input = "User: Open YouTube\nTOOL: YouTube package found\nTOOL: YouTube package found\nAction: open_app"
        val result = HeadroomCompressor.compressContext(input)

        assertEquals(
            "User: Open YouTube\nTOOL: YouTube package found\nAction: open_app",
            result.compressed
        )
        assertTrue(result.charactersSaved > 0)
    }

    @Test
    fun preservesUserCommandsAndActionJsonVerbatim() {
        val input = """User command: hello type karo
Action JSON: [{"action":"type_text","value":"hello"}]
SAFETY: Never tap Send or Delete."""
        val result = HeadroomCompressor.compressContext(input)

        assertEquals(input, result.compressed)
    }

    @Test
    fun collapsesExcessBlankLinesButKeepsParagraphBoundary() {
        val input = "System prompt\n\n\n\nUser command"
        val result = HeadroomCompressor.compressContext(input)

        assertEquals("System prompt\n\nUser command", result.compressed)
    }

    @Test
    fun doesNotRemoveRepeatedOrdinaryConversationLines() {
        val input = "ha\nha\nUser: repeat this"
        val result = HeadroomCompressor.compressContext(input)

        assertEquals(input, result.compressed)
    }

    @Test
    fun tokenEstimateIsTelemetryOnlyAndHandlesEmptyInput() {
        assertEquals(0, HeadroomCompressor.estimateTokens(""))
        assertEquals(1, HeadroomCompressor.estimateTokens("test"))
        assertEquals(2, HeadroomCompressor.estimateTokens("12345"))
    }

    @Test
    fun emptyAndWhitespaceOnlyContextsBecomeEmpty() {
        assertEquals("", HeadroomCompressor.compressContext("").compressed)
        assertEquals("", HeadroomCompressor.compressContext(" \n  \n").compressed)
    }
}
