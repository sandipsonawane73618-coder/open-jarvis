package com.openjarvis.agent

/** Blocks explicit internal reasoning and prompt/structured-action leakage from UI/TTS. */
internal object ConversationReplyPolicy {
    const val SAFE_FALLBACK = "Maaf kijiye, main abhi iska jawab nahi de pa raha hoon."

    private val reasoningHeaders = Regex("""(?im)^[ \t]*(?:plan|thinking|reasoning)\s*:""")
    private val thinkTags = Regex("""(?is)</?\s*think\b[^>]*>""")
    private val promptLeak = Regex(
        """(?is)(system\s+prompt|developer\s+message)|(?im)^\s*(?:user|assistant)\s*:"""
    )
    private val structuredInternals = Regex(
        """(?is)\{[\s\S]*?["'](?:action|skill|args)["']\s*:"""
    )

    fun safeReply(raw: String): String {
        val reply = raw.trim()
        if (reply.isBlank() || reply.length > 2400) return SAFE_FALLBACK
        if (reasoningHeaders.containsMatchIn(reply) ||
            thinkTags.containsMatchIn(reply) ||
            promptLeak.containsMatchIn(reply) ||
            structuredInternals.containsMatchIn(reply)
        ) return SAFE_FALLBACK
        return reply
    }
}
