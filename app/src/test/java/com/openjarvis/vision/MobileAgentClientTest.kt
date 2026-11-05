package com.openjarvis.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAgentClientTest {
    @Test fun acceptsOnlyKnownActionTypes() {
        assertTrue("tap" in MobileAgentClient.ALLOWED_ACTIONS)
        assertTrue("type" in MobileAgentClient.ALLOWED_ACTIONS)
        assertTrue("send_message" !in MobileAgentClient.ALLOWED_ACTIONS)
    }

    @Test fun rejectsEndpointWithoutHttpScheme() {
        assertTrue(runCatching { MobileAgentClient.normalizeEndpoint("localhost:8000/step") }.isFailure)
        assertEquals("http://127.0.0.1:8000/step", MobileAgentClient.normalizeEndpoint("http://127.0.0.1:8000/step"))
    }
}
