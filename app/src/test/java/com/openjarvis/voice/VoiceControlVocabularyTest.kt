package com.openjarvis.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceControlVocabularyTest {
    @Test fun recognizesMuteInEnglishHindiMarathiAndHinglish() {
        listOf(
            "mute", "be quiet", "shant raha", "shant ho jao",
            "चुप रहो", "शांत रहो", "गप्प बसा", "गप्प रहा"
        ).forEach { assertTrue("mute: $it", VoiceControlVocabulary.isMuteCommand(it)) }
    }

    @Test fun recognizesUnmuteInEnglishHindiMarathiAndHinglish() {
        listOf(
            "unmute", "listen again", "ab suno", "ab sunna shuru karo",
            "फिर से सुनो", "सुनना शुरू करो", "आता ऐका", "पुन्हा ऐका"
        ).forEach { assertTrue("unmute: $it", VoiceControlVocabulary.isUnmuteCommand(it)) }
    }

    @Test fun muteAndUnmuteRemainDistinct() {
        assertFalse(VoiceControlVocabulary.isMuteCommand("ab suno"))
        assertFalse(VoiceControlVocabulary.isUnmuteCommand("shant raho"))
    }
}
