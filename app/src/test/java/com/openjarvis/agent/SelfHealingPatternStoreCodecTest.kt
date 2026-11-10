package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHealingPatternStoreCodecTest {
    @Test fun migratesLegacyRowsWithMissingOutcomeFields() {
        val decoded = SelfHealingPatternCodec.decodeJson(
            """[{"area":"ACCESSIBILITY","count":3,"lastSeen":1234}]"""
        )
        assertEquals(1, decoded.size)
        val pattern = decoded.single()
        assertEquals(0, pattern.verifiedRecoveries)
        assertEquals(3, pattern.unresolvedFailures)
        assertEquals(3, pattern.consecutiveFailures)
        assertEquals("UNRESOLVED", pattern.lastOutcome)
    }

    @Test fun malformedJsonFailsClosedWithoutThrowing() {
        assertTrue(SelfHealingPatternCodec.decodeJson("{not-json").isEmpty())
        assertTrue(SelfHealingPatternCodec.decodeJson("").isEmpty())
        assertTrue(SelfHealingPatternCodec.decodeJson(null).isEmpty())
    }

    @Test fun malformedRowsAreSkippedWithoutDroppingValidRows() {
        val decoded = SelfHealingPatternCodec.decodeJson(
            """[null,{"area":"NOT_A_REAL_AREA","count":4,"lastSeen":9},{"area":"OVERLAY","count":2,"lastSeen":20},{"area":"SPEECH","count":0,"lastSeen":30}]"""
        )
        assertEquals(1, decoded.size)
        assertEquals("OVERLAY", decoded.single().area)
    }

    @Test fun duplicateAreasAreDeduplicatedAndHistoryRemainsBounded() {
        val rows = (0..14).joinToString(",") { index ->
            """{"area":"${if (index % 2 == 0) "OVERLAY" else "SPEECH"}","count":1,"lastSeen":${100 + index}}"""
        }
        val decoded = SelfHealingPatternCodec.decodeJson("[$rows]")
        assertTrue(decoded.size <= 2)
        assertEquals(114L, decoded.first().lastSeenMillis)
    }

    @Test fun encodeDecodeRoundTripPreservesOnlyAllowListedCounters() {
        val pattern = SelfHealingPatternCodec.Pattern(
            area = "ACCESSIBILITY",
            count = 4,
            lastSeenMillis = 9876L,
            verifiedRecoveries = 1,
            unresolvedFailures = 3,
            consecutiveFailures = 2,
            lastOutcome = "VERIFIED_READ_ONLY_RECOVERY"
        )
        val encoded = SelfHealingPatternCodec.encodeJson(listOf(pattern))
        val decoded = SelfHealingPatternCodec.decodeJson(encoded).single()
        assertEquals(pattern, decoded)
        assertFalse(encoded.contains("command"))
        assertFalse(encoded.contains("message"))
        assertFalse(encoded.contains("screen"))
    }

    @Test fun encoderDoesNotPersistInvalidAreasOrUnknownOutcomeText() {
        val encoded = SelfHealingPatternCodec.encodeJson(
            listOf(
                SelfHealingPatternCodec.Pattern(
                    area = "PRIVATE_USER_COMMAND",
                    count = 1,
                    lastSeenMillis = 1L
                ),
                SelfHealingPatternCodec.Pattern(
                    area = "OVERLAY",
                    count = 1,
                    lastSeenMillis = 2L,
                    lastOutcome = "raw message content"
                )
            )
        )
        assertFalse(encoded.contains("PRIVATE_USER_COMMAND"))
        assertFalse(encoded.contains("raw message content"))
        assertTrue(encoded.contains("UNRESOLVED"))
    }

    @Test fun countersSaturateInsteadOfOverflowing() {
        val atLimit = SelfHealingPatternCodec.Pattern(
            area = "OVERLAY",
            count = Int.MAX_VALUE,
            lastSeenMillis = 100L,
            verifiedRecoveries = Int.MAX_VALUE,
            unresolvedFailures = Int.MAX_VALUE,
            consecutiveFailures = Int.MAX_VALUE
        )
        val recorded = SelfHealingPatternCodec.record(listOf(atLimit), "OVERLAY", 200L).single()
        assertEquals(1_000_000, recorded.count)
        assertEquals(1_000_000, recorded.unresolvedFailures)
        assertEquals(1_000_000, recorded.consecutiveFailures)
        val recovered = SelfHealingPatternCodec.recordOutcome(listOf(atLimit), "OVERLAY", true, 300L).single()
        assertEquals(1_000_000, recovered.verifiedRecoveries)
        assertEquals(0, recovered.consecutiveFailures)
    }

    @Test fun callerCannotExpandPersistedHistoryBeyondTwelveEntries() {
        val many = SelfHealingRecoveryPolicy.FailureArea.values().mapIndexed { index, area ->
            SelfHealingPatternCodec.Pattern(area.name, 1, 100L + index)
        }
        assertTrue(SelfHealingPatternCodec.record(many, "UNKNOWN", 500L, 100).size <= 12)
        assertTrue(SelfHealingPatternCodec.recordOutcome(many, "UNKNOWN", false, 500L, 100).size <= 12)
        assertTrue(SelfHealingPatternCodec.decodeJson(SelfHealingPatternCodec.encodeJson(many)).size <= 12)
    }

    @Test fun countersSaturateAndHistoryCannotExceedTwelveEntries() {
        val saturated = SelfHealingPatternCodec.Pattern(
            area = "OVERLAY",
            count = Int.MAX_VALUE,
            lastSeenMillis = 100L,
            verifiedRecoveries = Int.MAX_VALUE,
            unresolvedFailures = Int.MAX_VALUE,
            consecutiveFailures = Int.MAX_VALUE
        )
        val updated = SelfHealingPatternCodec.record(listOf(saturated), "OVERLAY", 200L).single()
        assertEquals(1_000_000, updated.count)
        assertEquals(1_000_000, updated.unresolvedFailures)
        assertEquals(1_000_000, updated.consecutiveFailures)
        assertEquals(1_000_000, updated.verifiedRecoveries)

        var history = emptyList<SelfHealingPatternCodec.Pattern>()
        SelfHealingRecoveryPolicy.FailureArea.values().forEachIndexed { index, area ->
            history = SelfHealingPatternCodec.record(history, area.name, 1_000L + index)
        }
        assertEquals(SelfHealingRecoveryPolicy.FailureArea.values().size, history.size)
        assertTrue(history.size <= 12)
    }

    @Test fun callerCannotExpandHistoryLimitOrPersistNegativeCounters() {
        val rows = (0..20).map { index ->
            SelfHealingPatternCodec.Pattern(
                area = SelfHealingRecoveryPolicy.FailureArea.values()[index % SelfHealingRecoveryPolicy.FailureArea.values().size].name,
                count = -index,
                lastSeenMillis = (index + 1).toLong(),
                verifiedRecoveries = -1,
                unresolvedFailures = -2,
                consecutiveFailures = -3
            )
        }
        val encoded = SelfHealingPatternCodec.encodeJson(rows)
        assertTrue(encoded.length < 8_000)
        assertTrue(SelfHealingPatternCodec.decodeJson(encoded).size <= 12)
        val decoded = SelfHealingPatternCodec.decodeJson(encoded)
        decoded.forEach {
            assertTrue(it.count >= 0)
            assertTrue(it.verifiedRecoveries >= 0)
            assertTrue(it.unresolvedFailures >= 0)
            assertTrue(it.consecutiveFailures >= 0)
        }
    }


    @Test fun oversizedPersistedJsonFailsClosedBeforeParsing() {
        val oversized = "[" + " ".repeat(9_000) + "]"
        assertTrue(SelfHealingPatternCodec.decodeJson(oversized).isEmpty())
    }

}
