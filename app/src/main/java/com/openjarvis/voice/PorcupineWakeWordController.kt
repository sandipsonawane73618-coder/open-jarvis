package com.openjarvis.voice

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context

/**
 * Offline wake-word detector. The built-in JARVIS keyword is contained in the spoken phrase
 * "Hey Jarvis"; Porcupine performs audio inference locally after its AccessKey is validated.
 */
class PorcupineWakeWordController(
    context: Context,
    accessKey: String,
    private val onWakeWord: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val key = WakeWordConfig.normalizeAccessKey(accessKey)
    @Volatile private var manager: PorcupineManager? = null

    @Synchronized
    fun start(): String? {
        if (manager != null) return null
        if (key.isBlank()) return "Add a free Picovoice AccessKey in Settings to enable offline Hey Jarvis."
        return try {
            val callback = PorcupineManagerCallback { keywordIndex ->
                if (keywordIndex == 0) onWakeWord()
            }
            val created = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeywords(arrayOf(Porcupine.BuiltInKeyword.JARVIS))
                .build(appContext, callback)
            manager = created
            created.start()
            null
        } catch (error: Exception) {
            manager?.let { runCatching { it.delete() } }
            manager = null
            val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            onError(message)
            "Wake-word engine could not start: $message"
        }
    }

    @Synchronized
    fun stop() {
        val current = manager ?: return
        manager = null
        runCatching { current.stop() }
        runCatching { current.delete() }
    }

    fun isRunning(): Boolean = manager != null
}
