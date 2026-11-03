package com.openjarvis.bridge

import org.junit.Assert.*
import org.junit.Test

class SocketServerTest {

    @Test
    fun `parseRequest with valid JSON returns command and requestId`() {
        val result = SocketServer.parseRequest("""{"cmd":"open chrome","requestId":"abc123"}""")

        assertNotNull(result)
        assertEquals("abc123", result?.first)
        assertEquals("open chrome", result?.second)
    }

    @Test
    fun `parseRequest with missing cmd returns null`() {
        assertNull(SocketServer.parseRequest("""{"requestId":"abc123"}"""))
    }

    @Test
    fun `parseRequest with invalid JSON returns null`() {
        assertNull(SocketServer.parseRequest("not json"))
    }

    @Test
    fun `parseRequest with empty requestId uses empty string`() {
        val result = SocketServer.parseRequest("""{"cmd":"status"}""")

        assertNotNull(result)
        assertEquals("", result?.first)
        assertEquals("status", result?.second)
    }

    @Test
    fun `escapeJson escapes special characters`() {
        val escaped = SocketServer.escapeJson("hello\nworld\"test\tvalue")

        assertTrue(escaped.contains("\\n"))
        assertTrue(escaped.contains("\\\""))
        assertTrue(escaped.contains("\\t"))
    }

    @Test
    fun `createErrorResponse formats correctly`() {
        val response = SocketServer.createErrorResponse("req123", "test error")

        assertTrue(response.contains("req123"))
        assertTrue(response.contains("error"))
        assertTrue(response.contains("test error"))
    }

    @Test
    fun `special commands are parsed correctly`() {
        val commands = listOf("status", "history", "memory", "providers")

        commands.forEachIndexed { index, command ->
            val result = SocketServer.parseRequest(
                """{"cmd":"$command","requestId":"request-$index"}"""
            )
            assertNotNull(result)
            assertEquals("request-$index", result?.first)
            assertEquals(command, result?.second)
        }
    }

    @Test
    fun `requestId roundtrip preserved`() {
        val testIds = listOf("abc123", "xyz789", "test-id-001", "")

        testIds.forEach { id ->
            val json = if (id.isEmpty()) {
                """{"cmd":"test"}"""
            } else {
                """{"cmd":"test","requestId":"$id"}"""
            }

            val result = SocketServer.parseRequest(json)
            assertNotNull("Failed for id: $id", result)
            assertEquals(id, result?.first)
        }
    }

    @Test
    fun `malformed JSON handled gracefully`() {
        listOf("", "{", """{"cmd":""", "null", "[][]").forEach { input ->
            assertNull("Should return null for: $input", SocketServer.parseRequest(input))
        }
    }
}
