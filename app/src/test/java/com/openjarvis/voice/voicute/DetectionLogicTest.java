package com.openjarvis.voice.voicute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DetectionLogicTest {
    @Test
    public void highSensitivityAcceptsNormalConfidenceAboveConfiguredThreshold() {
        DetectionLogic detector = new DetectionLogic();

        detector.record(0.30f, "jarvis", 1_000f, 10_000L);
        assertNull(detector.evaluate("jarvis", 0.30f, 1_000f, 0.25f, 2, 2, 10_000L));

        detector.record(0.30f, "jarvis", 1_000f, 10_100L);
        assertEquals("jarvis", detector.evaluate("jarvis", 0.30f, 1_000f, 0.25f, 2, 2, 10_100L));
    }

    @Test
    public void scoreBelowConfiguredThresholdDoesNotTrigger() {
        DetectionLogic detector = new DetectionLogic();

        detector.record(0.20f, "jarvis", 1_000f, 10_000L);
        assertNull(detector.evaluate("jarvis", 0.20f, 1_000f, 0.25f, 2, 2, 10_000L));

        detector.record(0.20f, "jarvis", 1_000f, 10_100L);
        assertNull(detector.evaluate("jarvis", 0.20f, 1_000f, 0.25f, 2, 2, 10_100L));
    }
}
