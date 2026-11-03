package com.openjarvis.agent

import android.content.Context
import android.util.Log
import android.content.Intent
import android.content.pm.ResolveInfo
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.accessibility.ScreenReader
import com.openjarvis.graphify.AnalysisEngine
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.intelligence.AIAppInteractor
import com.openjarvis.intelligence.AIApps
import com.openjarvis.intelligence.AppAnalyzer
import com.openjarvis.intelligence.TaskRouter
import com.openjarvis.intelligence.FastCommandParser
import com.openjarvis.intelligence.TaskWorkingMemory
import com.openjarvis.llm.UniversalAdapter
import com.openjarvis.vision.VisionModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AgentCore(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val analysisEngine = AnalysisEngine(context)
    private val universalAdapter = UniversalAdapter(context)
    private val screenReader = ScreenReader(context)
    private val visionModule = VisionModule.getInstance(context)
    private val taskRouter = TaskRouter(context)
    private val appAnalyzer = AppAnalyzer(context)
    private val aiAppInteractor = AIAppInteractor(context)

    private var workingMemory = TaskWorkingMemory()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskMutex = Mutex()

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state

    private val systemPrompt = """
You are Jarvis, an Android control assistant. Return ONLY a valid JSON array of supported actions.
Preserve all user data. If the Euronics Service Report, WCR, or Petrol Sheet is visible, operate that screen only; never reset or clear it.
Allowed actions: open_app(package,label), tap(text), type(value), press_back, press_home, press_recents, wait_for(text,timeout_ms), read_screen, ai_prompt(package,prompt,outputKey), extract_text(outputKey), error(message).
Understand Hindi, Marathi, and English. Use exact visible text. Never invent a package or UI state.
For complex tasks, open the required app, verify with wait_for, and keep the action list short. If screen text is unclear, use read_screen. Return an error action if unsafe or impossible.
SCREEN: {SCREEN_OCR}
APP HINT: {APP_REASONING}
MEMORY: {GRAPHIFY_CONTEXT}
""".trimIndent()

    fun executeTask(cleanCommand: String) {
        workingMemory = TaskWorkingMemory()

        scope.launch {
            taskMutex.withLock {
                try {
                    val sanitized = PromptSanitizer.sanitize(cleanCommand)

                    when (sanitized) {
                        is PromptSanitizer.SanitizeResult.Rejected -> {
                            _state.value = AgentState.Error(sanitized.reason)
                            return@withLock
                        }

                        is PromptSanitizer.SanitizeResult.Suspicious -> {
                            _state.value = AgentState.Running("analyzing...")
                        }

                        is PromptSanitizer.SanitizeResult.Clean -> {
                        }
                    }

                    val safeCommand = when (sanitized) {
                        is PromptSanitizer.SanitizeResult.Suspicious ->
                            sanitized.sanitized

                        is PromptSanitizer.SanitizeResult.Clean ->
                            sanitized.text

                        else -> cleanCommand
                    }

                    _state.value = AgentState.Running("analyzing task...")

                    val fastTarget = FastCommandParser.openAppTarget(safeCommand)
                    if (fastTarget != null) {
                        _state.value = AgentState.Running("Opening $fastTarget...")
                        val opened = tryFastOpenApp(safeCommand)
                        if (opened != null) {
                            graphifyRepo.logTask(
                                command = safeCommand,
                                result = "success (fast path)",
                                provider = "Android fast path",
                                latencyMs = 0
                            )
                            _state.value = AgentState.Done("Opened $opened")
                            return@withLock
                        }
                    }

                    val plan = taskRouter.analyze(safeCommand)

                    _state.value = AgentState.Running("reading screen...")

                    val screenText = runCatching {
                        withContext(Dispatchers.IO) { screenReader.extractAllText() }
                    }.onFailure { Log.e(TAG, "Screen extraction failed; continuing without OCR", it) }
                        .getOrDefault("")

                    _state.value = AgentState.Running("getting context...")

                    val memoryContext = runCatching {
                        graphifyRepo.buildMemoryContext(safeCommand)
                    }.onFailure { Log.e(TAG, "Memory context failed; continuing without memory", it) }
                        .getOrDefault("")

                    val fullSystem = systemPrompt
                        .replace("{SCREEN_OCR}", screenText.take(800))
                        .replace("{APP_REASONING}", plan.reasoning.take(200))
                        .replace("{AI_APPS}", getInstalledAIApps())
                        .replace(
                            "{GRAPHIFY_CONTEXT}",
                            if (memoryContext.isBlank()) {
                                "No recent tasks"
                            } else {
                                memoryContext.take(250)
                            }
                        )

                    _state.value = AgentState.Running("Soch raha hoon...")

                    val startTime = System.currentTimeMillis()

                    val result = universalAdapter.complete(
                        fullSystem,
                        safeCommand
                    )

                    result.fold(
                        onSuccess = { rawJson ->
                            val latency =
                                System.currentTimeMillis() - startTime

                            val validation =
                                LLMResponseValidator.validate(rawJson)

                            if (!validation.isValid) {
                                val reason = validation.errors.firstOrNull() ?: "Empty action plan"
                                _state.value = AgentState.Error("Invalid AI action plan: $reason")
                                graphifyRepo.logTask(safeCommand, "failed: validation error", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            val actions = validation.actions

                            _state.value = AgentState.Running(
                                "executing ${actions.size} actions..."
                            )

                            executeActions(actions)
                            if (_state.value is AgentState.Error) {
                                graphifyRepo.logTask(safeCommand, "failed: action execution", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            graphifyRepo.logTask(
                                command = safeCommand,
                                result = "success",
                                provider = universalAdapter.getProviderName(),
                                latencyMs = latency
                            )

                            runCatching { analysisEngine.analyzeLastTask() }
                                .onFailure { Log.e(TAG, "Post-task analysis failed", it) }

                            _state.value =
                                AgentState.Done("done in ${latency}ms")
                        },

                        onFailure = { error ->
                            val msg = when {
                                error.message?.contains("401") == true ->
                                    "Invalid API key"

                                error.message?.contains("429") == true ->
                                    "Rate limited — wait a moment"

                                error.message?.contains("timeout") == true ->
                                    "Request timed out"

                                error.message?.contains("Unable to resolve") == true ->
                                    "Network error — check connection"

                                else -> {
                                    val detail = error.message?.takeIf { it.isNotBlank() }
                                        ?: error.cause?.message?.takeIf { it.isNotBlank() }
                                        ?: error.javaClass.simpleName
                                    if (universalAdapter.getProviderName() == "Ollama (Local)") {
                                        "Ollama error: $detail"
                                    } else detail.ifBlank { "Unknown error" }
                                }
                            }

                            _state.value = AgentState.Error(msg)

                            graphifyRepo.logTask(
                                safeCommand,
                                "failed: $msg",
                                "",
                                0
                            )
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Task execution crashed for command: $cleanCommand", e)
                    val detail = e.message?.takeIf { it.isNotBlank() }
                        ?: e.cause?.message?.takeIf { it.isNotBlank() }
                        ?: e.javaClass.simpleName
                    val errorMessage = if (universalAdapter.getProviderName() == "Ollama (Local)") {
                        "Ollama error: $detail"
                    } else detail.ifBlank { "Unknown error" }
                    _state.value = AgentState.Error(errorMessage)

                    graphifyRepo.logTask(
                        cleanCommand,
                        "failed: $errorMessage",
                        "",
                        0
                    )
                }
            }
        }
    }

    suspend fun testConnection(): Result<Long> {
        return universalAdapter.testConnection()
    }

    fun getCurrentProviderName(): String {
        return universalAdapter.getProviderName()
    }

    fun getStateFlow(): StateFlow<AgentState> = state

    private fun getInstalledAIApps(): String {
        return AIApps.KNOWN_AI_APPS.keys.joinToString(", ")
    }

    suspend fun getAnalyzedAppCount(): Int =
        appAnalyzer.getAnalyzedCount()

    suspend fun getAIAppCount(): Int =
        appAnalyzer.getAICount()

    private suspend fun executeActions(actions: List<Action>) {
        for ((index, action) in actions.withIndex()) {
            _state.value = AgentState.Running(
                "action ${index + 1}/${actions.size}"
            )

            when (action.action) {
                Action.OPEN_APP -> {
                    val label = action.label

                    val packageName = action.packageName ?: label?.let { findPackageByLabel(it) }
                    if (packageName.isNullOrBlank()) {
                        _state.value = AgentState.Error("App not found: ${label ?: "missing package name"}")
                        return
                    }
                    val service = JarvisAccessibilityService.instance
                    if (service == null || !service.openAppByPackage(packageName)) {
                        _state.value = AgentState.Error("Could not open app: ${label ?: packageName}")
                        return
                    }
                    graphifyRepo.logAppOpened(packageName, label ?: packageName)
                }

                Action.TAP -> {
                    val target = action.text
                    val service = JarvisAccessibilityService.instance
                    if (target.isNullOrBlank() || service == null || !service.tapByText(target)) {
                        _state.value = AgentState.Error("Could not tap screen text: ${target ?: "missing target"}")
                        return
                    }
                }

                Action.TYPE -> {
                    val value = action.value
                    val service = JarvisAccessibilityService.instance
                    if (value.isNullOrEmpty() || service == null || !service.typeText(value)) {
                        _state.value = AgentState.Error("Could not type: input field not found")
                        return
                    }
                }

                Action.PRESS_BACK -> {
                    if (JarvisAccessibilityService.instance?.pressBack() != true) {
                        _state.value = AgentState.Error("Back action failed")
                        return
                    }
                }

                Action.PRESS_HOME -> {
                    if (JarvisAccessibilityService.instance?.pressHome() != true) {
                        _state.value = AgentState.Error("Home action failed")
                        return
                    }
                }

                Action.PRESS_RECENTS -> {
                    if (JarvisAccessibilityService.instance?.pressRecents() != true) {
                        _state.value = AgentState.Error("Recent apps action failed")
                        return
                    }
                }

                Action.WAIT_FOR -> {
                    val expected = action.text
                    val timeout = action.timeoutMs.coerceIn(500L, 15000L)
                    val started = System.currentTimeMillis()
                    var found = false
                    while (expected != null && System.currentTimeMillis() - started < timeout) {
                        if (screenReader.extractAllText().contains(expected, ignoreCase = true)) {
                            found = true
                            break
                        }
                        kotlinx.coroutines.delay(300)
                    }
                    if (!found) {
                        _state.value = AgentState.Error("Timed out waiting for: ${expected ?: "screen text"}")
                        return
                    }
                }

                Action.READ_SCREEN -> {
                    val screen = screenReader.extractAllText()
                    if (screen.isBlank()) {
                        _state.value = AgentState.Error("Could not read current screen")
                        return
                    }
                    workingMemory.set(action.outputKey ?: "page_text", screen)
                }

                Action.SCREENSHOT, Action.TAP_COORDS, Action.LONG_PRESS,
                Action.SWIPE, Action.SCROLL, Action.CLEAR_TYPE -> {
                    _state.value = AgentState.Error("Action not supported yet: ${action.action}")
                    return
                }

                Action.AI_PROMPT -> {
                    val packageName = action.packageName
                    val prompt =
                        workingMemory.interpolate(action.prompt ?: "")
                    val outputKey = action.outputKey

                    if (packageName != null) {
                        val meta = AIApps.KNOWN_AI_APPS[packageName]

                        if (meta == null) {
                            _state.value = AgentState.Error("Unsupported AI app: $packageName")
                            return
                        }
                        aiAppInteractor.openAIApp(meta)
                        kotlinx.coroutines.delay(2000)
                        aiAppInteractor.typePrompt(prompt)
                        kotlinx.coroutines.delay(1000)
                        val response = aiAppInteractor.waitForResponse()
                        if (response.isBlank()) {
                            _state.value = AgentState.Error("AI app did not return a response")
                            return
                        }
                        if (outputKey != null) workingMemory.set(outputKey, response)
                    }
                }

                Action.EXTRACT_TEXT -> {
                    val outputKey = action.outputKey ?: "page_text"
                    val text = screenReader.extractAllText()
                    workingMemory.set(outputKey, text)
                }

                Action.ERROR -> {
                    _state.value = AgentState.Error(
                        action.message ?: "Task failed"
                    )
                    return
                }
            }

            kotlinx.coroutines.delay(500)
        }
    }

    private fun tryFastOpenApp(command: String): String? {
        val target = FastCommandParser.openAppTarget(command) ?: return null
        val packageName = if (target.equals("settings", ignoreCase = true)) {
            "com.android.settings"
        } else {
            findPackageByLabel(target)
        } ?: return null
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            target
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OpenJarvisAgent"
    }

    private fun findPackageByLabel(label: String): String? {
        val pm = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps: List<ResolveInfo> =
            pm.queryIntentActivities(intent, 0)

        val normalizedLabel = label.lowercase().trim()

        for (app in apps) {
            val appLabel =
                app.loadLabel(pm).toString().lowercase()

            if (
                appLabel == normalizedLabel ||
                appLabel.contains(normalizedLabel) ||
                normalizedLabel.contains(appLabel)
            ) {
                return app.activityInfo.packageName
            }
        }

        return null
    }
}
