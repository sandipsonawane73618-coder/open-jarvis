package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownCommandPolicyTest {
    @Test fun rejectsUnclearStatementsBeforeActionPlanner() {
        listOf("", "blah blah", "kuch bhi", "kuch bhi karo", "mujhe samajh nahi", "plan kya hai").filterNot {
            ConversationIntentPolicy.isChatRequest(it)
        }.forEach { assertTrue("$it should not reach action planner", UnknownCommandPolicy.shouldStopBeforePlanner(it)) }
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
}
