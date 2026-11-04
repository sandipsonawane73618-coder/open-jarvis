package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {
    @Test fun recognizesPreferredDeviceLocales() {
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("hi"))
        assertEquals("mr-IN", SpeechLanguage.preferredRecognitionLocale("mr"))
        assertEquals("en-IN", SpeechLanguage.preferredRecognitionLocale("en"))
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("unknown"))
    }

    @Test fun detectsHindiMarathiAndEnglishOutput() {
        assertEquals("hi", SpeechLanguage.languageForText("नमस्ते, आप कैसे हैं"))
        assertEquals("mr", SpeechLanguage.languageForText("मला मदत करा, हे चांगले आहे"))
        assertEquals("en", SpeechLanguage.languageForText("Hello, Jarvis"))
    }
}
