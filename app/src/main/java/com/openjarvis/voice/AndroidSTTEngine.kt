package com.openjarvis.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class AndroidSTTEngine(private val context: Context) : STTEngine {
    companion object {
        const val SAMPLE_RATE = 16000
        const val SILENCE_THRESHOLD = 500
        const val SILENCE_TIMEOUT_MS = 1500L
        const val MAX_RECORDING_MS = 30000L
        private const val TAG = "AndroidSTTEngine"
    }

    private var recognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private val _isListening = MutableStateFlow(false)
    val isListeningState: StateFlow<Boolean> = _isListening
    private var currentCallback: ((String?) -> Unit)? = null
    private var currentErrorCallback: ((String) -> Unit)? = null
    private var pendingResult: String? = null
    // Partial hypotheses are diagnostic only; they are never executed as commands.
    @Volatile private var latestPartialTranscript: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionActive = false
    private var silenceStartTime = -1L
    private var recordingStartTime = -1L
    private var localeAttempts: List<String> = emptyList()
    private var localeAttemptIndex = 0
    private var activeLocale = "hi-IN"

    init {
        mainHandler.post { createRecognizer() }
    }

    private fun createRecognizer() {
        // Strictly on-device recognition: never create the system/default recognizer,
        // because that service may route audio to a network-backed provider.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            recognizer = null
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { speechRecognizer ->
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (rmsdB * 100 < SILENCE_THRESHOLD) {
                        if (silenceStartTime < 0) silenceStartTime = System.currentTimeMillis()
                        if (System.currentTimeMillis() - silenceStartTime >= SILENCE_TIMEOUT_MS && audioRecord != null) {
                            stopListeningEarly()
                        }
                    } else silenceStartTime = -1
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }
                override fun onError(error: Int) {
                    _isListening.value = false
                    if (RecognitionLocaleRetryPolicy.shouldRetry(error)) {
                        val failedLocale = activeLocale
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            SpeechLocaleSupportManager(context).checkRecognitionSupport(failedLocale) { }
                        }
                        val nextIndex = localeAttemptIndex + 1
                        if (nextIndex < localeAttempts.size) {
                            localeAttemptIndex = nextIndex
                            mainHandler.post {
                                if (sessionActive) {
                                    createRecognizer()
                                    startListeningInternal()
                                }
                            }
                            return
                        }
                        sessionActive = false
                        latestPartialTranscript = null
                        currentErrorCallback?.invoke(
                            "Offline speech language packs are unavailable for Hindi, English, Marathi, and the device language. Open Settings > Voice > Offline speech languages to check or download a pack. No network speech fallback is used."
                        )
                        return
                    }
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network speech is disabled; check the offline speech service."
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network speech is disabled; check the offline speech service."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "On-device speech service error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Speech recognition error ($error)"
                    }
                    sessionActive = false
                    latestPartialTranscript = null
                    currentErrorCallback?.invoke(message)
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val text = RecognitionCandidateSelector.select(matches, confidenceScores)
                    val candidateCount = matches?.size ?: 0
                    val nonBlankCandidateCount = matches?.count { it.isNotBlank() } ?: 0
                    val bestPositiveScore = confidenceScores
                        ?.filter { it.isFinite() && it > 0f }
                        ?.maxOrNull()
                    val scoreSummary = confidenceScores?.joinToString(
                        prefix = "[", postfix = "]"
                    ) { score -> if (score.isFinite()) score.toString() else "non_finite" } ?: "unavailable"
                    val rejectReason = when {
                        text != null -> "none"
                        nonBlankCandidateCount == 0 -> "no_nonblank_candidate"
                        bestPositiveScore != null && bestPositiveScore < RecognitionCandidateSelector.MIN_CONFIDENCE ->
                            "positive_score_below_threshold"
                        else -> "no_usable_candidate"
                    }
                    // Never include recognized text in diagnostics; scores and counts are sufficient.
                    android.util.Log.i(
                        TAG,
                        "stt_result candidate_count=$candidateCount nonblank_count=$nonBlankCandidateCount " +
                            "scores=$scoreSummary best_positive_score=$bestPositiveScore " +
                            "threshold=${RecognitionCandidateSelector.MIN_CONFIDENCE} reject_reason=$rejectReason"
                    )
                    val hadPartialHypothesis = !latestPartialTranscript.isNullOrBlank()
                    latestPartialTranscript = null
                    pendingResult = text
                    sessionActive = false
                    if (!text.isNullOrBlank()) {
                        currentCallback?.invoke(text)
                    } else if (matches?.any { it.isNotBlank() } == true) {
                        currentErrorCallback?.invoke("LOW_CONFIDENCE: transcript confidence was below the safe threshold")
                    } else {
                        val reason = if (hadPartialHypothesis) {
                            "Speech was partially recognized, but no final transcript was returned. Please repeat."
                        } else {
                            "No speech detected"
                        }
                        currentErrorCallback?.invoke(reason)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidenceScores = partialResults?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    // Empty/blank partial bundles must not erase a previous useful hypothesis.
                    RecognitionCandidateSelector.select(matches, confidenceScores)
                        ?.let { latestPartialTranscript = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    override fun startListening(onResult: (String?) -> Unit, onError: (String) -> Unit) {
        currentCallback = onResult
        currentErrorCallback = onError
        pendingResult = null
        latestPartialTranscript = null
        sessionActive = true
        localeAttempts = RecognitionLocaleRetryPolicy.localeAttempts(Locale.getDefault().toLanguageTag())
        localeAttemptIndex = 0
        mainHandler.post {
            if (recognizer == null) createRecognizer()
            if (recognizer == null) {
                sessionActive = false
                _isListening.value = false
                currentErrorCallback?.invoke(
                    "On-device speech recognition is unavailable on this device. Enable an offline speech service; no cloud fallback is used."
                )
                return@post
            }
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!sessionActive || recognizer == null) return
        silenceStartTime = -1L
        activeLocale = localeAttempts.getOrNull(localeAttemptIndex) ?: "hi-IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLocale)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES",
                    ArrayList(SpeechLanguage.recognitionLocales))
            }
            // Bias common JARVIS vocabulary so app names and everyday Hinglish
            // phrases are less likely to be replaced by phonetically similar words.
            putStringArrayListExtra(
                "android.speech.extra.BIASING_STRINGS",
                arrayListOf(
                    "Jarvis", "JARVIS", "WhatsApp", "YouTube", "Instagram",
                    "time kya hua hai", "abhi kitne baje hain", "aaj ka samay kya hai",
                    "समय क्या हुआ है", "अभी कितने बजे हैं",
                    "आत्ता किती वाजले", "सध्या किती वाजले",
                    "खोलो", "बंद करो", "कॉल करो", "टॉर्च चालू करो",
                    "screen ka photo lo", "screen photo lo", "screen shot lo", "take screenshot",
                    "capture screen", "स्क्रीन का फोटो लो", "स्क्रीनशॉट लो", "स्क्रीनचा फोटो काढ",
                    "पडद्याचा फोटो काढा", "read screen", "screen par kya hai"
                ).apply {
                    addAll(VoiceControlVocabulary.recognitionPhrases)
                    addAll(MaharashtraSpeechProfile.recognitionHints)
                }
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
        }
        try {
            recognizer?.startListening(intent)
        } catch (error: IllegalStateException) {
            sessionActive = false
            _isListening.value = false
            currentErrorCallback?.invoke("Speech recognizer is busy; retrying automatically")
        }
    }

    private fun stopListeningEarly() {
        recognizer?.stopListening()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun stop(): String? {
        sessionActive = false
        _isListening.value = false
        mainHandler.post { recognizer?.cancel() }
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        val result = pendingResult
        pendingResult = null
        latestPartialTranscript = null
        currentCallback = null
        currentErrorCallback = null
        return result
    }

    override fun isListening(): Boolean = _isListening.value

    override fun release() {
        stop()
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun startAudioCapture(): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize * 4)
            audioRecord?.startRecording()
            recordingStartTime = System.currentTimeMillis()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    suspend fun captureWithVAD(): FloatArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val shortBuffer = ShortArray(bufferSize / 2)
        val allSamples = mutableListOf<Short>()
        while (_isListening.value) {
            if (System.currentTimeMillis() - recordingStartTime >= MAX_RECORDING_MS) break
            val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: break
            if (read <= 0) continue
            allSamples.addAll(shortBuffer.take(read))
            val amplitude = shortBuffer.take(read).maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
            if (amplitude < SILENCE_THRESHOLD) {
                if (silenceStartTime < 0) silenceStartTime = System.currentTimeMillis()
                if (System.currentTimeMillis() - silenceStartTime >= SILENCE_TIMEOUT_MS) {
                    stop()
                    break
                }
            } else silenceStartTime = -1
        }
        allSamples.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
    }
}