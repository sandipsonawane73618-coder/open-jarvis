package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactCommandParserTest {
    private fun p(text: String) = ContactCommandParser.parse(text)

    @Test fun missingNameAsksWho() {
        assertEquals(CallRequest(null), p("call karo na"))
        assertEquals(CallRequest(null), p("call karo"))
    }

    @Test fun nameIsExtracted() {
        assertEquals(CallRequest("rahul"), p("Rahul ko call karo"))
        assertEquals(CallRequest("rahul"), p("Rahul ko call karo na"))
        assertEquals(CallRequest("rahul"), p("Rahul ko call kar do"))
        assertEquals(CallRequest("rahul"), p("call Rahul"))
        assertEquals(CallRequest("rahul"), p("Rahul ko phone lagao"))
        assertEquals(CallRequest("rahul"), p("Rahul ko call karo please"))
        assertEquals(CallRequest("rahul"), p("mujhe Rahul ko call karna hai"))
        assertEquals(CallRequest("राहुल"), p("राहुल को कॉल करो"))
    }

    @Test fun fullNameIsKept() {
        assertEquals(CallRequest("rahul sharma"), p("Rahul Sharma ko call karo"))
    }

    @Test fun numbersAndOtherCommandsAreIgnored() {
        assertNull(p("call 9876543210")); assertNull(p("dial 100"))
        assertNull(p("hello")); assertNull(p("Chrome kholo"))
        assertNull(p("YouTube par lofi search karo")); assertNull(p(""))
    }
}
