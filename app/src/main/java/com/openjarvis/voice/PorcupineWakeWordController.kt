package com.openjarvis.voice

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log

/**
 * Offline wake-word detector. Porcupine listens for its built-in JARVIS keyword.
 * The spoken trigger is "Jarvis"; audio inference runs locally after AccessKey validation.
 */
class PorcupineWakeWordController(
    context: Context,
    accessKey: String,
    private val onWakeWord: () -> Unit,
    private val onError: (String) -> Unit
) : WakeWordEngine {
    private val appContext = context.applicationContext
    private val key = WakeWordConfig.normalizeAccessKey(accessKey)
    @Volatile private var manager: PorcupineManager? = null

    @Synchronized
    override fun start(): String? {
        if (manager != null) return null
        WakeWordConfig.validateAccessKey(key)?.let { return it }
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
            val sdkMessage = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            // Keep the SDK diagnostic for debugging, but never expose or log the full token.
            val safeDiagnostic = sdkMessage.replace(key, "${key.take(4)}…")
            Log.e(TAG, "Porcupine initialization failed (${WakeWordConfig.accessKeyDiagnostic(key)}): $safeDiagnostic (${error.javaClass.simpleName})")
            val friendly = "Picovoice wake word start nahi hua. AccessKey ko console.picovoice.ai se dobara copy karke Settings mein paste karo."
            onError(friendly)
            friendly
        }
    }

    @Synchronized
    override fun stop() {
        val current = manager ?: return
        manager = null
        runCatching { current.stop() }
        runCatching { current.delete() }
    }

    override fun isRunning(): Boolean = manager != null

    companion object {
        private const val TAG = "JarvisWakeWord"
    }
}
