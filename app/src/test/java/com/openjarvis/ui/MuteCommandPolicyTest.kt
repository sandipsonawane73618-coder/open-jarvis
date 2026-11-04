package com.openjarvis.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteCommandPolicyTest {
    @Test fun recognizesRequestedRomanizedCommands() {
        listOf(
            "Jarvis chup ho jao",
            "MUTE HO JAO",
            "Shant bas Jarvis",
            "gap bas bhai",
            "Jarvis silent mode"
        ).forEach { assertTrue("$it should mute", MuteCommandPolicy.isMuteCommand(it)) }
    }

    @Test fun recognizesHindiPhrases() {
        assertTrue(MuteCommandPolicy.isMuteCommand("जार्विस चुप हो जाओ"))
        assertTrue(MuteCommandPolicy.isMuteCommand("शांत बैठो"))
    }

    @Test fun doesNotMuteOnOrdinaryConversation() {
        listOf("Jarvis YouTube kholo", "mute ka matlab kya hai", "thoda shant music chalao")
            .forEach { assertFalse("$it should not mute", MuteCommandPolicy.isMuteCommand(it)) }
    }
}
