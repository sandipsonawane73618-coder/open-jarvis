package com.openjarvis.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openjarvis.agent.ExperimentalSelfHealingSettings
import com.openjarvis.agent.PersonalMemoryStore
import com.openjarvis.agent.JarvisPersonality
import com.openjarvis.agent.JarvisPersonalityStore
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.llm.providers.OllamaProvider
import com.openjarvis.llm.providers.OllamaResponseParser
import com.openjarvis.ui.theme.VoidColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSaveProvider: (String, String, String, String) -> Unit,
    onOpenCommandLab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val providerPrefs = remember {
        val key = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "jarvis_encrypted_prefs",
            key,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    val voicePrefs = remember {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "voice_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    val speechLocaleManager = remember { com.openjarvis.voice.SpeechLocaleSupportManager(context) }
    val speechLocaleLabels = listOf("hi-IN" to "Hindi", "en-IN" to "English (India)", "mr-IN" to "Marathi")
    val speechLocaleStatuses = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            speechLocaleLabels.forEach { (locale, _) -> put(locale, speechLocaleManager.savedStatus(locale)) }
        }
    }
    var localeAwaitingDownloadConsent by remember { mutableStateOf<String?>(null) }

    val savedProvider = "Ollama (Local)"
    val savedModel = providerPrefs.getString("provider_model", OllamaProvider.DEFAULT_MODEL)
        .orEmpty().let { OllamaProvider.supportedChatModel(it) }
    val migratedModel = OllamaProvider.DEFAULT_MODEL
    var selectedProvider by remember { mutableStateOf(savedProvider) }
    var baseUrl by remember { mutableStateOf(OllamaProvider.DEFAULT_BASE_URL) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember {
        mutableStateOf(
            if (savedProvider == "Ollama (Local)") {
                savedModel.takeIf { it in OllamaProvider.AVAILABLE_MODELS } ?: OllamaProvider.DEFAULT_MODEL
            } else migratedModel
        )
    }
    var showBaseUrlField by remember { mutableStateOf(savedProvider == "Custom" || savedProvider == "Ollama (Local)") }
    var showProvidersDropdown by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showOllamaModelMenu by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(0L) }
    var savedVisible by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(voicePrefs.getBoolean("voice_enabled", false)) }
    var experimentalSelfHealingEnabled by remember { mutableStateOf(ExperimentalSelfHealingSettings.isEnabled(context)) }
    var speakResults by remember { mutableStateOf(voicePrefs.getBoolean("speak_results", true)) }
    var hindiVoiceGender by remember { mutableStateOf(voicePrefs.getString("tts_voice_gender_hi", "Female") ?: "Female") }
    var marathiVoiceGender by remember { mutableStateOf(voicePrefs.getString("tts_voice_gender_mr", "Female") ?: "Female") }
    var englishVoiceGender by remember { mutableStateOf(voicePrefs.getString("tts_voice_gender_en", "Female") ?: "Female") }
    val personalMemoryStore = remember { PersonalMemoryStore(context) }
    val personalityStore = remember { JarvisPersonalityStore(context) }
    var memoryFacts by remember { mutableStateOf(personalMemoryStore.getAll()) }
    var preferredName by remember { mutableStateOf(personalityStore.load().preferredName) }
    var personalityTone by remember { mutableStateOf(personalityStore.load().tone) }
    var answerLength by remember { mutableStateOf(personalityStore.load().answerLength) }
    var personalityLanguage by remember { mutableStateOf(personalityStore.load().language) }
    var speechRate by remember { mutableStateOf(1.05f) }
    var sttMode by remember { mutableStateOf("Push to Talk") }
    val ollamaDebugLogs = remember(selectedProvider) {
        context.getSharedPreferences("ollama_diagnostics", android.content.Context.MODE_PRIVATE)
            .getString("recent_requests", "").orEmpty().lineSequence().filter { it.isNotBlank() }.take(5).toList()
    }
    
    LaunchedEffect(selectedProvider) {
        showBaseUrlField = selectedProvider == "Custom" || selectedProvider == "Ollama (Local)"
        if (selectedProvider == "Ollama (Local)") {
            if (baseUrl.isBlank()) baseUrl = OllamaProvider.DEFAULT_BASE_URL
            model = providerPrefs.getString("provider_model", "").orEmpty()
                .takeIf { it in OllamaProvider.AVAILABLE_MODELS } ?: OllamaProvider.DEFAULT_MODEL
            apiKey = ""
            providerPrefs.edit().apply {
                putString("provider_name", selectedProvider)
                putString("provider_base_url", baseUrl.trim())
                putString("provider_api_key", "")
                putString("provider_model", model)
                apply()
            }
        }
    }
    
    val scrollState = rememberScrollState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColor.Void950)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsHeader(onNavigateBack = onNavigateBack)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                SectionLabel("COMMAND LAB")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenCommandLab, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Command Lab • Logs & Self-test")
                }
                Spacer(modifier = Modifier.height(24.dp))
                HybridChatSettingsSection()
                Spacer(modifier = Modifier.height(28.dp))
                ConnectedAppsSettingsSection()
                Spacer(modifier = Modifier.height(28.dp))

                SectionLabel("AI PROVIDER")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ProviderSelectorCard(
                    selectedProvider = selectedProvider,
                    isExpanded = showProvidersDropdown,
                    onToggle = { showProvidersDropdown = !showProvidersDropdown },
                    onSelect = { provider ->
                        selectedProvider = provider
                        if (provider == "Ollama (Local)") {
                            if (baseUrl.isBlank()) baseUrl = OllamaProvider.DEFAULT_BASE_URL
                            model = providerPrefs.getString("provider_model", "").orEmpty()
                                .takeIf { it in OllamaProvider.AVAILABLE_MODELS } ?: OllamaProvider.DEFAULT_MODEL
                            apiKey = ""
                            providerPrefs.edit().apply {
                                putString("provider_name", provider)
                                putString("provider_base_url", baseUrl.trim())
                                putString("provider_api_key", "")
                                putString("provider_model", model)
                                apply()
                            }
                        } else {
                            providerPrefs.edit().putString("provider_name", provider).apply()
                        }
                        showProvidersDropdown = false
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FloatingLabelTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = OllamaProvider.DEFAULT_BASE_URL },
                    label = "Local Base URL (fixed)",
                    isFocused = showBaseUrlField,
                    visible = showBaseUrlField
                )
                
                FloatingLabelTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    isFocused = false,
                    isPassword = true,
                    showPassword = showPassword,
                    visible = selectedProvider != "Ollama (Local)",
                    onTogglePassword = { showPassword = !showPassword }
                )
                
                if (selectedProvider == "Ollama (Local)") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showOllamaModelMenu = true }) {
                            Text("Ollama model: $model  ▾")
                        }
                        DropdownMenu(
                            expanded = showOllamaModelMenu,
                            onDismissRequest = { showOllamaModelMenu = false }
                        ) {
                            OllamaProvider.AVAILABLE_MODELS.forEach { availableModel ->
                                DropdownMenuItem(
                                    text = { Text(availableModel) },
                                    onClick = {
                                        model = availableModel
                                        providerPrefs.edit().putString("provider_model", availableModel).apply()
                                        showOllamaModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    FloatingLabelTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            providerPrefs.edit().putString("provider_model", it.trim()).apply()
                        },
                        label = "Model",
                        isFocused = false
                    )
                }
                
                Text(
                    text = "Works with any OpenAI-compatible API",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(400),
                        fontSize = 11.sp,
                        color = VoidColor.TextDisabled
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TestConnectionButton(
                    provider = selectedProvider,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    onSave = { name, url, key, mdl ->
                        val savedUrl = if (name == "Ollama (Local)" && url.isNotBlank()) {
                            OllamaResponseParser.normalizeBaseUrl(url)
                        } else url.trim()
                        val savedKey = if (name == "Ollama (Local)") "" else key
                        val savedModel = if (name == "Ollama (Local)") {
                            mdl.trim().takeIf { it in OllamaProvider.AVAILABLE_MODELS } ?: OllamaProvider.DEFAULT_MODEL
                        } else mdl
                        providerPrefs.edit().apply {
                            putString("provider_name", name)
                            putString("provider_base_url", savedUrl)
                            putString("provider_api_key", savedKey)
                            putString("provider_model", savedModel)
                            apply()
                        }
                        onSaveProvider(name, savedUrl, savedKey, savedModel)
                        lastSaved = System.currentTimeMillis()
                        savedVisible = true
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (selectedProvider == "Ollama (Local)") {
                    SectionLabel("OLLAMA DEBUG LOG")
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void900,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (ollamaDebugLogs.isEmpty()) {
                                Text("No Ollama chat timings yet.", color = VoidColor.TextDisabled, fontSize = 12.sp)
                            } else {
                                ollamaDebugLogs.forEach { entry ->
                                    Text(entry, color = VoidColor.TextSecondary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                SectionLabel("MEMORY & PERSONALITY")
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = VoidColor.Void900,
                    border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Saved personal facts", color = VoidColor.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight(600))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Stored encrypted on this phone. Jarvis saves inferred facts only after you say yes.", color = VoidColor.TextDisabled, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        if (memoryFacts.isEmpty()) {
                            Text("No saved facts yet.", color = VoidColor.TextDisabled, fontSize = 12.sp)
                        } else {
                            memoryFacts.forEach { fact ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(fact, color = VoidColor.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        personalMemoryStore.delete(fact)
                                        memoryFacts = personalMemoryStore.getAll()
                                    }) { Text("Delete") }
                                }
                            }
                        }
                        if (memoryFacts.isNotEmpty()) {
                            TextButton(onClick = {
                                personalMemoryStore.clearAll()
                                memoryFacts = emptyList()
                            }) { Text("Delete all memories") }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("How Jarvis should address you", color = VoidColor.TextPrimary, fontSize = 14.sp)
                        OutlinedTextField(
                            value = preferredName,
                            onValueChange = { preferredName = it.take(24) },
                            label = { Text("Preferred name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            val values = listOf("Friendly", "Professional", "Mazedaar")
                            personalityTone = values[(values.indexOf(personalityTone).coerceAtLeast(0) + 1) % values.size]
                        }) { Text("Tone: $personalityTone  ·  tap to change") }
                        TextButton(onClick = {
                            val values = listOf("Chhota", "Medium")
                            answerLength = values[(values.indexOf(answerLength).coerceAtLeast(0) + 1) % values.size]
                        }) { Text("Answer length: $answerLength  ·  tap to change") }
                        TextButton(onClick = {
                            val values = listOf("Auto", "Hindi", "Marathi", "English")
                            personalityLanguage = values[(values.indexOf(personalityLanguage).coerceAtLeast(0) + 1) % values.size]
                        }) { Text("Language: $personalityLanguage  ·  tap to change") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                personalityStore.save(JarvisPersonality(preferredName, personalityTone, answerLength, personalityLanguage))
                                Toast.makeText(context, "Personality saved locally", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save personality") }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                SectionLabel("EXPERIMENTAL FEATURES")
                Spacer(modifier = Modifier.height(10.dp))
                SettingsToggleRow(
                    title = "Experimental self-healing helpers",
                    subtitle = "Optional Hindi/Marathi command aliases and verified local repair memory. Default OFF; no automatic device-action replay.",
                    enabled = experimentalSelfHealingEnabled,
                    onToggle = {
                        experimentalSelfHealingEnabled = !experimentalSelfHealingEnabled
                        ExperimentalSelfHealingSettings.setEnabled(context, experimentalSelfHealingEnabled)
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))

                SectionLabel("VOICE")
                Spacer(modifier = Modifier.height(10.dp))
                SettingsToggleRow(
                    title = "Voice Assistant",
                    subtitle = "On-device voice controls • tap Mic to start; no background listening",
                    enabled = voiceEnabled,
                    onToggle = {
                        voiceEnabled = !voiceEnabled
                        voicePrefs.edit().putBoolean("voice_enabled", voiceEnabled).apply()
                        // Voice capability is independent from the floating overlay lifecycle.
                        // Never destroy/recreate the overlay when this preference changes.
                        com.openjarvis.ui.OverlayService.onVoiceAssistantToggled(voiceEnabled)
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                if (voiceEnabled) {
                    SettingsToggleRow(
                        title = "Speak Results",
                        subtitle = "Jarvis reads results aloud",
                        enabled = speakResults,
                        onToggle = { speakResults = !speakResults; voicePrefs.edit().putBoolean("speak_results", speakResults).apply() }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void900,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Jarvis voice", color = VoidColor.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight(600))
                            Text("Choose Male or Female separately for each language. Voice gender matching depends on installed engine voice names.", color = VoidColor.TextDisabled, fontSize = 11.sp)
                            @Composable
                            fun VoiceLanguagePicker(label: String, locale: String, gender: String, onGender: (String) -> Unit, sample: String) {
                                Text(label, color = VoidColor.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight(600))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        onGender("Female")
                                        voicePrefs.edit().putString("tts_voice_gender_${locale.substringBefore('-')}", "Female").apply()
                                    }) { Text(if (gender == "Female") "✓ Female" else "Female") }
                                    TextButton(onClick = {
                                        onGender("Male")
                                        voicePrefs.edit().putString("tts_voice_gender_${locale.substringBefore('-')}", "Male").apply()
                                    }) { Text(if (gender == "Male") "✓ Male" else "Male") }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        com.openjarvis.voice.AndroidTTSEngine.preview(context, gender, locale, sample) {
                                            Toast.makeText(context, "$label voice preview unavailable. Check installed TTS voice data.", Toast.LENGTH_LONG).show()
                                        }
                                    }) { Text("Preview") }
                                }
                            }
                            VoiceLanguagePicker("Hindi", "hi-IN", hindiVoiceGender, { hindiVoiceGender = it }, "नमस्ते! मैं आपकी Jarvis हूँ। कैसे मदद करूँ?")
                            VoiceLanguagePicker("Marathi", "mr-IN", marathiVoiceGender, { marathiVoiceGender = it }, "नमस्कार! मी तुमची Jarvis आहे. मी कशी मदत करू?")
                            VoiceLanguagePicker("English (India)", "en-IN", englishVoiceGender, { englishVoiceGender = it }, "Hello! I am your Jarvis. How can I help you?")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // STT Mode selector
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void900,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    sttMode = if (sttMode == "Push to Talk") "Auto (VAD)" else "Push to Talk"
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "STT Mode",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(500),
                                        fontSize = 14.sp,
                                        color = VoidColor.TextPrimary
                                    )
                                )
                                Text(
                                    text = if (sttMode == "Push to Talk") 
                                        "Hold mic to record, release to send" 
                                    else 
                                        "Tap to record, auto-sends on silence",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(400),
                                        fontSize = 11.sp,
                                        color = VoidColor.TextDisabled
                                    )
                                )
                            }
                            Text(
                                text = sttMode,
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight(500),
                                    fontSize = 12.sp,
                                    color = VoidColor.Violet
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Model status
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void800,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = VoidColor.Green,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Speech Recognition",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(500),
                                        fontSize = 14.sp,
                                        color = VoidColor.TextPrimary
                                    )
                                )
                                Text(
                                    text = "Android on-device speech recognition",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight(400),
                                        fontSize = 11.sp,
                                        color = VoidColor.TextDisabled
                                    )
                                )
                            }
                            Text(
                                text = "Offline service required",
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight(500),
                                    fontSize = 12.sp,
                                    color = VoidColor.Green
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColor.Void900,
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Offline speech languages", color = VoidColor.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight(600))
                            Text(
                                "Check installed on-device packs. Downloads are requested only after your approval; speech recognition never falls back to network speech.",
                                color = VoidColor.TextDisabled, fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    speechLocaleLabels.forEach { (locale, _) ->
                                        speechLocaleStatuses[locale] = "Checking…"
                                        speechLocaleManager.checkRecognitionSupport(locale) { status ->
                                            speechLocaleStatuses[locale] = status
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Check language support") }
                            speechLocaleLabels.forEach { (locale, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("$label ($locale)", color = VoidColor.TextPrimary, fontSize = 12.sp)
                                        Text(speechLocaleStatuses[locale] ?: "Not checked", color = VoidColor.TextDisabled, fontSize = 11.sp)
                                    }
                                    if (speechLocaleStatuses[locale] in setOf("Available", "Download pending")) {
                                        TextButton(onClick = { localeAwaitingDownloadConsent = locale }) {
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    localeAwaitingDownloadConsent?.let { locale ->
                        val label = speechLocaleLabels.firstOrNull { it.first == locale }?.second ?: locale
                        AlertDialog(
                            onDismissRequest = { localeAwaitingDownloadConsent = null },
                            title = { Text("Download $label offline speech pack?") },
                            text = {
                                Text("Jarvis will request the Android on-device language model. This may use your network only to download the language pack. Speech recognition itself remains on-device. Android may show its own approval prompt.")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    localeAwaitingDownloadConsent = null
                                    speechLocaleStatuses[locale] = "Download requested"
                                    speechLocaleManager.requestDownload(locale) { status ->
                                        speechLocaleStatuses[locale] = status
                                    }
                                }) { Text("Allow download") }
                            },
                            dismissButton = {
                                TextButton(onClick = { localeAwaitingDownloadConsent = null }) { Text("Cancel") }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SectionLabel("PERMISSIONS")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                PermissionRow(
                    title = "Accessibility",
                    subtitle = "Required — Tap to enable"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                PermissionRow(
                    title = "Overlay",
                    subtitle = "Required — Tap to enable"
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SectionLabel("ABOUT")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                AboutSection()
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        SavedToast(
            visible = savedVisible,
            onDismiss = { savedVisible = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = VoidColor.TextSecondary
            )
        }
        
        Text(
            text = "Settings",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(600),
                fontSize = 20.sp,
                color = VoidColor.TextPrimary
            )
        )
    }
    
    Divider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = VoidColor.BorderSubtle
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight(600),
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            color = VoidColor.TextDisabled
        )
    )
}

@Composable
private fun ProviderSelectorCard(
    selectedProvider: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) VoidColor.BorderGlow else VoidColor.BorderSubtle,
        animationSpec = tween(200),
        label = "border"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderDot(provider = selectedProvider)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = selectedProvider,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(500),
                    fontSize = 14.sp,
                    color = VoidColor.TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            
            val rotationAngle by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = tween(200),
                label = "rotation"
            )
            
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VoidColor.TextDisabled,
                modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
            )
        }
    }
    
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically() + fadeOut(animationSpec = tween(200))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = VoidColor.Void800,
            border = BorderStroke(1.dp, VoidColor.BorderSubtle)
        ) {
            Column {
                UniversalAdapter.AVAILABLE_PROVIDERS.forEach { provider ->
                    ProviderOption(
                        label = provider,
                        isSelected = provider == selectedProvider,
                        onClick = { onSelect(provider) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderDot(provider: String) {
    val colors = mapOf(
        "Groq" to VoidColor.Violet,
        "Google Gemini" to VoidColor.Cyan,
        "OpenRouter" to VoidColor.Green,
        "Anthropic Claude" to VoidColor.Amber,
        "OpenAI" to VoidColor.Green,
        "Ollama (Local)" to VoidColor.Cyan,
        "Custom" to VoidColor.Violet
    )
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors[provider] ?: VoidColor.Violet)
    )
}

@Composable
private fun ProviderOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(VoidColor.Violet)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(15.dp))
        }
        
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(500),
                fontSize = 14.sp,
                color = if (isSelected) VoidColor.Violet else VoidColor.TextPrimary
            )
        )
    }
}

@Composable
fun FloatingLabelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isFocused: Boolean,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    visible: Boolean = true,
    onTogglePassword: (() -> Unit)? = null
) {
    val labelOffset by animateFloatAsState(
        targetValue = if (isFocused || value.isNotEmpty()) -20f else 0f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.75f),
        label = "offset"
    )
    
    val labelScale by animateFloatAsState(
        targetValue = if (isFocused || value.isNotEmpty()) 0.75f else 1f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.75f),
        label = "scale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) VoidColor.Violet else VoidColor.BorderSubtle,
        animationSpec = tween(200),
        label = "border"
    )
    
    if (!visible) return
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(400),
                    fontSize = 14.sp,
                    color = VoidColor.TextSecondary
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = labelOffset.dp.toPx()
                    scaleX = labelScale
                    scaleY = labelScale
                }
            )
            
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(400),
                    fontSize = 14.sp,
                    color = VoidColor.TextPrimary
                ),
                visualTransformation = if (isPassword && !showPassword) 
                    PasswordVisualTransformation() else VisualTransformation.None
            )
        }
    }
}

@Composable
private fun TestConnectionButton(
    provider: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    onSave: (String, String, String, String) -> Unit
) {
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val borderColor by animateColorAsState(
        targetValue = when (testState) {
            is TestState.Idle -> VoidColor.VioletDim
            is TestState.Loading -> VoidColor.Violet
            is TestState.Success -> VoidColor.Green
            is TestState.Error -> VoidColor.Red
        },
        animationSpec = tween(200),
        label = "btn_border"
    )
    
    val textColor = when (testState) {
        is TestState.Idle -> VoidColor.Violet
        is TestState.Loading -> VoidColor.Violet
        is TestState.Success -> VoidColor.Green
        is TestState.Error -> VoidColor.Red
    }
    
    OutlinedButton(
        onClick = {
            if (apiKey.isBlank() && provider != "Ollama (Local)" && provider != "Local (LlamaCpp)") {
                testState = TestState.Error("API key is required")
            } else {
                testState = TestState.Loading
                onSave(provider, baseUrl, apiKey, model)
                scope.launch {
                    val result = UniversalAdapter(context).testConnection()
                    testState = result.fold(
                        onSuccess = { TestState.Success(it) },
                        onFailure = { TestState.Error(it.message ?: "Connection failed") }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor
        )
    ) {
        when (val state = testState) {
            is TestState.Idle -> Text("Test Connection")
            is TestState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VoidColor.Violet
            )
            is TestState.Success -> {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connected — ${state.ms}ms")
            }
            is TestState.Error -> {
                Text("Failed — ${state.message}")
            }
        }
    }
}

sealed class TestState {
    data object Idle : TestState()
    data object Loading : TestState()
    data class Success(val ms: Long) : TestState()
    data class Error(val message: String) : TestState()
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(
                if (onToggle != null) Modifier.clickable { onToggle() }
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = VoidColor.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(500),
                        fontSize = 14.sp,
                        color = VoidColor.TextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(400),
                        fontSize = 11.sp,
                        color = VoidColor.TextDisabled
                    )
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle?.invoke() },
                enabled = onToggle != null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VoidColor.TextPrimary,
                    checkedTrackColor = VoidColor.Violet,
                    uncheckedThumbColor = VoidColor.TextDisabled,
                    uncheckedTrackColor = VoidColor.Void600
                )
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String
) {
    val isGranted by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VoidColor.Red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = VoidColor.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(500),
                        fontSize = 14.sp,
                        color = VoidColor.TextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(400),
                        fontSize = 11.sp,
                        color = VoidColor.Red
                    )
                )
            }
            
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VoidColor.Red
            )
        }
    }
}

@Composable
private fun AboutSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void900,
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Column {
            AboutRow(title = "Version", value = "M1.0 (build 1)")
            Divider(thickness = 1.dp, color = VoidColor.BorderSubtle)
            AboutRow(title = "License", value = "MIT Open Source")
            Divider(thickness = 1.dp, color = VoidColor.BorderSubtle)
            AboutRow(title = "GitHub", value = "tokenarc/open-jarvis", isLink = true)
            Divider(thickness = 1.dp, color = VoidColor.BorderSubtle)
            
            Text(
                text = "Built on Android · From Termux with love",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(400),
                    fontSize = 12.sp,
                    color = VoidColor.TextDisabled
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
    isLink: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(400),
                fontSize = 14.sp,
                color = VoidColor.TextSecondary
            )
        )
        
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight(400),
                fontSize = 14.sp,
                color = if (isLink) VoidColor.Violet else VoidColor.TextPrimary
            )
        )
    }
}

@Composable
private fun SavedToast(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(1500)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { 40 } + fadeIn(),
        exit = slideOutVertically { 40 } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = VoidColor.Void800,
            border = BorderStroke(1.dp, VoidColor.Green.copy(alpha = 0.25f))
        ) {
            Text(
                text = "✓ saved",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight(500),
                    fontSize = 12.sp,
                    color = VoidColor.Green
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}