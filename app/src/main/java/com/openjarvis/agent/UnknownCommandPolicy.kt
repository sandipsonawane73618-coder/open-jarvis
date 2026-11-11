package com.openjarvis.agent

/** Conservative gate: unclear non-question text must not trigger an LLM action plan. */
internal object UnknownCommandPolicy {
    const val FALLBACK = "Samajh nahi aaya, dobara bolo."

    private val explicitAction = Regex(
        """(?i)(?<![\p{L}\p{N}_])(open|launch|start|close|exit|click|tap|press|type|write|send|reply|call|dial|scroll|back|home|play|pause|stop|search|find|install|uninstall|mute|unmute|screenshot|swipe|upload|download|share|select|choose|enter|save|delete|set|turn|increase|decrease|navigate|kholo|khol|ugha?da|chalu|band karo|dabao|likho|bhejo|bhej|call karo|dhundo|khojo|bajao|lagao|badhao|kam karo|wapas|peeche|ghar jao|photo lo|padho|dekho|dakhva|dakhav|shodha|pathva|उघडा|उघड|खोलो|खोल|बंद करो|दबाओ|लिखो|भेजो|कॉल|स्क्रोल|वापस|घर जाओ|बजाओ|लगाओ|बढ़ाओ|कम करो|चालू करो|स्क्रीनशॉट|फोटो लो|पढ़ो|देखो|खोजो|अपलोड|डाउनलोड|साझा|हटाओ|चुनो|स्वाइप|टैप)(?![\p{L}\p{N}_])"""
    )

    fun shouldStopBeforePlanner(command: String): Boolean {
        val text = command.trim()
        return text.isBlank() || !explicitAction.containsMatchIn(text)
    }
}
