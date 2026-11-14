package com.openjarvis.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

/** Contact lookup must never silently select the first of several plausible people. */
class ContactLookupPolicyTest {
    private fun select(
        requested: String,
        candidates: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        val type = Class.forName("com.openjarvis.intelligence.ContactLookupPolicy")
        val instance = type.getField("INSTANCE").get(null)
        @Suppress("UNCHECKED_CAST")
        return type.getMethod("select", String::class.java, List::class.java)
            .invoke(instance, requested, candidates) as List<Pair<String, String>>
    }

    @Test fun exactNameWinsOverPrefixCandidates() {
        val candidates = listOf(
            "Amit Sharma" to "111",
            "Amit" to "222",
            "Amit Patil" to "333"
        )
        assertEquals(listOf("Amit" to "222"), select("Amit", candidates))
    }

    @Test fun duplicateExactNamesRemainAmbiguousInsteadOfChoosingFirst() {
        val candidates = listOf(
            "Amit" to "111",
            "Amit" to "222",
            "Amit Sharma" to "333"
        )
        assertEquals(listOf("Amit" to "111", "Amit" to "222"), select("Amit", candidates))
    }

    @Test fun prefixOnlyMatchesAreReturnedForDisambiguation() {
        val candidates = listOf("Amit Sharma" to "111", "Amit Patil" to "222")
        assertEquals(candidates, select("Amit", candidates))
    }

    @Test fun unrelatedNamesAndBlankQueriesDoNotMatch() {
        assertEquals(emptyList<Pair<String, String>>(), select("Amit", listOf("Rahul" to "111")))
        assertEquals(emptyList<Pair<String, String>>(), select(" ", listOf("Amit" to "111")))
    }
}
