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
import com.openjarvis.bridge.SocketServer
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.voice.VoiceManager
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
    private var socketServer: SocketServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
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
                        CommandListeningPolicy.Action.MUTE -> {
                            isMuted = true
                            setVisualState("IDLE", "MUTED — say only “Ab bolo”")
                            voiceManager.speak("ठीक है, Jarvis mute हो गया।")
                        }
                        CommandListeningPolicy.Action.UNMUTE -> {
                            isMuted = false
                            setVisualState("IDLE", "JARVIS active — listening…")
                            voiceManager.speak("Jarvis is active. I am listening.")
                        }
                        CommandListeningPolicy.Action.IGNORE -> if (isMuted) {
                            statusText?.text = "MUTED — say only “Ab bolo”"
                            serviceScope.launch { delay(300); if (isMuted && voiceManager.isVoiceEnabled()) voiceManager.startListening() }
                        } else submitCommand(state.text)
                        CommandListeningPolicy.Action.EXECUTE_COMMAND -> submitCommand(state.text)
                    }
                    is VoiceManager.VoiceState.Error -> setVisualState("IDLE", state.message)
                    is VoiceManager.VoiceState.Recording -> setVisualState("LISTENING", if (isMuted) "Muted — listening for “Ab bolo”" else "Listening… speak now")
                    is VoiceManager.VoiceState.Transcribing -> setVisualState("THINKING", "Thinking…")
                    is VoiceManager.VoiceState.Speaking -> setVisualState("SPEAKING", "Speaking…")
                    is VoiceManager.VoiceState.Idle -> setVisualState("IDLE", if (isMuted) "MUTED — say only “Ab bolo”" else "Ready — type a command or use mic")
                }
            }
        }
        serviceScope.launch {
            agentCore.state.collect { state ->
                when (state) {
                    is com.openjarvis.agent.AgentState.Idle -> statusText?.text = "Ready — type a command or use mic"
                    is com.openjarvis.agent.AgentState.Running -> setVisualState("THINKING", state.step)
                    is com.openjarvis.agent.AgentState.Done -> {
                        setVisualState("SPEAKING", state.result)
                        voiceManager.speak(com.openjarvis.agent.AgentResultSpeech.textForDone(state.result))
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        setVisualState("SPEAKING", "Jarvis error: ${state.message} • tap to copy debug log")
                        voiceManager.speak(if (responseLanguage == "mr") "कमांड पूर्ण झाली नाही. ${state.message}" else "कमांड पूरी नहीं हुई। ${state.message}")
                    }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) { recentTasks = graphifyRepo.getRecentTasks(10) }
        if (microphoneGranted && voiceManager.isVoiceEnabled()) voiceManager.startListening()
        else if (!microphoneGranted) statusText?.text = "Allow microphone permission to enable Jarvis"
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
        val viewToRemove = overlayView ?: if (activeInstance === this) attachedOverlayView else null
        try {
            viewToRemove?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeViewImmediate(it) }
        } catch (_: Exception) { }
        if (attachedOverlayView === viewToRemove) attachedOverlayView = null
        overlayView = null
        visualState = "IDLE"
        mainHandler.removeCallbacks(waveformRunnable)
        waveformText = null
        voiceManager.stopListening()
        if (activeInstance === this) activeInstance = null
        socketServer?.stop()
        socketServer = null
        voiceManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun attachAssistant() {
        if (!Settings.canDrawOverlays(this)) return
        // Only the currently registered service instance may own the floating window.
        if (activeInstance !== this) return
        if (overlayView?.isAttachedToWindow == true) return

        // Reconcile a previously registered window before creating another one.
        // This covers service recreation where the instance-local view reference was lost.
        attachedOverlayView?.let { previous ->
            if (previous.isAttachedToWindow) {
                try {
                    (getSystemService(WINDOW_SERVICE) as WindowManager).removeViewImmediate(previous)
                } catch (e: Exception) {
                    android.util.Log.w("OpenJarvis", "Could not remove stale JARVIS overlay", e)
                }
            }
            attachedOverlayView = null
        }
        // A stale detached View must not block recreation of the floating overlay.
        overlayView = null
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
                    } else if (voiceManager.isSessionActive()) {
                        status.text = if (isMuted) "Muted — waiting only for “Ab bolo”" else "Already listening for your command"
                    } else {
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
            val actionWidth = dp(48)
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
            attachedOverlayView = root
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
        isMuted = true
        statusText?.text = "MUTED — say only “Ab bolo”"
        voiceManager.speak("ठीक है, Jarvis mute हो गया।")
    }

    private fun submitCommand(voiceCommand: String? = null) {
        val command = if (voiceCommand != null) voiceCommand.trim() else commandInput?.text?.toString()?.trim().orEmpty()
        if (command.isBlank()) { statusText?.text = "No speech detected — try again"; return }
        if (command.any { it in '\u0900'..'\u097F' }) {
            val marathiMarkers = listOf("आहे", "आणि", "मला", "काय", "झाले", "झालं", "नाही", "दाखवा", "उघडा", "करायचे", "माझे", "तुम्ही", "सांगा", "नको")
            responseLanguage = if (marathiMarkers.any { command.contains(it) }) "mr" else "hi"
        }
        if (com.openjarvis.accessibility.JarvisAccessibilityService.instance == null) {
            statusText?.text = "Accessibility service is OFF. Enable Open Jarvis in Android Accessibility settings."
            return
        }
        statusText?.text = "Jarvis is working…"
        voiceManager.markThinking()
        agentCore.executeTask(command)
        commandInput?.text?.clear()
    }

    private fun copyLatestDebugLog() {
        val report = com.openjarvis.agent.AgentDiagnostics.latest(this)
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
        @Volatile private var activeInstance: OverlayService? = null
        @Volatile private var attachedOverlayView: LinearLayout? = null
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val NOTIFICATION_ID = 1
        private const val OVERLAY_WATCHDOG_INTERVAL_MS = 1_500L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
