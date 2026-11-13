package com.openjarvis.agent

/** Controls the one-time user-facing hint when ordinary chat is routed to local Ollama. */
internal object LocalModelFallbackHintPolicy {
    const val HINT = "Behtar samajhne ke liye Settings me Gemini key daalo."

    fun shouldShow(
        onlineChatEnabled: Boolean,
        selectedProviderKeyAvailable: Boolean,
        localModelUsed: Boolean,
        alreadyShown: Boolean
    ): Boolean = localModelUsed && !alreadyShown &&
        (!onlineChatEnabled || !selectedProviderKeyAvailable)
}
