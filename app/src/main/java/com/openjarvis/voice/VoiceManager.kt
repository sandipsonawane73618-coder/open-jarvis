package com.openjarvis.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "voice_prefs"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_STT_MODE = "stt_mode"
        private const val KEY_SPEAK_RESULTS = "speak_results"
        private const val KEY_SPEECH_RATE = "speech_rate"
    }

    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, PREFS_NAME, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val sttEngine = AndroidSTTEngine(context)
    private val ttsEngine = AndroidTTSEngine(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateMachine = VoiceSessionStateMachine()
    private var continuousSession = false

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
    fun getPicovoiceAccessKey(): String = prefs.getString(WakeWordConfig.PREFERENCE_KEY, "").orEmpty()
    fun setPicovoiceAccessKey(key: String) {
        prefs.edit().putString(WakeWordConfig.PREFERENCE_KEY, WakeWordConfig.normalizeAccessKey(key)).apply()
    }
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

    fun startListening() {
        if (!isVoiceEnabled()) {
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }
        continuousSession = true
        beginRecognition()
    }

    private fun beginRecognition() {
        if (!continuousSession) return
        stateMachine.beginListening()
        _state.value = VoiceState.Recording
        sttEngine.startListening(
            onResult = { text ->
                if (!text.isNullOrBlank()) {
                    stateMachine.onTranscriptReceived(true)
                    _state.value = VoiceState.Result(text)
                } else {
                    stateMachine.onRecoverableError()
                    _state.value = VoiceState.Recording
                }
            },
            onError = { error ->
                if (error.startsWith("RETRYING:")) {
                    stateMachine.onRecoverableError()
                    _state.value = VoiceState.Recording
                } else {
                    _state.value = VoiceState.Error(error)
                }
            }
        )
    }

    fun markThinking() {
        if (continuousSession) {
            stateMachine.onCommandStarted()
            _state.value = VoiceState.Transcribing
        }
    }

    fun stopListening(): String? {
        continuousSession = false
        stateMachine.stop()
        val text = sttEngine.stop()
        _state.value = if (!text.isNullOrBlank()) VoiceState.Result(text) else VoiceState.Idle
        return text
    }

    fun startListeningWithVAD() = startListening()

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isSpeakResultsEnabled()) {
            resumeAfterResponse()
            onComplete?.invoke()
            return
        }
        if (continuousSession) {
            stateMachine.onSpeakingStarted()
            _state.value = VoiceState.Speaking
        }
        ttsEngine.speak(text) { resumeAfterResponse(); onComplete?.invoke() }
    }

    private fun resumeAfterResponse() {
        if (continuousSession) {
            stateMachine.onSpeakingFinished(true)
            beginRecognition()
        } else {
            stateMachine.onSpeakingFinished(false)
            _state.value = VoiceState.Idle
        }
    }

    fun stopSpeaking() { ttsEngine.stop() }

    fun resetState() {
        if (_state.value is VoiceState.Result || _state.value is VoiceState.Error) {
            _state.value = if (continuousSession) VoiceState.Recording else VoiceState.Idle
        }
    }

    fun release() {
        continuousSession = false
        stateMachine.stop()
        sttEngine.release()
        ttsEngine.release()
        scope.cancel()
    }
}