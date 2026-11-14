package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLanguageTest {
    @Test fun recognizesPreferredDeviceLocales() {
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("hi"))
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("mr"))
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("en"))
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("unknown"))
    }

    @Test fun detectsHindiMarathiAndEnglishOutput() {
        assertEquals("hi", SpeechLanguage.languageForText("नमस्ते, आप कैसे हैं"))
        assertEquals("mr", SpeechLanguage.languageForText("मला मदत करा, हे चांगले आहे"))
        assertEquals("en", SpeechLanguage.languageForText("Hello, Jarvis"))
    }

    @Test fun detectsMaharashtraRegionalDialects() {
        assertEquals(MaharashtraSpeechProfile.Dialect.VARHADI, MaharashtraSpeechProfile.detectDialect("मले तुले कायले सांग"))
        assertEquals(MaharashtraSpeechProfile.Dialect.MALVANI, MaharashtraSpeechProfile.detectDialect("माका तुका किदें सांग"))
        assertEquals(MaharashtraSpeechProfile.Dialect.AHIRANI_KHANDESHI, MaharashtraSpeechProfile.detectDialect("माले कायले नाय"))
        assertEquals(MaharashtraSpeechProfile.Dialect.STANDARD_MARATHI, MaharashtraSpeechProfile.detectDialect("मला मदत करा, हे चांगले आहे"))
        assertEquals(MaharashtraSpeechProfile.Dialect.HINDI, MaharashtraSpeechProfile.detectDialect("मुझे मदद करो"))
        assertEquals(MaharashtraSpeechProfile.Dialect.HINGLISH, MaharashtraSpeechProfile.detectDialect("mala WhatsApp kholo"))
    }

    @Test fun selectsMarathiVoiceForRegionalDialectsAndHindiForHinglish() {
        assertEquals("mr", SpeechLanguage.languageForText("मले तुले कायले सांग"))
        assertEquals("mr", SpeechLanguage.languageForText("माका तुका किदें सांग"))
        assertEquals("mr", SpeechLanguage.languageForText("माले कायले नाय"))
        assertEquals("hi", SpeechLanguage.languageForText("mala WhatsApp kholo"))
    }
    @Test fun classifiesRomanHinglishMessageUtteranceAsHindiRecognitionLanguage() {
        assertEquals("hi", SpeechLanguage.languageForText("WhatsApp per Rahul ko message bhejo ki hello"))
    }
    @Test fun HindiIsAlwaysPrimaryWithEnglishAndMarathiAlternatives() {
        assertEquals(listOf("hi-IN", "en-IN", "mr-IN"), SpeechLanguage.recognitionLocales)
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("en"))
        assertEquals("hi-IN", SpeechLanguage.preferredRecognitionLocale("mr"))
    }
}

