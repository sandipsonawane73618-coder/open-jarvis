package com.openjarvis.voice

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class AndroidTTSEngine(private val context: Context) {
    companion object {
        fun preview(context: Context, gender: String, localeTag: String, text: String, onUnavailable: () -> Unit = {}) {
            var previewEngine: TextToSpeech? = null
            previewEngine = TextToSpeech(context.applicationContext) { status ->
                val engine = previewEngine
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    engine?.shutdown()
                    onUnavailable()
                    return@TextToSpeech
                }
                val locale = Locale.forLanguageTag(localeTag)
                val voices = engine.voices.orEmpty().filter {
                    it.locale.language == locale.language && it.locale.country.equals("IN", true)
                }
                val preferred = voices.filter { TtsVoiceSelectionPolicy.matchesGender(it.name, gender) }
                val selected = (preferred.ifEmpty { voices })
                    .maxWithOrNull(compareBy<android.speech.tts.Voice> { it.quality }.thenBy { !it.isNetworkConnectionRequired })
                if (selected != null) engine.voice = selected else engine.language = locale
                engine.setSpeechRate(0.92f)
                engine.setPitch(1.0f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { engine.shutdown() }
                    override fun onError(utteranceId: String?) { engine.shutdown() }
                })
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_voice_preview") != TextToSpeech.SUCCESS) {
                    engine.shutdown()
                    onUnavailable()
                }
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var speechRate = 1.05f
    private var pitch = 0.95f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val completionCallbacks = UtteranceCompletionRegistry()
    private val voicePreferences by lazy {
        val key = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context.applicationContext, "voice_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun finishSpeech(utteranceId: String?) {
        val callback = utteranceId?.let(completionCallbacks::take) ?: return
        mainHandler.post(callback)
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.language = Locale.forLanguageTag("hi-IN")
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finishSpeech(utteranceId) }
                    override fun onError(utteranceId: String?) { finishSpeech(utteranceId) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { finishSpeech(utteranceId) }
                })
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) { onDone?.let { mainHandler.post(it) }; return }
        
        val clean = text
            .replace(Regex("\\[\\{.*?\\}\\]"), "")
            .replace(Regex("[\\[\\]{}\":]"), " ")
            .trim()
        
        if (clean.isBlank()) { onDone?.let { mainHandler.post(it) }; return }
        configureVoiceForText(clean)
        
        val utteranceId = "jarvis_${UUID.randomUUID()}"
        onDone?.let { completionCallbacks.register(utteranceId, it) }
        val status = tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            ?: TextToSpeech.ERROR
        if (status != TextToSpeech.SUCCESS) finishSpeech(utteranceId)
    }

    fun speakQueued(text: String) {
        if (!isReady) return
        
        val clean = text
            .replace(Regex("\\[\\{.*?\\}\\]"), "")
            .replace(Regex("[\\[\\]{}\":]"), " ")
            .trim()
        
        if (clean.isBlank()) return
        configureVoiceForText(clean)
        
        val utteranceId = "jarvis_${UUID.randomUUID()}"
        tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun configureVoiceForText(text: String) {
        val language = SpeechLanguage.languageForText(text)
        val locale = Locale.forLanguageTag("$language-IN")
        val engine = tts ?: return
        val voices = engine.voices
            ?.filter { it.locale.language == language && it.locale.country.equals("IN", ignoreCase = true) }
            .orEmpty()
        val preferredGender = voicePreferences.getString(
            "tts_voice_gender_$language",
            voicePreferences.getString("tts_voice_gender", "Female")
        ) ?: "Female"
        val genderMatched = voices.filter { voice ->
            TtsVoiceSelectionPolicy.matchesGender(voice.name, preferredGender)
        }
        // Android does not expose reliable gender metadata. Match descriptive engine voice
        // names when available; otherwise keep the engine's highest-quality locale voice.
        val bestVoice = (genderMatched.ifEmpty { voices })
            .maxWithOrNull(compareBy<android.speech.tts.Voice> { it.quality }.thenBy { !it.isNetworkConnectionRequired })
        if (bestVoice != null) {
            engine.voice = bestVoice
        } else {
            engine.language = locale
        }
    }
    fun stop() {
        tts?.stop()
        completionCallbacks.drain().forEach { callback -> mainHandler.post(callback) }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    fun setPitch(pitchValue: Float) {
        pitch = pitchValue.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
    }

    fun getSpeechRate(): Float = speechRate
    fun getPitch(): Float = pitch

    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    fun release() {
        tts?.stop()
        completionCallbacks.clear()
        tts?.shutdown()
        tts = null
    }
}

/** Best-effort name matching only: Android TTS does not expose reliable voice gender metadata. */
internal object TtsVoiceSelectionPolicy {
    private val femaleTokens = listOf("female", "woman", "zira", "samantha", "aria", "jenny", "susan", "heera", "lekha")
    private val maleTokens = listOf("male", "man", "david", "mark", "guy", "ravi", "hemant", "madhur")

    fun matchesGender(voiceName: String, gender: String): Boolean {
        val nameTokens = voiceName.lowercase().split(Regex("[^a-z0-9]+"))
        val expected = if (gender.equals("Male", ignoreCase = true)) maleTokens else femaleTokens
        return nameTokens.any { it in expected }
    }
}
