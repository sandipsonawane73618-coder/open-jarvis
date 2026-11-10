package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNameMatcherTest {
    @Test fun matchesLatinAndDevanagariSpellingsCaseInsensitively() {
        assertTrue(ContactNameMatcher.matches("Rahul", "राहुल"))
        assertTrue(ContactNameMatcher.matches("राहुल", "RAHUL"))
        assertTrue(ContactNameMatcher.matches("RAHUL", "Rahul"))
    }

    @Test fun matchesAFullContactNameButDoesNotMatchAnUnrelatedPrefix() {
        assertTrue(ContactNameMatcher.matches("Rahul", "राहुल शर्मा"))
        assertTrue(ContactNameMatcher.matches("राहुल", "Rahul Patil"))
        assertFalse(ContactNameMatcher.matches("Rahu", "Rahul"))
        assertFalse(ContactNameMatcher.matches("Amit", "Rahul"))
    }

    @Test fun exactNameWinsOverContactsThatShareItsPrefix() {
        val candidates = listOf(
            "Tejas Patil" to "111",
            "Tejas" to "222",
            "Tejas More" to "333"
        )
        assertEquals(listOf("Tejas" to "222"), ContactNameMatcher.bestMatches("Tejas", candidates))
    }

    @Test fun prefixOnlyMatchIsNotAnExactMatch() {
        val candidates = listOf("Tejas Patil" to "111")
        assertTrue(ContactNameMatcher.exactMatches("Tejas", candidates).isEmpty())
        assertEquals(candidates, ContactNameMatcher.bestMatches("Tejas", candidates))
    }

    @Test fun multipleExactNumbersRemainAmbiguous() {
        val candidates = listOf("Tejas" to "111", "Tejas" to "222", "Tejas Patil" to "333")
        assertEquals(listOf("Tejas" to "111", "Tejas" to "222"), ContactNameMatcher.bestMatches("Tejas", candidates))
    }

    @Test fun prefixCandidatesRemainAvailableWhenNoExactNameExists() {
        val candidates = listOf("Tejas Patil" to "111", "Tejas More" to "222")
        assertEquals(candidates, ContactNameMatcher.bestMatches("Tejas", candidates))
    }
}
