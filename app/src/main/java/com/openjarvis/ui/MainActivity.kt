package com.openjarvis.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.agent.AgentCore
import com.openjarvis.agent.PersonalMemoryStore
import com.openjarvis.agent.SessionSuggestionPolicy
import com.openjarvis.agent.AgentState
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.ui.dashboard.DashboardScreen
import com.openjarvis.ui.settings.SettingsScreen
import com.openjarvis.ui.theme.OpenJarvisTheme

class MainActivity : ComponentActivity() {

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) "Microphone enabled" else "Microphone permission is required for voice input",
            Toast.LENGTH_LONG
        ).show()
        requestNotificationPermissionIfNeeded()
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) "JARVIS proactive alerts enabled" else "Allow notifications to receive reminders and battery alerts",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private lateinit var graphifyRepo: GraphifyRepository
    private lateinit var agentCore: AgentCore
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestNotificationPermissionIfNeeded()
        }
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        graphifyRepo = GraphifyRepository(this)
        agentCore = AgentCore(this)
        
        setContent {
            OpenJarvisTheme {
                val context = LocalContext.current
                var recentTasks by remember { mutableStateOf<List<TaskNode>>(emptyList()) }
                var proactiveSuggestion by remember { mutableStateOf<String?>(null) }
                var suggestionFact by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    recentTasks = graphifyRepo.getRecentTasks(10)
                    val prefs = context.getSharedPreferences("jarvis_suggestion_session", MODE_PRIVATE)
                    val alreadyShown = prefs.getBoolean("shown", false)
                    val candidate = PersonalMemoryStore(context).getAll().firstOrNull()
                    if (!alreadyShown && candidate != null) {
                        val suggestion = SessionSuggestionPolicy.choose(
                            sessionId = "app-open",
                            alreadyShown = false,
                            memoryNotes = listOf(candidate),
                            hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        )
                        if (suggestion != null) {
                            suggestionFact = candidate
                            proactiveSuggestion = suggestion
                            prefs.edit().putBoolean("shown", true).apply()
                        }
                    }
                }
                
                val accessibilityEnabled = isAccessibilityServiceEnabled()
                val overlayEnabled = Settings.canDrawOverlays(this)
                
                val agentState by agentCore.state.collectAsState()

                proactiveSuggestion?.let { suggestion ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { proactiveSuggestion = null },
                        title = { androidx.compose.material3.Text("Jarvis suggestion") },
                        text = { androidx.compose.material3.Text(suggestion) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                val fact = suggestionFact
                                proactiveSuggestion = null
                                if (!fact.isNullOrBlank()) agentCore.executeTask("Reminder lagao: $fact")
                            }) { androidx.compose.material3.Text("Haan") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                proactiveSuggestion = null
                            }) { androidx.compose.material3.Text("Abhi nahi") }
                        }
                    )
                }

                if (!accessibilityEnabled || !overlayEnabled) {
                    PermissionScreen(
                        accessibilityEnabled = accessibilityEnabled,
                        overlayEnabled = overlayEnabled,
                        onEnableAccessibility = { startAccessibilitySettings() },
                        onEnableOverlay = { startOverlaySettings() }
                    )
                } else {
                    DashboardScreen(
                        onStartOverlay = { startOverlayService() },
                        onOpenSettings = { openSettings() },
                        onOpenEuronics = { startOverlayService(); startActivity(Intent(this, EuronicsActivity::class.java)) },
                        graphifyRepo = graphifyRepo,
                        isSystemActive = accessibilityEnabled && overlayEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val componentName = ComponentName(this, JarvisAccessibilityService::class.java)
        return enabledServices.contains(componentName.flattenToString())
    }
    
    private fun startAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
    
    private fun startOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
            android.net.Uri.parse("package:$packageName")))
    }
    
    private fun startOverlayService() {
        OverlayService.start(this)
        Toast.makeText(this, "Jarvis is active", Toast.LENGTH_SHORT).show()
    }
    
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}

@Composable
fun PermissionScreen(
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit
) {
    val primaryColor = com.openjarvis.ui.theme.VoidColor.Violet
    val onSurfaceColor = com.openjarvis.ui.theme.VoidColor.TextPrimary
    val onSurfaceVariantColor = com.openjarvis.ui.theme.VoidColor.TextSecondary
    
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Text(
            text = "Permission Required",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = onSurfaceColor
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.material3.Text(
            text = "Open Jarvis needs accessibility and overlay permissions to control your device.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariantColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        
        if (!accessibilityEnabled) {
            androidx.compose.material3.Button(
                onClick = onEnableAccessibility,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text("Enable Accessibility Service")
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
        
        if (!overlayEnabled) {
            androidx.compose.material3.Button(
                onClick = onEnableOverlay,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text("Enable Overlay Permission")
            }
        }
        
        if (accessibilityEnabled && overlayEnabled) {
            androidx.compose.material3.Text(
                text = "Jarvis is active",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = primaryColor
            )
        }
    }
}