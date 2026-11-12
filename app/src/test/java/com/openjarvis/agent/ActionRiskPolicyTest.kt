package com.openjarvis.agent
import org.junit.Assert.*
import org.junit.Test
class ActionRiskPolicyTest {
    @Test fun sendDeleteAndPayButtonsRequireExplicitConfirmation() {
        listOf("Send", "Delete", "Pay now").forEach { assertTrue(it, ActionRiskPolicy.requiresConfirmation(Action(action = Action.CLICK, text = it))) }
    }
    @Test fun ordinaryControlsDoNotRequireConfirmation() {
        listOf("Search", "Play", "Profile").forEach { assertFalse(ActionRiskPolicy.requiresConfirmation(Action(action = Action.CLICK, text = it))) }
    }
    @Test fun paymentActionRemainsHardBlockedEvenAfterConfirmation() {
        assertTrue(ActionRiskPolicy.isPaymentAction(Action(action = Action.CLICK, text = "Pay now")))
        assertFalse(ActionRiskPolicy.isPaymentAction(Action(action = Action.CLICK, text = "Search")))
    }
    @Test fun approvalAndDeclinePhrasesAreExplicit() {
        assertEquals(true, ActionRiskPolicy.approvalDecision("haan"))
        assertEquals(true, ActionRiskPolicy.approvalDecision("yes confirm"))
        assertEquals(false, ActionRiskPolicy.approvalDecision("no cancel"))
        assertEquals(false, ActionRiskPolicy.approvalDecision("नहीं"))
        assertNull(ActionRiskPolicy.approvalDecision("okay maybe"))
    }
}
