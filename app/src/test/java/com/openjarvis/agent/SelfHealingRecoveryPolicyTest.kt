package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class SelfHealingRecoveryPolicyTest {
    @Test fun ollamaFailurePlansLocalVerificationWithoutProviderSwitch() {
        val result = SelfHealingRecoveryPolicy.classify(Action(Action.ERROR), "Ollama connection refused at 127.0.0.1:11434")
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY, result.area)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.VERIFY_LOCAL_SERVICE, result.recovery)
        assertTrue(result.mayRetryAutomatically)
        assertTrue(result.verifyAfterRecovery)
        assertFalse(result.requiresUserConfirmation)
    }

    @Test fun modelChangeIsNeverAutomatic() {
        val result = SelfHealingRecoveryPolicy.classify(Action(Action.ERROR), "qwen3:1.7b model not found")
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.MODEL_UNAVAILABLE, result.area)
        assertFalse(result.mayRetryAutomatically)
        assertTrue(result.requiresUserConfirmation)
    }

    @Test fun sideEffectingActionsAreNeverAutomaticallyReplayed() {
        listOf(Action.TAP, Action.TYPE, Action.CLICK, Action.BACK, Action.HOME).forEach { kind ->
            val result = SelfHealingRecoveryPolicy.classify(Action(kind), "action failed")
            assertEquals("action=$kind", SelfHealingRecoveryPolicy.FailureArea.ACTION, result.area)
            assertFalse("action=$kind", result.mayRetryAutomatically)
            assertTrue(result.requiresUserConfirmation)
        }
        val launch = SelfHealingRecoveryPolicy.classify(Action(Action.OPEN_APP), "action failed")
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.APP_LAUNCH, launch.area)
        assertFalse(launch.mayRetryAutomatically)
        assertTrue(launch.requiresUserConfirmation)
    }

    @Test fun appLaunchFailureTextIsClassifiedEvenWithoutOpenAppAction() {
        val result = SelfHealingRecoveryPolicy.classify(Action(Action.ERROR), "Android could not launch com.example.app")
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.APP_LAUNCH, result.area)
    }

    @Test fun onlyReadOnlyObservationMayBeMarkedForAutomaticRetry() {
        val observation = SelfHealingRecoveryPolicy.classify(
            Action(Action.READ_SCREEN), "visible text not found"
        )
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY, observation.area)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.REFRESH_OBSERVATION, observation.recovery)
        assertTrue(observation.mayRetryAutomatically)
        assertFalse(observation.requiresUserConfirmation)

        val tap = SelfHealingRecoveryPolicy.classify(
            Action(Action.TAP, text = "Continue"), "tap failed"
        )
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.ACTION, tap.area)
        assertFalse(tap.mayRetryAutomatically)
        assertTrue(tap.requiresUserConfirmation)
    }

    @Test fun emptyReadOnlyScreenObservationCanBeRefreshedSafely() {
        val result = SelfHealingRecoveryPolicy.classify(
            Action(Action.READ_SCREEN), "Could not read current screen (no accessible text)"
        )
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY, result.area)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.REFRESH_OBSERVATION, result.recovery)
        assertTrue(result.mayRetryAutomatically)
        assertFalse(result.requiresUserConfirmation)
    }

    @Test fun extractTextRuntimeFailureIsRecognizedAsRefreshableObservation() {
        val result = SelfHealingRecoveryPolicy.classify(
            Action(Action.EXTRACT_TEXT), "Could not extract text from current screen"
        )
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY, result.area)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.REFRESH_OBSERVATION, result.recovery)
        assertTrue(result.mayRetryAutomatically)
        assertFalse(result.requiresUserConfirmation)
    }

    @Test fun accessibilityServiceFailureStillRequiresUserAction() {
        val result = SelfHealingRecoveryPolicy.classify(
            Action(Action.READ_SCREEN), "Android accessibility service is disabled"
        )
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY, result.area)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.USER_ACTION_REQUIRED, result.recovery)
        assertFalse(result.mayRetryAutomatically)
        assertTrue(result.requiresUserConfirmation)
    }

    @Test fun verifiedReadOnlyRecoveryResetsConsecutiveFailureLearning() {
        var patterns = emptyList<SelfHealingPatternCodec.Pattern>()
        repeat(3) { index ->
            patterns = SelfHealingPatternCodec.record(patterns, "ACCESSIBILITY", 100L + index)
        }
        assertTrue(SelfHealingPatternCodec.recurring(patterns.single()))
        patterns = SelfHealingPatternCodec.recordOutcome(patterns, "ACCESSIBILITY", true, 200L)
        val learned = patterns.single()
        assertEquals(3, learned.count)
        assertEquals(1, learned.verifiedRecoveries)
        assertEquals(2, learned.unresolvedFailures)
        assertEquals(0, learned.consecutiveFailures)
        assertEquals("VERIFIED_READ_ONLY_RECOVERY", learned.lastOutcome)
        assertFalse(SelfHealingPatternCodec.recurring(learned))
    }

    @Test fun learnedRecurrenceEscalatesTheRecoveryDecisionAtThreshold() {
        val base = SelfHealingRecoveryPolicy.classify(
            Action(Action.READ_SCREEN), "visible text not found"
        )
        val belowThreshold = SelfHealingRecoveryPolicy.applyLearning(base, 2)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.REFRESH_OBSERVATION, belowThreshold.recovery)
        assertTrue(belowThreshold.mayRetryAutomatically)
        assertFalse(belowThreshold.requiresUserConfirmation)

        val escalated = SelfHealingRecoveryPolicy.applyLearning(base, 3)
        assertEquals(SelfHealingRecoveryPolicy.RecoveryKind.ESCALATE, escalated.recovery)
        assertFalse(escalated.mayRetryAutomatically)
        assertTrue(escalated.requiresUserConfirmation)
        assertTrue(escalated.explanation.contains("3 occurrences"))
    }

    @Test fun learningNeverWeakensAnAlreadyUnsafeRecoveryDecision() {
        val unsafe = SelfHealingRecoveryPolicy.classify(
            Action(Action.TAP, text = "Send"), "tap failed"
        )
        val learned = SelfHealingRecoveryPolicy.applyLearning(unsafe, 0)
        assertEquals(unsafe, learned)
        assertFalse(learned.mayRetryAutomatically)
        assertTrue(learned.requiresUserConfirmation)
    }

    @Test fun failuresAfterVerifiedRecoveryStartANewConsecutiveSequence() {
        var patterns = emptyList<SelfHealingPatternCodec.Pattern>()
        repeat(2) { index ->
            patterns = SelfHealingPatternCodec.record(patterns, "ACCESSIBILITY", 100L + index)
        }
        patterns = SelfHealingPatternCodec.recordOutcome(patterns, "ACCESSIBILITY", true, 200L)
        patterns = SelfHealingPatternCodec.record(patterns, "ACCESSIBILITY", 201L)
        assertEquals(3, patterns.single().count)
        assertEquals(1, patterns.single().consecutiveFailures)
        assertFalse(SelfHealingPatternCodec.recurring(patterns.single()))
    }

    @Test fun unresolvedRecurringFailuresRemainEscalatedUntilVerifiedRecovery() {
        var patterns = emptyList<SelfHealingPatternCodec.Pattern>()
        repeat(4) { index ->
            patterns = SelfHealingPatternCodec.record(patterns, "OVERLAY", 100L + index)
        }
        assertTrue(SelfHealingPatternCodec.recurring(patterns.single()))
        assertEquals(4, patterns.single().unresolvedFailures)
        patterns = SelfHealingPatternCodec.recordOutcome(patterns, "OVERLAY", false, 300L)
        assertTrue(SelfHealingPatternCodec.recurring(patterns.single()))
        assertEquals(4, patterns.single().consecutiveFailures)
    }

    @Test fun uiObservationCannotPretendToVerifyOllamaRecovery() {
        val read = Action(Action.READ_SCREEN)
        assertFalse(SelfHealingVerificationPolicy.confirmsReadOnlyRecovery(
            read, SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY
        ))
        assertTrue(SelfHealingVerificationPolicy.confirmsReadOnlyRecovery(
            read, SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY
        ))
        assertFalse(SelfHealingVerificationPolicy.confirmsReadOnlyRecovery(
            Action(Action.TAP, text = "Continue"), SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY
        ))
        assertFalse(SelfHealingVerificationPolicy.confirmsReadOnlyRecovery(
            Action(Action.READ_SCREEN), SelfHealingRecoveryPolicy.FailureArea.OVERLAY
        ))
    }

    @Test fun firstVerifiedRecoveryIsPersistedEvenWithoutPriorUnresolvedPattern() {
        val recovered = SelfHealingPatternCodec.recordOutcome(
            emptyList(), "ACCESSIBILITY", verified = true, now = 500L
        )
        assertEquals(1, recovered.size)
        assertEquals(1, recovered.single().count)
        assertEquals(1, recovered.single().verifiedRecoveries)
        assertEquals(0, recovered.single().unresolvedFailures)
        assertEquals(0, recovered.single().consecutiveFailures)
        assertEquals("VERIFIED_READ_ONLY_RECOVERY", recovered.single().lastOutcome)
    }

    @Test fun unverifiedOutcomeDoesNotCreateSuccessfulLearningEntry() {
        val unchanged = SelfHealingPatternCodec.recordOutcome(
            emptyList(), "OLLAMA_CONNECTIVITY", verified = false, now = 500L
        )
        assertTrue(unchanged.isEmpty())
    }

    @Test fun recurringPatternsStoreOnlyBoundedCategories() {
        var patterns = emptyList<SelfHealingPatternCodec.Pattern>()
        repeat(3) { index ->
            patterns = SelfHealingPatternCodec.record(patterns, "OLLAMA_CONNECTIVITY", 100L + index)
        }
        assertEquals(3, patterns.single().count)
        assertTrue(SelfHealingPatternCodec.recurring(patterns.single()))
        val unknown = SelfHealingPatternCodec.record(patterns, "raw command text", 200L).first()
        assertEquals("UNKNOWN", unknown.area)
        assertEquals(1, SelfHealingPatternCodec.record(patterns, "OLLAMA_CONNECTIVITY", 201L, 0).size)
    }

    @Test fun ollamaNativeMissingModelMessageIsClassifiedAsModelUnavailable() {
        val result = SelfHealingRecoveryPolicy.classify(
            Action(Action.ERROR),
            "Error: model 'qwen3:1.7b' not found, try pulling it first"
        )
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.MODEL_UNAVAILABLE, result.area)
        assertFalse(result.mayRetryAutomatically)
        assertTrue(result.requiresUserConfirmation)
    }

    @Test fun boundedWaitTimeoutsAreClassifiedAsSafeRefreshableObservations() {
        listOf(Action.WAIT, Action.WAIT_FOR).forEach { kind ->
            val decision = SelfHealingRecoveryPolicy.classify(
                Action(kind), "Timed out waiting for visible text"
            )
            assertEquals(kind.toString(), SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY, decision.area)
            assertEquals(kind.toString(), SelfHealingRecoveryPolicy.RecoveryKind.REFRESH_OBSERVATION, decision.recovery)
            assertTrue(kind.toString(), decision.mayRetryAutomatically)
            assertFalse(kind.toString(), decision.requiresUserConfirmation)
            assertTrue(kind.toString(), SelfHealingSafetyPolicy.shouldRetry(Action(kind), "Timed out waiting for visible text"))
            assertTrue(kind.toString(), SelfHealingSafetyPolicy.shouldRetryAfterLearning(
                Action(kind), "Timed out waiting for visible text", 0
            ))
        }
    }

}
