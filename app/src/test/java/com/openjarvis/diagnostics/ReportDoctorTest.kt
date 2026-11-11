package com.openjarvis.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDoctorTest {
    @Test fun diagnosesLocalOllamaWithoutSuggestingCloudFallback() {
        val report = ReportDoctor.diagnose("LLM provider", "Connection refused at 127.0.0.1:11434")
        assertEquals(ReportDoctor.Area.OLLAMA_CONNECTION, report.area)
        assertTrue(report.safeNextStep.contains("Termux"))
        assertFalse(report.safeNextStep.contains("cloud"))
    }

    @Test fun diagnosesManualMicButtonFlow() {
        val report = ReportDoctor.diagnose("Manual listening", "mic button recording did not start")
        assertEquals(ReportDoctor.Area.MANUAL_LISTENING, report.area)
        assertTrue(report.safeNextStep.contains("mic button"))
    }

    @Test fun diagnosesMicrophonePermissionSeparately() {
        val report = ReportDoctor.diagnose("Audio", "RECORD_AUDIO permission denied")
        assertEquals(ReportDoctor.Area.MICROPHONE_PERMISSION, report.area)
        assertTrue(report.safeNextStep.contains("Microphone"))
    }

    @Test fun diagnosesSpeechRecognitionFailure() {
        val report = ReportDoctor.diagnose("STT", "no speech match")
        assertEquals(ReportDoctor.Area.SPEECH_RECOGNITION, report.area)
        assertTrue(report.safeNextStep.contains("on-device language support"))
    }

    @Test fun diagnosesOverlayWithoutAutomaticallyChangingSettings() {
        val report = ReportDoctor.diagnose("Overlay", "floating window detached")
        assertEquals(ReportDoctor.Area.OVERLAY, report.area)
        assertTrue(report.safeNextStep.contains("Check Display over other apps"))
        assertFalse(report.safeNextStep.contains("restart"))
    }

    @Test fun diagnosesAccessibilityAndAppLaunchFailures() {
        assertEquals(
            ReportDoctor.Area.ACCESSIBILITY,
            ReportDoctor.diagnose("Accessibility", "accessibility service disabled").area
        )
        assertEquals(
            ReportDoctor.Area.APP_LAUNCH,
            ReportDoctor.diagnose("App launch", "package not found").area
        )
    }

    @Test fun diagnosesUnusableLocalModelResponse() {
        val report = ReportDoctor.diagnose("LLM response", "invalid json")
        assertEquals(ReportDoctor.Area.MODEL_RESPONSE, report.area)
        assertTrue(report.safeNextStep.contains("deterministic phone actions"))
    }

    @Test fun unknownFailureProducesSafeManualNextStep() {
        val report = ReportDoctor.diagnose("unknown", "unclassified failure")
        assertEquals(ReportDoctor.Area.UNKNOWN, report.area)
        assertEquals(ReportDoctor.Severity.INFO, report.severity)
    }
}
