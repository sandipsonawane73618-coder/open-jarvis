package com.openjarvis.agent

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
}
