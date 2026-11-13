package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownCommandPolicyTest {
    @Test fun stopsOnlyForBlankVeryShortOrPureFiller() {
        listOf("", " ", "a", "ok", "haan", "hmm", "acha").forEach {
            assertTrue("${it.ifBlank { "<blank>" }} should stop", UnknownCommandPolicy.shouldStopBeforePlanner(it))
        }
        listOf(
            "blah blah", "kuch bhi", "kuch bhi karo", "mujhe samajh nahi", "plan kya hai",
            "kal barish hogi?", "mera phone garam ho raha hai", "Instagram par profile dhoondo"
        ).forEach {
            assertFalse("${it} must reach the router", UnknownCommandPolicy.shouldStopBeforePlanner(it))
        }
    }

    @Test fun allowsExplicitActionRequestsToReachExistingPlanner() {
        listOf(
            "upload a reel to Instagram",
            "click the profile button",
            "WhatsApp par message likho",
            "स्क्रीनशॉट लो",
            "उघडा settings",
            "aaj ka screenshot lo",
            "WhatsApp message pathva"
        ).forEach { assertFalse("$it is an explicit action request", UnknownCommandPolicy.shouldStopBeforePlanner(it)) }
    }

    @Test fun vagueInputsAskForClarificationAndNeverReachActionPlanning() {
        listOf("kuch bhi", "blah blah").forEach { command ->
            var clarifyCount = 0
            var actionPlanCount = 0
            UnknownCommandPolicy.dispatch(
                command = command,
                onStop = {},
                onAskClarify = { clarifyCount++ },
                onActionPlan = { actionPlanCount++ }
            )
            assertEquals("$command must ask for clarification", 1, clarifyCount)
            assertEquals("$command must never create an action plan", 0, actionPlanCount)
        }
    }
}
