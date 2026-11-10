package com.openjarvis.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHealingVerificationPolicyTest {
    @Test fun everyFailureAreaHasConcreteVerificationGuidance() {
        SelfHealingRecoveryPolicy.FailureArea.values().forEach { area ->
            val instruction = SelfHealingVerificationPolicy.instruction(area)
            assertTrue("Missing guidance for $area", instruction.isNotBlank())
            assertTrue("Guidance too short for $area", instruction.length >= 30)
        }
    }

    @Test fun sideEffectVerificationRequiresStateInspectionAndUserApproval() {
        val instruction = SelfHealingVerificationPolicy.instruction(
            SelfHealingRecoveryPolicy.FailureArea.ACTION
        )
        assertTrue(instruction.contains("inspect the current screen"))
        assertTrue(instruction.contains("user-approved retry"))
    }

    @Test fun unknownFailureStopsAutomation() {
        val instruction = SelfHealingVerificationPolicy.instruction(
            SelfHealingRecoveryPolicy.FailureArea.UNKNOWN
        )
        assertTrue(instruction.contains("stop automation"))
        assertTrue(instruction.contains("no automatic retry"))
    }

    @Test fun modelFailureNeverSuggestsAutomaticSwitch() {
        val instruction = SelfHealingVerificationPolicy.instruction(
            SelfHealingRecoveryPolicy.FailureArea.MODEL_UNAVAILABLE
        )
        assertTrue(instruction.contains("do not switch"))
    }
}
