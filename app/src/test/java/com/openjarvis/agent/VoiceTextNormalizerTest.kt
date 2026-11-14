package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextNormalizerTest {
    private fun normalize(input: String): String {
        val type = Class.forName("com.openjarvis.agent.VoiceTextNormalizer")
        val instance = type.getField("INSTANCE").get(null)
        return type.getMethod("normalize", String::class.java).invoke(instance, input) as String
    }

    private fun strip(input: String): String {
        val type = Class.forName("com.openjarvis.agent.VoiceTextNormalizer")
        val instance = type.getField("INSTANCE").get(null)
        return type.getMethod("stripWakePhrase", String::class.java).invoke(instance, input) as String
    }

    @Test fun normalizesAllReportedDevanagariEnglishAppAndActionWords() {
        val cases = linkedMapOf(
            "ओपन यूट्यूब" to "open youtube",
            "ओपेन व्हाट्सएप" to "open whatsapp",
            "एंड प्ले सॉन्ग" to "and play song",
            "पॉज स्टॉप सर्च बैक स्क्रीन होम" to "pause stop search back screen home",
            "मैप कॉल मैसेज टेक्स्ट टाइमर अलार्म" to "map call message text timer alarm",
            "वॉल्यूम टॉर्च फ्लैशलाइट" to "volume torch flashlight",
            "फॉलोअर्स मीनिंग" to "followers meaning",
            "इंस्टा इंस्टाग्राम टेलीग्राम क्रोम कैमरा" to "insta instagram telegram chrome camera",
            "सेटिंग सेटिंग्स गैलरी स्पॉटिफाई जीमेल मैप्स" to "settings settings gallery spotify gmail maps",
            "हे जरवेस ओपन यूट्यूब" to "hey jarvis open youtube"
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, normalize(input)) }
    }

    @Test fun stripsAllReportedWakeAliasesAtTheBeginningAndMidSentencePrefix() {
        listOf(
            "हे जरवेस ओपन यूट्यूब" to "ओपन यूट्यूब",
            "जरवेस ओपन यूट्यूब" to "ओपन यूट्यूब",
            "जार्विस ओपन यूट्यूब" to "ओपन यूट्यूब",
            "जारविस ओपन यूट्यूब" to "ओपन यूट्यूब",
            "जर्विस ओपन यूट्यूब" to "ओपन यूट्यूब",
            "हे जार्विस ओपन यूट्यूब" to "ओपन यूट्यूब",
            "hey jarvis open youtube" to "open youtube",
            "jarvis open youtube" to "open youtube",
            "एचआरवी ओपन व्हाट्सएप" to "ओपन व्हाट्सएप"
        ).forEach { (input, expected) -> assertEquals(input, expected, strip(input)) }
    }

    @Test fun bareWakeAliasIsAcknowledgementAndNotAnAction() {
        val type = Class.forName("com.openjarvis.agent.VoiceTextNormalizer")
        val instance = type.getField("INSTANCE").get(null)
        val method = type.getMethod("isBareWakePhrase", String::class.java)
        listOf("जरवेस", "jarvis", "जार्विस").forEach {
            assertEquals(true, method.invoke(instance, it))
        }
    }
}
