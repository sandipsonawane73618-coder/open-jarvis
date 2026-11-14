package com.openjarvis.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisThemeTest {
    @Test fun darkSchemeKeepsExistingBackground() {
        assertEquals(VoidColor.Void950, jarvisColorScheme(true).background)
    }

    @Test fun lightSchemeUsesLightBackgroundAndDarkText() {
        val scheme = jarvisColorScheme(false)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF7F7FB), scheme.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF171521), scheme.onBackground)
    }
}
