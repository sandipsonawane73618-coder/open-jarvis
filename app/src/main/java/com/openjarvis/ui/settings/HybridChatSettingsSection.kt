package com.openjarvis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openjarvis.llm.HybridChatProvider
import com.openjarvis.llm.HybridChatSettings
import com.openjarvis.llm.HybridChatSettingsStore
import com.openjarvis.llm.providers.GeminiProvider
import com.openjarvis.llm.providers.GroqProvider
import com.openjarvis.ui.theme.VoidColor
import kotlinx.coroutines.launch

@Composable
internal fun HybridChatSettingsSection() {
    val context = LocalContext.current
    val store = remember { HybridChatSettingsStore(context) }
    val initial = remember { store.load() }
    var onlineEnabled by remember { mutableStateOf(initial.onlineChatEnabled) }
    var localFallback by remember { mutableStateOf(initial.localFallbackEnabled) }
    var selectedProvider by remember { mutableStateOf(initial.provider) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf("") }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showOnlineWarning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Keys are encrypted on this device.") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasSavedKey = remember(selectedProvider, apiKey) {
        apiKey.isNotBlank() || store.apiKey(selectedProvider).isNotBlank()
    }

    fun persist(enabled: Boolean = onlineEnabled) {
        store.save(
            HybridChatSettings(
                onlineChatEnabled = enabled,
                provider = selectedProvider,
                model = model,
                localFallbackEnabled = localFallback
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("HYBRID ONLINE CHAT", color = VoidColor.TextDisabled)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = VoidColor.Void900
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Online Chat", color = VoidColor.TextPrimary)
                        Text("OFF by default. Only normal chat can use cloud providers.", color = VoidColor.TextDisabled)
                    }
                    Switch(
                        checked = onlineEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !onlineEnabled) showOnlineWarning = true
                            else {
                                onlineEnabled = false
                                persist(false)
                            }
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local fallback", color = VoidColor.TextPrimary)
                        Text("Use local Ollama if online providers fail.", color = VoidColor.TextDisabled)
                    }
                    Switch(
                        checked = localFallback,
                        onCheckedChange = {
                            localFallback = it
                            persist()
                        }
                    )
                }
                Text("Provider", color = VoidColor.TextPrimary)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { showProviderMenu = true }) {
                        Text(if (selectedProvider == HybridChatProvider.GEMINI) "Google Gemini" else "Groq")
                        Text("  ▾")
                    }
                    DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Google Gemini") },
                            onClick = {
                                selectedProvider = HybridChatProvider.GEMINI
                                model = selectedProvider.defaultModel
                                apiKey = ""
                                persist()
                                showProviderMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Groq") },
                            onClick = {
                                selectedProvider = HybridChatProvider.GROQ
                                model = selectedProvider.defaultModel
                                apiKey = ""
                                persist()
                                showProviderMenu = false
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it.take(100); persist() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasSavedKey) "API key (saved; enter to replace)" else "API key") },
                    placeholder = { Text(if (hasSavedKey) "••••••••••••••••" else "Paste API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = {
                        if (apiKey.isNotBlank()) {
                            store.saveApiKey(selectedProvider, apiKey)
                            apiKey = ""
                            status = "API key saved securely."
                        } else {
                            status = if (store.apiKey(selectedProvider).isNotBlank()) {
                                "An encrypted API key is already saved."
                            } else "Enter an API key first."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save API key") }
                OutlinedButton(
                    onClick = {
                        val key = apiKey.takeIf { it.isNotBlank() } ?: store.apiKey(selectedProvider)
                        if (key.isBlank()) {
                            status = "Enter and save the selected provider's API key first."
                        } else {
                            if (apiKey.isNotBlank()) store.saveApiKey(selectedProvider, apiKey)
                            apiKey = ""
                            persist()
                            testing = true
                            status = "Testing connection…"
                            scope.launch {
                                val result = if (selectedProvider == HybridChatProvider.GEMINI) {
                                    GeminiProvider(key, model).testConnection()
                                } else {
                                    GroqProvider(key, model).testConnection()
                                }
                                testing = false
                                status = result.fold(
                                    onSuccess = { "Connected successfully in ${it}ms." },
                                    onFailure = {
                                        "Connection failed. Verify the API key and internet connection."
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing
                ) { Text(if (testing) "Testing…" else "Test connection") }
                Text(status, color = VoidColor.TextDisabled)
                Text(
                    "When enabled, normal chat messages and short chat history may be sent to Google Gemini or Groq. Personal memory, OCR, contacts and screen text must remain local.",
                    color = VoidColor.TextDisabled
                )
            }
        }
    }

    if (showOnlineWarning) {
        AlertDialog(
            onDismissRequest = { showOnlineWarning = false },
            title = { Text("Enable Online Chat?") },
            text = { Text("Normal chat content and short conversation history will be sent to the selected provider (Google Gemini or Groq). Do not enable if you want chat to stay fully local. Personal memory and device data are not part of the cloud request.") },
            confirmButton = {
                TextButton(onClick = {
                    showOnlineWarning = false
                    onlineEnabled = true
                    persist(true)
                }) { Text("Enable online chat") }
            },
            dismissButton = {
                TextButton(onClick = { showOnlineWarning = false }) { Text("Cancel") }
            }
        )
    }
}
