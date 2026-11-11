package com.openjarvis.agent

import com.openjarvis.voice.VoiceControlVocabulary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPlanPolicyPayloadAndVoiceVocabularyTest {
    @Test
    fun recognizesEveryRequestedMutePhrase() {
        listOf(
            "shant raha",
            "shant raho",
            "shant ho jao"
        ).forEach { phrase ->
            assertTrue("Expected mute phrase: $phrase", VoiceControlVocabulary.isMuteCommand(phrase))
        }
    }

    @Test
    fun recognizesEveryRequestedUnmutePhrase() {
        listOf(
            "ab suno",
            "ab sunna shuru karo"
        ).forEach { phrase ->
            assertTrue("Expected unmute phrase: $phrase", VoiceControlVocabulary.isUnmuteCommand(phrase))
        }
    }

    @Test
    fun validatesRequiredPayloadForEverySupportedActionType() {
        val validActions = listOf(
            Action(action = Action.OPEN_APP, label = "Instagram"),
            Action(action = Action.CLICK, text = "Profile"),
            Action(action = Action.TAP, text = "Profile"),
            Action(action = Action.TYPE_TEXT, value = "hello"),
            Action(action = Action.TYPE, value = "hello"),
            Action(action = Action.SCROLL, direction = "down"),
            Action(action = Action.WAIT, text = "1000"),
            Action(action = Action.WAIT_FOR, text = "Profile"),
            Action(action = Action.AI_PROMPT, packageName = "com.example.app", prompt = "Summarize this screen")
        )

        validActions.forEach { action ->
            assertTrue("Expected valid payload for action ${action.action}", AgentPlanPolicy.hasRequiredPayloads(listOf(action)))
        }
    }

    @Test
    fun rejectsIncompletePayloadForEverySupportedActionType() {
        val incompleteActions = listOf(
            Action(action = Action.OPEN_APP),
            Action(action = Action.CLICK),
            Action(action = Action.TAP),
            Action(action = Action.TYPE_TEXT),
            Action(action = Action.TYPE),
            Action(action = Action.SCROLL),
            Action(action = Action.WAIT),
            Action(action = Action.WAIT_FOR),
            Action(action = Action.AI_PROMPT, packageName = "com.example.app")
        )

        incompleteActions.forEach { action ->
            assertFalse("Expected incomplete payload rejection for action ${action.action}", AgentPlanPolicy.hasRequiredPayloads(listOf(action)))
        }
    }
}
