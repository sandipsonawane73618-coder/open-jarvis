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
    fun `parseRequest rejects missing command token or malformed JSON`() {
        assertNull(SocketServer.parseRequest("""{"requestId":"abc123","token":"secret"}"""))
        assertNull(SocketServer.parseRequest("""{"cmd":"status","requestId":"abc123"}"""))
        assertNull(SocketServer.parseRequest("not json"))
    }

    @Test
    fun `parseRequest rejects command and request id over configured limits`() {
        assertNull(SocketServer.parseRequest("""{"cmd":"${"x".repeat(2001)}","token":"secret"}"""))
        assertNull(SocketServer.parseRequest("""{"cmd":"status","requestId":"${"x".repeat(129)}","token":"secret"}"""))
    }

    @Test
    fun `token comparison accepts exact token and rejects mismatch`() {
        assertTrue(SocketServer.isValidToken("secret-token", "secret-token"))
        assertFalse(SocketServer.isValidToken("secret-token", "different-token"))
        assertFalse(SocketServer.isValidToken("", "secret-token"))
    }

    @Test
    fun `token provider allows only app Termux and ADB shell UIDs`() {
        assertTrue(BridgeTokenProvider.isTrustedUid(10123, appUid = 10123, termuxUid = 10234))
        assertTrue(BridgeTokenProvider.isTrustedUid(10234, appUid = 10123, termuxUid = 10234))
        assertTrue(BridgeTokenProvider.isTrustedUid(2000, appUid = 10123, termuxUid = 10234))
        assertFalse(BridgeTokenProvider.isTrustedUid(10345, appUid = 10123, termuxUid = 10234))
        assertFalse(BridgeTokenProvider.isTrustedUid(10345, appUid = 10123, termuxUid = null))
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
    fun `bounded line reader rejects oversized input before newline`() {
        val input = ByteArrayInputStream("123456789".toByteArray())
        try {
            SocketServer.readBoundedLine(input, 8)
            fail("Expected RequestTooLargeException")
        } catch (_: RequestTooLargeException) {
            // expected
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
