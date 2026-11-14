package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOffCommandPolicyTest {

    @Test
    fun recognizesExplicitEnglishHindiAndMarathiPowerOffCommands() {
        listOf(
            "shutdown ho jao",
            "Phone band kar do",
            "mobile ko band kar do",
            "Power off the phone",
            "turn off device",
            "फोन बंद कर दो",
            "मोबाइल बंद करो",
            "फोन बंद करा",
            "मोबाईल बंद करा"
        ).forEach { command ->
            assertTrue("Expected power-off intent for: $command", PowerOffCommandPolicy.isPowerOffCommand(command))
        }
    }

    @Test
    fun doesNotTreatAppCloseOrUnrelatedCommandsAsDevicePowerOff() {
        listOf(
            "close app",
            "shutdown app",
            "phone kholo",
            "restart phone",
            "screen off",
            "shutdown timer"
        ).forEach { command ->
            assertFalse("Unexpected power-off intent for: $command", PowerOffCommandPolicy.isPowerOffCommand(command))
        }
    }

    @Test
    fun acceptsExplicitPowerOffConfirmation() {
        listOf("yes", "haan", "haan power off", "हाँ बंद करो", "हो बंद करा").forEach { command ->
            assertEquals(true, PowerOffCommandPolicy.confirmationDecision(command))
        }
    }

    @Test
    fun acceptsCancellationAndDoesNotTreatUnrelatedSpeechAsConfirmation() {
        listOf("no", "cancel", "nahi", "नहीं", "नको").forEach { command ->
            assertEquals(false, PowerOffCommandPolicy.confirmationDecision(command))
        }
        assertEquals(null, PowerOffCommandPolicy.confirmationDecision("open YouTube"))
    }
}
