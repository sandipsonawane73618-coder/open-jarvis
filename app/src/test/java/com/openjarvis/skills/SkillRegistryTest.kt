package com.openjarvis.skills

import org.junit.Assert.*
import org.junit.Test

class SkillRegistryTest {
    @Test fun alarmMorningAndEvening() {
        assertEquals(mapOf("hour" to "6", "minute" to "0"), SkillRegistry.parse("subah 6 baje ka alarm lagao")?.args)
        assertEquals(mapOf("hour" to "17", "minute" to "30"), SkillRegistry.parse("shaam 5:30 baje alarm")?.args)
        assertEquals(mapOf("hour" to "22", "minute" to "0"), SkillRegistry.parse("रात 10 बजे अलार्म लगाओ")?.args)
        assertEquals(mapOf("hour" to "6", "minute" to "0"), SkillRegistry.parse("सुबह 6 बजे अलार्म लगाओ")?.args)
    }
    @Test fun torchOnOffAndReject() {
        assertEquals("true", SkillRegistry.parse("torch on")?.args?.get("enabled"))
        assertEquals("false", SkillRegistry.parse("torch off")?.args?.get("enabled"))
        assertNull(SkillRegistry.parse("torch maybe"))
    }
    @Test fun settingsPositiveNegative() {
        assertEquals("wifi", SkillRegistry.parse("wifi settings kholo")?.args?.get("screen"))
        assertNull(SkillRegistry.parse("wifi password batao"))
    }
    @Test fun appPositiveNegative() {
        assertEquals("youtube", SkillRegistry.parse("open youtube")?.args?.get("app"))
        assertNull(SkillRegistry.parse("youtube video"))
    }
    @Test fun timerPositiveNegative() {
        assertEquals("120", SkillRegistry.parse("timer for 2 minutes")?.args?.get("seconds"))
        assertNull(SkillRegistry.parse("timer for many minutes"))
    }
    @Test fun volumePositiveNegative() {
        assertEquals("up", SkillRegistry.parse("volume up")?.args?.get("direction"))
        assertNull(SkillRegistry.parse("volume maybe"))
    }
    @Test fun messagingPositiveNegative() {
        assertEquals("hello", SkillRegistry.parse("whatsapp message hello bhejo")?.args?.get("text"))
        assertNull(SkillRegistry.parse("whatsapp kholo"))
    }
    @Test fun mapsPositiveNegative() {
        assertEquals("pune", SkillRegistry.parse("navigate to pune")?.args?.get("destination"))
        assertNull(SkillRegistry.parse("open maps"))
    }
    @Test fun currentTimeQuestionsAreNativeAndUnrelatedQuestionsAreNot() {
        listOf(
            "Time kya hua hai",
            "Abhi kitne baje hain",
            "Aaj ka samay kya hai",
            "Aaj ka samay kaha hai",
            "What time is it",
            "समय क्या हुआ है",
            "आत्ता किती वाजले"
        ).forEach { phrase ->
            assertEquals("TIME for: $phrase", SkillRegistry.Kind.TIME, SkillRegistry.parse(phrase)?.kind)
        }
        assertNull(SkillRegistry.parse("time machine kaise kaam karti hai"))
    }
    @Test fun unknownIsNegative() { assertNull(SkillRegistry.parse("hello jarvis")) }
    @Test fun appLaunchParserRemovesTrailingFillerAndRejectsCompoundSentence() {
        assertEquals("instagram", SkillRegistry.parse("open instagram again")?.args?.get("app"))
        assertNull(SkillRegistry.parse("open insta and tell me what to do dear"))
    }

}
