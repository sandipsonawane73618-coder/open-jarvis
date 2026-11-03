package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LLMResponseValidatorNullSafetyTest {

    @Test
    fun minimalActionWithoutOptionalFieldsDoesNotCrash() {
        val result = LLMResponseValidator.validate("""[{"action":"press_back"}]""")

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(1, result.actions.size)
        assertEquals(Action.PRESS_BACK, result.actions.single().action)
        assertNull(result.actions.single().packageName)
        assertNull(result.actions.single().text)
    }

    @Test
    fun explicitNullAndBlankOptionalFieldsAreTreatedAsAbsent() {
        val result = LLMResponseValidator.validate(
            """[{"action":"tap","package":null,"label":"","text":null,"value":"  ","message":null}]"""
        )

        assertTrue(result.errors.toString(), result.isValid)
        val action = result.actions.single()
        assertNull(action.packageName)
        assertNull(action.label)
        assertNull(action.text)
        assertNull(action.value)
        assertNull(action.message)
    }

    @Test
    fun nonStringOptionalFieldsAreIgnoredWithoutCrashing() {
        val result = LLMResponseValidator.validate(
            """[{"action":"open_app","package":{"unexpected":"object"},"label":["unexpected","array"]}]"""
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertNull(result.actions.single().packageName)
        assertNull(result.actions.single().label)
    }

    @Test
    fun populatedOptionalFieldsArePreserved() {
        val result = LLMResponseValidator.validate(
            """[{"action":"open_app","package":"com.example.app","label":"Example"}]"""
        )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("com.example.app", result.actions.single().packageName)
        assertEquals("Example", result.actions.single().label)
    }

    @Test
    fun legacyActionJsonParserUsesSameNullSafeFieldReader() {
        val parsed = ActionJsonParser.parse("""[{"action":"press_back","text":null}]""")

        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
        assertNull(parsed.single().text)
    }
}
