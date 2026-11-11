package com.openjarvis.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEchoGuardTest {
    @Test fun ignoresExactFullJarvisTtsReplyDuringAndShortlyAfterSpeech() {
        val reply = "Jarvis is listening now and ready to help you with your commands."
        val guard = TtsEchoGuard()
        guard.onSpeechStarted(reply)
        assertTrue(guard.shouldIgnore(reply, 100L))
        guard.onSpeechFinished(200L)
        assertTrue(guard.shouldIgnore(reply, 1_000L))
    }

    @Test fun ignoresNearCompleteTtsReplyWithAtLeastEightyPercentTokenOverlap() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Jarvis is listening now and ready to help")
        guard.onSpeechFinished(100L)
        assertTrue(guard.shouldIgnore("Jarvis is listening now and ready to", 200L))
    }

    @Test fun neverIgnoresShortTimeCommandEvenWhenItMatchesTtsExactly() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("time kya hua")
        guard.onSpeechFinished(100L)
        assertFalse(guard.shouldIgnore("time kya hua", 200L))
    }

    @Test fun neverIgnoresShortCommandContainedInLongerTtsReply() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Abhi samay nau baje hain aur main aapki madad kar sakta hoon")
        guard.onSpeechFinished(100L)
        assertFalse(guard.shouldIgnore("time kya hua", 200L))
    }

    @Test fun doesNotIgnoreDifferentUserCommand() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Jarvis is listening now and ready to help you")
        guard.onSpeechFinished(100L)
        assertFalse(guard.shouldIgnore("volume badhao", 200L))
    }

    @Test fun expiresEchoWindowAfterFifteenHundredMilliseconds() {
        val guard = TtsEchoGuard()
        guard.onSpeechStarted("Jarvis is listening now and ready to help you")
        guard.onSpeechFinished(100L)
        assertFalse(guard.shouldIgnore(
            "Jarvis is listening now and ready to help you",
            100L + VoiceSessionTiming.TTS_ECHO_WINDOW_MS + 1L
        ))
    }

    @Test fun postTtsRearmDelayIsSevenHundredMilliseconds() {
        assertTrue(VoiceSessionTiming.POST_TTS_REARM_DELAY_MS == 700L)
    }

    @Test fun ttsEchoWindowIsFifteenHundredMilliseconds() {
        assertTrue(VoiceSessionTiming.TTS_ECHO_WINDOW_MS == 1_500L)
    }
}
