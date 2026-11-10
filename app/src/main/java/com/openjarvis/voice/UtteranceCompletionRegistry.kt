package com.openjarvis.voice

/**
 * Associates completion callbacks with their own TTS utterance IDs.
 * A flushed older utterance can no longer complete a newer utterance's callback.
 */
internal class UtteranceCompletionRegistry {
    private val callbacks = linkedMapOf<String, () -> Unit>()

    @Synchronized
    fun register(utteranceId: String, callback: () -> Unit) {
        callbacks[utteranceId] = callback
    }

    @Synchronized
    fun take(utteranceId: String): (() -> Unit)? = callbacks.remove(utteranceId)

    @Synchronized
    fun drain(): List<() -> Unit> {
        val pending = callbacks.values.toList()
        callbacks.clear()
        return pending
    }

    @Synchronized
    fun clear() {
        callbacks.clear()
    }
}
