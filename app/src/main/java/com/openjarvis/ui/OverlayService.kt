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
import com.openjarvis.voice.PorcupineWakeWordController
import com.openjarvis.voice.WakeWordConfig
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
    private var panel: LinearLayout? = null
    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private var wakeWordController: PorcupineWakeWordController? = null
    private var wakeWordControllerKey: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWordCommandPending = false
    private var wakeWordResponsePending = false
    private var recentTasks = emptyList<TaskNode>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isExpanded = false
    private var responseLanguage = "hi"

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
        serviceScope.launch {
            voiceManager.state.collect { state ->
                when (state) {
                    is VoiceManager.VoiceState.Result -> {
                        if (wakeWordCommandPending) {
                            wakeWordCommandPending = false
                            voiceManager.stopListening()
                        }
                        commandInput?.setText(state.text)
                        statusText?.text = "Voice captured — executing command…"
                        submitCommand(state.text)
                    }
                    is VoiceManager.VoiceState.Error -> {
                        statusText?.text = state.message
                        if (wakeWordCommandPending) {
                            wakeWordCommandPending = false
                            wakeWordResponsePending = false
                            voiceManager.stopListening()
                            serviceScope.launch { delay(700); startWakeWordListening() }
                        }
                    }
                    is VoiceManager.VoiceState.Recording -> statusText?.text = "Listening… speak now"
                    is VoiceManager.VoiceState.Transcribing -> statusText?.text = "Thinking…"
                    is VoiceManager.VoiceState.Speaking -> statusText?.text = "Speaking…"
                    is VoiceManager.VoiceState.Idle -> Unit
                }
            }
        }
        serviceScope.launch {
            agentCore.state.collect { state ->
                when (state) {
                    is com.openjarvis.agent.AgentState.Idle -> statusText?.text = "Ready — type a command or use mic"
                    is com.openjarvis.agent.AgentState.Running -> statusText?.text = state.step
                    is com.openjarvis.agent.AgentState.Done -> {
                        statusText?.text = state.result
                        voiceManager.speak(com.openjarvis.agent.AgentResultSpeech.textForDone(state.result)) { resumeWakeWordAfterResponse() }
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        statusText?.text = "Jarvis error: ${state.message} • tap to copy debug log"
                        voiceManager.speak(if (responseLanguage == "mr") "कमांड पूर्ण झाली नाही. ${state.message}" else "कमांड पूरी नहीं हुई। ${state.message}") { resumeWakeWordAfterResponse() }
                    }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) { recentTasks = graphifyRepo.getRecentTasks(10) }
        if (microphoneGranted) startWakeWordListening()
        else statusText?.text = "Allow microphone permission to enable Jarvis"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-check the saved AccessKey whenever the app asks the existing service to start.
        // This also refreshes wake-word setup after the user edits the key in Settings.
        if (::voiceManager.isInitialized) startWakeWordListening()
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        overlayView = null
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
                minimumWidth = dp(WakeWordConfig.OVERLAY_WIDTH_DP)
                minimumHeight = dp(48)
                background = rounded(Color.rgb(20, 18, 35), 24)
                elevation = 18f
            }
            val pill = TextView(this).apply {
                text = "●  JARVIS  ▾"
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(14), dp(22), dp(14))
                background = rounded(Color.rgb(55, 35, 95), 40)
                setOnClickListener { togglePanel() }
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
                        if (wakeWordController?.isRunning() == true) {
                            stopWakeWordListening()
                            wakeWordCommandPending = true
                            wakeWordResponsePending = true
                        }
                        // Manual mic is independent of wake-word initialization/key state.
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

    private fun togglePanel() {
        isExpanded = !isExpanded
        panel?.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
        windowParams?.let { params ->
            params.flags = if (isExpanded) WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) { }
        }
        if (isExpanded) commandInput?.requestFocus()
    }

    private fun submitCommand(voiceCommand: String? = null) {
        val command = voiceCommand?.trim().takeUnless { it.isNullOrBlank() }
            ?: commandInput?.text?.toString()?.trim().orEmpty()
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
            return
        }
        if (wakeWordController?.isRunning() == true) {
            stopWakeWordListening()
            wakeWordResponsePending = true
        }
        statusText?.text = "Jarvis is working…"
        voiceManager.markThinking()
        agentCore.executeTask(command)
        commandInput?.text?.clear()
    }

    private fun startWakeWordListening() {
        if (!voiceManager.isVoiceEnabled()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText?.text = "Microphone permission is required for Jarvis"
            return
        }
        val accessKey = voiceManager.getPicovoiceAccessKey()
        if (accessKey.isBlank()) {
            statusText?.text = "Add your Picovoice AccessKey in Settings to enable offline Jarvis"
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), types)
        }
        // Settings can update the key while this foreground service remains alive.
        // Porcupine captures its AccessKey at construction, so rebuild it when the key changes.
        if (wakeWordController == null || wakeWordControllerKey != accessKey) {
            wakeWordController?.stop()
            wakeWordController = PorcupineWakeWordController(
                this,
                accessKey,
                onWakeWord = { mainHandler.post { handleWakeWordDetected() } },
                onError = { message -> mainHandler.post { statusText?.text = "Wake-word error: $message" } }
            )
            wakeWordControllerKey = accessKey
        }
        val error = wakeWordController?.start()
        statusText?.text = error ?: "Standby — say Jarvis (offline)"
    }

    private fun handleWakeWordDetected() {
        if (!voiceManager.isVoiceEnabled() || wakeWordCommandPending) return
        stopWakeWordListening()
        wakeWordCommandPending = true
        wakeWordResponsePending = true
        statusText?.text = "Yes? Listening for your command…"
        voiceManager.startListening()
    }

    private fun stopWakeWordListening() {
        wakeWordController?.stop()
    }

    private fun resumeWakeWordAfterResponse() {
        if (!wakeWordResponsePending) return
        wakeWordResponsePending = false
        serviceScope.launch {
            delay(500)
            startWakeWordListening()
        }
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
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val NOTIFICATION_ID = 1
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
