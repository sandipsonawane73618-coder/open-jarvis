package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuickCommandRouterTest {
    @Test fun simpleEnglishAppOpenUsesFastPath() {
        val action = QuickCommandRouter.parse("open WhatsApp")
        assertNotNull(action)
        assertEquals(Action.OPEN_APP, action!!.action)
        assertEquals("WhatsApp", action.label)
    }

    @Test fun launchAndStartAliasesUseFastPath() {
        assertEquals("Chrome", QuickCommandRouter.parse("launch Chrome")?.label)
        assertEquals("YouTube", QuickCommandRouter.parse("start YouTube")?.label)
    }

    @Test fun compoundOrNonOpenCommandsStayOnFullReasoningPath() {
        assertNull(QuickCommandRouter.parse("open WhatsApp and send a message"))
        assertNull(QuickCommandRouter.parse("what is WhatsApp"))
        assertNull(QuickCommandRouter.parse("open"))
    }

    @Test fun appNamesWithSpacesArePreserved() {
        assertEquals("Google Maps", QuickCommandRouter.parse("open Google Maps")?.label)
    }
}
