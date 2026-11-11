package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationIntentPolicyTest {
    @Test fun routesGeneralQuestionsToChat() {
        listOf(
            "India ka capital kya hai?",
            "India ka capital kya hai",
            "Aaj ka din kaisa hai?",
            "Mujhe ek joke sunao",
            "joke sunao",
            "story sunao",
            "ek kahani sunao",
            "kahani sunao",
            "ek katha sunao",
            "Ollama kya hai?",
            "Java aur Kotlin mein difference?",
            "What is Android?",
            "काय चाललंय?",
            "काय चाललंय",
            "कुठे आहे",
            "मला सांग काय झालं",
            "आज कसा आहेस",
            "Aaj kasa ahes?",
            "Aaj kay zala?",
            "Kay zala?",
            "Kaay chalu aahe?",
            "Tu kashi ahes?",
            "He kuthe aahe?",
            "Kadhi yenar?",
            "Kiti vel lagel?",
            "Mala sang, Ollama kay aahe?",
            "How do I open YouTube?",
            "How can I mute the phone?",
            "WhatsApp kasa open karu?",
            "Volume kasa kami karaycha?",
            "Kaise khole settings?"
        ).forEach { assertTrue("$it should use chat", ConversationIntentPolicy.isChatRequest(it)) }
    }

    @Test fun leavesPhoneActionsForActionPipeline() {
        listOf(
            "YouTube kholo",
            "Open WhatsApp",
            "click Search",
            "WhatsApp mein message likho",
            "go back",
            "mute ho jao",
            "यूट्यूब उघडा",
            "फोन बंद करो",
            "YouTube खोलो"
        ).forEach { assertFalse("$it should use action pipeline", ConversationIntentPolicy.isChatRequest(it)) }
    }

    @Test fun rejectsEmptyAndLongInputs() {
        assertFalse(ConversationIntentPolicy.isChatRequest(" "))
        assertFalse(ConversationIntentPolicy.isChatRequest("x".repeat(1201)))
    }
}
