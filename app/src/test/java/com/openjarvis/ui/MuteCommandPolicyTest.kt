package com.openjarvis.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteCommandPolicyTest {
    @Test fun recognizesEnglishHindiMarathiAndHinglishMuteVocabulary() {
        listOf(
            "Mute", "Jarvis mute", "Stop listening", "Mic off",
            "Chup ho jao", "mic band karo", "sunna band karo",
            "माइक बंद करो", "चुप हो जाओ", "सुनना बंद करो",
            "mic band kar", "aikna band kar",
            "माइक बंद करा", "ऐकणं बंद कर", "आवाज बंद करा"
        ).forEach { assertTrue("$it should mute", MuteCommandPolicy.isMuteCommand(it)) }
    }

    @Test fun recognizesEnglishHindiMarathiAndHinglishUnmuteVocabulary() {
        listOf(
            "Unmute", "Jarvis unmute", "Resume listening", "Mic on",
            "Ab bolo", "mic chalu karo", "sunna shuru karo",
            "अब बोलो", "माइक चालू करो", "सुनना शुरू करो",
            "aikayla suru kar", "punha aika",
            "पुन्हा ऐका", "ऐकायला सुरू करा", "माइक सुरू करा"
        ).forEach { assertTrue("$it should unmute", MuteCommandPolicy.isUnmuteCommand(it)) }
    }

    @Test fun ordinaryConversationDoesNotTriggerVoiceControls() {
        listOf(
            "mute ka matlab kya hai",
            "thoda shant music chalao",
            "ab bolo kya hua",
            "YouTube kholo",
            "Jarvis",
            "I want to learn how to unmute a video"
        ).forEach {
            assertFalse("$it should not mute", MuteCommandPolicy.isMuteCommand(it))
            assertFalse("$it should not unmute", MuteCommandPolicy.isUnmuteCommand(it))
        }
    }
}
