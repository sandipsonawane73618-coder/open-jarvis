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

    @Test fun readsOpenAppTargetAsAnAppLabel() {
        val result = LLMResponseValidator.validate("""[{"action":"open_app","target":"WhatsApp"}]""")
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("WhatsApp", result.actions.single().label)
    }

    @Test fun rejectsUnknownActionsAndEmptyPlansClearly() {
        val unknown = LLMResponseValidator.validate("""[{"action":"launch_moon"}]""")
        assertFalse(unknown.isValid)
        assertTrue(unknown.errors.first().contains("Unsupported action type"))
        val empty = LLMResponseValidator.validate("[]")
        assertFalse(empty.isValid)
        assertEquals("Empty action plan", empty.errors.first())
    }

    @Test fun repairsOnlyTheKnownMixedArrayActionShape() {
        val repaired = LLMResponseValidator.validate("""["type_text","value":"hello"]""")
        assertTrue(repaired.errors.toString(), repaired.isValid)
        assertTrue(repaired.wasRepaired)
        assertEquals(Action.TYPE_TEXT, repaired.actions.single().action)
        assertEquals("hello", repaired.actions.single().value)

        val uncertain = LLMResponseValidator.validate("""["unknown_action","value":"hello"]""")
        assertFalse(uncertain.isValid)
    }

    @Test fun stripsThinkBlocksAndNormalizesSingleQuotedJson() {
        val result = LLMResponseValidator.validate("<think>hidden</think> [{'action':'type_text','value':'hello'}]")
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("hello", result.actions.single().value)
    }

    @Test fun preservesTheSpecificNoJsonDiagnostic() {
        val result = LLMResponseValidator.validate("model returned plain prose")
        assertFalse(result.isValid)
        assertEquals("Response is not JSON: no action array/object found", result.errors.first())
    }

    @Test fun rejectsActionsWithMissingRequiredPayloadsBeforeExecution() {
        val missingText = LLMResponseValidator.validate("""[{"action":"type_text"}]""")
        assertFalse(missingText.isValid)
        assertEquals("Action 0 type_text requires non-empty value", missingText.errors.single())
        val missingTarget = LLMResponseValidator.validate("""[{"action":"click"}]""")
        assertFalse(missingTarget.isValid)
        assertEquals("Action 0 click requires non-empty target", missingTarget.errors.single())
        val missingApp = LLMResponseValidator.validate("""[{"action":"open_app"}]""")
        assertFalse(missingApp.isValid)
        assertEquals("Action 0 open_app requires non-empty label or package", missingApp.errors.single())
    }

    @Test fun reportsNonObjectItemsWithoutCrashing() {
        val result = LLMResponseValidator.validate("""["click"]""")
        assertFalse(result.isValid)
        assertEquals("Item 0 is not an object", result.errors.first())
    }

    @Test fun mapsStepsSchemaScrollTargetToDirection() {
        val result = LLMResponseValidator.validate(
            """{"steps":[{"action":"scroll","target":"down","value":""}]}"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("down", result.actions.single().direction)
    }

    @Test fun mapsStepsSchemaErrorValueToUserFacingMessage() {
        val result = LLMResponseValidator.validate(
            """{"steps":[{"action":"error","target":"","value":"Search control was not visible"}]}"""
        )
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals("Search control was not visible", result.actions.single().message)
    }
}
