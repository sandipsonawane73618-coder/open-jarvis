package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomLiteContextTest {
    @Test
    fun collapsesOnlyAdjacentIdenticalLinesAndPreservesFinalNewline() {
        val input = "Settings\nSettings\nWiFi\nWiFi\nWiFi\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals("Settings ×2\nWiFi ×3\n", result.text)
        assertEquals(3, result.collapsedLines)
        assertTrue(result.charsSaved > 0)
    }

    @Test
    fun preservesWarningsErrorsAndFailuresEvenWhenRepeated() {
        val input = "WARNING: mic busy\nWARNING: mic busy\nERROR: no permission\nERROR: no permission"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(input, result.text)
        assertEquals(0, result.collapsedLines)
    }

    @Test
    fun preservesUniqueAndNonAdjacentLinesInOriginalOrder() {
        val input = "Home\nSettings\nHome\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(input, result.text)
        assertEquals(0, result.collapsedLines)
    }

    @Test
    fun supportsHindiAndMarathiUnicodeWithoutNormalization() {
        val input = "सेटिंग्स\nसेटिंग्स\nआवाज\nआवाज\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals("सेटिंग्स ×2\nआवाज ×2\n", result.text)
        assertEquals(2, result.collapsedLines)
        assertTrue(result.charsSaved > 0)
    }

    @Test
    fun keepsEmptyInputAndBlankLineBoundariesUnchanged() {
        assertEquals("", HeadroomLiteContext.compressRepeatedLines("").text)
        assertEquals("\n\n", HeadroomLiteContext.compressRepeatedLines("\n\n").text)
        assertEquals("one", HeadroomLiteContext.compressRepeatedLines("one").text)
    }

    @Test
    fun doesNotExpandShortRepeatedLines() {
        val input = "a\na\n"

        val result = HeadroomLiteContext.compressRepeatedLines(input)

        assertEquals(input, result.text)
        assertEquals(0, result.collapsedLines)
        assertEquals(0, result.charsSaved)
    }

    @Test
    fun preservesRepeatedUrlsIdentifiersAndCodeExactly() {
        val protectedLines = listOf(
            "https://example.com/device?id=123456",
            "device-serial-12345",
            "550e8400-e29b-41d4-a716-446655440000",
            "package com.openjarvis.agent",
            "val deviceId = 12345",
            "status: connected"
        )

        protectedLines.forEach { line ->
            val input = "$line\n$line"
            val result = HeadroomLiteContext.compressRepeatedLines(input)
            assertEquals("must preserve structured line: $line", input, result.text)
            assertEquals(0, result.collapsedLines)
        }
    }

    @Test
    fun benchmarkFixtureReportsSavingsWithoutChangingUniqueScreenFacts() {
        val repeatedOcrFixture = listOf(
            "Settings", "Settings", "Wi-Fi", "Wi-Fi", "Wi-Fi",
            "Battery", "Battery", "Bluetooth"
        ).joinToString("\n")

        val result = HeadroomLiteContext.compressRepeatedLines(repeatedOcrFixture)

        assertEquals(
            "Settings ×2\nWi-Fi ×3\nBattery ×2\nBluetooth",
            result.text
        )
        assertEquals(repeatedOcrFixture.length, result.originalChars)
        assertEquals(result.text.length, result.compactedChars)
        assertEquals(result.originalChars - result.compactedChars, result.charsSaved)
        assertEquals(4, result.collapsedLines)
        assertEquals(result.text, HeadroomLiteContext.compressRepeatedLines(result.text).text)
    }
    @Test
    fun promptBoundaryCompactsOnlyOcrAndPreservesRequestsConfirmationsAndActionableElements() {
        val template = """CURRENT REQUEST: Open Wi-Fi settings
CONFIRMATION: User approved
SCREEN OCR: {SCREEN_OCR}
INTERACTIVE ELEMENTS:
{SCREEN_ELEMENTS}
ACTION SCHEMA: tap(id), type(id, value)"""
        val elements = "button id=wifi_on label=Wi-Fi\nbutton id=wifi_on label=Wi-Fi\nbutton id=wifi_off label=Wi-Fi"

        val result = HeadroomLiteContext.applyScreenContext(
            promptTemplate = template,
            ocrText = "Settings\nSettings\nWi-Fi\nWi-Fi",
            interactiveElements = elements
        )

        assertTrue(result.contains("CURRENT REQUEST: Open Wi-Fi settings"))
        assertTrue(result.contains("CONFIRMATION: User approved"))
        assertTrue(result.contains("ACTION SCHEMA: tap(id), type(id, value)"))
        assertTrue(result.contains("SCREEN OCR: Settings ×2\nWi-Fi ×2"))
        assertTrue(result.contains(elements))
        assertEquals(3, result.substringAfter("INTERACTIVE ELEMENTS:\n").substringBefore("\nACTION SCHEMA:").lines().size)
    }

    @Test
    fun promptBoundaryAppliesIndependentLimitsWithoutMutatingElementIdentity() {
        val elements = "id=button_101 label=Open\nid=button_102 label=Open\nid=button_103 label=Open"
        val result = HeadroomLiteContext.applyScreenContext(
            promptTemplate = "OCR={SCREEN_OCR};ELEMENTS={SCREEN_ELEMENTS};REQUEST=Do not alter",
            ocrText = "Unique OCR fact",
            interactiveElements = elements,
            maxOcrChars = 8,
            maxElementChars = elements.length
        )

        assertEquals("OCR=Unique O;ELEMENTS=$elements;REQUEST=Do not alter", result)
    }

    @Test
    fun leavesMemoryAndPreviousStepContextOutsideTheScreenCompactionBoundary() {
        val memory = "- User prefers Marathi\n- Never send messages without review"
        val previousStepContext = "app=WhatsApp; contact=Anil; messageText=Please call me"
        val request = "Send this exact message only after I review it"
        val template = """MEMORY: $memory
CURRENT REQUEST: $request
CONFIRMATION: not granted
SCREEN: {SCREEN_OCR}
ELEMENTS: {SCREEN_ELEMENTS}
PREVIOUS STEP CONTEXT: $previousStepContext
ACTION CONTRACT: never send messages"""
        val elements = "id=send_101 label=Send\nid=send_102 label=Send"

        val result = HeadroomLiteContext.applyScreenContext(
            promptTemplate = template,
            ocrText = "WhatsApp\nWhatsApp",
            interactiveElements = elements
        )

        assertTrue(result.contains("MEMORY: $memory"))
        assertTrue(result.contains("CURRENT REQUEST: $request"))
        assertTrue(result.contains("CONFIRMATION: not granted"))
        assertTrue(result.contains("PREVIOUS STEP CONTEXT: $previousStepContext"))
        assertTrue(result.contains("ACTION CONTRACT: never send messages"))
        assertTrue(result.contains("SCREEN: WhatsApp ×2"))
        assertTrue(result.contains("ELEMENTS: $elements"))
    }

}
