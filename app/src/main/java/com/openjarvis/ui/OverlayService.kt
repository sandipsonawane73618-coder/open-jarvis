package com.openjarvis.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.graphify.GraphifyRepository
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
    private var panel: LinearLayout? = null
    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private var recentTasks = emptyList<TaskNode>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isExpanded = true
    private var responseLanguage = "hi"

    override fun onCreate() {
        super.onCreate()
        agentCore = AgentCore(this)
        voiceManager = VoiceManager(this)
        graphifyRepo = GraphifyRepository(this)
        startForeground(NOTIFICATION_ID, createNotification())
        attachAssistant()
        serviceScope.launch {
            voiceManager.state.collect { state ->
                when (state) {
                    is VoiceManager.VoiceState.Result -> {
                        commandInput?.setText(state.text)
                        statusText?.text = "Voice captured — executing command…"
                        submitCommand(state.text)
                    }
                    is VoiceManager.VoiceState.Error -> statusText?.text = state.message
                    is VoiceManager.VoiceState.Recording -> statusText?.text = "Listening… speak now"
                    is VoiceManager.VoiceState.Transcribing -> statusText?.text = "Transcribing…"
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
                        statusText?.text = "Done — tap Mic to give the next command"
                        voiceManager.speak(state.result)
                        voiceManager.resetState()
                    }
                    is com.openjarvis.agent.AgentState.Error -> {
                        statusText?.text = "Command failed — tap Mic to try again"
                        voiceManager.speak(if (responseLanguage == "mr") "काम पूर्ण होऊ शकलं नाही. कृपया पुन्हा प्रयत्न करा." else "Bhai, command poori nahi hui. Ek baar phir try karo.")
                        voiceManager.resetState()
                    }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) { recentTasks = graphifyRepo.getRecentTasks(10) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        overlayView = null
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
                setPadding(0, 0, 0, 0)
                setBackgroundColor(Color.TRANSPARENT)
                elevation = 8f
            }
            // Compact transparent robot-only launcher. Drag to reposition; tap to expand.
            val robot = ImageView(this).apply {
                setImageResource(R.drawable.jarvis_robot)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = "Open Jarvis"
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            var downRawX = 0f
            var downRawY = 0f
            var startX = 0
            var startY = 0
            var dragged = false
            robot.setOnTouchListener { _, event ->
                val params = windowParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params?.x ?: 0
                        startY = params?.y ?: 0
                        dragged = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) dragged = true
                        if (dragged && params != null) {
                            params.x = (startX - dx.toInt()).coerceAtLeast(0)
                            params.y = (startY + dy.toInt()).coerceAtLeast(0)
                            try { windowManager?.updateViewLayout(root, params) } catch (_: Exception) { }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) startVoiceListening()
                        true
                    }
                    else -> true
                }
            }
            root.addView(robot, LinearLayout.LayoutParams(dp(48), dp(48)))

            val commandPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = android.view.View.VISIBLE
                setPadding(12, 12, 12, 12)
                background = rounded(Color.rgb(20, 18, 35), 20)
                elevation = 12f
            }
            val status = TextView(this).apply {
                text = "Ready — type a command or use mic"
                textSize = 12f
                setTextColor(Color.LTGRAY)
                setPadding(8, 4, 8, 8)
            }
            statusText = status
            commandPanel.addView(status)

            val input = EditText(this).apply {
                hint = "Ask Jarvis to do something…"
                textSize = 14f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setSingleLine(false)
                minLines = 2
                maxLines = 4
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = EditorInfo.IME_ACTION_SEND
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        submitCommand()
                        true
                    } else false
                }
                setPadding(12, 8, 12, 8)
                background = rounded(Color.rgb(42, 38, 58), 12)
            }
            commandInput = input
            commandPanel.addView(input, LinearLayout.LayoutParams(280, -2))

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val mic = Button(this).apply {
                text = "🎙 Mic"
                setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this@OverlayService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        status.text = "Allow microphone permission in Android app settings"
                    } else if (!voiceManager.isVoiceEnabled()) {
                        status.text = "Enable Voice Assistant in Jarvis Settings first"
                    } else {
                        voiceManager.resetState()
                        status.text = "Listening… speak now"
                        voiceManager.startListening()
                    }
                }
            }
            val send = Button(this).apply {
                text = "Send"
                setOnClickListener { submitCommand() }
            }
            actions.addView(mic)
            actions.addView(send)
            commandPanel.addView(actions)
            root.addView(commandPanel)
            panel = commandPanel

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    private fun startVoiceListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText?.text = "Microphone permission missing. Allow it in Android app settings."
            Toast.makeText(this, "Allow Jarvis microphone permission first", Toast.LENGTH_LONG).show()
            return
        }
        // Tapping the floating Jarvis icon is an explicit request to use the microphone.
        voiceManager.setVoiceEnabled(true)
        voiceManager.resetState()
        statusText?.text = "Listening… speak your command"
        voiceManager.startListening()
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
        statusText?.text = "Jarvis is working…"
        agentCore.executeTask(command)
        commandInput?.text?.clear()
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
