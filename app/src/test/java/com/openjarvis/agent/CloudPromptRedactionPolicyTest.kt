package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class CloudPromptRedactionPolicyTest {
    @Test fun redactsPasswordsOtpPinCardsAndApiKeysBeforeCloudDispatch() {
        val input = "password=hunter2 OTP 482913 PIN 1234 card 4111111111111111 api_key=sk-abcdefghijklmnop"
        val redacted = CloudPromptRedactionPolicy.redact(input)
        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("482913"))
        assertFalse(redacted.contains("1234"))
        assertFalse(redacted.contains("4111111111111111"))
        assertFalse(redacted.contains("sk-abcdefghijklmnop"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test fun bankingSafetyPolicyBlockedContentIsRedactedEvenWithoutCredentialPattern() {
        val input = "Please send money to Rahul 5000 via UPI transfer"
        val redacted = CloudPromptRedactionPolicy.redact(input)
        assertFalse(redacted.contains("send money"))
        assertFalse(redacted.contains("5000"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test fun preservesOrdinaryPersonalMemoryAndContactContext() {
        val input = "prefers concise Hinglish; contact Rahul; reminder to service bike"
        assertEquals(input, CloudPromptRedactionPolicy.redact(input))
    }
}
