package com.openjarvis.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.Locale

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
    private var wakeWordMode = false
    private var wakeLoopJob: Job? = null

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    sealed class VoiceState {
        object Idle : VoiceState()
        object Recording : VoiceState()
        object Transcribing : VoiceState()
        object WakeListening : VoiceState()
        object AwaitingCommand : VoiceState()
        data class Result(val text: String) : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    init { ttsEngine.setSpeechRate(getSpeechRate()) }

    fun isVoiceEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_ENABLED, false)
    fun setVoiceEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply() }
    fun getSTTMode(): STTMode {
        return try { STTMode.valueOf(prefs.getString(KEY_STT_MODE, STTMode.PUSH_TO_TALK.name) ?: STTMode.PUSH_TO_TALK.name) }
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

    fun startWakeWordMode() {
        if (!isVoiceEnabled()) setVoiceEnabled(true)
        wakeWordMode = true
        wakeLoopJob?.cancel()
        armWakeWord()
    }

    fun pauseWakeWordListening() {
        if (!wakeWordMode) return
        wakeLoopJob?.cancel()
        wakeLoopJob = null
        sttEngine.stop()
    }

    fun stopWakeWordMode() {
        wakeWordMode = false
        wakeLoopJob?.cancel()
        wakeLoopJob = null
        sttEngine.stop()
        _state.value = VoiceState.Idle
    }

    fun resumeWakeWordMode() {
        if (!wakeWordMode) return
        wakeLoopJob?.cancel()
        wakeLoopJob = scope.launch { delay(900); armWakeWord() }
    }

    private fun armWakeWord() {
        if (!wakeWordMode) return
        _state.value = VoiceState.WakeListening
        sttEngine.startListening(
            onResult = { recognized ->
                val command = recognized?.let(::stripWakePhrase)
                if (command == null) retryWakeWord(700)
                else if (command.isBlank()) listenForCommand()
                else _state.value = VoiceState.Result(command)
            },
            onError = { retryWakeWord(if (it.contains("busy", true)) 1400 else 900) }
        )
    }

    private fun listenForCommand() {
        if (!wakeWordMode) return
        _state.value = VoiceState.AwaitingCommand
        scope.launch {
            delay(250)
            if (!wakeWordMode) return@launch
            sttEngine.startListening(
                onResult = { command ->
                    if (!command.isNullOrBlank()) _state.value = VoiceState.Result(command.trim())
                    else retryWakeWord(700)
                },
                onError = { retryWakeWord(if (it.contains("busy", true)) 1400 else 900) }
            )
        }
    }

    private fun retryWakeWord(waitMs: Long) {
        if (!wakeWordMode) return
        wakeLoopJob?.cancel()
        wakeLoopJob = scope.launch {
            _state.value = VoiceState.WakeListening
            delay(waitMs)
            armWakeWord()
        }
    }

    private fun stripWakePhrase(raw: String): String? {
        val lower = raw.lowercase(Locale.ROOT)
        val phrases = listOf("hey jarvis", "ok jarvis", "okay jarvis", "हे जार्विस", "ए जार्विस", "ओके जार्विस")
        val match = phrases.mapNotNull { phrase ->
            val index = lower.indexOf(phrase)
            if (index >= 0) index to phrase.length else null
        }.minByOrNull { it.first } ?: return null
        return raw.substring(match.first + match.second).trim().trimStart(',', '.', '!', '?', ' ')
    }

    fun startListening() {
        resetState()
        wakeLoopJob?.cancel()
        wakeLoopJob = null
        sttEngine.stop()
        if (!isVoiceEnabled()) {
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }
        scope.launch {
            _state.value = VoiceState.Recording
            sttEngine.startListening(
                onResult = { text ->
                    if (!text.isNullOrBlank()) _state.value = VoiceState.Result(text.trim())
                    else _state.value = VoiceState.Error("Could not understand audio")
                },
                onError = { error -> _state.value = VoiceState.Error(error) }
            )
        }
    }

    fun stopListening(): String? {
        val text = sttEngine.stop()
        _state.value = if (!text.isNullOrBlank()) VoiceState.Result(text) else VoiceState.Idle
        return text
    }

    fun startListeningWithVAD() {
        if (!isVoiceEnabled()) {
            _state.value = VoiceState.Error("Voice disabled in settings")
            return
        }
        scope.launch {
            _state.value = VoiceState.Recording
            sttEngine.startListening(
                onResult = { text ->
                    if (!text.isNullOrBlank()) {
                        _state.value = VoiceState.Result(text)
                        if (isSpeakResultsEnabled()) ttsEngine.speak("Heard: $text")
                    } else _state.value = VoiceState.Idle
                },
                onError = { error -> _state.value = VoiceState.Error(error) }
            )
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (isSpeakResultsEnabled()) ttsEngine.speak(text, onDone) else onDone?.invoke()
    }
    fun stopSpeaking() { ttsEngine.stop() }
    fun resetState() {
        if (_state.value is VoiceState.Result || _state.value is VoiceState.Error) _state.value = VoiceState.Idle
    }
    fun release() {
        wakeWordMode = false
        wakeLoopJob?.cancel()
        sttEngine.release()
        ttsEngine.release()
        scope.cancel()
    }
}