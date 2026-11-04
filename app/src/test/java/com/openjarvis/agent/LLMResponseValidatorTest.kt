package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LLMResponseValidatorTest {
    @Test fun acceptsCanonicalM4Actions() {
        val result = LLMResponseValidator.validate(
            """[{"action":"click","text":"Search"},{"action":"type_text","value":"weather"},{"action":"scroll","direction":"down"},{"action":"back"},{"action":"home"},{"action":"wait","text":"Done"},{"action":"read_screen"}]"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(7, result.actions.size)
    }

    @Test fun acceptsFencedArrayWithIntroText() {
        val result = LLMResponseValidator.validate(
            """Here is the plan:
            ```json
            [{"action":"tap","text":"Search"},{"action":"type","value":"hello"}]
            ```"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(Action.CLICK, result.actions[0].action)
        assertEquals(Action.TYPE_TEXT, result.actions[1].action)
    }

    @Test fun acceptsWrappedActionsObjectAndFieldAliases() {
        val result = LLMResponseValidator.validate(
            """{"actions":[{"type":"click","target":"Settings"},{"command":"wait","text":"Network","timeoutMs":6000}]}"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("Settings", result.actions[0].text)
        assertEquals(6000L, result.actions[1].timeoutMs)
    }

    @Test fun normalizesCommonModelActionSynonymsToExecutorActions() {
        val result = LLMResponseValidator.validate(
            """[{"type":"swipe","direction":"down"},{"action":"tap_on","target":"Search"},{"command":"launch_app","app":"YouTube"},{"action":"input","value":"hello"}]"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(Action.SCROLL, result.actions[0].action)
        assertEquals(Action.CLICK, result.actions[1].action)
        assertEquals(Action.OPEN_APP, result.actions[2].action)
        assertEquals(Action.TYPE_TEXT, result.actions[3].action)
    }

    @Test fun rejectsUnknownActionsAndEmptyPlansClearly() {
        val unknown = LLMResponseValidator.validate("""[{"action":"launch_moon"}]""")
        assertFalse(unknown.isValid)
        assertTrue(unknown.errors.first().contains("Unsupported action type"))
        val empty = LLMResponseValidator.validate("[]")
        assertFalse(empty.isValid)
        assertEquals("Empty action plan", empty.errors.first())
    }

    @Test fun reportsNonObjectItemsWithoutCrashing() {
        val result = LLMResponseValidator.validate("""["click"]""")
        assertFalse(result.isValid)
        assertEquals("Item 0 is not an object", result.errors.first())
    }
    @Test fun repairsQwenSingleActionTupleFormattingWithoutGuessing() {
        val result = LLMResponseValidator.validate("""["type_text","value":"hello"]""")
        assertTrue(result.errors.toString(), result.isValid)
        assertTrue("Repair must be reported", result.wasRepaired)
        assertEquals(1, result.actions.size)
        assertEquals(Action.TYPE_TEXT, result.actions[0].action)
        assertEquals("hello", result.actions[0].value)
    }

    @Test fun doesNotRepairAmbiguousOrIncompleteTupleOutput() {
        val missingValue = LLMResponseValidator.validate("""["type_text","value":]""")
        assertFalse(missingValue.isValid)
        val missingFields = LLMResponseValidator.validate("""["type_text"]""")
        assertFalse(missingFields.isValid)
    }

    @Test fun acceptsStructuredStepsSchemaAndMapsTargetFields() {
        val result = LLMResponseValidator.validate(
            """{"steps":[{"action":"open_app","target":"WhatsApp","value":""},{"action":"click","target":"Search","value":""},{"action":"type_text","target":"","value":"hello"}]}"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(Action.OPEN_APP, result.actions[0].action)
        assertEquals("WhatsApp", result.actions[0].label)
        assertEquals("Search", result.actions[1].text)
        assertEquals("hello", result.actions[2].value)
    }

    @Test fun acceptsFencedJsonAndIgnoresTrailingExplanation() {
        val result = LLMResponseValidator.validate(
            """```json
            {"steps":[{"action":"type_text","target":"","value":"hello"}]}
            ```
            Done."""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("hello", result.actions.single().value)
    }

    @Test fun stripsThinkBlockBeforeValidatingJson() {
        val result = LLMResponseValidator.validate(
            """<think>Need type text.</think>{"steps":[{"action":"type_text","target":"","value":"hello"}]}"""
        )
        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test fun rejectsPlainTextAndStringOnlyArrays() {
        val plain = LLMResponseValidator.validate("I will type hello now.")
        assertFalse(plain.isValid)
        val strings = LLMResponseValidator.validate("""["type_text","hello"]""")
        assertFalse(strings.isValid)
    }

}
