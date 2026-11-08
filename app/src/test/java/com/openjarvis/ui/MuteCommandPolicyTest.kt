package com.openjarvis.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteCommandPolicyTest {
    @Test fun recognizesOnlyRequestedMuteCommands() {
        listOf("Chup ho jao", "JARVIS CHUP HO JAO", "चुप हो जाओ", "जार्विस चुप हो जाओ")
            .forEach { assertTrue("$it should mute", MuteCommandPolicy.isMuteCommand(it)) }
        listOf("mute ho jao", "shant bas", "bas kar", "Jarvis chup", "chup ho ja")
            .forEach { assertFalse("$it must not trigger mute", MuteCommandPolicy.isMuteCommand(it)) }
    }

    @Test fun recognizesOnlyRequestedUnmuteCommands() {
        listOf("Ab bolo", "AB BOLO", "अब बोलो")
            .forEach { assertTrue("$it should unmute", MuteCommandPolicy.isUnmuteCommand(it)) }
        listOf("unmute", "unmute ho ja", "awaaz chalu karo", "Jarvis")
            .forEach { assertFalse("$it must not trigger unmute", MuteCommandPolicy.isUnmuteCommand(it)) }
    }

    @Test fun ordinaryConversationDoesNotTriggerVoiceControls() {
        listOf("mute ka matlab kya hai", "thoda shant music chalao", "ab bolo kya hua", "YouTube kholo")
            .forEach {
                assertFalse("$it should not mute", MuteCommandPolicy.isMuteCommand(it))
                assertFalse("$it should not unmute", MuteCommandPolicy.isUnmuteCommand(it))
            }
    }
}
