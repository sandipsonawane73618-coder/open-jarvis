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
        listOf("volume up", "Aawaz vadhav", "Aawaz thodi badha do", "आवाज बढ़ाओ", "आवाज वाढवा").forEach {
            assertEquals("up for $it", "up", SkillRegistry.parse(it)?.args?.get("direction"))
        }
        listOf("volume down", "Aawaz kami kara", "Aawaz thoda kam karo", "आवाज कम करो").forEach {
            assertEquals("down for $it", "down", SkillRegistry.parse(it)?.args?.get("direction"))
        }
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
            "Time kya hua",
            "Abhi kitne baje hain",
            "Aaj ka samay kya hai",
            "Aaj ka samay kaha hai",
            "What time is it",
            "समय क्या हुआ है",
            "time now",
            "abhi time kya hai",
            "वेळ सांगा",
            "अभी समय बताओ",
            "आत्ता किती वाजले"
        ).forEach { phrase ->
            assertEquals("TIME for: $phrase", SkillRegistry.Kind.TIME, SkillRegistry.parse(phrase)?.kind)
        }
        assertNull(SkillRegistry.parse("time machine kaise kaam karti hai"))
    }
    @Test fun requestedDayAndDatePhrasesRouteOnlyToDate() {
        listOf(
            "aaj kaun sa din hai", "aaj kya din hai", "din kya hai", "aaj kaunsa din",
            "aaj tarikh", "tarikh kya hai", "आज कौन सा दिन है", "आज की तारीख",
            "आजची तारीख", "आज कोणता दिवस", "what day is it", "today's date"
        ).forEach { phrase ->
            assertEquals("DATE for: $phrase", SkillRegistry.Kind.DATE, SkillRegistry.parse(phrase)?.kind)
        }
        listOf("open date settings", "date format kaise badle", "din bhar kaam karna hai",
            "today's date format in excel").forEach { phrase ->
            assertNotEquals("Must not classify as date: $phrase", SkillRegistry.Kind.DATE, SkillRegistry.parse(phrase)?.kind)
        }
    }

    @Test fun dateReplyUsesInjectedFixedClockAndQuestionLanguage() {
        val clock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-11-11T12:00:00Z"),
            java.time.ZoneId.of("Asia/Kolkata")
        )
        assertEquals("Today is Wednesday, 11 November 2026.", SkillRegistry.dateReply("today's date", clock))
        val hindi = SkillRegistry.dateReply("आज की तारीख", clock)
        assertTrue(hindi.startsWith("आज "))
        assertTrue(hindi.contains("बुधवार") && hindi.contains("नवंबर") && hindi.contains("2026"))
        assertTrue(hindi.endsWith("है।"))
        val marathi = SkillRegistry.dateReply("आजची तारीख", clock)
        assertTrue(marathi.startsWith("आज "))
        assertTrue(marathi.contains("बुधवार") && marathi.contains("नोव्हेंबर") && marathi.contains("2026"))
        assertTrue(marathi.endsWith("आहे."))
    }

    @Test fun dateQuestionsAreNativeAndUnrelatedQuestionsAreNot() {
        listOf(
            "What is today's date",
            "current date",
            "Aaj ki date kya hai",
            "aaj ki tarikh batao",
            "आज की तारीख क्या है",
            "आज कितनी तारीख है",
            "आजची तारीख काय आहे",
            "आज किती तारीख आहे",
            "aajchi tarikh sanga"
        ).forEach { phrase ->
            assertEquals("DATE for: $phrase", SkillRegistry.Kind.DATE, SkillRegistry.parse(phrase)?.kind)
        }
        assertNull(SkillRegistry.parse("date format kaise badle"))
    }

    @Test fun unknownIsNegative() { assertNull(SkillRegistry.parse("hello jarvis")) }
    @Test fun appLaunchParserRemovesTrailingFillerAndRejectsCompoundSentence() {
        assertEquals("instagram", SkillRegistry.parse("open instagram again")?.args?.get("app"))
        assertNull(SkillRegistry.parse("open insta and tell me what to do dear"))
    }

    @Test fun exactHinglishVolumeCommandsNeverBecomeTorchCommands() {
        assertEquals(SkillRegistry.Kind.VOLUME, SkillRegistry.parse("awaaz badhao")?.kind)
        assertEquals("up", SkillRegistry.parse("awaaz badhao")?.args?.get("direction"))
        assertEquals(SkillRegistry.Kind.VOLUME, SkillRegistry.parse("awaaz kam karo")?.kind)
        assertEquals("down", SkillRegistry.parse("awaaz kam karo")?.args?.get("direction"))
        assertEquals(SkillRegistry.Kind.TORCH, SkillRegistry.parse("torch on")?.kind)
    }

    @Test fun reportedVolumeAndTorchPhrasesRouteThroughNativeSkillRegistry() {
        listOf(
            "Awaaz badhao" to "up",
            "awaaz kam karo" to "down",
            "avaz vadhva" to "up",
            "आवाज बढ़ाओ" to "up",
            "volume up" to "up"
        ).forEach { (phrase, direction) ->
            val request = SkillRegistry.parse(phrase)
            assertEquals("VOLUME route for: $phrase", SkillRegistry.Kind.VOLUME, request?.kind)
            assertEquals("direction for: $phrase", direction, request?.args?.get("direction"))
        }
        listOf(
            "torch on" to "true",
            "flashlight off" to "false"
        ).forEach { (phrase, enabled) ->
            val request = SkillRegistry.parse(phrase)
            assertEquals("TORCH route for: $phrase", SkillRegistry.Kind.TORCH, request?.kind)
            assertEquals("enabled for: $phrase", enabled, request?.args?.get("enabled"))
        }
    }

}
