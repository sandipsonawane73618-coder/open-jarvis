package com.openjarvis.agent

/**
 * Conservative front-door intent routing. Explicit Android commands are left
 * for deterministic parsers/action planning; natural questions and chat requests
 * go to the conversational LLM prompt.
 */
internal object ConversationIntentPolicy {
    private val actionIntent = Regex(
        """(?<![\p{L}\p{N}_])(open|launch|start|close|exit|click|tap|press|type|write|send|reply|call|dial|scroll|back|home|play|pause|stop|search|find|install|uninstall|mute|unmute|खोलो|खोल|उघडा|उघड|दाबा|लिहा|पाठवा|कॉल|मागे|घरी|चलाओ|बंद करो|चालू करो)(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )

    private val questionIntent = Regex(
        """(\?|(?<![\p{L}\p{N}_])(what|who|when|where|why|how|which|explain|describe|define|difference|compare|meaning|tell me|teach me|about|joke|story|kahani|katha|kissa|translate|summarize|kya|kaise|kaisa|kaisi|kyun|kyu|kab|kaha|kaun|kitna|kitni|kis|kay|kaay|kasa|kashi|kase|kuthe|kon|koni|kadhi|kiti|zala|zale|sanga|sang|samjhav|mhanje|आज|कसा|कशी|कसे|काय|का|कधी|कुठे|कोण|समजाव|सांग|फरक|म्हणजे|विनोद|कहानी|जोक|बताओ|समझाओ|क्यों|कैसे|कब|कहाँ|कौन|मतलब)(?![\p{L}\p{N}_]))""",
        RegexOption.IGNORE_CASE
    )

    // Educational "how do I..." questions may mention an action verb but must
    // stay in chat; direct requests such as "open YouTube" still go to the action path.
    private val instructionalQuestionIntent = Regex(
        """(?<![\p{L}\p{N}_])(how to|how do i|how can i|what happens if|where do i find|kasa karaycha|kashi karaychi|kase karayche|kasa .{0,40} karaycha|kashi .{0,40} karaychi|kase .{0,40} karayche|kasa karu|kashi karu|kase karu|kasa .{0,40} karu|kashi .{0,40} karu|kase .{0,40} karu|kaise karu|kaise kare|kaise khole)(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )

    fun isChatRequest(command: String): Boolean {
        val text = command.trim()
        if (text.isEmpty() || text.length > 1200) return false
        if (instructionalQuestionIntent.containsMatchIn(text)) return true
        if (actionIntent.containsMatchIn(text)) return false
        if (text.matches(Regex("^(hi|hello|hey|hello jarvis|hi jarvis|how are you|what's up|नमस्ते|नमस्कार|हाय|कसे आहात|काय चाललंय)[.!? ]*$", RegexOption.IGNORE_CASE))) return true
        return questionIntent.containsMatchIn(text)
    }
}
