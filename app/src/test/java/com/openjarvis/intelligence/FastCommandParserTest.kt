package com.openjarvis.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastCommandParserTest {
    @Test fun recognizesEnglishOpenAppCommand() {
        assertEquals("YouTube", FastCommandParser.openAppTarget("open YouTube"))
    }

    @Test fun recognizesHindiHinglishOpenAppCommand() {
        assertEquals("YouTube", FastCommandParser.openAppTarget("  YouTube   kholo  "))
        assertEquals("Settings", FastCommandParser.openAppTarget("Settings khol do"))
    }

    @Test fun ignoresCommandsThatAreNotSimpleAppLaunches() {
        assertNull(FastCommandParser.openAppTarget("play music"))
        assertNull(FastCommandParser.openAppTarget(" "))
    }
}
