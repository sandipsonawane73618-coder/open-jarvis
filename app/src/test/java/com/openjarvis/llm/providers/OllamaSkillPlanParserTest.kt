package com.openjarvis.llm.providers

import org.junit.Assert.*
import org.junit.Test

class OllamaSkillPlanParserTest {
    @Test fun parsesNativeSkillAndArguments() {
        val plan = OllamaSkillPlanParser.parse("""{"skill":"TORCH","args":{"enabled":true}}""")
        assertEquals("TORCH", plan.skill)
        assertTrue(plan.args.getBoolean("enabled"))
    }

    @Test fun parsesOnlyUiActionEnvelope() {
        val plan = OllamaSkillPlanParser.parse(
            """{"skill":"ANDROID_UI_ACTIONS","args":{"actions":[{"action":"open_app","label":"Chrome"}]}}"""
        )
        assertEquals("ANDROID_UI_ACTIONS", plan.skill)
        assertEquals(1, plan.args.getJSONArray("actions").length())
    }

    @Test fun rejectsLegacyActionArray() {
        assertInvalid("""[{"action":"open_app","label":"Chrome"}]""")
    }

    @Test fun rejectsExtraTopLevelAndArgumentKeys() {
        assertInvalid("""{"skill":"TORCH","args":{"enabled":true},"extra":"no"}""")
        assertInvalid("""{"skill":"TORCH","args":{"enabled":true,"run_shell":"rm"}}""")
    }

    @Test fun rejectsUnknownSkillAndMalformedUiActions() {
        assertInvalid("""{"skill":"UNKNOWN","args":{}}""")
        assertInvalid("""{"skill":"ANDROID_UI_ACTIONS","args":{"actions":["tap"]}}""")
    }

    private fun assertInvalid(raw: String) {
        try {
            OllamaSkillPlanParser.parse(raw)
            fail("Expected invalid skill plan")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
