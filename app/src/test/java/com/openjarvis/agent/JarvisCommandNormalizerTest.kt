package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisCommandNormalizerTest {
    @Test fun stripsEnglishWakeWordOnlyAtCommandStart() {
        assertEquals("YouTube kholo aur song play karo", JarvisCommandNormalizer.stripWakeWord("Jarvis, YouTube kholo aur song play karo"))
        assertEquals("Rahul ko message bhejo: main aa raha hoon", JarvisCommandNormalizer.stripWakeWord("jarvis Rahul ko message bhejo: main aa raha hoon"))
    }

    @Test fun stripsDevanagariWakeWord() {
        assertEquals("इंस्टाग्राम खोलो", JarvisCommandNormalizer.stripWakeWord("जार्विस, इंस्टाग्राम खोलो"))
    }

    @Test fun preservesWakeWordInsideQueryAndMessageBody() {
        assertEquals("search Jarvis songs", JarvisCommandNormalizer.stripWakeWord("search Jarvis songs"))
        assertEquals("Rahul ko message bhejo: Jarvis aa raha hai", JarvisCommandNormalizer.stripWakeWord("Rahul ko message bhejo: Jarvis aa raha hai"))
    }

    @Test fun emptyAndUnprefixedCommandsRemainSafe() {
        assertEquals("", JarvisCommandNormalizer.stripWakeWord("Jarvis"))
        assertEquals("Instagram kholo", JarvisCommandNormalizer.stripWakeWord("Instagram kholo"))
    }
}
