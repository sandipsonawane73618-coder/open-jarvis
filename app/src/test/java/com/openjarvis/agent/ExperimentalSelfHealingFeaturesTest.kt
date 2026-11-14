package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalSelfHealingFeaturesTest {
    @Test fun featureFlagDefaultsOffAndRequiresExplicitTrue() {
        assertFalse(ExperimentalSelfHealingSettings.isEnabled(null))
        assertFalse(ExperimentalSelfHealingSettings.isEnabled(false))
        assertTrue(ExperimentalSelfHealingSettings.isEnabled(true))
    }

    @Test fun commandDictionaryNormalizesEnglishHindiAndMarathiAliases() {
        assertEquals("press back", CommandDictionary.normalize("Go back"))
        assertEquals("press back", CommandDictionary.normalize("वापस जाओ"))
        assertEquals("press back", CommandDictionary.normalize("मागे जा"))
        assertEquals("open whatsapp", CommandDictionary.normalize("व्हाट्सएप खोलो"))
    }

    @Test fun commandDictionaryPreservesUnknownAndSensitiveCommands() {
        val command = "Please fill the service report for Rahul"
        assertEquals(command, CommandDictionary.normalize(command))
    }

    @Test fun diagnosisRetriesTimeoutButNeverPermissionFailure() {
        val timeout = ErrorDiagnosis.fromThrowable(java.net.SocketTimeoutException("timeout"))
        assertTrue(timeout.retryable)
        assertEquals("JARVIS-NET-001", timeout.code)
        val denied = ErrorDiagnosis.fromThrowable(SecurityException("permission denied"))
        assertFalse(denied.retryable)
        assertEquals("JARVIS-PERM-001", denied.code)
    }

    @Test fun repairMemoryHashesOriginalAndRejectsUnverifiedOrSensitiveCorrections() {
        val key = RepairMemoryPolicy.commandKey("send message to Rahul")
        assertNotEquals("send message to Rahul", key)
        assertEquals(key, RepairMemoryPolicy.commandKey(" SEND   MESSAGE TO RAHUL "))
        assertFalse(RepairMemoryPolicy.canRemember("back", "press back", verified = false))
        assertFalse(RepairMemoryPolicy.canRemember("send", "send message to 9876543210", verified = true))
        assertFalse(RepairMemoryPolicy.canRemember("open", "open https://example.com", verified = true))
        assertTrue(RepairMemoryPolicy.canRemember("go back", "press back", verified = true))
    }

    @Test fun selfHealingRetryRequiresRetryableFailureBeforeAnyActionAndCooldown() {
        assertTrue(SelfHealingRetryPolicy.canClaim(true, false, false, 600_000L))
        assertFalse(SelfHealingRetryPolicy.canClaim(false, false, false, 600_000L))
        assertFalse(SelfHealingRetryPolicy.canClaim(true, true, false, 600_000L))
        assertFalse(SelfHealingRetryPolicy.canClaim(true, false, true, 600_000L))
        assertFalse(SelfHealingRetryPolicy.canClaim(true, false, false, 599_999L))
    }
}
