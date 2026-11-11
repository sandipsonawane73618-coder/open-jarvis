package com.openjarvis.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteCommandPolicyTest {
    @Test fun recognizesEnglishHindiMarathiAndHinglishMuteVocabulary() {
        listOf(
            "Mute", "Jarvis mute", "Stop listening", "Mic off", "Mute ho ja", "Mute ho ja Jarvis",
            "Chup ho ja", "Chup ho jao", "Chup ho ja Jarvis", "mic band karo", "sunna band karo",
            "माइक बंद करो", "चुप हो जा", "चुप हो जाओ", "गप्प बस", "आवाज़ बंद करो", "सुनना बंद करो",
            "mic band kar", "aikna band kar",
            "माइक बंद करा", "ऐकणं बंद कर", "आवाज बंद करा"
        ).forEach { assertTrue("$it should mute", MuteCommandPolicy.isMuteCommand(it)) }
    }

    @Test fun recognizesEnglishHindiMarathiAndHinglishUnmuteVocabulary() {
        listOf(
            "Unmute", "Unmute karo", "Jarvis unmute", "Resume listening", "Mic on", "Awaaz chalu karo",
            "Ab bolo", "mic chalu karo", "sunna shuru karo",
            "अब बोलो", "माइक चालू करो", "आवाज़ चालू करो", "आवाज सुरू करा", "सुनना शुरू करो",
            "aikayla suru kar", "punha aika",
            "पुन्हा ऐका", "ऐकायला सुरू करा", "माइक सुरू करा"
        ).forEach { assertTrue("$it should unmute", MuteCommandPolicy.isUnmuteCommand(it)) }
    }

    @Test fun acceptsWakeWordPrefixedVoiceControlsAndExplicitOnOffAliases() {
        listOf("Hey Jarvis, mute", "Jarvis mute now", "Voice assistant off", "Mute please", "Mute ho ja Jarvis")
            .forEach { assertTrue("$it should mute", MuteCommandPolicy.isMuteCommand(it)) }
        listOf("Hey Jarvis, unmute", "Jarvis unmute now", "Voice assistant on", "Unmute please", "Jarvis unmute karo")
            .forEach { assertTrue("$it should unmute", MuteCommandPolicy.isUnmuteCommand(it)) }
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
