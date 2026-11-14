package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCommandPolicyTest {
    @Test fun missingPlayQueryClarifiesWhenYouTubeIsNotForeground() {
        assertTrue(PlayCommandPolicy.shouldAskForQuery("play karo", youtubeForeground = false))
        assertTrue(PlayCommandPolicy.shouldAskForQuery("play", youtubeForeground = false))
    }

    @Test fun foregroundYouTubeKeepsExistingMediaControlSemantics() {
        assertFalse(PlayCommandPolicy.shouldAskForQuery("play karo", youtubeForeground = true))
    }

    @Test fun queryBearingPlayCommandIsNotClarified() {
        assertFalse(PlayCommandPolicy.shouldAskForQuery("play Arijit Singh", youtubeForeground = false))
    }
}