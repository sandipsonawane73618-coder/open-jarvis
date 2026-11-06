package com.openjarvis.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.voice.VoiceManager
import com.openjarvis.voice.ActivationGate
import com.openjarvis.voice.PorcupineWakeWordController
import com.openjarvis.voice.WakeWordEngine
import com.openjarvis.voice.SherpaOnnxWakeWordController
import com.openjarvis.voice.WakeWordConfig
import com.openjarvis.voice.WakeWordSessionStateMachine
import com.openjarvis.voice.WakeWordFallbackPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var commandInput: EditText? = null
    private var statusText: TextView? = null
    private var waveformText: TextView? = null
    private var visualState: String = "IDLE"
    private var waveformFrame: Int = 0
    private val waveformRunnable = object : Runnable {
        override fun run() {
            val view = waveformText ?: return
            if (visualState == "IDLE") return
            val frames = arrayOf("▁ ▃ ▆ ▄ ▂", "▃ ▆ ▄ ▂ ▅", "▆ ▄ ▂ ▅ ▃", "▄ ▂ ▅ ▃ ▆")
            view.text = frames[waveformFrame++ % frames.size]
            mainHandler.postDelayed(this, if (visualState == "THINKING") 420L else 150L)
        }
    }
    private var panel: LinearLayout? = null
    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private var wakeWordController: WakeWordEngine? = null
    private var wakeWordControllerKey: String? = null
    private var wakeWordCpuLock: PowerManager.WakeLock? = null
    private var wakeWordRestartAttempt = 0
    private var wakeWordRestartRunnable: Runnable? = null
    private var wakeWordFeedbackActive = false
    private var wakeWordListeningStartedAtMs = 0L
    private var wakeWordFallbackWarningShown = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activationGate = ActivationGate(windowMs = 10_000L, duplicateCooldownMs = 2_500L)
    private val wakeWordSession = WakeWordSessionStateMachine(WAKE_WORD_STT_WATCHDOG_MS)
    private val wakeWordCommandPending get() = wakeWordSession.commandPending
    private val wakeWordResponsePending get() = wakeWordSession.responsePending
    private val wakeWordTranscriptReceived get() = wakeWordSession.transcriptReceived
    private val wakeWordWatchdog = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            wakeWordSession.onWatchdog(now, wakeWordController) {
                android.util.Log.w("OpenJarvisWakeWord", "STT watchdog expired; clearing pending wake-word state")
                activationGate.reset()
                voiceManager.stopListening()
                statusText?.text = "Voice capture timed out — wake word restarted"
                serviceScope.launch {
                    delay(350)
                    startWakeWordListening()
                }
            }
            checkWakeWordRecognitionTimeout(now)
            mainHandler.postDelayed(this, WAKE_WORD_WATCHDOG_TICK_MS)
        }
    }
    private var recentTasks = emptyList<TaskNode>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isExpanded = false
    private var responseLanguage = "hi"
    @Volatile private var mutedUntilWakeWord = false
    @Volatile private var awaitingUnmuteCommand = false

    override fun onCreate() {
        super.onCreate()
        agentCore = AgentCore(this)
        voiceManager = VoiceManager(this)
        graphifyRepo = GraphifyRepository(this)
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 34) {
            val foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (microphoneGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), foregroundTypes)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        attachAssistant()
        mainHandler.postDelayed(wakeWordWatchdog, WAKE_WORD_WATCHDOG_TICK_MS)
        serviceScope.launch {
            voiceManager.state.collect { state ->
                when (state) {
                    is VoiceManager.VoiceState.Result -> {
                        val hadPendingTranscript = wakeWordSession.commandPending
                        wakeWordFeedbackActive = false
                        wakeWordSession.onTranscriptReceived()
                        if (awaitingUnmuteCommand) {
                            voiceManager.stopListening()
                            if (MuteCommandPolicy.isUnmuteCommand(state.text)) {
                                awaitingUnmuteCommand = false
                                mutedUntilWakeWord = false
                                statusText?.text = "JARVIS active"
                                voiceManager.speak("Jarvis is active. I am listening.") { resumeWakeWordAfterResponse() }
                            } else {
                                statusText?.text = "Still muted — say Jarvis, then say unmute ho ja"
                                wakeWordSession.clearPending()
                                startWakeWordListening()
                            }
                            return@collect
                        }
                        if (MuteCommandPolicy.isUnmuteCommand(state.text)) {
                            voiceManager.stopListening()
                            mutedUntilWakeWord = false
                            statusText?.text = "JARVIS active"
                            voiceManager.speak("Jarvis is active. I am listening.") { resumeWakeWordAfterResponse() }
                            return@collect
                        }
                        if (MuteCommandPolicy.isMuteCommand(state.text)) {
                            enterMuteMode()
                            return@collect
                        }
                        if (hadPendingTranscript) {
                            voiceManager.stopListening()
                        }
                        commandInput?.setText(state.text)
                        statusText?.text = "Voice captured — executing command…"
                        submitCommand(state.text)
                    }
                    is VoiceManager.VoiceState.Error -> {
                        wakeWordFeedbackActive = false
                        statusText?.text = state.message
                        if (wakeWordCommandPending || wakeWordResponsePending) {
                            clearWakeWordPendingState()
                            activationGate.reset()
                            voiceManager.stopListening()
                            serviceScope.launch { delay(700); startWakeWordListening() }
                        }
                    }
                    is VoiceManager.VoiceState.Recording -> setVisualState(
                        "LISTENING",
                        if (wakeWordFeedbackActive) "Jarvis ✓ — listening… speak now" else "Listening… speak now"
                    )
                    is VoiceManager.VoiceState.Transcribing -> setVisualState("THINKING", "Thinking…")
                    is VoiceManager.VoiceState.Speaking -> setVisualState("SPEAKING", "Speaking…")
                    is VoiceManager.VoiceState.Idle -> {
                        // Always clear both flags on Idle. If STT ended without a
                        // transcript, re-arm immediately; successful transcripts resume
                        // after the agent response/TTS callback.
                        wakeWordSession.onIdle(wakeWordController) {
                            activationGate.reset()
                            serviceScope.launch { delay(350); startWakeWordListening() }
                        }
                        setVisualState("IDLE", "Ready — type a command or use mic")
                    }
                }
            }
        }
        serviceScope.launch {
            agentCore.state.collect { state ->
                when (state) {
                    is com.openjarvis.agent.AgentState.Idle -> statusText?.text = "Ready — type a command or use mic"
                    is com.openjarvis.agent.AgentState.Running -> setVisualState("THINKING", state.step)
                    is com.openjarvis.agent.AgentState.Done -> {
                        setVisualState(if (mutedUntilWakeWord) "IDLE" else "SPEAKING", if (mutedUntilWakeWord) "MUTED — say Jarvis to wake" else state.result)
                        if (!mutedUntilWakeWord) voiceManager.speak(com.openjarvis.agent.AgentResultSpeech.textForDone(state.result)) { resumeWakeWordAfterResponse() }
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        setVisualState(if (mutedUntilWakeWord) "IDLE" else "SPEAKING", if (mutedUntilWakeWord) "MUTED — say Jarvis to wake" else "Jarvis error: ${state.message} • tap to copy debug log")
                        if (!mutedUntilWakeWord) voiceManager.speak(if (responseLanguage == "mr") "कमांड पूर्ण झाली नाही. ${state.message}" else "कमांड पूरी नहीं हुई। ${state.message}") { resumeWakeWordAfterResponse() }
                    }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) { recentTasks = graphifyRepo.getRecentTasks(10) }
        if (microphoneGranted) startWakeWordListening()
        else statusText?.text = "Allow microphone permission to enable Jarvis"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SIMULATE_WAKE_WORD) {
            val transcript = intent.getStringExtra(EXTRA_SIMULATED_TRANSCRIPT).orEmpty()
            mainHandler.post { runSimulatedWakeWordFlow(transcript) }
            return START_STICKY
        }
        if (intent?.action == ACTION_RESTART_WAKE_WORD) {
            mainHandler.post {
                stopWakeWordListening()
                startWakeWordListening()
            }
            return START_STICKY
        }
        // Re-check the saved AccessKey whenever the app asks the existing service to start.
        // This also refreshes wake-word setup after the user edits the key in Settings.
        if (::voiceManager.isInitialized) startWakeWordListening()
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        overlayView = null
        visualState = "IDLE"
        mainHandler.removeCallbacks(waveformRunnable)
        mainHandler.removeCallbacks(wakeWordWatchdog)
        wakeWordRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        wakeWordRestartRunnable = null
        waveformText = null
        clearWakeWordPendingState()
        stopWakeWordListening()
        voiceManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun attachAssistant() {
        if (!Settings.canDrawOverlays(this) || overlayView != null) return
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                minimumWidth = dp(64)
                minimumHeight = dp(48)
                background = rounded(Color.rgb(20, 18, 35), 24)
                elevation = 18f
            }
            val pill = TextView(this).apply {
                text = "● JARVIS"
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(14), dp(22), dp(14))
                background = rounded(Color.rgb(55, 35, 95), 40)
            }
            var downRawX = 0f
            var downRawY = 0f
            var downWindowX = 0
            var downWindowY = 0
            var dragged = false
            pill.setOnTouchListener { _, event ->
                val params = windowParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        downWindowX = params?.x ?: 0
                        downWindowY = params?.y ?: 0
                        dragged = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) dragged = true
                        if (dragged && params != null) {
                            params.x = (downWindowX - dx.toInt()).coerceAtLeast(0)
                            params.y = (downWindowY + dy.toInt()).coerceAtLeast(0)
                            runCatching { windowManager?.updateViewLayout(overlayView, params) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) togglePanel()
                        true
                    }
                    else -> true
                }
            }
            root.addView(pill, LinearLayout.LayoutParams(-2, -2))

            val commandPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = android.view.View.GONE
                setPadding(dp(4), dp(8), dp(4), dp(4))
            }
            val status = TextView(this).apply {
                text = "Ready — type a command or use mic"
                textSize = 12f
                setTextColor(Color.LTGRAY)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            statusText = status
            status.setOnClickListener { copyLatestDebugLog() }
            commandPanel.addView(status)
            val waveform = TextView(this).apply {
                text = "▁ ▃ ▆ ▄ ▂"
                textSize = 19f
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(70, 220, 255))
                setPadding(dp(8), 0, dp(8), dp(2))
                visibility = android.view.View.GONE
                contentDescription = "JARVIS voice activity waveform"
            }
            waveformText = waveform
            commandPanel.addView(waveform, LinearLayout.LayoutParams(-1, dp(30)))

            val input = EditText(this).apply {
                hint = "Ask Jarvis to do something…"
                textSize = 14f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setSingleLine(false)
                minLines = 1
                maxLines = 2
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = EditorInfo.IME_ACTION_SEND
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(Color.rgb(42, 38, 58), 12)
            }
            commandInput = input

            // Keep the text field and both actions in one weighted row. Fixed action
            // widths prevent clipping on compact (~360dp) displays.
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
            }
            actions.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
            val mic = Button(this).apply {
                text = "Mic"
                isAllCaps = false
                minWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this@OverlayService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        status.text = "Allow microphone permission in Android app settings"
                    } else if (!voiceManager.isVoiceEnabled()) {
                        status.text = "Enable Voice Assistant in Jarvis Settings first"
                    } else {
                        if (wakeWordController?.isRunning() == true) stopWakeWordListening()
                        // A manual mic tap is an explicit activation in both STT modes.
                        activationGate.activate(ActivationGate.Source.PUSH_TO_TALK)
                        wakeWordFeedbackActive = false
                        markWakeWordCommandPending()
                        voiceManager.startListening()
                    }
                }
            }
            val send = Button(this).apply {
                text = "Send"
                isAllCaps = false
                minWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener { submitCommand() }
            }
            val actionWidth = dp(WakeWordConfig.ACTION_BUTTON_WIDTH_DP)
            actions.addView(mic, LinearLayout.LayoutParams(actionWidth, dp(48)))
            actions.addView(send, LinearLayout.LayoutParams(actionWidth, dp(48)))
            commandPanel.addView(actions, LinearLayout.LayoutParams(-1, -2))
            root.addView(commandPanel)
            panel = commandPanel
            isExpanded = false

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 220
            }
            windowParams = params
            windowManager?.addView(root, params)
            overlayView = root
        } catch (e: Exception) {
            android.util.Log.e("OpenJarvis", "Unable to attach assistant overlay", e)
        }
    }

    private fun setVisualState(state: String, label: String) {
        visualState = state
        statusText?.text = label
        waveformText?.visibility = if (state == "IDLE") android.view.View.GONE else android.view.View.VISIBLE
        mainHandler.removeCallbacks(waveformRunnable)
        if (state != "IDLE") {
            waveformFrame = 0
            waveformRunnable.run()
        }
    }

    private fun togglePanel() {
        isExpanded = !isExpanded
        panel?.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
        windowParams?.let { params ->
            params.width = if (isExpanded) dp(304) else WindowManager.LayoutParams.WRAP_CONTENT
            // The overlay must never be touch-modal: taps outside the floating panel
            // must continue to reach the app underneath, even while the input panel is open.
            // Keep the expanded panel focusable so its EditText can receive keyboard input.
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (isExpanded) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) { }
        }
        if (isExpanded) commandInput?.requestFocus()
    }

    private fun enterMuteMode() {
        mutedUntilWakeWord = true
        awaitingUnmuteCommand = false
        clearWakeWordPendingState()
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        statusText?.text = "MUTED — say Jarvis to wake"
        startWakeWordListening()
    }

    private fun submitCommand(voiceCommand: String? = null) {
        val command = voiceCommand?.trim().takeUnless { it.isNullOrBlank() }
            ?: commandInput?.text?.toString()?.trim().orEmpty()
        // Typed commands are explicit UI input and intentionally bypass the voice gate.
        // Voice transcripts must have a fresh wake-word or manual-mic activation token.
        if (voiceCommand != null) {
            val decision = activationGate.validateTranscript(command, System.currentTimeMillis())
            if (!decision.accepted) {
                statusText?.text = "Voice ignored: ${decision.reason}"
                resumeWakeWordAfterResponse()
                return
            }
        }
        if (command.isBlank()) {
            statusText?.text = "Type a command first"
            return
        }
        if (command.any { it in '\u0900'..'\u097F' }) {
            val marathiMarkers = listOf("आहे", "आणि", "मला", "काय", "झाले", "झालं", "नाही", "दाखवा", "उघडा", "करायचे", "माझे", "तुम्ही", "सांगा", "नको")
            responseLanguage = if (marathiMarkers.any { command.contains(it) }) "mr" else "hi"
        }
        if (com.openjarvis.accessibility.JarvisAccessibilityService.instance == null) {
            statusText?.text = "Accessibility service is OFF. Enable Open Jarvis in Android Accessibility settings."
            if (voiceCommand != null) resumeWakeWordAfterResponse()
            return
        }
        if (wakeWordController?.isRunning() == true) {
            stopWakeWordListening()
            wakeWordSession.markResponsePending()
        }
        statusText?.text = "Jarvis is working…"
        voiceManager.markThinking()
        agentCore.executeTask(command)
        commandInput?.text?.clear()
    }

    private fun startWakeWordListening() {
        if (!voiceManager.isVoiceEnabled()) {
            stopWakeWordListening()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopWakeWordListening()
            statusText?.text = "Microphone permission is required for Jarvis"
            return
        }
        val accessKey = voiceManager.getPicovoiceAccessKey()
        if (Build.VERSION.SDK_INT >= 34) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), types)
        }

        // Key-free sherpa-onnx is primary. Porcupine remains an optional fallback
        // until the new engine passes the user's phone gate.
        if (wakeWordController is PorcupineWakeWordController && wakeWordControllerKey != accessKey) {
            stopWakeWordListening()
            wakeWordController = null
            wakeWordControllerKey = null
        }
        if (wakeWordController == null) {
            wakeWordController = SherpaOnnxWakeWordController(
                this,
                onWakeWord = { mainHandler.post { handleWakeWordDetected() } },
                onError = { message -> mainHandler.post { scheduleWakeWordRestart(message) } }
            )
        }
        val wasAlreadyRunning = wakeWordController?.isRunning() == true
        var error = wakeWordController?.start()
        if (error != null && accessKey.isNotBlank() && wakeWordController !is PorcupineWakeWordController) {
            stopWakeWordListening()
            wakeWordController = PorcupineWakeWordController(
                this,
                accessKey,
                onWakeWord = { mainHandler.post { handleWakeWordDetected() } },
                onError = { message -> mainHandler.post { scheduleWakeWordRestart(message) } }
            )
            wakeWordControllerKey = accessKey
            error = wakeWordController?.start()
        }
        if (error == null) {
            wakeWordRestartAttempt = 0
            if (wakeWordController is SherpaOnnxWakeWordController) {
                if (!wasAlreadyRunning || wakeWordListeningStartedAtMs <= 0L) {
                    wakeWordListeningStartedAtMs = System.currentTimeMillis()
                    wakeWordFallbackWarningShown = false
                }
            } else {
                wakeWordListeningStartedAtMs = 0L
            }
            wakeWordRestartRunnable?.let { mainHandler.removeCallbacks(it) }
            wakeWordRestartRunnable = null
            acquireWakeWordCpuLock()
        } else {
            releaseWakeWordCpuLock()
            scheduleWakeWordRestart(error)
        }
        statusText?.text = error ?: if (mutedUntilWakeWord) {
            "MUTED — say Jarvis to wake"
        } else if (wakeWordController is SherpaOnnxWakeWordController) {
            "Standby — say Jarvis (offline, no key)"
        } else {
            "Standby — say Jarvis (Porcupine fallback)"
        }
    }

    /** Switch only after sustained non-silent speech has gone unrecognized. */
    private fun checkWakeWordRecognitionTimeout(nowMs: Long) {
        if (wakeWordController !is SherpaOnnxWakeWordController || wakeWordController?.isRunning() != true) return
        val accessKey = voiceManager.getPicovoiceAccessKey()
        val diagnostics = com.openjarvis.voice.WakeWordDiagnostics.snapshot()
        val decision = WakeWordFallbackPolicy.evaluate(
            nowMs = nowMs,
            startedAtMs = wakeWordListeningStartedAtMs,
            lastDetectionAtMs = diagnostics.lastDetectionAtMs,
            framesProcessed = diagnostics.framesProcessed,
            silentFrames = diagnostics.silentFrames,
            hasValidAccessKey = WakeWordConfig.validateAccessKey(accessKey) == null
        )
        when (decision) {
            WakeWordFallbackPolicy.Decision.WAIT -> Unit
            WakeWordFallbackPolicy.Decision.FALLBACK_TO_PORCUPINE -> {
                android.util.Log.w("OpenJarvisWakeWord", "Sherpa recognition timeout; switching to Porcupine fallback")
                stopWakeWordListening()
                wakeWordController = PorcupineWakeWordController(
                    this,
                    accessKey,
                    onWakeWord = { mainHandler.post { handleWakeWordDetected() } },
                    onError = { message -> mainHandler.post { scheduleWakeWordRestart(message) } }
                )
                wakeWordControllerKey = accessKey
                val error = wakeWordController?.start()
                if (error == null) {
                    wakeWordListeningStartedAtMs = 0L
                    wakeWordRestartAttempt = 0
                    acquireWakeWordCpuLock()
                    statusText?.text = "Standby — say Jarvis (Porcupine fallback)"
                } else {
                    scheduleWakeWordRestart("Sherpa recognition timeout; Porcupine fallback failed: $error")
                }
            }
            WakeWordFallbackPolicy.Decision.NO_FALLBACK_KEY -> {
                if (!wakeWordFallbackWarningShown) {
                    wakeWordFallbackWarningShown = true
                    android.util.Log.w("OpenJarvisWakeWord", "Sherpa has active audio but no keyword detection; no Picovoice AccessKey for fallback")
                    statusText?.text = "Sherpa is hearing audio but not detecting Jarvis. Add a Picovoice AccessKey for automatic fallback."
                }
            }
        }
    }

    private fun provideWakeWordFeedback() {
        runCatching {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(55L)
            }
        }
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            mainHandler.postDelayed({ tone.release() }, 180L)
        }
    }

    private fun runSimulatedWakeWordFlow(transcript: String) {
        if (transcript.isBlank()) {
            statusText?.text = "Simulation needs a recognized command"
            return
        }
        if (!voiceManager.isVoiceEnabled()) {
            statusText?.text = "Enable Voice Assistant before running the wake-word simulation"
            return
        }
        activationGate.activate(ActivationGate.Source.WAKE_WORD)
        statusText?.text = "Simulated Jarvis ✓ — injecting STT transcript"
        submitCommand(transcript)
    }

    private fun handleWakeWordDetected() {
        if (!voiceManager.isVoiceEnabled() || wakeWordCommandPending) return
        val wasMuted = mutedUntilWakeWord
        if (wasMuted) awaitingUnmuteCommand = true
        stopWakeWordListening()
        activationGate.activate(ActivationGate.Source.WAKE_WORD)
        markWakeWordCommandPending()
        wakeWordFeedbackActive = true
        provideWakeWordFeedback()
        statusText?.text = if (wasMuted) {
            "Jarvis ✓ — say unmute ho ja"
        } else {
            "Jarvis ✓ — listening for your command…"
        }
        voiceManager.startListening()
    }

    private fun scheduleWakeWordRestart(message: String) {
        if (!::voiceManager.isInitialized || !voiceManager.isVoiceEnabled() || wakeWordCommandPending) {
            statusText?.text = "Wake-word unavailable: $message"
            return
        }
        stopWakeWordListening(cancelRestart = false)
        wakeWordRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        val delays = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
        val retryDelay = delays[wakeWordRestartAttempt.coerceIn(0, delays.lastIndex)]
        wakeWordRestartAttempt = (wakeWordRestartAttempt + 1).coerceAtMost(delays.lastIndex)
        statusText?.text = "Mic/wake-word error: $message — retry in ${retryDelay / 1_000}s"
        val retry = Runnable {
            wakeWordRestartRunnable = null
            if (voiceManager.isVoiceEnabled() && !wakeWordCommandPending) startWakeWordListening()
        }
        wakeWordRestartRunnable = retry
        mainHandler.postDelayed(retry, retryDelay)
    }

    private fun markWakeWordCommandPending() {
        wakeWordSession.beginCommand()
    }

    private fun clearWakeWordPendingState() {
        wakeWordSession.reset()
    }

    private fun stopWakeWordListening(cancelRestart: Boolean = true) {
        if (cancelRestart) {
            wakeWordRestartRunnable?.let { mainHandler.removeCallbacks(it) }
            wakeWordRestartRunnable = null
        }
        wakeWordController?.stop()
        releaseWakeWordCpuLock()
    }

    /** Keep the CPU alive while the foreground service is actively listening with screen off. */
    private fun acquireWakeWordCpuLock() {
        if (wakeWordCpuLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = wakeWordCpuLock ?: powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenJarvis:WakeWord"
        ).apply { setReferenceCounted(false) }.also { wakeWordCpuLock = it }
        if (!lock.isHeld) lock.acquire()
    }

    private fun releaseWakeWordCpuLock() {
        val lock = wakeWordCpuLock ?: return
        if (lock.isHeld) lock.release()
    }

    private fun resumeWakeWordAfterResponse() {
        clearWakeWordPendingState()
        wakeWordSession.finishResponse()
        if (!voiceManager.isVoiceEnabled()) return
        serviceScope.launch {
            delay(500)
            startWakeWordListening()
        }
    }

    private fun copyLatestDebugLog() {
        val baseReport = com.openjarvis.agent.AgentDiagnostics.latest(this)
        val wake = com.openjarvis.voice.WakeWordDiagnostics.snapshot()
        val detectionAt = wake.lastDetectionAtMs?.let {
            android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", java.util.Date(it)).toString()
        } ?: "never"
        val wakeReport = buildString {
            appendLine("Wake-word diagnostics:")
            appendLine("engine=${wake.engineName}, state=${wake.state}, micPermission=${wake.micPermissionGranted}")
            appendLine("framesProcessed=${wake.framesProcessed}, liveRms=${"%.4f".format(wake.rms)}, peakLevel=${"%.4f".format(wake.peakLevel)}")
            appendLine("silentFrames=${wake.silentFrames}, clippedSamples=${wake.clippedSamples}")
            appendLine("audioSource=${wake.audioSource}, sampleRate=${wake.sampleRate}")
            appendLine("AGC available=${wake.agcAvailable}, enabled=${wake.agcEnabled}; NoiseSuppressor available=${wake.noiseSuppressorAvailable}, enabled=${wake.noiseSuppressorEnabled}")
            appendLine("lastDetectionAt=$detectionAt, keyword=${wake.lastDetectionKeyword ?: "none"}, score=${wake.lastDetectionScore?.toString() ?: "unavailable"}")
            appendLine("keywords=${wake.keywords.joinToString()}, threshold=${wake.threshold}, keywordBoost=${wake.keywordBoost}")
            appendLine("lastError=${wake.lastError ?: "none"}")
        }
        val report = "$baseReport\n\n$wakeReport"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS Debug Log", report))
        Toast.makeText(this, "Debug log copied. Paste it here in ChatGPT.", Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        setStroke(1, Color.rgb(125, 95, 190))
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Open Jarvis")
            .setContentText("Assistant is running — tap the floating Jarvis button")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun getAgentCore() = agentCore
    fun getVoiceManager() = voiceManager
    fun getRecentTasks() = recentTasks

    fun executeCommand(command: String) = agentCore.executeTask(command)

    companion object {
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val ACTION_SIMULATE_WAKE_WORD = "com.openjarvis.action.SIMULATE_WAKE_WORD"
        private const val ACTION_RESTART_WAKE_WORD = "com.openjarvis.action.RESTART_WAKE_WORD"
        private const val EXTRA_SIMULATED_TRANSCRIPT = "simulated_transcript"

        fun restartWakeWord(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_RESTART_WAKE_WORD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun simulateWakeWordDetection(context: Context, transcript: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SIMULATE_WAKE_WORD
                putExtra(EXTRA_SIMULATED_TRANSCRIPT, transcript)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
        private const val NOTIFICATION_ID = 1
        private const val WAKE_WORD_STT_WATCHDOG_MS = 15_000L
        private const val WAKE_WORD_WATCHDOG_TICK_MS = 1_000L
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
