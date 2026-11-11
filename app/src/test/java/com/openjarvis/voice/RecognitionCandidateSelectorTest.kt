package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionCandidateSelectorTest {
    @Test fun choosesHighestConfidenceNonBlankCandidate() {
        assertEquals(
            "Jarvis volume badhao",
            RecognitionCandidateSelector.select(
                listOf("Jarvis volume badal", "   ", "Jarvis volume badhao"),
                floatArrayOf(0.42f, 0.99f, 0.91f)
            )
        )
    }

    @Test fun skipsBlankCandidatesAndUsesFirstNonBlankWhenScoresMissing() {
        assertEquals(
            "Time kya hua",
            RecognitionCandidateSelector.select(listOf(" ", "", "Time kya hua", "What time is it"))
        )
    }

    @Test fun keepsRecognizerOrderWhenConfidenceScoresTieOrAreInvalid() {
        assertEquals(
            "पहला विकल्प",
            RecognitionCandidateSelector.select(
                listOf("पहला विकल्प", "दूसरा विकल्प"),
                floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY)
            )
        )
        assertEquals(
            "पहला",
            RecognitionCandidateSelector.select(
                listOf("पहला", "दूसरा"),
                floatArrayOf(0.8f, 0.8f)
            )
        )
    }

    @Test fun rejectsOnlyClearlyPositiveScoresBelowPointFifteen() {
        assertNull(
            RecognitionCandidateSelector.select(
                listOf("low confidence"),
                floatArrayOf(0.10f)
            )
        )
        assertEquals(
            "acceptable confidence",
            RecognitionCandidateSelector.select(
                listOf("acceptable confidence"),
                floatArrayOf(0.20f)
            )
        )
    }

    @Test fun treatsZeroConfidenceAsUnavailable() {
        assertEquals(
            "time kya hua",
            RecognitionCandidateSelector.select(listOf("time kya hua"), floatArrayOf(0.0f))
        )
        assertEquals(
            "first candidate",
            RecognitionCandidateSelector.select(
                listOf("first candidate", "second candidate"),
                floatArrayOf(0.0f, 0.0f)
            )
        )
    }

    @Test fun treatsNullAndEmptyConfidenceArraysAsUnavailable() {
        assertEquals(
            "time kya hua",
            RecognitionCandidateSelector.select(listOf("time kya hua"), null)
        )
        assertEquals(
            "time kya hua",
            RecognitionCandidateSelector.select(listOf("time kya hua"), floatArrayOf())
        )
    }

    @Test fun treatsNegativeOneConfidenceAsUnavailableNotLowConfidence() {
        assertEquals(
            "पहला विकल्प",
            RecognitionCandidateSelector.select(
                listOf("पहला विकल्प", "दूसरा विकल्प"),
                floatArrayOf(-1f, -1f)
            )
        )
    }

    @Test fun mixedUnavailableScoresDoNotRejectNonblankCandidates() {
        assertEquals(
            "first candidate",
            RecognitionCandidateSelector.select(
                listOf("first candidate", "second candidate"),
                floatArrayOf(-1f, 0.0f)
            )
        )
        assertEquals(
            "second candidate",
            RecognitionCandidateSelector.select(
                listOf("first candidate", "second candidate"),
                floatArrayOf(0.0f, 0.20f)
            )
        )
    }

    @Test fun returnsNullForMissingOrEntirelyBlankResults() {
        assertNull(RecognitionCandidateSelector.select(null))
        assertNull(RecognitionCandidateSelector.select(emptyList()))
        assertNull(RecognitionCandidateSelector.select(listOf(" ", "\t", "\n")))
    }

    @Test fun aBlankPartialCandidateCannotBecomeAnExecutableTranscript() {
        val partial = RecognitionCandidateSelector.select(listOf("", "   "))
        val final = RecognitionCandidateSelector.select(emptyList())
        assertNull(partial)
        assertNull(final)
    }
}
