package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaybackPolicyTest {
    @Test fun playUtteranceRequestsFirstResultAndPlayerVerification() {
        assertTrue(YouTubePlaybackPolicy.shouldPlayFirstResult("YouTube per arijeet Singh play karo"))
        assertFalse(YouTubePlaybackPolicy.shouldPlayFirstResult("YouTube par arijeet Singh search karo"))
    }

    @Test fun selectsFirstScreenGroundedClickableVideoAndRejectsNavigationControls() {
        val observation = ScreenObservation(
            packageName = "com.google.android.youtube",
            visibleText = "Search results",
            interactiveElements = "text=Home clickable=true\ntext=Arijit Singh - Tum Hi Ho clickable=true\ntext=Shorts clickable=true",
            observedAtMs = 1L
        )
        assertEquals("Arijit Singh - Tum Hi Ho", YouTubePlaybackPolicy.firstVideoTarget(observation))
    }

    @Test fun requiresForegroundPlaybackControlsAndVideoContext() {
        assertTrue(YouTubePlaybackPolicy.isPlayerVerified(
            "com.google.android.youtube", "Arijit Singh • Pause • Seek bar", true
        ))
        assertFalse(YouTubePlaybackPolicy.isPlayerVerified(
            "com.google.android.youtube", "Search results", false
        ))
        assertFalse(YouTubePlaybackPolicy.isPlayerVerified(
            "com.instagram.android", "Arijit Singh • Pause • Seek bar", true
        ))
    }
}