package com.openjarvis.voice

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns one user-started speech session.
 * A continuous session starts only after an explicit mic action; it never starts
 * merely because the service or overlay was created.
 */
class VoiceManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "voice_prefs"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_STT_MODE = "stt_mode"
        private const val KEY_SPEAK_RESULTS = "speak_results"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val INITIAL_RETRY_DELAY_MS = 750L
        private const val MAX_RETRY_DELAY_MS = 8_000L
    }

    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, PREFS_NAME, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val sttEngine = AndroidSTTEngine(context)
    private val ttsEngine = AndroidTTSEngine(context)
    private val stateMachine = VoiceSessionStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var listening = false
    @Volatile private var continuousSession = false
    private var retryDelayMs = INITIAL_RETRY_DELAY_MS
    private val rearmRunnable = Runnable {
        if (continuousSession && !listening) beginRecognition()
    }

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    sealed class VoiceState {
        object Idle : VoiceState()
        object Recording : VoiceState()
        object Transcribing : VoiceState()
        object Speaking : VoiceState()
        data class Result(val text: String) : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    init { ttsEngine.setSpeechRate(getSpeechRate()) }

    fun isVoiceEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_ENABLED, false)

    /** True while the user-started continuous session is armed, including command processing. */
    fun isSessionActive(): Boolean = continuousSession || listening

    fun isContinuousSessionActive(): Boolean = continuousSession

    fun setVoiceEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply() }

    fun getSTTMode(): STTMode {
        val mode = prefs.getString(KEY_STT_MODE, STTMode.PUSH_TO_TALK.name)
        return try { STTMode.valueOf(mode ?: STTMode.PUSH_TO_TALK.name) }
        catch (_: Exception) { STTMode.PUSH_TO_TALK }
    }

    fun setSTTMode(mode: STTMode) { prefs.edit().putString(KEY_STT_MODE, mode.name).apply() }
    fun isSpeakResultsEnabled(): Boolean = prefs.getBoolean(KEY_SPEAK_RESULTS, true)
    fun setSpeakResultsEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SPEAK_RESULTS, enabled).apply() }
    fun getSpeechRate(): Float = prefs.getFloat(KEY_SPEECH_RATE, 1.05f)

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.5f, 2.0f)).apply()
        ttsEngine.setSpeechRate(rate)
    }

    /** One mic tap starts a continuous conversation session. */
    @Synchronized
    fun startContinuousSession() {
        if (continuousSession) return
        if (!isVoiceEnabled()) {
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }
        continuousSession = true
        retryDelayMs = INITIAL_RETRY_DELAY_MS
        mainHandler.removeCallbacks(rearmRunnable)
        stateMachine.beginContinuousSession()
        beginRecognition()
    }

    /** Legacy one-shot entry point, retained for callers that explicitly need push-to-talk. */
    @Synchronized
    fun startListening() {
        if (listening) return
        if (!isVoiceEnabled()) {
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }
        continuousSession = false
        mainHandler.removeCallbacks(rearmRunnable)
        stateMachine.stop()
        beginRecognition()
    }

    @Synchronized
    private fun beginRecognition() {
        if (listening) return
        if (!continuousSession && stateMachine.isContinuousSessionActive()) stateMachine.stop()
        if (!isVoiceEnabled()) {
            continuousSession = false
            stateMachine.stop()
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }

        listening = true
        stateMachine.beginListening()
        _state.value = VoiceState.Recording
        sttEngine.startListening(
            onResult = { text ->
                listening = false
                retryDelayMs = INITIAL_RETRY_DELAY_MS
                stateMachine.onTranscriptReceived(!text.isNullOrBlank())
                if (!text.isNullOrBlank()) {
                    _state.value = VoiceState.Result(text)
                } else if (continuousSession) {
                    _state.value = VoiceState.Error("No speech detected — listening again")
                    scheduleContinuousRetry()
                } else {
                    _state.value = VoiceState.Error("No speech detected — tap Mic to try again")
                }
            },
            onError = { error ->
                listening = false
                val cleanError = error.removePrefix("RETRYING: ")
                stateMachine.onTranscriptReceived(false)
                _state.value = VoiceState.Error(cleanError)
                if (continuousSession && isRecoverableRecognitionError(cleanError)) {
                    scheduleContinuousRetry()
                } else {
                    continuousSession = false
                    stateMachine.stop()
                }
            }
        )
    }

    private fun isRecoverableRecognitionError(error: String): Boolean {
        val normalized = error.lowercase()
        if (listOf("permission", "unavailable", "network").any(normalized::contains)) return false
        return listOf(
            "no speech", "timeout", "busy", "client error",
            "audio recording error", "server error", "recognition error"
        ).any(normalized::contains)
    }

    private fun scheduleContinuousRetry() {
        if (!continuousSession) return
        mainHandler.removeCallbacks(rearmRunnable)
        val delay = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        mainHandler.postDelayed(rearmRunnable, delay)
    }

    /** Called after a transcript has been handled or Jarvis has finished speaking. */
    @Synchronized
    fun resumeContinuousSession() {
        if (!continuousSession || listening) return
        mainHandler.removeCallbacks(rearmRunnable)
        retryDelayMs = INITIAL_RETRY_DELAY_MS
        stateMachine.onSpeakingFinished()
        beginRecognition()
    }

    fun markThinking() {
        listening = false
        stateMachine.onCommandStarted()
        _state.value = VoiceState.Transcribing
    }

    /** Stops recognition completely and clears the user-started continuous session. */
    @Synchronized
    fun stopListening(): String? {
        continuousSession = false
        mainHandler.removeCallbacks(rearmRunnable)
        listening = false
        stateMachine.stop()
        val text = sttEngine.stop()
        // An explicit stop always discards any late/pending transcript.
        _state.value = VoiceState.Idle
        return text
    }

    fun stopContinuousSession() {
        stopListening()
    }

    /** Compatibility alias; VAD mode remains a one-shot recognition attempt. */
    fun startListeningWithVAD() = startListening()

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isSpeakResultsEnabled()) {
            stateMachine.onSpeakingFinished()
            _state.value = VoiceState.Idle
            onComplete?.invoke()
            return
        }
        stateMachine.onSpeakingStarted()
        _state.value = VoiceState.Speaking
        ttsEngine.speak(text) {
            stateMachine.onSpeakingFinished()
            _state.value = VoiceState.Idle
            onComplete?.invoke()
        }
    }

    fun stopSpeaking() {
        ttsEngine.stop()
        stateMachine.onSpeakingFinished()
        _state.value = VoiceState.Idle
    }

    fun resetState() {
        if (_state.value is VoiceState.Result || _state.value is VoiceState.Error) {
            _state.value = VoiceState.Idle
        }
    }

    @Synchronized
    fun release() {
        continuousSession = false
        listening = false
        mainHandler.removeCallbacks(rearmRunnable)
        stateMachine.stop()
        sttEngine.release()
        ttsEngine.release()
    }
}
