package com.openjarvis.agent

import com.openjarvis.llm.LLMProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandBrainTest {
    private class FakeProvider(
        private val responseFor: (String) -> String
    ) : LLMProvider {
        override val name: String = "fake-command-brain"
        override val baseUrl: String = "https://fake.invalid"
        val calls = mutableListOf<String>()

        override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> {
            calls += userMessage
            return Result.success(responseFor(userMessage))
        }

        override suspend fun testConnection(): Result<Long> = Result.success(1L)
    }

    private fun brain(provider: FakeProvider) = CommandBrain { system, user, timeout ->
        assertTrue(system.contains("NOISY speech-to-text"))
        assertTrue(timeout > 0L)
        provider.completeJson(system, user)
    }

    @Test fun exactHindiHinglishUtterancesProduceExpectedPlans() = runBlocking {
        val responses = mapOf(
            "ओपन यूट्यूब" to """{"steps":[{"tool":"open_app","args":{"label":"YouTube"}}]}""",
            "ओपन यूट्यूब एंड सर्च" to """{"steps":[{"tool":"open_app","args":{"label":"YouTube"}},{"tool":"clarify","args":{"question":"Kya search karun?"}}]}""",
            "एंड प्ले रोमांटिक सोंग" to """{"steps":[{"tool":"play_media","args":{"query":"romantic song","app":null}}]}""",
            "ओपन यूट्यूब एंड प्ले अरीजित सिंह सोंग" to """{"steps":[{"tool":"open_app","args":{"label":"YouTube"}},{"tool":"play_media","args":{"query":"arijit singh song","app":"YouTube"}}]}""",
            "बैक स्क्रीन पर जाओ" to """{"steps":[{"tool":"back","args":{}}]}""",
            "व्हाट्सएप खोलो" to """{"steps":[{"tool":"open_app","args":{"label":"WhatsApp"}}]}""",
            "WhatsApp per Rahul ko message bhejo hello" to """{"steps":[{"tool":"message_draft","args":{"app":"WhatsApp","contact":"Rahul","text":"hello"}}]}""",
            "Awaaz badhao" to """{"steps":[{"tool":"volume","args":{"direction":"up"}}]}""",
            "इंडिया का कैपिटल" to """{"steps":[{"tool":"answer","args":{"text":"भारत की राजधानी नई दिल्ली है।"}}]}""",
            "मद्राचोद" to """{"steps":[{"tool":"none","args":{}}]}"""
        )
        val expected = mapOf(
            "ओपन यूट्यूब" to CommandBrainPlan(listOf(CommandBrainStep.OpenApp("YouTube"))),
            "ओपन यूट्यूब एंड सर्च" to CommandBrainPlan(listOf(
                CommandBrainStep.OpenApp("YouTube"), CommandBrainStep.Clarify("Kya search karun?")
            )),
            "एंड प्ले रोमांटिक सोंग" to CommandBrainPlan(listOf(CommandBrainStep.PlayMedia("romantic song", null))),
            "हे जरवेस ओपन यूट्यूब एंड प्ले अरीजित सिंह सोंग" to CommandBrainPlan(listOf(
                CommandBrainStep.OpenApp("YouTube"), CommandBrainStep.PlayMedia("arijit singh song", "YouTube")
            )),
            "बैक स्क्रीन पर जाओ" to CommandBrainPlan(listOf(CommandBrainStep.Back)),
            "व्हाट्सएप खोलो" to CommandBrainPlan(listOf(CommandBrainStep.OpenApp("WhatsApp"))),
            "WhatsApp per Rahul ko message bhejo hello" to CommandBrainPlan(listOf(
                CommandBrainStep.MessageDraft("WhatsApp", "Rahul", "hello")
            )),
            "Awaaz badhao" to CommandBrainPlan(listOf(CommandBrainStep.Volume("up"))),
            "इंडिया का कैपिटल" to CommandBrainPlan(listOf(CommandBrainStep.Answer("भारत की राजधानी नई दिल्ली है।"))),
            "मद्राचोद" to CommandBrainPlan(listOf(CommandBrainStep.None))
        )
        val provider = FakeProvider { responses.getValue(it) }
        val commandBrain = brain(provider)

        expected.forEach { (utterance, expectedPlan) ->
            assertEquals(utterance, expectedPlan, commandBrain.plan(utterance, 1_500L).getOrThrow())
        }
        assertEquals(10, provider.calls.size)
    }

    @Test fun bareWakePhraseIsAcknowledgedWithoutCallingProvider() = runBlocking {
        val provider = FakeProvider { error("Bare wake phrase must not call the provider") }
        val plan = brain(provider).plan("जरवेस", 1_500L).getOrThrow()
        assertTrue(plan.acknowledge)
        assertTrue(plan.steps.isEmpty())
        assertTrue(provider.calls.isEmpty())
    }

    @Test fun unknownToolAndBadArgumentsAreRejected() {
        assertTrue(CommandBrainPlanParser.parse("""{"steps":[{"tool":"maps","args":{"destination":"Pune"}}]}""").isFailure)
        assertTrue(CommandBrainPlanParser.parse("""{"steps":[{"tool":"open_app","args":{"label":""}}]}""").isFailure)
        assertTrue(CommandBrainPlanParser.parse("""{"steps":[{"tool":"volume","args":{"direction":"sideways"}}]}""").isFailure)
        assertTrue(CommandBrainPlanParser.parse("""{"steps":[{"tool":"timer","args":{"seconds":"30"}}]}""").isFailure)
    }

    @Test fun mapsOnlyProviderCannotCreateNavigationPlanWithoutExplicitIntent() {
        val alwaysMaps = """{"steps":[{"tool":"maps","args":{"destination":"Pune"}}]}"""
        assertTrue(CommandBrainPlanParser.parse(alwaysMaps).isFailure)
        assertEquals(null, LlmSkillRouteSafetyPolicy.acceptedSkill("MAPS", "ओपन यूट्यूब"))
        assertEquals(null, LlmSkillRouteSafetyPolicy.acceptedSkill("MAPS", "जरवेस"))
        assertEquals(null, LlmSkillRouteSafetyPolicy.acceptedSkill("MAPS", "प्ले ए सोंग"))
        assertEquals(null, LlmSkillRouteSafetyPolicy.acceptedSkill("CALL", "WhatsApp per Rahul ko message bhejo hello"))
        assertEquals(null, LlmSkillRouteSafetyPolicy.acceptedSkill("SEARCH", "ओपन यूट्यूब"))
    }

    @Test fun validatedPlansDispatchEveryExactStepInOrder() = runBlocking {
        val responses = mapOf(
            "ओपन यूट्यूब" to """{"steps":[{"tool":"open_app","args":{"label":"YouTube"}}]}""",
            "ओपन यूट्यूब एंड सर्च" to """{"steps":[{"tool":"open_app","args":{"label":"YouTube"}},{"tool":"clarify","args":{"question":"Kya search karun?"}}]}""",
            "एंड प्ले रोमांटिक सोंग" to """{"steps":[{"tool":"play_media","args":{"query":"romantic song","app":null}}]}""",
            "ओपन यूट्यूब एंड प्ले अरीजित सिंह सोंग" to """{"steps":[{"tool":"open_app","args":{"label":"YouTube"}},{"tool":"play_media","args":{"query":"arijit singh song","app":"YouTube"}}]}""",
            "बैक स्क्रीन पर जाओ" to """{"steps":[{"tool":"back","args":{}}]}""",
            "व्हाट्सएप खोलो" to """{"steps":[{"tool":"open_app","args":{"label":"WhatsApp"}}]}""",
            "WhatsApp per Rahul ko message bhejo hello" to """{"steps":[{"tool":"message_draft","args":{"app":"WhatsApp","contact":"Rahul","text":"hello"}}]}""",
            "Awaaz badhao" to """{"steps":[{"tool":"volume","args":{"direction":"up"}}]}""",
            "इंडिया का कैपिटल" to """{"steps":[{"tool":"answer","args":{"text":"भारत की राजधानी नई दिल्ली है।"}}]}""",
            "मद्राचोद" to """{"steps":[{"tool":"none","args":{}}]}"""
        )
        val expected = mapOf(
            "ओपन यूट्यूब" to listOf(CommandBrainStep.OpenApp("YouTube")),
            "ओपन यूट्यूब एंड सर्च" to listOf(
                CommandBrainStep.OpenApp("YouTube"), CommandBrainStep.Clarify("Kya search karun?")
            ),
            "एंड प्ले रोमांटिक सोंग" to listOf(CommandBrainStep.PlayMedia("romantic song", null)),
            "हे जरवेस ओपन यूट्यूब एंड प्ले अरीजित सिंह सोंग" to listOf(
                CommandBrainStep.OpenApp("YouTube"), CommandBrainStep.PlayMedia("arijit singh song", "YouTube")
            ),
            "बैक स्क्रीन पर जाओ" to listOf(CommandBrainStep.Back),
            "व्हाट्सएप खोलो" to listOf(CommandBrainStep.OpenApp("WhatsApp")),
            "WhatsApp per Rahul ko message bhejo hello" to listOf(
                CommandBrainStep.MessageDraft("WhatsApp", "Rahul", "hello")
            ),
            "Awaaz badhao" to listOf(CommandBrainStep.Volume("up")),
            "इंडिया का कैपिटल" to listOf(CommandBrainStep.Answer("भारत की राजधानी नई दिल्ली है।")),
            "मद्राचोद" to listOf(CommandBrainStep.None)
        )
        expected.forEach { (utterance, expectedSteps) ->
            val provider = FakeProvider { input -> responses.getValue(input) }
            val validated = brain(provider).plan(utterance, 1_500L).getOrThrow()
            assertEquals("$utterance validated steps", expectedSteps, validated.steps)

            val executed = mutableListOf<CommandBrainStep>()
            CommandBrainExecution.execute(validated) { step ->
                executed += step
                Result.success("executed")
            }.getOrThrow()
            assertEquals("$utterance executed steps", expectedSteps, executed)
        }
    }

    @Test fun executionPolicyRejectsBankingAndPreservesMessageDraftOnly() {
        val safeDraft = CommandBrainPlan(listOf(
            CommandBrainStep.MessageDraft("WhatsApp", "Rahul", "hello")
        ))
        assertTrue(CommandBrainExecutionPolicy.validate(
            safeDraft, "WhatsApp per Rahul ko message bhejo hello"
        ).isSuccess)
        assertTrue(CommandBrainExecutionPolicy.validate(
            safeDraft, "WhatsApp per Rahul ko message bhejo hello and make payment now"
        ).isFailure)
        assertTrue(CommandBrainPlanParser.parse(
            """{"steps":[{"tool":"maps","args":{"destination":"Pune"}}]}"""
        ).isFailure)
        assertTrue(CommandBrainPlanParser.parse(
            """{"steps":[{"tool":"message_draft","args":{"app":"WhatsApp","contact":"Rahul","text":"hello","send":true}}]}"""
        ).isFailure)
    }
}
