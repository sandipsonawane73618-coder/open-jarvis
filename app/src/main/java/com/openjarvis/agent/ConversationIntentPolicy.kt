package com.openjarvis.agent

/**
 * Conservative front-door intent routing. Explicit Android commands are left
 * for deterministic parsers/action planning; natural questions and chat requests
 * go to the conversational LLM prompt.
 */
internal object ConversationIntentPolicy {
    private val actionIntent = Regex(
        """\b(open|launch|start|close|exit|click|tap|press|type|write|send|reply|call|dial|scroll|back|home|play|pause|stop|search|find|install|uninstall|mute|unmute|खोलो|खोल|उघडा|उघड|दाबा|लिहा|पाठवा|कॉल|मागे|घरी|चलाओ|बंद करो|चालू करो)\b""",
        RegexOption.IGNORE_CASE
    )

    private val questionIntent = Regex(
        """(\?|\b(what|who|when|where|why|how|which|explain|describe|define|difference|compare|meaning|tell me|teach me|about|joke|story|translate|summarize|kya|kaise|kaisa|kaisi|kyun|kyu|kab|kaha|kaun|kitna|kitni|kis|आज|कसा|कशी|कसे|काय|का|कधी|कुठे|कोण|समजाव|सांग|फरक|म्हणजे|विनोद|कहानी|जोक|बताओ|समझाओ|क्यों|कैसे|कब|कहाँ|कौन|मतलब)\b)""",
        RegexOption.IGNORE_CASE
    )

    fun isChatRequest(command: String): Boolean {
        val text = command.trim()
        if (text.isEmpty() || text.length > 1200) return false
        if (actionIntent.containsMatchIn(text)) return false
        if (text.matches(Regex("^(hi|hello|hey|hello jarvis|hi jarvis|how are you|what's up|नमस्ते|नमस्कार|हाय|कसे आहात|काय चाललंय)[.!? ]*$", RegexOption.IGNORE_CASE))) return true
        return questionIntent.containsMatchIn(text)
    }
}
