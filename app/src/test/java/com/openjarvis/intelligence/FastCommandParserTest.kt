package com.openjarvis.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastCommandParserTest {
    @Test fun recognizesEnglishAndHinglishOpenAppCommands() {
        assertEquals("YouTube", FastCommandParser.openAppTarget("open YouTube"))
        assertEquals("YouTube", FastCommandParser.openAppTarget("  YouTube   kholo  "))
        assertEquals("Settings", FastCommandParser.openAppTarget("Settings khol do"))
        assertEquals(FastCommandParser.Command.OpenApp("YouTube"), FastCommandParser.parse("launch YouTube"))
    }

    @Test fun recognizesHindiAndMarathiOpenAndNavigationPhrases() {
        assertEquals(FastCommandParser.Command.OpenApp("YouTube"), FastCommandParser.parse("यूट्यूब खोलो"))
        assertEquals(FastCommandParser.Command.OpenApp("ChatGPT"), FastCommandParser.parse("ChatGPT खोल दो"))
        assertTrue(FastCommandParser.parse("वापस जाओ") is FastCommandParser.Command.Back)
        assertTrue(FastCommandParser.parse("घर जाओ") is FastCommandParser.Command.Home)
    }

    @Test fun recognizesNaturalHindiMarathiAppLaunchPhrases() {
        assertEquals(FastCommandParser.Command.OpenApp("YouTube"), FastCommandParser.parse("YouTube chalu karo"))
        assertEquals(FastCommandParser.Command.OpenApp("YouTube"), FastCommandParser.parse("यूट्यूब चालू करो"))
    }

    @Test fun keepsCompoundAppTasksInTheAiActionPlanner() {
        assertEquals(FastCommandParser.Command.OpenApp("WhatsApp"), FastCommandParser.parse("open WhatsApp"))
        assertNull(FastCommandParser.parse("open WhatsApp and send a message to Rahul"))
    }

    @Test fun recognizesBackHomeAndClose() {
        assertTrue(FastCommandParser.parse("go back") is FastCommandParser.Command.Back)
        assertTrue(FastCommandParser.parse("wapas") is FastCommandParser.Command.Back)
        assertTrue(FastCommandParser.parse("home") is FastCommandParser.Command.Home)
        assertTrue(FastCommandParser.parse("close app") is FastCommandParser.Command.CloseCurrentApp)
    }

    @Test fun recognizesScrollDirections() {
        assertEquals(FastCommandParser.Command.Scroll("up"), FastCommandParser.parse("scroll up"))
        assertEquals(FastCommandParser.Command.Scroll("down"), FastCommandParser.parse("scroll neeche"))
    }

    @Test fun recognizesSettingsScreens() {
        assertEquals(FastCommandParser.Command.OpenSettings("general"), FastCommandParser.parse("open settings"))
        assertEquals(FastCommandParser.Command.OpenSettings("wifi"), FastCommandParser.parse("wifi settings kholo"))
        assertEquals(FastCommandParser.Command.OpenSettings("bluetooth"), FastCommandParser.parse("bluetooth settings"))
        assertEquals(FastCommandParser.Command.OpenSettings("notifications"), FastCommandParser.parse("open notification access settings"))
        assertEquals(FastCommandParser.Command.OpenSettings("wifi"), FastCommandParser.parse("वायफाय सेटिंग्ज उघडा"))
        assertEquals(FastCommandParser.Command.OpenSettings("bluetooth"), FastCommandParser.parse("ब्लूटूथ सेटिंग्ज उघडा"))
        assertEquals(FastCommandParser.Command.OpenSettings("general"), FastCommandParser.parse("सेटिंग्ज उघडा"))
    }

    @Test fun recognizesExplicitPersonalMemoryCommands() {
        assertEquals(FastCommandParser.Command.Remember("I prefer Marathi"), FastCommandParser.parse("remember that I prefer Marathi"))
        assertEquals(FastCommandParser.Command.Forget("Marathi"), FastCommandParser.parse("forget Marathi"))
        assertTrue(FastCommandParser.parse("show my memory") is FastCommandParser.Command.ShowMemory)
        assertEquals(FastCommandParser.Command.Remember("मला मराठी आवडते"), FastCommandParser.parse("लक्षात ठेव मला मराठी आवडते"))
    }

    @Test fun recognizesMultilingualMemoryCommands() {
        assertEquals(FastCommandParser.Command.Remember("mera naam Sagar hai"), FastCommandParser.parse("yaad rakho mera naam Sagar hai"))
        assertEquals(FastCommandParser.Command.Remember("मला मराठी आवडते"), FastCommandParser.parse("लक्षात ठेवा मला मराठी आवडते"))
        assertEquals(FastCommandParser.Command.Forget("Marathi"), FastCommandParser.parse("bhool jao Marathi"))
        assertEquals(FastCommandParser.Command.Forget("all"), FastCommandParser.parse("sab bhool jao"))
        assertEquals(FastCommandParser.Command.Forget("all"), FastCommandParser.parse("sab kuch bhool jao"))
        assertEquals(FastCommandParser.Command.Forget("all"), FastCommandParser.parse("सगळं विसर"))
        assertTrue(FastCommandParser.parse("mujhe kya yaad hai list bolo") is FastCommandParser.Command.ShowMemory)
        assertTrue(FastCommandParser.parse("मुझे क्या याद है") is FastCommandParser.Command.ShowMemory)
        assertTrue(FastCommandParser.parse("मला काय लक्षात आहे") is FastCommandParser.Command.ShowMemory)
    }

    @Test fun recognizesYoutubeAndContactFastSkills() {
        assertEquals(FastCommandParser.Command.YoutubeSearch("lofi"), FastCommandParser.parse("YouTube par lofi search karo"))
        assertEquals(FastCommandParser.Command.YoutubeSearch("lofi"), FastCommandParser.parse("YouTube kholo aur lofi search karo"))
        assertEquals(FastCommandParser.Command.DialContact("Rahul"), FastCommandParser.parse("Rahul ko call karo"))
        assertEquals(FastCommandParser.Command.DialContact("राहुल"), FastCommandParser.parse("राहुल को कॉल करो"))
        assertEquals(FastCommandParser.Command.DialContact("राहुल"), FastCommandParser.parse("राहुल ला कॉल करा"))
        assertEquals(FastCommandParser.Command.DialContact("Rahul"), FastCommandParser.parse("call Rahul"))
    }

    @Test fun splitsRecognizedCompoundCommandsAndStopsAtUnknown() {
        assertEquals(listOf(FastCommandParser.Command.OpenApp("Chrome"), FastCommandParser.Command.Back), FastCommandParser.parseSequence("Chrome kholo aur back"))
        assertEquals(
            listOf(FastCommandParser.Command.OpenSettings("general"), FastCommandParser.Command.OpenSettings("wifi")),
            FastCommandParser.parseSequence("Settings kholo aur WiFi settings kholo")
        )
        assertNull(FastCommandParser.parseSequence("Chrome kholo aur unknown action"))
        assertNull(FastCommandParser.parseSequence("back then home then back then home then back then home"))
    }

    @Test fun recognizesEnglishTimerCommandsAndRejectsInvalidDurations() {
        assertEquals(FastCommandParser.Command.SetTimer(120), FastCommandParser.parse("Set a timer for 2 minutes"))
        assertEquals(FastCommandParser.Command.SetTimer(30), FastCommandParser.parse("timer 30 seconds"))
        assertEquals(FastCommandParser.Command.SetTimer(3600), FastCommandParser.parse("1 hour timer"))
        assertNull(FastCommandParser.parse("set a timer for many minutes"))
        assertNull(FastCommandParser.parse("set a timer for 0 seconds"))
        assertNull(FastCommandParser.parse("set a timer for 25 hours"))
    }

    @Test fun recognizesDialNumberWithoutPlacingCall() {
        assertEquals(FastCommandParser.Command.Dial("+919876543210"), FastCommandParser.parse("call +91 98765 43210"))
    }

    @Test fun ignoresCommandsOutsideTheFastPath() {
        assertNull(FastCommandParser.openAppTarget("play music"))
        assertNull(FastCommandParser.parse(" "))
        assertNull(FastCommandParser.parse("search for weather"))
    }
    @Test fun recognizesAllReportedBackScreenPhrasesAsGlobalBack() {
        listOf(
            "back screen per jao",
            "peeche jao",
            "peeche ja",
            "wapas jao",
            "wapas ja",
            "mage ja",
            "mage jaa",
            "go back",
            "piche jao",
            "पीछे जाओ",
            "वापस जाओ",
            "मागे जा"
        ).forEach { phrase ->
            assertEquals("BACK for: $phrase", FastCommandParser.Command.Back, FastCommandParser.parse(phrase))
        }

        // Navigation route words must not steal a deterministic BACK command.
        assertNull(FastCommandParser.openAppTarget("peeche jao"))
        assertNull(FastCommandParser.openAppTarget("maps par jao"))
    }
}
