package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadroomContextBudgetTest {
    @Test
    fun keepsAllContextWhenItFitsWithinItsPriorityCaps() {
        val result = HeadroomContextBudget.allocate(
            memory = "saved fact",
            screenText = "Visible heading",
            screenElements = "Button: Search",
            installedApps = "WhatsApp"
        )

        assertEquals("saved fact", result.memory)
        assertEquals("Visible heading", result.screenText)
        assertEquals("Button: Search", result.screenElements)
        assertEquals("WhatsApp", result.installedApps)
        assertEquals(result.outputChars, result.inputChars)
    }

    @Test
    fun allocatesCompleteRecordsWithinPriorityCaps() {
        val result = HeadroomContextBudget.allocate(
            memory = "M".repeat(350) + "\n" + "m".repeat(50),
            screenText = "O".repeat(150),
            screenElements = "E".repeat(450) + "\n" + "e".repeat(250),
            installedApps = "A".repeat(50) + ", " + "B".repeat(20)
        )

        assertEquals(450, result.screenElements.length)
        assertEquals(350, result.memory.length)
        assertEquals(150, result.screenText.length)
        assertEquals(50, result.installedApps.length)
        assertTrue(result.outputChars <= HeadroomContextBudget.MAX_TOTAL_CHARS)
    }

    @Test
    fun keepsOnlyWholeInteractiveElementLinesWhenTruncating() {
        val firstLine = "[#1 clickable] " + "a".repeat(245)
        val secondLine = "[#2 editable] " + "b".repeat(500)
        val result = HeadroomContextBudget.allocate(
            memory = "",
            screenText = "",
            screenElements = "$firstLine\n$secondLine",
            installedApps = ""
        )

        assertEquals(firstLine, result.screenElements)
        assertTrue(!result.screenElements.contains("[#2"))
    }

    @Test
    fun dropsOversizedFirstInteractiveElementInsteadOfCuttingItsTarget() {
        val oversizedTarget = "[#1 clickable] " + "x".repeat(500)
        val result = HeadroomContextBudget.allocate(
            memory = "remembered fact",
            screenText = "",
            screenElements = oversizedTarget,
            installedApps = ""
        )

        assertEquals("", result.screenElements)
        assertEquals("remembered fact", result.memory)
    }

    @Test
    fun memoryFactsAreNeverCutMidLine() {
        val firstFact = "- Saved fact: " + "a".repeat(170)
        val secondFact = "- Another fact: " + "b".repeat(170)
        val result = HeadroomContextBudget.allocate(
            memory = "$firstFact\n$secondFact",
            screenText = "",
            screenElements = "",
            installedApps = ""
        )

        assertEquals(firstFact, result.memory)
    }

    @Test
    fun appLabelsAreKeptAsCompleteCommaDelimitedItems() {
        val result = HeadroomContextBudget.fitDelimitedItems(
            "WhatsApp, YouTube Music, Google Chrome",
            ", ",
            20
        )
        assertEquals("WhatsApp", result)
    }

    @Test
    fun ocrTruncationKeepsWholeWords() {
        val input = ("visible heading ").repeat(20)
        val result = HeadroomContextBudget.fitTextAtWordBoundary(input, 50)

        assertTrue(result.length <= 50)
        assertTrue(result.endsWith("…"))
        assertTrue(result.dropLast(1).endsWith("heading"))
    }

    @Test
    fun oversizedElementDoesNotHideLaterCompleteTargets() {
        val oversized = "[#1 clickable] " + "x".repeat(500)
        val later = "[#2 editable] text=Search bounds=1,2,3,4"
        val result = HeadroomContextBudget.fitCompleteLines("$oversized\n$later", 450)

        assertEquals(later, result)
    }

    @Test
    fun oversizedAppLabelDoesNotHideLaterCompleteAppNames() {
        val result = HeadroomContextBudget.fitDelimitedItems(
            "A".repeat(60) + ", Chrome",
            ", ",
            50
        )
        assertEquals("Chrome", result)
    }

    @Test
    fun ocrTruncationDropsAnUnbrokenLongTokenInsteadOfSplittingIt() {
        val result = HeadroomContextBudget.fitTextAtWordBoundary("x".repeat(200), 50)
        assertEquals("", result)
    }

    @Test
    fun emptyContextStaysEmpty() {
        val result = HeadroomContextBudget.allocate("", "", "", "")
        assertEquals(0, result.outputChars)
        assertEquals(0, result.inputChars)
    }
}
