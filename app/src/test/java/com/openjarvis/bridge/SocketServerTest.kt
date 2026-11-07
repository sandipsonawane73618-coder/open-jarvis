package com.openjarvis.bridge

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class SocketServerTest {

    @Test
    fun `parseRequest with valid JSON returns command requestId and token`() {
        val result = SocketServer.parseRequest(
            """{"cmd":"open chrome","requestId":"abc123","token":"secret"}"""
        )
        assertNotNull(result)
        assertEquals("abc123", result?.first)
        assertEquals("open chrome", result?.second)
        assertEquals("secret", result?.third)
    }

    @Test
    fun `parseRequest accepts exact command and request id limits`() {
        val command = "x".repeat(SocketServer.MAX_COMMAND_LENGTH)
        val requestId = "r".repeat(SocketServer.MAX_REQUEST_ID_LENGTH)
        val result = SocketServer.parseRequest(
            """{"cmd":"$command","requestId":"$requestId","token":"secret"}"""
        )
        assertNotNull(result)
        assertEquals(command, result?.second)
        assertEquals(requestId, result?.first)
    }

    @Test
    fun `parseRequest rejects missing command token or malformed JSON`() {
        assertNull(SocketServer.parseRequest("""{"requestId":"abc123","token":"secret"}"""))
        assertNull(SocketServer.parseRequest("""{"cmd":"status","requestId":"abc123"}"""))
        assertNull(SocketServer.parseRequest("""{"cmd":"status","token":""}"""))
        assertNull(SocketServer.parseRequest("not json"))
    }

    @Test
    fun `parseRequest rejects command and request id one over configured limits`() {
        assertNull(SocketServer.parseRequest("""{"cmd":"${"x".repeat(SocketServer.MAX_COMMAND_LENGTH + 1)}","token":"secret"}"""))
        assertNull(SocketServer.parseRequest("""{"cmd":"status","requestId":"${"x".repeat(SocketServer.MAX_REQUEST_ID_LENGTH + 1)}","token":"secret"}"""))
    }

    @Test
    fun `token comparison accepts exact token and rejects mismatch`() {
        assertTrue(SocketServer.isValidToken("secret-token", "secret-token"))
        assertFalse(SocketServer.isValidToken("secret-token", "different-token"))
        assertFalse(SocketServer.isValidToken("", "secret-token"))
        assertFalse(SocketServer.isValidToken("secret-token", ""))
    }

    @Test
    fun `token provider accepts only the exact content config URI`() {
        assertTrue(
            BridgeTokenProvider.isConfigUri(
                "content://com.openjarvis.preview.bridgeconfig/config",
                "com.openjarvis.preview.bridgeconfig"
            )
        )
        assertFalse(
            BridgeTokenProvider.isConfigUri(
                "content://com.openjarvis.preview.bridgeconfig/other",
                "com.openjarvis.preview.bridgeconfig"
            )
        )
        assertFalse(
            BridgeTokenProvider.isConfigUri(
                "content://attacker.example/config",
                "com.openjarvis.preview.bridgeconfig"
            )
        )
        assertFalse(
            BridgeTokenProvider.isConfigUri(
                "https://com.openjarvis.preview.bridgeconfig/config",
                "com.openjarvis.preview.bridgeconfig"
            )
        )
    }

    @Test
    fun `token provider allows only app Termux and ADB shell UIDs`() {
        assertTrue(BridgeTokenProvider.isTrustedUid(10123, appUid = 10123, termuxUid = 10234))
        assertTrue(BridgeTokenProvider.isTrustedUid(10234, appUid = 10123, termuxUid = 10234))
        assertTrue(BridgeTokenProvider.isTrustedUid(2000, appUid = 10123, termuxUid = 10234))
        assertFalse(BridgeTokenProvider.isTrustedUid(10345, appUid = 10123, termuxUid = 10234))
        assertFalse(BridgeTokenProvider.isTrustedUid(10345, appUid = 10123, termuxUid = null))
        assertFalse(BridgeTokenProvider.isTrustedUid(10234, appUid = 10123, termuxUid = null))
    }

    @Test
    fun `escapeJson escapes special characters`() {
        val escaped = SocketServer.escapeJson("hello\nworld\"test\tvalue")
        assertTrue(escaped.contains("\\n"))
        assertTrue(escaped.contains("\\\""))
        assertTrue(escaped.contains("\\t"))
    }

    @Test
    fun `createErrorResponse escapes untrusted values`() {
        val response = SocketServer.createErrorResponse("id\"\n", "bad\"\nvalue")
        assertTrue(response.contains("id\\\"\\n"))
        assertTrue(response.contains("bad\\\"\\nvalue"))
    }

    @Test
    fun `bounded line reader reads UTF8 request and strips carriage return`() {
        val bytes = ("""{"cmd":"नमस्ते"}""" + "\r\n").toByteArray(Charsets.UTF_8)
        assertEquals(
            """{"cmd":"नमस्ते"}""",
            SocketServer.readBoundedLine(ByteArrayInputStream(bytes), 100)
        )
    }

    @Test
    fun `bounded line reader accepts exactly the configured 8 KiB limit`() {
        val request = "x".repeat(SocketServer.MAX_REQUEST_BYTES)
        val input = ByteArrayInputStream((request + "\n").toByteArray())
        assertEquals(request, SocketServer.readBoundedLine(input, SocketServer.MAX_REQUEST_BYTES))
    }

    @Test
    fun `bounded line reader rejects one byte over the configured 8 KiB limit`() {
        val request = "x".repeat(SocketServer.MAX_REQUEST_BYTES + 1)
        val input = ByteArrayInputStream((request + "\n").toByteArray())
        try {
            SocketServer.readBoundedLine(input, SocketServer.MAX_REQUEST_BYTES)
            fail("Expected RequestTooLargeException")
        } catch (_: RequestTooLargeException) {
            // expected
        }
    }

    @Test
    fun `bounded line reader counts stripped carriage returns toward byte limit`() {
        val request = "x".repeat(SocketServer.MAX_REQUEST_BYTES - 1) + "\r\r"
        val input = ByteArrayInputStream((request + "\n").toByteArray())
        try {
            SocketServer.readBoundedLine(input, SocketServer.MAX_REQUEST_BYTES)
            fail("Expected RequestTooLargeException")
        } catch (_: RequestTooLargeException) {
            // expected: CR bytes count even though they are removed from parsed content.
        }
    }

    @Test
    fun `bounded line reader returns null for empty stream`() {
        assertNull(SocketServer.readBoundedLine(ByteArrayInputStream(byteArrayOf()), 8))
    }

    @Test
    fun `rate limiter allows ten requests and rejects eleventh in same window`() {
        val limiter = SlidingWindowRateLimiter(maxRequests = 10, windowMs = 1_000)
        repeat(10) { assertTrue(limiter.allow(clientKey = 10001, nowMs = it.toLong())) }
        assertFalse(limiter.allow(clientKey = 10001, nowMs = 10))
    }

    @Test
    fun `rate limiter expires a request at the exact rolling window boundary`() {
        val limiter = SlidingWindowRateLimiter(maxRequests = 1, windowMs = 1_000)
        assertTrue(limiter.allow(clientKey = 10001, nowMs = 50))
        assertFalse(limiter.allow(clientKey = 10001, nowMs = 1_049))
        assertTrue(limiter.allow(clientKey = 10001, nowMs = 1_050))
    }

    @Test
    fun `rate limiter isolates client keys and resets after window`() {
        val limiter = SlidingWindowRateLimiter(maxRequests = 1, windowMs = 1_000)
        assertTrue(limiter.allow(clientKey = 10001, nowMs = 0))
        assertFalse(limiter.allow(clientKey = 10001, nowMs = 10))
        assertTrue(limiter.allow(clientKey = 10002, nowMs = 10))
        assertTrue(limiter.allow(clientKey = 10001, nowMs = 1_000))
    }

    @Test
    fun `special commands are parsed correctly`() {
        val commands = listOf("status", "history", "memory", "providers")
        commands.forEachIndexed { index, command ->
            val result = SocketServer.parseRequest(
                """{"cmd":"$command","requestId":"request-$index","token":"secret"}"""
            )
            assertNotNull(result)
            assertEquals("request-$index", result?.first)
            assertEquals(command, result?.second)
            assertEquals("secret", result?.third)
        }
    }

    @Test
    fun `malformed JSON handled gracefully`() {
        listOf("", "{", """{"cmd":""", "null", "[][]").forEach { input ->
            assertNull("Should return null for: $input", SocketServer.parseRequest(input))
        }
    }
}
