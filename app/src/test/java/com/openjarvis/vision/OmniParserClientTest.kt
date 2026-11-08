package com.openjarvis.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniParserClientTest {
    @Test fun validatesEndpointScheme() {
        assertEquals("http://127.0.0.1:8001/parse", OmniParserClient.normalizeEndpoint("http://127.0.0.1:8001/parse"))
        assertTrue(runCatching { OmniParserClient.normalizeEndpoint("127.0.0.1:8001/parse") }.isFailure)
    }
}
