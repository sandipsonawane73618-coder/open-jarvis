package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCommandParserTest {
    @Test fun parsesWhatsappSendOnlyWhenExplicitSendWordIsPresent() {
        val request = MessageCommandParser.parse("Rahul ko WhatsApp pe hello kal milte hain bhejo")
        assertEquals("Rahul", request?.contactName)
        assertEquals(MessageRequest.Channel.WHATSAPP, request?.channel)
        assertEquals("hello kal milte hain", request?.message)
        assertTrue(request?.sendNow == true)
    }

    @Test fun stripsConversationalFillerBeforeContactName() {
        val request = MessageCommandParser.parse("mujhe Rahul ko WhatsApp pe hello bhejo")
        assertEquals("Rahul", request?.contactName)
        assertEquals("hello", request?.message)
        assertTrue(request?.sendNow == true)
    }

    @Test fun whatsappWithoutSendMeansPrefillOnly() {
        val request = MessageCommandParser.parse("Rahul ko WhatsApp pe hello kal milte hain")
        assertEquals("Rahul", request?.contactName)
        assertEquals("hello kal milte hain", request?.message)
        assertFalse(request?.sendNow ?: true)
    }

    @Test fun parsesSmsAndMarathiSendWords() {
        val request = MessageCommandParser.parse("राहुल ला SMS वर मी येतो पाठवा")
        assertEquals("राहुल", request?.contactName)
        assertEquals(MessageRequest.Channel.SMS, request?.channel)
        assertEquals("मी येतो", request?.message)
        assertTrue(request?.sendNow == true)
    }

    @Test fun parsesMarathiDhadSendVerbBeforeColonWithoutAutoSendingOtherCommands() {
        val request = MessageCommandParser.parse("Amit la WhatsApp message dhad: mi yetoy")
        assertEquals("Amit", request?.contactName)
        assertEquals(MessageRequest.Channel.WHATSAPP, request?.channel)
        assertEquals("mi yetoy", request?.message)
        assertTrue(request?.sendNow == true)
        assertNull(MessageCommandParser.parse("dhad"))
    }

    @Test fun rejectsMissingContactOrMessage() {
        assertNull(MessageCommandParser.parse("ko WhatsApp pe hello bhejo"))
        assertNull(MessageCommandParser.parse("Rahul ko WhatsApp pe bhejo"))
        assertNull(MessageCommandParser.parse("hello Rahul"))
    }
}
