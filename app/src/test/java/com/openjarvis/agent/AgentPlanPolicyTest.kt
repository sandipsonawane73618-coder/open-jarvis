package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPlanPolicyTest {
    private val openInstagram = listOf(Action(action = Action.OPEN_APP, label = "Instagram", packageName = "com.instagram.android"))

    @Test fun conversationalFollowUpWordsDoNotTriggerUnderPlannedGuard() {
        listOf(
            "Instagram kholo aur batao about options",
            "Instagram kholo and tell me about this section",
            "Instagram kholo aur display karo",
            "Instagram kholo and read this"
        ).forEach { command ->
            assertFalse(command, AgentPlanPolicy.isLikelyUnderPlanned(command, openInstagram))
        }
    }

    @Test fun actionableFollowUpWordsTriggerUnderPlannedGuard() {
        listOf(
            "Instagram kholo aur search karo",
            "Instagram kholo and message Rahul",
            "Instagram kholo aur likho hello",
            "Instagram kholo and click profile",
            "Instagram search profile type Rahul"
        ).forEach { command ->
            assertTrue(command, AgentPlanPolicy.isLikelyUnderPlanned(command, openInstagram))
        }
    }

    @Test fun singleOpenAppWithoutActionableFollowUpIsExecutable() {
        assertTrue(AgentPlanPolicy.isExecutablePlan(openInstagram, "Instagram kholo aur batao"))
    }

    @Test fun detectsOnlyALoneErrorAsModelRefusal() {
        assertTrue(AgentPlanPolicy.isSingleModelRefusal(listOf(Action(action = Action.ERROR, message = "not recognized"))))
        assertFalse(AgentPlanPolicy.isSingleModelRefusal(openInstagram))
        assertFalse(AgentPlanPolicy.isSingleModelRefusal(listOf(Action(action = Action.ERROR), Action(action = Action.BACK))))
    }

    @Test fun blocksModelGeneratedSendButtonTap() {
        val sendTap = listOf(Action(action = Action.CLICK, text = "Send"))
        assertTrue(AgentPlanPolicy.hasMessageSendTap(sendTap))
        assertFalse(AgentPlanPolicy.isExecutablePlan(sendTap, "send this message"))
        assertEquals("RULE_MESSAGE_SEND_CONFIRMATION", AgentPlanPolicy.failureRule(sendTap, "send this message"))
        assertTrue(AgentPlanPolicy.userGuidance("RULE_MESSAGE_SEND_CONFIRMATION").contains("manually"))
    }

    @Test fun mixedErrorAndActionNeedsReplan() {
        val mixed = listOf(
            Action(action = Action.OPEN_APP, label = "Instagram", packageName = "com.instagram.android"),
            Action(action = Action.ERROR, message = "uncertain")
        )
        assertTrue(AgentPlanPolicy.needsCompactReplan(mixed))
    }

    @Test fun moreThanTenActionsNeedsReplan() {
        val actions = List(11) { Action(action = Action.BACK) }
        assertTrue(AgentPlanPolicy.needsCompactReplan(actions))
    }
    @Test fun screenlessAccessibilityAllowsOnlyOneResolvedAppLaunch() {
        assertTrue(AgentPlanPolicy.canLaunchResolvedAppWithoutScreen(openInstagram))
        assertFalse(AgentPlanPolicy.canLaunchResolvedAppWithoutScreen(listOf(Action(action = Action.OPEN_APP, label = null))))
        assertFalse(AgentPlanPolicy.canLaunchResolvedAppWithoutScreen(openInstagram + Action(action = Action.CLICK, text = "Search")))
    }

    @Test fun classifiesEachGuardFailureWithNamedRule() {
        assertEquals("RULE_MAX_ACTIONS", AgentPlanPolicy.failureRule(List(11) { Action(action = Action.BACK) }, "do it"))
        assertEquals(
            "RULE_MIXED_ERROR",
            AgentPlanPolicy.failureRule(
                listOf(Action(action = Action.BACK), Action(action = Action.ERROR)),
                "go back"
            )
        )
        assertEquals(
            "RULE_UNRESOLVED_APP",
            AgentPlanPolicy.failureRule(listOf(Action(action = Action.OPEN_APP, label = null)), "open app")
        )
        assertEquals(
            "RULE_UNDER_PLANNED",
            AgentPlanPolicy.failureRule(openInstagram, "Instagram kholo aur search karo")
        )
        assertTrue(AgentPlanPolicy.userGuidance("RULE_UNDER_PLANNED").contains("steps"))
    }
}
