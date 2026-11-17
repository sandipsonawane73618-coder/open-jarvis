package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedJarvisFoundationTest {
    @Test fun activityHidesUnapprovedRawInputs() {
        val event = ToolActivityFactory.create(
            id = "task-1",
            tool = "phone_task",
            numericInput = mapOf("level" to 75)
        )
        assertEquals("Inputs kept private", event.safeInput)
        assertFalse(event.safeInput.contains("secret"))
    }

    @Test fun activityAllowsOnlyBoundedSafeNumericMetadata() {
        val event = ToolActivityFactory.create(
            id = "task-2",
            tool = "device_control",
            numericInput = mapOf("level" to 75)
        )
        assertEquals("Level: 75%", event.safeInput)
        val invalid = ToolActivityFactory.create(
            id = "task-3",
            tool = "device_control",
            numericInput = mapOf("level" to 101)
        )
        assertEquals("Inputs kept private", invalid.safeInput)
    }

    @Test fun listenerFailureDoesNotEscapeIntoExecution() {
        val engine = ToolActivityEngine(ToolActivityListener { error("UI failure") })
        val initial = ToolActivityFactory.create("task-4", "web_search")
        val updated = engine.progress(initial, 0, ProgressKind.SEARCH, atEpochMs = 10)
        assertEquals(ActivityStatus.RUNNING, updated.status)
        assertEquals(1, updated.progress?.sequence)
    }

    @Test fun finishRecordsElapsedTimeAndUnconfirmedState() {
        val engine = ToolActivityEngine()
        val initial = ToolActivityFactory.create("task-5", "phone_task", atEpochMs = 100)
        val result = engine.finish(initial, ActivityStatus.UNCONFIRMED, 100, 450)
        assertEquals(ActivityStatus.UNCONFIRMED, result.status)
        assertEquals(350L, result.elapsedMs)
    }

    @Test fun specialistMentionSupportsHandleAndSpokenForms() {
        val handle = AgentProfiles.parseMention("@research compare these sources")
        assertEquals(SpecialistAgent.RESEARCH, handle.first)
        assertEquals("compare these sources", handle.second)

        val spoken = AgentProfiles.parseMention("tell the scheduler agent to remind me")
        assertEquals(SpecialistAgent.SCHEDULER, spoken.first)
        assertEquals("remind me", spoken.second)
    }

    @Test fun everyVisibleSpecialistHandleRoutesToItsOwnProfile() {
        val cases = listOf(
            "@phone open settings" to SpecialistAgent.PHONE,
            "@research compare these options" to SpecialistAgent.RESEARCH,
            "@files list my documents" to SpecialistAgent.FILES,
            "@scheduler remind me tomorrow" to SpecialistAgent.SCHEDULER,
            "@content draft a message" to SpecialistAgent.CONTENT
        )
        cases.forEach { (command, expectedAgent) ->
            val (actualAgent, routedCommand) = AgentProfiles.parseMention(command)
            assertEquals(expectedAgent, actualAgent)
            assertTrue(routedCommand.isNotBlank())
            assertFalse(routedCommand.startsWith("@"))
        }
    }

    @Test fun unknownAgentMentionRemainsOrdinaryText() {
        val parsed = AgentProfiles.parseMention("@unknown do something")
        assertNull(parsed.first)
        assertEquals("@unknown do something", parsed.second)
    }

    @Test fun specialistProfilesSelectOnlyTheirIntendedExecutionRoutes() {
        assertTrue(AgentProfiles.usesConversationRoute(SpecialistAgent.RESEARCH))
        assertTrue(AgentProfiles.usesConversationRoute(SpecialistAgent.FILES))
        assertTrue(AgentProfiles.usesConversationRoute(SpecialistAgent.CONTENT))
        assertFalse(AgentProfiles.usesConversationRoute(SpecialistAgent.PHONE))
        assertFalse(AgentProfiles.usesConversationRoute(SpecialistAgent.SCHEDULER))
        assertFalse(AgentProfiles.usesConversationRoute(SpecialistAgent.AUTO))
    }

    @Test fun specialistProfilesDoNotClaimUnavailableResearchOrFileAccess() {
        assertTrue(AgentProfiles.byId(SpecialistAgent.RESEARCH).guidance.contains("never claim"))
        assertTrue(AgentProfiles.byId(SpecialistAgent.FILES).guidance.contains("Never invent"))
        assertTrue(AgentProfiles.byId(SpecialistAgent.SCHEDULER).guidance.contains("never claim"))
    }

    @Test fun profileGuidanceKeepsSensitiveActionsApprovalGated() {
        assertTrue(AgentProfiles.byId(SpecialistAgent.PHONE).guidance.contains("explicit approval"))
        assertTrue(AgentProfiles.byId(SpecialistAgent.CONTENT).guidance.contains("explicit user approval"))
    }
}
