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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.agent.UnknownCommandPolicy
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.bridge.SocketServer
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.voice.VoiceManager
import com.openjarvis.commandlab.CommandLogStore
import com.openjarvis.commandlab.CommandLabRouterProbe
import com.openjarvis.voice.VoiceSessionState
import com.openjarvis.ui.home.VoiceUiBridge
import com.openjarvis.ui.overlay.FloatingPanelMode
import com.openjarvis.ui.overlay.FloatingVoiceState
import com.openjarvis.ui.overlay.mapFloatingViewStyle
import com.openjarvis.ui.overlay.snapFloatingBubblePosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.cos
import android.view.animation.OvershootInterpolator

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var commandInput: EditText? = null
    private var statusText: TextView? = null
    private var waveformText: View? = null
    private var orbView: TextView? = null
    private var voiceStateLabelText: TextView? = null
    private var micActionButton: TextView? = null
    private var visualState: String = "IDLE"
    private var waveformFrame: Int = 0
    private val waveformRunnable = object : Runnable {
        override fun run() {
            val view = waveformText ?: return
            if (visualState == "IDLE") return
            view.invalidate()
            waveformFrame++
            mainHandler.postDelayed(this, 33L)
        }
    }
    private var panel: LinearLayout? = null
    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private val commandLogStore by lazy { CommandLogStore(this) }
    @Volatile private var activeCommandLogId: String? = null
    private lateinit var graphifyRepo: GraphifyRepository
    private var socketServer: SocketServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayAttachmentGate = OverlayAttachmentGate()
    private val overlayWatchdog = object : Runnable {
        override fun run() {
            if (overlayView?.isAttachedToWindow != true) attachAssistant()
            mainHandler.postDelayed(this, OVERLAY_WATCHDOG_INTERVAL_MS)
        }
    }
    private var recentTasks = emptyList<TaskNode>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isExpanded = false
    private var responseLanguage = "hi"
    @Volatile private var isMuted = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        agentCore = AgentCore(this)
        voiceManager = VoiceManager(this)
        VoiceUiBridge.registerStopCallback { voiceManager.stopSpeaking() }
        graphifyRepo = GraphifyRepository(this)
        socketServer = SocketServer(this, agentCore, graphifyRepo).also { it.start() }
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 34) {
            val foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (microphoneGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), foregroundTypes)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        attachAssistant()
        mainHandler.postDelayed(overlayWatchdog, OVERLAY_WATCHDOG_INTERVAL_MS)
        serviceScope.launch {
            voiceManager.state.collect { state ->
                when (state) {
                    is VoiceManager.VoiceState.Result -> when (CommandListeningPolicy.classify(state.text, isMuted)) {
                        CommandListeningPolicy.Action.MUTE -> enterMuteMode()
                        CommandListeningPolicy.Action.UNMUTE -> enterUnmuteMode()
                        CommandListeningPolicy.Action.IGNORE -> {
                            statusText?.text = if (isMuted) {
                                "COMMAND MUTED — mic active for Unmute; other commands ignored"
                            } else {
                                "No command detected — listening again"
                            }
                            voiceManager.resumeContinuousSession()
                        }
                        CommandListeningPolicy.Action.EXECUTE_COMMAND -> submitCommand(state.text)
                    }
                    is VoiceManager.VoiceState.LowConfidence -> {
                        setVisualState("IDLE", "Low-confidence transcript ignored")
                        voiceManager.speak(UnknownCommandPolicy.FALLBACK) {
                            voiceManager.resumeContinuousSession()
                        }
                    }
                    is VoiceManager.VoiceState.Error -> {
                        setVisualState("IDLE", state.message)
                        if (!voiceManager.isContinuousSessionActive()) {
                            updateNotification("Microphone off — tap Mic to start")
                        }
                    }
                    is VoiceManager.VoiceState.Recording -> {
                        if (isMuted) setVisualState("IDLE", "COMMAND MUTED — listening only for Unmute")
                        else setVisualState("LISTENING", "Listening continuously… speak now")
                    }
                    is VoiceManager.VoiceState.Transcribing -> setVisualState("THINKING", "Thinking…")
                    is VoiceManager.VoiceState.Speaking -> setVisualState("SPEAKING", "Speaking…")
                    is VoiceManager.VoiceState.Idle -> {
                        // Do not let the voice session's Idle event hide the HUD while
                        // AgentCore is still executing a command.
                        if (agentCore.state.value is com.openjarvis.agent.AgentState.Running) {
                            setVisualState("THINKING", "Jarvis is working…")
                        } else if (!voiceManager.isVoiceEnabled()) {
                            setVisualState("IDLE", "Voice assistant disabled — microphone off")
                        } else if (isMuted) {
                            setVisualState("IDLE", "COMMAND MUTED — mic active for Unmute; other commands ignored")
                        } else if (voiceManager.isContinuousSessionActive()) {
                            setVisualState("IDLE", "Ready — listening continuously")
                        } else {
                            setVisualState("IDLE", "Ready — tap Mic once to start continuous voice")
                        }
                    }
                }
            }
        }
        serviceScope.launch {
            agentCore.state.collect { state ->
                when (state) {
                    is com.openjarvis.agent.AgentState.Idle -> {
                        statusText?.text = when {
                            !voiceManager.isVoiceEnabled() -> "Voice assistant disabled — microphone off"
                            voiceManager.isContinuousSessionActive() && isMuted -> "COMMAND MUTED — mic active for Unmute; other commands ignored"
                            voiceManager.isContinuousSessionActive() -> "Ready — listening continuously"
                            else -> "Ready — tap Mic once to start continuous voice or type a command"
                        }
                    }
                    is com.openjarvis.agent.AgentState.Running -> setVisualState("THINKING", state.step)
                    is com.openjarvis.agent.AgentState.Done -> {
                        commandLogStore.complete(activeCommandLogId, state.result, null)
                        activeCommandLogId = null
                        setVisualState("SPEAKING", state.result)
                        voiceManager.speak(com.openjarvis.agent.AgentResultSpeech.textForDone(state.result)) {
                            voiceManager.resumeContinuousSession()
                        }
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        commandLogStore.complete(activeCommandLogId, null, state.message)
                        activeCommandLogId = null
                        setVisualState("SPEAKING", "Jarvis error: ${state.message} • tap to copy debug log")
                        voiceManager.speak(if (responseLanguage == "mr") "कमांड पूर्ण झाली नाही. ${state.message}" else "कमांड पूरी नहीं हुई। ${state.message}") {
                            voiceManager.resumeContinuousSession()
                        }
                    }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) { recentTasks = graphifyRepo.getRecentTasks(10) }
        if (!microphoneGranted) statusText?.text = "Allow microphone permission, then tap Mic to start"
        else statusText?.text = "Ready — tap Mic once to start continuous voice"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::voiceManager.isInitialized) {
            if (overlayView?.isAttachedToWindow != true) attachAssistant()
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(overlayWatchdog)
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        overlayView = null
        visualState = "IDLE"
        mainHandler.removeCallbacks(waveformRunnable)
        mainHandler.removeCallbacks(orbPulseRunnable)
        orbView = null
        voiceStateLabelText = null
        micActionButton = null
        waveformText = null
        voiceManager.stopListening()
        VoiceUiBridge.registerStopCallback(null)
        VoiceUiBridge.publish(VoiceSessionState.Idle)
        if (activeInstance === this) activeInstance = null
        socketServer?.stop()
        socketServer = null
        voiceManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun attachAssistant() {
        // WindowManager and Android Views must only be touched on the main thread.
        // Multiple start/watchdog callbacks are serialized before they can create a window.
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            mainHandler.post { attachAssistant() }
            return
        }
        if (!Settings.canDrawOverlays(this)) return
        if (!overlayAttachmentGate.begin(overlayView?.isAttachedToWindow == true)) return
        try {
            // A stale detached View must not block recreation of the floating overlay.
            overlayView = null
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val panelBackground = if (isNightMode) Color.argb(238, 17, 20, 34) else Color.argb(246, 247, 251, 255)
            val panelSurface = if (isNightMode) Color.argb(210, 37, 42, 61) else Color.argb(225, 255, 255, 255)
            val panelText = if (isNightMode) Color.WHITE else Color.rgb(24, 29, 44)
            val panelMutedText = if (isNightMode) Color.rgb(190, 199, 218) else Color.rgb(91, 101, 122)
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                elevation = dp(16).toFloat()
                isFocusableInTouchMode = true
            }
            val pill = ArcReactorOrbView(this).apply {
                elevation = dp(14).toFloat()
                contentDescription = "JARVIS floating assistant. Tap to expand, drag to move."
            }
            orbView = pill
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
                        if (dragged && params != null) {
                            val snapped = snapFloatingBubblePosition(
                                screenWidthPx = resources.displayMetrics.widthPixels,
                                screenHeightPx = resources.displayMetrics.heightPixels,
                                bubbleWidthPx = dp(64),
                                bubbleHeightPx = dp(64),
                                marginPx = dp(12),
                                currentXFromEndPx = params.x,
                                currentTopPx = params.y
                            )
                            params.x = snapped.xFromEndPx
                            params.y = snapped.topPx
                            runCatching { windowManager?.updateViewLayout(overlayView, params) }
                            pill.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } else if (!dragged) {
                            togglePanel()
                        }
                        true
                    }
                    else -> true
                }
            }
            root.addView(pill, LinearLayout.LayoutParams(dp(64), dp(64)))

            val commandPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = android.view.View.GONE
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        if (isNightMode) Color.argb(246, 43, 43, 67) else Color.argb(248, 255, 255, 255),
                        if (isNightMode) Color.argb(238, 18, 22, 38) else Color.argb(244, 232, 239, 250)
                    )
                ).apply {
                    cornerRadius = dp(24).toFloat()
                    setStroke(dp(1), if (isNightMode) Color.argb(150, 145, 132, 205) else Color.argb(170, 255, 255, 255))
                }
                elevation = dp(12).toFloat()
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val stateLabel = TextView(this).apply {
                text = "●  J A R V I S   O F F"
                textSize = 10f
                letterSpacing = 0.10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(mapFloatingViewStyle(FloatingPanelMode.EXPANDED, FloatingVoiceState.OFF).colorArgb)
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            voiceStateLabelText = stateLabel
            header.addView(stateLabel, LinearLayout.LayoutParams(0, -2, 1f))
            val menuButton = TextView(this).apply {
                text = "⋮"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(panelMutedText)
                contentDescription = "More assistant options"
                setPadding(dp(12), dp(2), dp(8), dp(2))
                setOnClickListener { anchor ->
                    PopupMenu(this@OverlayService, anchor).apply {
                        menu.add("Share Diagnostic Report").setOnMenuItemClickListener {
                            shareLatestDiagnostic()
                            true
                        }
                    }.show()
                }
            }
            header.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(40)))
            commandPanel.addView(header)

            val status = TextView(this).apply {
                text = "Ready — type a command or use mic"
                textSize = 12f
                setTextColor(panelMutedText)
                setPadding(dp(4), dp(2), dp(4), dp(6))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            statusText = status
            status.setOnClickListener { copyLatestDebugLog() }
            commandPanel.addView(status)
            val waveform = VoiceWaveformView(this).apply {
                visibility = View.GONE
                contentDescription = "JARVIS animated voice activity waveform"
            }
            waveformText = waveform
            commandPanel.addView(waveform, LinearLayout.LayoutParams(-1, dp(28)))

            val input = EditText(this).apply {
                hint = "Ask Jarvis to do something…"
                textSize = 14f
                setTextColor(panelText)
                setHintTextColor(panelMutedText)
                setSingleLine(false)
                minLines = 1
                maxLines = 2
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = EditorInfo.IME_ACTION_SEND
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(panelSurface)
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), if (isNightMode) Color.argb(145, 134, 137, 176) else Color.argb(170, 190, 202, 225))
                }
            }
            commandInput = input
            input.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isExpanded) {
                    togglePanel()
                    true
                } else false
            }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
            }
            actions.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
            val mic = TextView(this).apply {
                text = "🎙"
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                contentDescription = "Mic"
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(mapFloatingViewStyle(FloatingPanelMode.EXPANDED, FloatingVoiceState.OFF).colorArgb)
                    setStroke(dp(1), Color.argb(150, 255, 255, 255))
                }
                elevation = dp(5).toFloat()
                setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this@OverlayService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        status.text = "Allow microphone permission in Android app settings"
                    } else if (!voiceManager.isVoiceEnabled()) {
                        status.text = "Enable Voice Assistant in Jarvis Settings first"
                    } else if (voiceManager.isContinuousSessionActive()) {
                        voiceManager.stopContinuousSession()
                        isMuted = false
                        status.text = "Voice session stopped — microphone off"
                        updateNotification("Microphone off — tap Mic to start")
                    } else {
                        isMuted = false
                        voiceManager.startContinuousSession()
                        status.text = "Listening continuously — say “Mute” to lock commands"
                        updateNotification("Microphone active — say Mute to lock commands")
                    }
                }
            }
            micActionButton = mic
            val send = TextView(this).apply {
                text = "↑"
                textSize = 27f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                contentDescription = "Send command"
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.rgb(91, 103, 224))
                    setStroke(dp(1), Color.argb(160, 255, 255, 255))
                }
                elevation = dp(5).toFloat()
                setOnClickListener { submitCommand() }
            }
            val actionWidth = dp(48)
            actions.addView(mic, LinearLayout.LayoutParams(actionWidth, dp(48)).apply { leftMargin = dp(6); rightMargin = dp(4) })
            actions.addView(send, LinearLayout.LayoutParams(actionWidth, dp(48)).apply { leftMargin = dp(4) })
            commandPanel.addView(actions, LinearLayout.LayoutParams(-1, -2))
            root.addView(commandPanel, LinearLayout.LayoutParams(-1, -2))
            panel = commandPanel
            isExpanded = false

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    blurBehindRadius = dp(22)
                }
                x = 16
                y = 220
            }
            windowParams = params
            windowManager?.addView(root, params)
            overlayView = root
            root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && isExpanded) {
                    togglePanel()
                    true
                } else false
            }
            root.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isExpanded) {
                    togglePanel()
                    true
                } else false
            }
            applyFloatingStyle()
            mainHandler.removeCallbacks(orbPulseRunnable)
            mainHandler.post(orbPulseRunnable)
        } catch (e: Exception) {
            android.util.Log.e("OpenJarvis", "Unable to attach assistant overlay", e)
        } finally {
            overlayAttachmentGate.finish()
        }
    }

    private val orbPulseRunnable = object : Runnable {
        private var phase = 0f
        override fun run() {
            val orb = orbView ?: return
            if (isExpanded || !orb.isAttachedToWindow) return
            phase += 0.20f
            (orb as? ArcReactorOrbView)?.setPulse(phase)
            val scale = 1f + (0.018f * (0.5f + 0.5f * sin(phase)))
            orb.scaleX = scale
            orb.scaleY = scale
            mainHandler.postDelayed(this, 33L)
        }
    }

    private fun currentFloatingVoiceState(): FloatingVoiceState = when (visualState) {
        "LISTENING" -> FloatingVoiceState.LISTENING
        "THINKING" -> FloatingVoiceState.THINKING
        "SPEAKING" -> FloatingVoiceState.SPEAKING
        else -> FloatingVoiceState.OFF
    }

    private fun applyFloatingStyle() {
        val mode = if (isExpanded) FloatingPanelMode.EXPANDED else FloatingPanelMode.COLLAPSED
        val style = mapFloatingViewStyle(mode, currentFloatingVoiceState())
        (orbView as? ArcReactorOrbView)?.setStateColor(style.colorArgb)
        orbView?.contentDescription = "${style.label}. Tap to expand, drag to move."
        voiceStateLabelText?.apply {
            text = style.label
            setTextColor(style.colorArgb)
        }
        micActionButton?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(style.colorArgb)
            setStroke(dp(1), Color.argb(160, 255, 255, 255))
        }
    }

    private fun setVisualState(state: String, label: String) {
        visualState = state
        VoiceUiBridge.publish(when (state) {
            "LISTENING" -> VoiceSessionState.Listening
            "THINKING" -> VoiceSessionState.Thinking
            "SPEAKING" -> VoiceSessionState.Speaking
            else -> VoiceSessionState.Idle
        })
        statusText?.text = label
        applyFloatingStyle()
        waveformText?.visibility = if (state == "IDLE") View.GONE else View.VISIBLE
        (waveformText as? VoiceWaveformView)?.setVoiceColor(mapFloatingViewStyle(FloatingPanelMode.EXPANDED, currentFloatingVoiceState()).colorArgb)
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
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (isExpanded) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0)
            try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) { }
        }
        applyFloatingStyle()
        mainHandler.removeCallbacks(orbPulseRunnable)
        val animatedRoot = overlayView
        if (isExpanded) {
            animatedRoot?.animate()?.cancel()
            animatedRoot?.scaleX = 0.90f
            animatedRoot?.scaleY = 0.90f
            animatedRoot?.alpha = 0.72f
            animatedRoot?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
                ?.setDuration(260L)?.setInterpolator(OvershootInterpolator(0.72f))?.start()
            commandInput?.requestFocus()
        } else {
            animatedRoot?.animate()?.cancel()
            animatedRoot?.scaleX = 0.94f
            animatedRoot?.scaleY = 0.94f
            animatedRoot?.alpha = 0.82f
            animatedRoot?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
                ?.setDuration(210L)?.setInterpolator(OvershootInterpolator(0.55f))?.start()
            mainHandler.post(orbPulseRunnable)
        }
    }

    private fun enterMuteMode() {
        isMuted = true
        statusText?.text = "COMMAND MUTED — mic active for Unmute; other commands ignored"
        updateNotification("Mic active for Unmute; other commands ignored")
        voiceManager.speak("ठीक है, Jarvis command mute हो गया। Unmute बोलकर फिर से शुरू करें।") {
            voiceManager.resumeContinuousSession()
        }
    }
    private fun enterUnmuteMode() {
        val wasMuted = isMuted
        isMuted = false
        setVisualState("IDLE", "JARVIS ACTIVE — listening continuously")
        updateNotification("Continuous microphone listening active")
        val reply = if (wasMuted) "Jarvis is active. I am listening." else "Jarvis already active. I am listening."
        voiceManager.speak(reply) {
            voiceManager.resumeContinuousSession()
        }
    }

    private fun submitCommand(voiceCommand: String? = null) {
        val command = if (voiceCommand != null) voiceCommand.trim() else commandInput?.text?.toString()?.trim().orEmpty()
        if (command.isBlank()) {
            statusText?.text = "No speech detected — listening again"
            voiceManager.resumeContinuousSession()
            return
        }
        // Command Lab records only redacted metadata; it never changes command execution.
        val routeProbe = CommandLabRouterProbe.classify(command)
        val logId = commandLogStore.record(
            command = command,
            confidence = null, // VoiceState currently exposes transcript text only; never fabricate confidence.
            language = CommandLabRouterProbe.detectLanguage(command),
            route = routeProbe.route.name.lowercase(),
            source = if (voiceCommand != null) "voice" else "typed"
        )
        activeCommandLogId = logId
        // Typed commands use the same deterministic voice-control gate as speech transcripts.
        when (CommandListeningPolicy.classify(command, isMuted)) {
            CommandListeningPolicy.Action.MUTE -> {
                commandLogStore.complete(logId, "Handled by voice mute control", null)
                activeCommandLogId = null
                enterMuteMode()
                commandInput?.text?.clear()
                return
            }
            CommandListeningPolicy.Action.UNMUTE -> {
                commandLogStore.complete(logId, "Handled by voice mute control", null)
                activeCommandLogId = null
                enterUnmuteMode()
                commandInput?.text?.clear()
                return
            }
            CommandListeningPolicy.Action.IGNORE -> {
                commandLogStore.complete(logId, null, "Command ignored while muted")
                activeCommandLogId = null
                statusText?.text = "COMMAND MUTED — only say Unmute / Awaaz chalu karo"
                commandInput?.text?.clear()
                voiceManager.resumeContinuousSession()
                return
            }
            CommandListeningPolicy.Action.EXECUTE_COMMAND -> Unit
        }
        if (command.any { it in '\u0900'..'\u097F' }) {
            val marathiMarkers = listOf("आहे", "आणि", "मला", "काय", "झाले", "झालं", "नाही", "दाखवा", "उघडा", "करायचे", "माझे", "तुम्ही", "सांगा", "नको")
            responseLanguage = if (marathiMarkers.any { command.contains(it) }) "mr" else "hi"
        }
        if (com.openjarvis.accessibility.JarvisAccessibilityService.instance == null) {
            commandLogStore.complete(logId, null, "Accessibility service is disabled")
            activeCommandLogId = null
            statusText?.text = "Accessibility service is OFF. Enable Open Jarvis in Android Accessibility settings."
            voiceManager.resumeContinuousSession()
            return
        }
        statusText?.text = "Jarvis is working…"
        // Give voice users immediate feedback while the task continues asynchronously.
        // The final verified result still replaces this acknowledgement when ready.
        if (voiceCommand != null && voiceManager.isContinuousSessionActive()) {
            voiceManager.speak(com.openjarvis.agent.AgentResultSpeech.acknowledgement(responseLanguage))
        }
        voiceManager.markThinking()
        agentCore.executeTask(command)
        commandInput?.text?.clear()
    }

    private fun shareLatestDiagnostic() {
        val report = com.openjarvis.agent.AgentDiagnostics.latest(this)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Open Jarvis Diagnostic Report")
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "Share Jarvis diagnostic").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            Toast.makeText(this, "Unable to open sharing options: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyLatestDebugLog() {
        val report = com.openjarvis.agent.AgentDiagnostics.latest(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS Debug Log", report))
        Toast.makeText(this, "Debug log copied. Paste it here in ChatGPT.", Toast.LENGTH_LONG).show()
    }

    private inner class ArcReactorOrbView(context: Context) : TextView(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var stateColor: Int = 0xFF87909F.toInt()
        private var pulse = 0f

        init {
            setText("J")
            textSize = 17f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            background = null
        }

        fun setStateColor(color: Int) {
            stateColor = color
            invalidate()
        }

        fun setPulse(value: Float) {
            pulse = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.46f
            val disk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(0xEE252B3C.toInt(), 0xF20D1220.toInt(), 0xE91B2032.toInt()),
                    null, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(cx, cy, radius, disk)
            ringPaint.color = Color.argb(45, 255, 255, 255)
            ringPaint.strokeWidth = dp(1).toFloat()
            canvas.drawCircle(cx, cy, radius * 0.84f, ringPaint)
            ringPaint.color = Color.argb(55, Color.red(stateColor), Color.green(stateColor), Color.blue(stateColor))
            ringPaint.strokeWidth = dp(5).toFloat()
            canvas.drawCircle(cx, cy, radius * (0.64f + pulse * 0.015f), ringPaint)
            ringPaint.color = stateColor
            ringPaint.strokeWidth = dp(2).toFloat()
            val arc = RectF(cx - radius * 0.66f, cy - radius * 0.66f, cx + radius * 0.66f, cy + radius * 0.66f)
            canvas.drawArc(arc, -88f + pulse * 12f, 108f, false, ringPaint)
            canvas.drawArc(arc, 62f + pulse * 12f, 76f, false, ringPaint)
            canvas.drawArc(arc, 188f + pulse * 12f, 54f, false, ringPaint)
            corePaint.color = Color.argb(190, Color.red(stateColor), Color.green(stateColor), Color.blue(stateColor))
            canvas.drawCircle(cx, cy, radius * 0.12f, corePaint)
            super.onDraw(canvas)
        }
    }

    private inner class VoiceWaveformView(context: Context) : View(context) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; strokeCap = Paint.Cap.ROUND }
        private var voiceColor = 0xFF31D8E8.toInt()
        fun setVoiceColor(color: Int) { voiceColor = color; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val count = 27
            val gap = dp(4).toFloat()
            val barWidth = dp(2).toFloat()
            val total = count * barWidth + (count - 1) * gap
            val startX = (width - total) / 2f
            val centerY = height / 2f
            val t = System.nanoTime() / 1_000_000_000.0
            barPaint.color = voiceColor
            for (i in 0 until count) {
                val wave = 0.18 + 0.82 * (0.5 + 0.5 * sin(t * 3.0 + i * 0.47))
                val h = (dp(4) + dp(18) * wave).toFloat()
                val x = startX + i * (barWidth + gap)
                canvas.drawRoundRect(x, centerY - h / 2f, x + barWidth, centerY + h / 2f, barWidth, barWidth, barPaint)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        setStroke(1, Color.rgb(125, 95, 190))
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(
        message: String = "Assistant is running — tap the floating Jarvis button"
    ): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Open Jarvis")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun getAgentCore() = agentCore
    fun getVoiceManager() = voiceManager
    fun getRecentTasks() = recentTasks

    fun executeCommand(command: String) = agentCore.executeTask(command)

    private fun applyVoiceAssistantEnabled(enabled: Boolean) {
        voiceManager.setVoiceEnabled(enabled)
        if (!enabled) {
            isMuted = false
            voiceManager.stopContinuousSession()
            setVisualState("IDLE", "Voice assistant disabled — microphone off")
            updateNotification("Voice assistant disabled — microphone off")
        } else {
            setVisualState("IDLE", "Voice enabled — tap Mic once to start")
            updateNotification("Voice enabled — microphone off until Mic is tapped")
        }
    }

    companion object {
        @Volatile private var activeInstance: OverlayService? = null
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val NOTIFICATION_ID = 1
        private const val OVERLAY_WATCHDOG_INTERVAL_MS = 1_500L

        @Synchronized
        fun start(context: Context) {
            val current = activeInstance
            if (current != null) {
                current.mainHandler.post {
                    if (activeInstance === current && current.overlayView?.isAttachedToWindow != true) {
                        current.attachAssistant()
                    }
                }
                return
            }
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun onVoiceAssistantToggled(enabled: Boolean) {
            activeInstance?.applyVoiceAssistantEnabled(enabled)
        }
    }
}
