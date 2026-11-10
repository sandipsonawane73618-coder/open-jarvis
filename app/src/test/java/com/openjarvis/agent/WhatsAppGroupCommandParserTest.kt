package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppGroupCommandParserTest {
    @Test
    fun parsesHindiHinglishAndEnglishGroupOpenCommands() {
        assertEquals("Pune Service", WhatsAppGroupCommandParser.parseGroupName("Pune Service group kholo"))
        assertEquals("Family", WhatsAppGroupCommandParser.parseGroupName("Family WhatsApp group open"))
        assertEquals("काम टीम", WhatsAppGroupCommandParser.parseGroupName("काम टीम ग्रुप खोलो"))
        assertNull(WhatsAppGroupCommandParser.parseGroupName("WhatsApp kholo"))
        assertNull(WhatsAppGroupCommandParser.parseGroupName("Pune Service group"))
    }

    @Test
    fun parsesTypeFollowUpOnlyWhenWhatsappGroupContextExists() {
        assertEquals(
            "Hi",
            WhatsAppGroupCommandParser.parseFollowUpText("Hi likho", "whatsapp", "Pune Service")
        )
        assertEquals(
            "Meeting at 5",
            WhatsAppGroupCommandParser.parseFollowUpText("Meeting at 5 type karo", "WhatsApp", "Pune Service")
        )
        assertNull(WhatsAppGroupCommandParser.parseFollowUpText("Hi likho", "sms", "Pune Service"))
        assertNull(WhatsAppGroupCommandParser.parseFollowUpText("Hi likho", "whatsapp", null))
        assertNull(WhatsAppGroupCommandParser.parseFollowUpText("Hi", "whatsapp", "Pune Service"))
    }
}
