package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSearchFallbackTest {
    @Test fun unresolvedYouTubeIntentFallsBackToBrowser() {
        assertTrue(shouldFallbackToYouTubeBrowser(youtubeResolved = false, youtubeLaunchSucceeded = true))
    }

    @Test fun missingYouTubeHandlerFallsBackAfterActivityNotFound() {
        assertTrue(shouldFallbackToYouTubeBrowser(youtubeResolved = true, youtubeLaunchSucceeded = false))
    }

    @Test fun resolvedAndSuccessfullyLaunchedYouTubeDoesNotFallBack() {
        assertFalse(shouldFallbackToYouTubeBrowser(youtubeResolved = true, youtubeLaunchSucceeded = true))
    }
}
