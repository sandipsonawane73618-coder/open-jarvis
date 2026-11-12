package com.openjarvis.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRegistryNavigationTest {
    @Test fun mapsRequiresExplicitNavigationIntent() {
        assertFalse(SkillRegistry.hasExplicitNavigationIntent("Instagram kholo"))
        assertFalse(SkillRegistry.hasExplicitNavigationIntent("WhatsApp kholo"))
        assertFalse(SkillRegistry.hasExplicitNavigationIntent("YouTube kholo aur song play karo"))
        assertTrue(SkillRegistry.hasExplicitNavigationIntent("Pune ka raasta batao"))
        assertTrue(SkillRegistry.hasExplicitNavigationIntent("navigate to Pune station"))
        assertTrue(SkillRegistry.hasExplicitNavigationIntent("show directions to airport"))
        assertTrue(SkillRegistry.hasExplicitNavigationIntent("Google Maps: navigate to Pune"))
        assertFalse(SkillRegistry.hasExplicitNavigationIntent("Instagram kholo, Shah Rukh Khan search karo"))
    }

    @Test fun instagramLaunchNeverClassifiesAsMaps() {
        assertEquals(SkillRegistry.Kind.OPEN_APP, SkillRegistry.parse("open Instagram")?.kind)
        assertEquals(SkillRegistry.Kind.OPEN_APP, SkillRegistry.parse("open WhatsApp")?.kind)
        assertEquals(null, SkillRegistry.parse("Instagram kholo")?.takeIf { it.kind == SkillRegistry.Kind.MAPS })
    }
}
