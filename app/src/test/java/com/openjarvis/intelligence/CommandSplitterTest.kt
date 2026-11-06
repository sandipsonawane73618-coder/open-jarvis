package com.openjarvis.intelligence

import org.junit.Assert.*
import org.junit.Test

class CommandSplitterTest {
    @Test fun splitsHindiEnglishAndMarathiConnectorsAndCapsAtFive() {
        assertEquals(2, CommandSplitter.split("WiFi settings kholo aur Bluetooth settings kholo")!!.size)
        assertEquals(2, CommandSplitter.split("back phir home")!!.size)
        assertEquals(2, CommandSplitter.split("back ani home")!!.size)
        assertNull(CommandSplitter.split("a then b then c then d then e then f"))
    }

    @Test fun carriesRecognizedContextToFollowingStep() {
        val steps = CommandSplitter.split("YouTube kholo aur lofi search karo")!!
        assertEquals("YouTube", steps[1].inheritedContext)
    }

    @Test fun allSixAcceptanceUtterancesSplitWithoutDroppingText() {
        val phrases = listOf(
            "YouTube kholo aur lofi search karo",
            "Rahul ko call karo",
            "WhatsApp kholo aur Rahul ko message bhejo ki kal milte hain",
            "WiFi settings kholo aur phir Bluetooth settings kholo",
            "Chrome kholo aur google par mausam search karo",
            "subah 6 baje ka alarm lagao aur phir torch on karo"
        )
        phrases.forEach { phrase ->
            val result = CommandSplitter.split(phrase)
            assertNotNull("Could not split: $phrase", result)
            assertTrue(result!!.size in 1..5)
            result.forEach { step -> assertTrue(phrase.contains(step.text)) }
        }
    }
}
