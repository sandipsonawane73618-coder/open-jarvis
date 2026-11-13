package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppSearchPolicyTest {
    @Test fun verifiesInstagramSearchOnlyWhenQueryAndResultEvidenceAreVisible() {
        assertTrue(InAppSearchPolicy.isVerified(
            "com.instagram.android", "Shahrukh Khan",
            "Search Shahrukh Khan Top Accounts Tags Places"
        ))
    }

    @Test fun rejectsQueryTextWithoutResultsOrWrongForegroundPackage() {
        assertFalse(InAppSearchPolicy.isVerified(
            "com.instagram.android", "Shahrukh Khan", "Search Shahrukh Khan"
        ))
        assertFalse(InAppSearchPolicy.isVerified(
            "com.android.chrome", "Shahrukh Khan", "Search Shahrukh Khan Top Accounts"
        ))
    }

    @Test fun rejectsBlankQueryAndUnrelatedResultText() {
        assertFalse(InAppSearchPolicy.isVerified(
            "com.instagram.android", " ", "Top Accounts"
        ))
        assertFalse(InAppSearchPolicy.isVerified(
            "com.instagram.android", "Shahrukh Khan", "Top Accounts Narendra Modi"
        ))
    }
}
