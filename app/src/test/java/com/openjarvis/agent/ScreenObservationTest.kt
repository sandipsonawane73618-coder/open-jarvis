package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class ScreenObservationTest {
    @Test fun identicalSnapshotsHaveIdenticalFingerprints() {
        val a = ScreenObservation("com.youtube", "Home Search", "Search clickable", 1)
        val b = ScreenObservation("com.youtube", "Home Search", "Search clickable", 2)
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test fun textElementsAndPackageContributeToFingerprint() {
        val base = ScreenObservation("com.youtube", "Home", "Search clickable", 1)
        assertNotEquals(base.fingerprint, ScreenObservation("com.instagram", "Home", "Search clickable", 1).fingerprint)
        assertNotEquals(base.fingerprint, ScreenObservation("com.youtube", "Video", "Search clickable", 1).fingerprint)
        assertNotEquals(base.fingerprint, ScreenObservation("com.youtube", "Home", "Profile clickable", 1).fingerprint)
    }
}
