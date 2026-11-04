package com.openjarvis.agent

import android.content.Context
import android.util.Log
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.provider.ContactsContract
import android.app.SearchManager
import android.content.pm.PackageManager
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
import kotlinx.coroutines.withTimeoutOrNull

class AgentCore(private val context: Context) {

    private val graphifyRepo = GraphifyRepository(context)
    private val analysisEngine = AnalysisEngine(context)
    private val universalAdapter = UniversalAdapter(context)
    private val screenReader = ScreenReader(context)
    private val visionModule = VisionModule.getInstance(context)
    private val taskRouter = TaskRouter(context)
    private val appAnalyzer = AppAnalyzer(context)
    private val aiAppInteractor = AIAppInteractor(context)
    private val personalMemoryStore = PersonalMemoryStore(context)

    private var workingMemory = TaskWorkingMemory()
    private val contactFollowUpWindow = ContactFollowUpWindow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskMutex = Mutex()

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state

    private val systemPrompt = """
You are Jarvis, an Android action planner. Return ONLY a JSON array of action objects.
Example: [{"action":"open_app","label":"<exact installed label>"}]
Actions: open_app, click, type_text, wait, scroll, back, home, read_screen, extract_text, error.
Use exact app labels from INSTALLED APPS and exact targets from SCREEN ELEMENTS. For type_text include target and value.
Never invent contacts, app labels, UI controls, or results. If uncertain, return [{"action":"error","message":"brief reason"}].
Understand Hindi, Marathi, Hinglish and English. Do not explain or use markdown.
MEMORY: {PERSONAL_MEMORY}
SCREEN: {SCREEN_OCR}
ELEMENTS: {SCREEN_ELEMENTS}
INSTALLED APPS: {INSTALLED_APPS}
""".trimIndent()

    private fun isSimpleConversation(command: String): Boolean =
        Regex("^(hi|hello|hey|hello jarvis|hi jarvis|how are you|what's up|नमस्ते|नमस्कार|हाय|कसे आहात|काय चाललंय)[.!? ]*$", RegexOption.IGNORE_CASE)
            .matches(command.trim())

    private suspend fun completeWithinBudget(
        system: String,
        user: String,
        budgetStartMs: Long,
        conversation: Boolean = false
    ): Result<String> {
        val remaining = 45_000L - (System.currentTimeMillis() - budgetStartMs)
        if (remaining <= 0L) return Result.failure(IllegalStateException("LLM time budget exceeded (45 seconds). Try a shorter command."))
        return withTimeoutOrNull(remaining) {
            universalAdapter.completeWithDeadline(system, user, remaining, conversation)
        } ?: Result.failure(IllegalStateException("LLM time budget exceeded (45 seconds). Try a shorter command."))
    }

    /**
     * Validate a model plan and make at most one structure-only JSON repair request.
     * The repair is never allowed to invent or change the requested operation.
     */
    private suspend fun validateWithOneRepair(
        system: String,
        userCommand: String,
        rawResponse: String,
        budgetStartMs: Long
    ): Pair<String, LLMResponseValidator.ValidationResult> {
        val initial = LLMResponseValidator.validate(rawResponse)
        if (initial.isValid) return rawResponse to initial

        val repairPrompt = """
You are a strict JSON syntax repairer for Android action plans.
Repair ONLY the JSON structure of the supplied response. Preserve every action, field, target,
and value exactly as intended. Do not add, remove, reorder, or invent actions or data.
Every item in the output array must be a JSON object. Return ONLY a valid JSON array,
with double-quoted keys and string values. No markdown or explanation.
Required shape example: [{"action":"type_text","value":"hello"}]
Malformed response:
${rawResponse.take(1000)}
""".trimIndent()

        val repairResult = completeWithinBudget(repairPrompt, userCommand, budgetStartMs)
        if (repairResult.isFailure && repairResult.exceptionOrNull()?.message.orEmpty().contains("time budget", ignoreCase = true)) {
            throw repairResult.exceptionOrNull()!!
        }
        val repaired = repairResult.getOrNull() ?: return rawResponse to initial

        val repairedValidation = LLMResponseValidator.validate(repaired)
        return if (repairedValidation.isValid) repaired to repairedValidation
        else rawResponse to initial
    }

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

                    _state.value = AgentState.Running("checking direct command...")

                    // Consume the next utterance as a contact name only during the 10s pending window.
                    if (contactFollowUpWindow.consume(System.currentTimeMillis())) {
                            try {
                                val result = executeFastCommand(FastCommandParser.Command.DialContact(safeCommand.trim()))
                                Log.i(TAG, "path=rule skill=contact-follow-up")
                                _state.value = AgentState.Done(result)
                            } catch (error: Exception) {
                                val detail = error.message.orEmpty()
                                when {
                                    detail.startsWith("No contact matched") -> {
                                        Log.i(TAG, "Contact lookup had no match for a user request")
                                        _state.value = AgentState.Done("Mujhe '${safeCommand.trim()}' naam ka contact nahi mila. Kya dialer kholun?")
                                    }
                                    detail.startsWith("Multiple contacts found") -> {
                                        Log.i(TAG, "Contact lookup requires user disambiguation")
                                        _state.value = AgentState.Done(detail)
                                    }
                                    else -> {
                                    AgentDiagnostics.record(context, "contact-follow-up", safeCommand, detail, error = error, provider = "Android fast path", model = "Not used")
                                    _state.value = AgentState.Error(detail.ifBlank { error.javaClass.simpleName })
                                }
                                }
                            }
                            return@withLock
                    }

                    // Search parsing must precede compound splitting and all LLM requests.
                    val youtubeQuery = SearchCommandParser.parseYouTubeQuery(safeCommand)
                    if (youtubeQuery != null) {
                        try {
                            val result = executeFastCommand(FastCommandParser.Command.YoutubeSearch(youtubeQuery))
                            Log.i(TAG, "path=rule skill=youtube_search")
                            graphifyRepo.logTask(safeCommand, "success (YouTube search rule)", "Android fast path", 0)
                            _state.value = AgentState.Done(result)
                        } catch (error: Exception) {
                            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                            AgentDiagnostics.record(context, "youtube-search", safeCommand, detail, error = error, provider = "Android fast path", model = "Not used")
                            _state.value = AgentState.Error(detail)
                        }
                        return@withLock
                    }

                    val webQuery = SearchCommandParser.parseWebQuery(safeCommand)
                    if (webQuery != null) {
                        try {
                            val webIntent = Intent(Intent.ACTION_WEB_SEARCH)
                                .putExtra(SearchManager.QUERY, webQuery)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(webIntent)
                            Log.i(TAG, "path=rule skill=web_search")
                            graphifyRepo.logTask(safeCommand, "success (web search rule)", "Android fast path", 0)
                            _state.value = AgentState.Done("Searching the web for $webQuery")
                        } catch (error: Exception) {
                            _state.value = AgentState.Error("Could not open web search: " + (error.message ?: error.javaClass.simpleName))
                        }
                        return@withLock
                    }

                    when (val callRequest = ContactCommandParser.parse(safeCommand)) {
                        null -> Unit
                        else -> {
                            if (callRequest.name == null) {
                                contactFollowUpWindow.start(System.currentTimeMillis())
                                Log.i(TAG, "path=rule skill=contact_call awaiting_name timeout_ms=30000")
                                _state.value = AgentState.Done("Kisko call karun?")
                            } else {
                                try {
                                    val result = executeFastCommand(FastCommandParser.Command.DialContact(callRequest.name))
                                    Log.i(TAG, "path=rule skill=contact_call")
                                    graphifyRepo.logTask(safeCommand, "success (contact dialer rule)", "Android fast path", 0)
                                    _state.value = AgentState.Done(result)
                                } catch (error: Exception) {
                                    val detail = error.message.orEmpty()
                                    when {
                                        detail.startsWith("No contact matched") -> {
                                            Log.i(TAG, "Contact lookup had no match for a user request")
                                            _state.value = AgentState.Done("Mujhe '${callRequest.name}' naam ka contact nahi mila. Kya dialer kholun?")
                                        }
                                        detail.startsWith("Multiple contacts found") -> {
                                            Log.i(TAG, "Contact lookup requires user disambiguation")
                                            _state.value = AgentState.Done(detail)
                                        }
                                        else -> {
                                            AgentDiagnostics.record(context, "contact-call", safeCommand, detail, error = error, provider = "Android fast path", model = "Not used")
                                            _state.value = AgentState.Error(detail.ifBlank { error.javaClass.simpleName })
                                        }
                                    }
                                }
                            }
                            return@withLock
                        }
                    }

                    if (isSimpleConversation(safeCommand)) {
                        val chatSystem = "You are Jarvis. Reply naturally and briefly in the user's language. This is conversation, not an Android action plan. Do not return JSON."
                        val chatStart = System.currentTimeMillis()
                        val chatTokens = (chatSystem.length + safeCommand.length) / 4
                        Log.i(TAG, "path=LLM mode=conversation approx_prompt_tokens=$chatTokens provider=${universalAdapter.getProviderName()}")
                        val reply = completeWithinBudget(
                            chatSystem,
                            safeCommand,
                            chatStart,
                            conversation = true
                        ).getOrElse { throw it }
                        graphifyRepo.logTask(safeCommand, "conversation response", universalAdapter.getProviderName(), System.currentTimeMillis() - chatStart)
                        _state.value = AgentState.Done(reply)
                        return@withLock
                    }

                    val fastCommands = FastCommandParser.parseSequence(safeCommand)
                    if (fastCommands != null) {
                        Log.i(TAG, "path=rule skill=fast_command count=${fastCommands.size}")
                        val historyCommand = when {
                            fastCommands.any { it is FastCommandParser.Command.Remember } -> "remember [encrypted local note]"
                            fastCommands.any { it is FastCommandParser.Command.Forget } -> "forget [local memory query]"
                            else -> safeCommand
                        }
                        val hideMemoryResult = fastCommands.any {
                            it is FastCommandParser.Command.Remember || it is FastCommandParser.Command.Forget
                        }
                        try {
                            val completed = mutableListOf<String>()
                            for ((index, command) in fastCommands.withIndex()) {
                                try {
                                    completed += executeFastCommand(command)
                                } catch (stepError: Exception) {
                                    val detail = stepError.message?.takeIf { it.isNotBlank() } ?: stepError.javaClass.simpleName
                                    throw IllegalStateException("Step ${index + 1} failed ($command): $detail", stepError)
                                }
                            }
                            val result = completed.joinToString(". ")
                            graphifyRepo.logTask(historyCommand, if (hideMemoryResult) "success (local memory updated)" else "success (fast path verified): $result", "Android fast path", 0)
                            _state.value = AgentState.Done(result)
                        } catch (error: Exception) {
                            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                            if (detail.startsWith("No contact matched")) {
                                val requested = detail.substringAfter("No contact matched '").substringBefore("'")
                                Log.i(TAG, "Contact lookup had no match for a user request")
                                _state.value = AgentState.Done("Mujhe '$requested' naam ka contact nahi mila. Kya dialer kholun?")
                            } else if (detail.startsWith("Multiple contacts found")) {
                                Log.i(TAG, "Contact lookup requires user disambiguation")
                                _state.value = AgentState.Done(detail)
                            } else {
                                AgentDiagnostics.record(context, "fast-command-execution", safeCommand, detail, error = error, provider = "Android fast path", model = "Not used")
                                graphifyRepo.logTask(historyCommand, "failed (fast path): $detail", "Android fast path", 0)
                                _state.value = AgentState.Error(detail)
                            }
                        }
                        return@withLock
                    }

                    _state.value = AgentState.Running("reading screen...")
                    // Keep local-model command planning lean: app ranking and graph-memory
                    // retrieval were adding latency before every request.
                    val screenText = runCatching {
                        screenReader.extractAllText()
                    }.onFailure { Log.e(TAG, "Screen extraction failed; continuing without OCR", it) }
                        .getOrDefault("")
                    val screenElements = runCatching {
                        screenReader.extractInteractiveElements()
                    }.onFailure { Log.e(TAG, "Interactive element extraction failed", it) }
                        .getOrDefault("")

                    val installedAppTargets = getInstalledLauncherApps()
                    val installedAppsPrompt = installedAppTargets
                        .filter { safeCommand.contains(it.label, ignoreCase = true) }
                        .joinToString(", ") { it.label }.take(150)
                    val needsMemory = Regex("remember|forget|memory|yaad|याद|लक्षात|विसर", RegexOption.IGNORE_CASE).containsMatchIn(safeCommand)
                    val memoryPrompt = if (needsMemory) personalMemoryStore.promptContext().take(150) else ""

                    val fullSystem = systemPrompt
                        .replace("{PERSONAL_MEMORY}", memoryPrompt)
                        .replace("{SCREEN_OCR}", screenText.take(150))
                        .replace("{SCREEN_ELEMENTS}", screenElements.take(650))
                        .replace("{INSTALLED_APPS}", installedAppsPrompt)

                    val estimatedPromptTokens = fullSystem.length / 4
                    Log.i(TAG, "path=LLM provider=${universalAdapter.getProviderName()} approx_prompt_tokens=${estimatedPromptTokens} chars=${fullSystem.length}")
                    if (estimatedPromptTokens > 400) Log.w(TAG, "Action prompt exceeded the ~400-token target after adding screen context")
                    _state.value = AgentState.Running("Soch raha hoon...")

                    val startTime = System.currentTimeMillis()

                    val result = completeWithinBudget(fullSystem, safeCommand, startTime)

                    result.fold(
                        onSuccess = { rawJson ->
                            var latency = System.currentTimeMillis() - startTime
                            val initialValidation = validateWithOneRepair(fullSystem, safeCommand, rawJson, startTime)
                            var effectiveRawJson = initialValidation.first
                            var validation = initialValidation.second

                            if (!validation.isValid) {
                                val reason = validation.errors.firstOrNull() ?: "Empty action plan"
                                AgentDiagnostics.record(context, "response-validation", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                _state.value = AgentState.Error("AI response invalid: $reason")
                                graphifyRepo.logTask(safeCommand, "failed: validation error", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            var actions = validation.actions

                            // A read_screen action cannot work as a later step in the same
                            // generated plan: the model has already finished planning before
                            // Android executes it. Read the screen locally, then ask the model
                            // once more with that actual text before executing any actions.
                            if (actions.any { it.action == Action.READ_SCREEN || it.action == Action.EXTRACT_TEXT }) {
                                _state.value = AgentState.Running("updating screen context...")
                                val refreshedScreen = runCatching { screenReader.extractAllText() }
                                    .getOrDefault("")
                                if (refreshedScreen.isBlank()) {
                                    val reason = "Android accessibility returned no readable screen text. Open the target app and make sure Jarvis Accessibility is enabled."
                                    AgentDiagnostics.record(context, "screen-context-refresh", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                    _state.value = AgentState.Error(reason)
                                    graphifyRepo.logTask(safeCommand, "failed: screen context unavailable", universalAdapter.getProviderName(), latency)
                                    return@fold
                                }

                                val refreshedElements = runCatching { screenReader.extractInteractiveElements() }.getOrDefault("")
                                val refreshedPrompt = systemPrompt
                                    .replace("{PERSONAL_MEMORY}", memoryPrompt)
                                    .replace("{SCREEN_OCR}", refreshedScreen.take(150))
                                    .replace("{SCREEN_ELEMENTS}", refreshedElements.take(650))
                                    .replace("{INSTALLED_APPS}", installedAppsPrompt) +
                                    "\n\nThe screen text and interactive elements above describe the fresh current screen. Now produce executable actions directly. Do not return read_screen or extract_text."
                                _state.value = AgentState.Running("screen samajh kar action bana raha hoon...")
                                val refreshedRaw = completeWithinBudget(refreshedPrompt, safeCommand, startTime).getOrElse { throw it }
                                val refreshedValidation = validateWithOneRepair(refreshedPrompt, safeCommand, refreshedRaw, startTime)
                                effectiveRawJson = refreshedValidation.first
                                latency = System.currentTimeMillis() - startTime
                                validation = refreshedValidation.second
                                if (!validation.isValid) {
                                    val reason = validation.errors.firstOrNull() ?: "The refreshed action plan was empty"
                                    AgentDiagnostics.record(context, "screen-replan-validation", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                    _state.value = AgentState.Error("AI could not create an action plan: $reason")
                                    graphifyRepo.logTask(safeCommand, "failed: refreshed plan invalid", universalAdapter.getProviderName(), latency)
                                    return@fold
                                }
                                actions = validation.actions
                            }

                            // Ground app launches in the user's command and actual installed launcher list.
                            actions = resolveOpenAppTargets(actions, safeCommand, installedAppTargets)

                            // OpenDroid-style bounded plan repair: small local models sometimes
                            // append an error marker after a partial plan or emit a long mixed
                            // navigation chain. Re-observe and ask once for a short executable
                            // plan before refusing or touching the device.
                            if (AgentPlanPolicy.needsCompactReplan(actions, safeCommand)) {
                                _state.value = AgentState.Running("clarifying action plan...")
                                val planScreen = runCatching { screenReader.extractAllText() }.getOrDefault("")
                                val planElements = runCatching { screenReader.extractInteractiveElements() }.getOrDefault("")
                                if (planScreen.isNotBlank()) {
                                    val compactPrompt = systemPrompt
                                        .replace("{PERSONAL_MEMORY}", memoryPrompt)
                                        .replace("{SCREEN_OCR}", planScreen.take(150))
                                        .replace("{SCREEN_ELEMENTS}", planElements.take(650))
                                        .replace("{INSTALLED_APPS}", installedAppsPrompt) +
                                        "\n\nPLAN REPAIR: Your previous response was not executable. " +
                                        "Return at most " + AgentPlanPolicy.MAX_ACTIONS + " short actions for the original goal. " +
                                        "Never mix an error action with executable actions. If uncertain, return exactly one error action. " +
                                        "Do not add back or home unless the user explicitly requested it. " +
                                        "Use the current screen above; no actions have run yet."
                                    val compactRaw = completeWithinBudget(compactPrompt, safeCommand, startTime).getOrElse { throw it }
                                    val compactValidation = validateWithOneRepair(compactPrompt, safeCommand, compactRaw, startTime)
                                    effectiveRawJson = compactValidation.first
                                    validation = compactValidation.second
                                    latency = System.currentTimeMillis() - startTime
                                    val groundedCompactActions = resolveOpenAppTargets(
                                        validation.actions, safeCommand, installedAppTargets
                                    )
                                    if (validation.isValid && AgentPlanPolicy.isExecutablePlan(groundedCompactActions, safeCommand)) {
                                        actions = groundedCompactActions
                                    } else {
                                        val reason = validation.errors.firstOrNull()
                                            ?: validation.actions.firstOrNull { it.action == Action.ERROR }?.message
                                            ?: "The local model could not produce a short, safe action plan."
                                        AgentDiagnostics.record(context, "action-plan-repair", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                        _state.value = AgentState.Error("Jarvis could not safely plan this task: " + reason)
                                        graphifyRepo.logTask(safeCommand, "failed: compact plan rejected", universalAdapter.getProviderName(), latency)
                                        return@fold
                                    }
                                }
                            }

                            // An explicit model refusal is not an Android action and must
                            // never be retried by the executor as if it were a device failure.
                            val refusal = actions.firstOrNull { it.action == Action.ERROR }
                            if (refusal != null) {
                                val rawReason = refusal.message.orEmpty().trim()
                                val reason = if (rawReason.isBlank() || rawReason.lowercase() in setOf("reason", "error", "unknown", "unavailable")) {
                                    "I couldn't determine a safe action from the current screen. Make sure the target app is open and give Jarvis a specific instruction."
                                } else rawReason
                                AgentDiagnostics.record(context, "action-planning-refusal", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                _state.value = AgentState.Error(reason)
                                graphifyRepo.logTask(safeCommand, "failed: model could not plan an action", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            // A second observation request means the model still has not
                            // produced executable work. Stop with a useful explanation.
                            if (actions.any { it.action == Action.READ_SCREEN || it.action == Action.EXTRACT_TEXT }) {
                                val reason = "The local model requested another screen read instead of choosing an action. Try a more specific command or reopen the target app."
                                AgentDiagnostics.record(context, "screen-replan-loop", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                _state.value = AgentState.Error(reason)
                                graphifyRepo.logTask(safeCommand, "failed: repeated screen observation request", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            // Final fail-closed gate: no unresolved app target or partial
                            // observation plan may reach the Android action executor.
                            if (!AgentPlanPolicy.isExecutablePlan(actions, safeCommand)) {
                                val reason = "The local model did not produce a complete, grounded action plan. Name the target app explicitly and try again. No device action was run."
                                AgentDiagnostics.record(context, "action-plan-final-guard", safeCommand, reason, effectiveRawJson, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                _state.value = AgentState.Error(reason)
                                graphifyRepo.logTask(safeCommand, "failed: final plan guard rejected actions", universalAdapter.getProviderName(), latency)
                                return@fold
                            }

                            _state.value = AgentState.Running(
                                "executing ${actions.size} actions..."
                            )

                            var execution = executeActions(actions)
                            var reEvaluationResponse = ""
                            val failedActionForReplan = execution.failedAction
                            if (!execution.succeeded && failedActionForReplan != null &&
                                AgentReEvaluationPolicy.canReplan(execution.completedActions, failedActionForReplan)
                            ) {
                                _state.value = AgentState.Running("checking why the step failed...")
                                val freshScreen = runCatching { screenReader.extractAllText() }.getOrDefault("")
                                if (freshScreen.isNotBlank()) {
                                    val completedSummary = execution.completedActions.joinToString(", ") { it.action }
                                        .ifBlank { "none" }
                                    val freshElements = runCatching { screenReader.extractInteractiveElements() }.getOrDefault("")
                                    val reEvaluationPrompt = systemPrompt
                                        .replace("{PERSONAL_MEMORY}", memoryPrompt)
                                        .replace("{SCREEN_OCR}", freshScreen.take(150))
                                        .replace("{SCREEN_ELEMENTS}", freshElements.take(650))
                                        .replace("{INSTALLED_APPS}", installedAppsPrompt) +
                                        "\n\nAGENT RE-EVALUATION: The original goal is still: $safeCommand. " +
                                        "Already completed steps: $completedSummary. " +
                                        "The last safe navigation/observation step failed: ${failedActionForReplan.action}. " +
                                        "Failure: ${execution.failure ?: "verification failed"}. " +
                                        "Re-plan ONLY the remaining work from the fresh screen above. " +
                                        "Do not repeat completed steps or repeat the failed step. Return only a non-empty JSON action array."
                                    _state.value = AgentState.Running("re-planning remaining steps...")
                                    val rawReEvaluation = completeWithinBudget(reEvaluationPrompt, safeCommand, startTime)
                                        .getOrElse { throw it }
                                    val repairedReEvaluation = validateWithOneRepair(reEvaluationPrompt, safeCommand, rawReEvaluation, startTime)
                                    reEvaluationResponse = repairedReEvaluation.first
                                    val reEvaluation = repairedReEvaluation.second
                                    if (reEvaluation.isValid &&
                                        AgentReEvaluationPolicy.isAcceptableReplan(reEvaluation.actions, execution.completedActions, failedActionForReplan)
                                    ) {
                                        latency = System.currentTimeMillis() - startTime
                                        _state.value = AgentState.Running("executing revised plan...")
                                        execution = executeActions(reEvaluation.actions)
                                    } else {
                                        val reason = reEvaluation.errors.firstOrNull()
                                            ?: reEvaluation.actions.firstOrNull { it.action == Action.ERROR }?.message
                                            ?: "The revised plan repeated a failed step or was not safe to execute."
                                        AgentDiagnostics.record(context, "agent-re-evaluation", safeCommand, reason, reEvaluationResponse, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
                                        _state.value = AgentState.Error("Jarvis could not safely re-plan: $reason")
                                    }
                                }
                            }
                            if (!execution.succeeded || _state.value is AgentState.Error) {
                                val failure = (_state.value as? AgentState.Error)?.message
                                    ?: execution.failure
                                    ?: "Action execution returned false"
                                AgentDiagnostics.record(context, "action-execution", safeCommand, failure, reEvaluationResponse.ifBlank { effectiveRawJson }, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
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
                                AgentState.Done("Completed ${actions.size} actions • done in ${latency}ms")
                        },

                        onFailure = { error ->
                            if (error.message?.contains("time budget", ignoreCase = true) == true) {
                                val spoken = "Ye command samajhne mein der lag rahi hai, chhota command bolo."
                                Log.i(TAG, "LLM time budget exceeded; returning spoken slow-model guidance")
                                graphifyRepo.logTask(safeCommand, "slow local model; shorter command requested", "Ollama (Local)", System.currentTimeMillis() - startTime)
                                _state.value = AgentState.Done(spoken)
                                return@fold
                            }
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
                                    } else detail.ifBlank { "${error.javaClass.simpleName} (no error message)" }
                                }
                            }

                            AgentDiagnostics.record(context, "ollama-request", safeCommand, msg, error = error, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
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
                    if (e.message?.contains("time budget", ignoreCase = true) == true) {
                        Log.i(TAG, "LLM time budget exceeded; returning spoken slow-model guidance")
                        _state.value = AgentState.Done("Ye command samajhne mein der lag rahi hai, chhota command bolo.")
                        return@withLock
                    }
                    Log.e(TAG, "Task execution crashed for command: $cleanCommand", e)
                    val detail = e.message?.takeIf { it.isNotBlank() }
                        ?: e.cause?.message?.takeIf { it.isNotBlank() }
                        ?: e.javaClass.simpleName
                    val errorMessage = if (universalAdapter.getProviderName() == "Ollama (Local)") {
                        "Ollama error: $detail"
                    } else detail.ifBlank { "${e.javaClass.simpleName} (no error message)" }
                    AgentDiagnostics.record(context, "agent-crash", cleanCommand, errorMessage, error = e, provider = universalAdapter.getProviderName(), model = "qwen3:1.7b")
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

    private fun getInstalledLauncherApps(): List<InstalledAppTarget> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString().trim() }.getOrNull()
                if (label.isNullOrBlank()) null else InstalledAppTarget(label, activity.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun resolveOpenAppTargets(
        actions: List<Action>,
        command: String,
        installedApps: List<InstalledAppTarget>
    ): List<Action> {
        val mentionedApps = AppTargetResolver.findMentionedApps(command, installedApps)
        var nextAppIndex = 0
        return actions.map { action ->
            if (action.action != Action.OPEN_APP) return@map action
            val target = mentionedApps?.getOrNull(nextAppIndex)
            nextAppIndex += 1
            if (target == null) action.copy(label = null, packageName = null)
            else action.copy(label = target.label, packageName = target.packageName)
        }
    }

    suspend fun getAnalyzedAppCount(): Int =
        appAnalyzer.getAnalyzedCount()

    suspend fun getAIAppCount(): Int =
        appAnalyzer.getAICount()

    private suspend fun executeActions(actions: List<Action>): ActionExecutionResult {
        val results = mutableListOf<String>()
        val completedActions = mutableListOf<Action>()

        suspend fun executeOnce(action: Action): String? {
            val activeService = JarvisAccessibilityService.instance
            return when (action.action) {
                Action.OPEN_APP -> {
                    val label = action.label
                    val packageName = action.packageName ?: label?.let { resolveAppPackage(it) }
                    if (packageName.isNullOrBlank()) return "No installed app was matched to the app name in your command"
                    // Prefer Android's native launch intent (same path as the fast-command
                    // executor). Accessibility remains responsible for observing and operating
                    // the opened app, not for starting it.
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: return "No launcher activity is available for ${label ?: packageName}"
                    try {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    } catch (error: Exception) {
                        return "Android could not launch ${label ?: packageName}: ${error.message ?: error.javaClass.simpleName}"
                    }
                    var foregroundConfirmed = false
                    for (check in 0 until 16) {
                        if (JarvisAccessibilityService.instance?.isPackageActive(packageName) == true) {
                            foregroundConfirmed = true
                            break
                        }
                        kotlinx.coroutines.delay(250)
                    }
                    if (!foregroundConfirmed) {
                        Log.w(TAG, "Native app launch accepted for $packageName; foreground confirmation unavailable. Continuing with fresh screen observation.")
                    }
                    null
                }

                Action.CLICK, Action.TAP -> {
                    val target = action.text
                    if (target.isNullOrBlank()) return "Click target is missing"
                    if (activeService == null || !activeService.tapByText(target)) return "Could not click visible text: $target"
                    null // AccessibilityNodeInfo.ACTION_CLICK returned true.
                }

                Action.TYPE_TEXT, Action.TYPE -> {
                    val value = action.value
                    if (value.isNullOrEmpty()) return "Text to enter is missing"
                    val fieldTarget = action.text ?: action.hint
                    if (activeService == null || !activeService.typeText(value, fieldTarget)) {
                        return if (fieldTarget.isNullOrBlank()) "Could not type: no editable field accepted the text"
                        else "Could not find the requested editable field: $fieldTarget"
                    }
                    kotlinx.coroutines.delay(250)
                    if (activeService.containsVisibleText(value)) null else "Text entry was sent but could not be verified on screen"
                }

                Action.SCROLL -> {
                    val direction = action.direction?.lowercase()?.takeIf { it in listOf("up", "down", "forward", "backward") } ?: "down"
                    if (activeService == null || !activeService.scrollScreen(direction)) "No scrollable area accepted scroll $direction" else null
                }

                Action.BACK, Action.PRESS_BACK -> if (activeService?.pressBack() == true) null else "Android Back action failed"
                Action.HOME, Action.PRESS_HOME -> if (activeService?.pressHome() == true) null else "Android Home action failed"
                Action.PRESS_RECENTS -> if (activeService?.pressRecents() == true) null else "Android Recent Apps action failed"

                Action.WAIT, Action.WAIT_FOR -> {
                    val expected = action.text
                    if (expected.isNullOrBlank()) return "Wait action is missing the text to verify"
                    val timeout = action.timeoutMs.coerceIn(500L, 15000L)
                    val started = System.currentTimeMillis()
                    while (System.currentTimeMillis() - started < timeout) {
                        if (activeService?.containsVisibleText(expected) == true ||
                            screenReader.extractAllText().contains(expected, ignoreCase = true)) return null
                        kotlinx.coroutines.delay(300)
                    }
                    "Timed out waiting for visible text: $expected"
                }

                Action.READ_SCREEN -> {
                    val screen = screenReader.extractAllText()
                    if (screen.isBlank()) "Could not read current screen (no accessible text)" else {
                        workingMemory.set(action.outputKey ?: "page_text", screen)
                        null
                    }
                }

                Action.AI_PROMPT -> {
                    val packageName = action.packageName
                    val prompt = workingMemory.interpolate(action.prompt ?: "")
                    val outputKey = action.outputKey
                    if (packageName == null) return "AI app package is missing"
                    val meta = AIApps.KNOWN_AI_APPS[packageName] ?: return "Unsupported AI app: $packageName"
                    aiAppInteractor.openAIApp(meta)
                    kotlinx.coroutines.delay(2000)
                    aiAppInteractor.typePrompt(prompt)
                    kotlinx.coroutines.delay(1000)
                    val response = aiAppInteractor.waitForResponse()
                    if (response.isBlank()) "AI app did not return a response" else {
                        if (outputKey != null) workingMemory.set(outputKey, response)
                        null
                    }
                }

                Action.EXTRACT_TEXT -> {
                    val outputKey = action.outputKey ?: "page_text"
                    val text = screenReader.extractAllText()
                    if (text.isBlank()) "Could not extract text from current screen" else {
                        workingMemory.set(outputKey, text)
                        null
                    }
                }

                Action.ERROR -> action.message ?: "Task failed"
                else -> "Unsupported action: ${action.action}"
            }
        }

        for ((index, action) in actions.withIndex()) {
            var failure: String? = null
            var completed = false
            val maxAttempts = ActionRetryPolicy.maxAttempts(action)
            for (attempt in 1..maxAttempts) {
                _state.value = AgentState.Running(
                    "Action ${index + 1}/${actions.size}: ${action.action} (attempt $attempt/$maxAttempts)"
                )
                failure = try {
                    executeOnce(action)
                } catch (error: Exception) {
                    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                }
                if (failure == null) {
                    completed = true
                    break
                }
                Log.w(TAG, "Action ${index + 1} ${action.action} attempt $attempt/$maxAttempts failed: $failure")
                if (ActionRetryPolicy.shouldRetry(action, attempt)) kotlinx.coroutines.delay(400)
            }

            if (!completed) {
                val retryNote = if (maxAttempts > 1) "after one safe retry" else "without retry (to prevent duplicate side effects)"
                val message = "Action ${index + 1}/${actions.size} (${action.action}) failed $retryNote: ${failure ?: "verification failed"}"
                results.add(message)
                Log.e(TAG, message)
                _state.value = AgentState.Error(message)
                return ActionExecutionResult(
                    succeeded = false,
                    completedActions = completedActions.toList(),
                    failedAction = action,
                    failure = message
                )
            }
            completedActions.add(action)
            val actionResult = if (action.action == Action.OPEN_APP) {
                "Action ${index + 1}/${actions.size} ${action.action}: launch accepted"
            } else {
                "Action ${index + 1}/${actions.size} ${action.action}: completed"
            }
            results.add(actionResult)
            Log.i(TAG, results.last())
            kotlinx.coroutines.delay(250)
        }
        Log.i(TAG, "Command completed: ${results.joinToString("; ")}")
        return ActionExecutionResult(
            succeeded = true,
            completedActions = completedActions.toList()
        )
    }

    private suspend fun executeFastCommand(command: FastCommandParser.Command): String {
        // An app launch is not safe to retry after startActivity succeeds: a failed
        // foreground observation must never launch the same app a second time.
        if (command is FastCommandParser.Command.OpenApp ||
            command is FastCommandParser.Command.DialContact ||
            command is FastCommandParser.Command.YoutubeSearch) {
            return executeFastCommandOnce(command)
        }
        var lastFailure = "Command could not be verified"
        for (attempt in 1..2) {
            try {
                return executeFastCommandOnce(command)
            } catch (error: Exception) {
                lastFailure = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                Log.w(TAG, "Fast command attempt $attempt failed: $lastFailure")
                if (attempt == 1) kotlinx.coroutines.delay(400)
            }
        }
        throw IllegalStateException("Command failed after one retry: $lastFailure")
    }

    private suspend fun executeFastCommandOnce(command: FastCommandParser.Command): String {
        return when (command) {
            is FastCommandParser.Command.OpenApp -> {
                val packageName = resolveAppPackage(command.target)
                    ?: throw IllegalStateException("App not found: ${command.target}")
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw IllegalStateException("No launch activity available for ${command.target} ($packageName)")
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } catch (error: Exception) {
                    throw IllegalStateException("Android could not launch ${command.target}: ${error.message ?: error.javaClass.simpleName}", error)
                }

                var foregroundConfirmed = false
                for (check in 0 until 16) {
                    if (JarvisAccessibilityService.instance?.isPackageActive(packageName) == true) {
                        foregroundConfirmed = true
                        break
                    }
                    kotlinx.coroutines.delay(250)
                }
                if (foregroundConfirmed) {
                    "Opened ${command.target} (foreground confirmed)"
                } else {
                    Log.w(TAG, "Launch accepted for ${command.target} ($packageName), but foreground confirmation was unavailable")
                    "Launch sent to ${command.target}; Android foreground confirmation unavailable"
                }
            }
            is FastCommandParser.Command.Remember -> {
                if (!personalMemoryStore.remember(command.fact)) {
                    throw IllegalArgumentException("Memory note must be 3 to 180 characters")
                }
                "Saved locally: ${command.fact}"
            }
            is FastCommandParser.Command.Forget -> {
                if (personalMemoryStore.forget(command.query)) "Removed matching saved memory"
                else "No matching saved memory found"
            }
            FastCommandParser.Command.ShowMemory -> {
                val items = personalMemoryStore.getAll()
                if (items.isEmpty()) "No personal memories saved yet"
                else "Your saved memories:\\n" + items.joinToString("\\n") { "• $it" }
            }
            FastCommandParser.Command.CloseCurrentApp -> {
                val service = JarvisAccessibilityService.instance
                if (service?.pressHome() != true) throw IllegalStateException("Could not return to Home to close the current app")
                "Closed current app (returned to Home)"
            }
            FastCommandParser.Command.Back -> {
                if (JarvisAccessibilityService.instance?.pressBack() != true) throw IllegalStateException("Android Back action failed")
                "Went back"
            }
            FastCommandParser.Command.Home -> {
                if (JarvisAccessibilityService.instance?.pressHome() != true) throw IllegalStateException("Android Home action failed")
                "Returned Home"
            }
            is FastCommandParser.Command.Scroll -> {
                if (JarvisAccessibilityService.instance?.scrollScreen(command.direction) != true) {
                    throw IllegalStateException("No scrollable area accepted scroll ${command.direction}")
                }
                "Scrolled ${command.direction}"
            }
            is FastCommandParser.Command.OpenSettings -> {
                val action = when (command.action) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                kotlinx.coroutines.delay(700)
                if (screenReader.extractAllText().isBlank()) throw IllegalStateException("Settings opened, but the screen could not be verified")
                "Opened ${command.action} settings"
            }
            is FastCommandParser.Command.Dial -> {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", command.number, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                kotlinx.coroutines.delay(700)
                if (screenReader.extractAllText().isBlank()) throw IllegalStateException("Dialer opened, but the screen could not be verified")
                "Opened dialer for ${command.number} (call not placed)"
            }
            is FastCommandParser.Command.DialContact -> {
                val number = resolveContactNumber(command.name)
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Dialer launch requested for ${command.name} (call not placed)"
            }
            is FastCommandParser.Command.YoutubeSearch -> {
                val encoded = java.net.URLEncoder.encode(command.query, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val youtube = "com.google.android.youtube"
                val youtubeInstalled = runCatching { context.packageManager.getPackageInfo(youtube, 0) }.isSuccess
                if (youtubeInstalled) intent.setPackage(youtube)
                val resolved = intent.resolveActivity(context.packageManager)
                    ?: throw IllegalStateException("No app or browser can open YouTube search")
                val targetPackage = if (youtubeInstalled) youtube else resolved.packageName
                context.startActivity(intent)
                var foregroundConfirmed = false
                for (check in 0 until 16) {
                    if (JarvisAccessibilityService.instance?.isPackageActive(targetPackage) == true) {
                        foregroundConfirmed = true
                        break
                    }
                    kotlinx.coroutines.delay(250)
                }
                if (!foregroundConfirmed) {
                    Log.w(TAG, "YouTube ACTION_VIEW was accepted, but foreground confirmation was unavailable")
                    "YouTube search launch requested for ${command.query}; foreground confirmation unavailable"
                } else {
                    "Opened YouTube search for ${command.query} (foreground confirmed)"
                }
            }
        }
    }

    private suspend fun resolveContactNumber(requestedName: String): String {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = context.getSharedPreferences("jarvis_contact_permission", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("completed", false).putBoolean("granted", false).apply()
            try {
                context.startActivity(Intent(context, com.openjarvis.ui.ContactPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (error: Exception) {
                throw IllegalStateException("Contacts permission is required. Open Jarvis and allow Contacts permission.", error)
            }
            var completed = false
            for (attempt in 0 until 60) {
                kotlinx.coroutines.delay(500)
                if (prefs.getBoolean("completed", false)) { completed = true; break }
                if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) { completed = true; break }
            }
            if (!completed || context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                throw IllegalStateException("Contacts permission not granted. Enable it in Android Settings > Apps > Open Jarvis > Permissions > Contacts.")
            }
        }
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        val matches = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")?.use { cursor ->
            val ni = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pi = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(ni).orEmpty()
                val number = cursor.getString(pi).orEmpty()
                if (name.isNotBlank() && number.isNotBlank() && contactNameMatches(requestedName, name)) matches += name to number
            }
        } ?: throw IllegalStateException("Could not read contacts. Check Contacts permission and try again.")
        val unique = matches.distinctBy {
            it.first.trim().lowercase(java.util.Locale.ROOT) + "|" + it.second.replace(Regex("[^+0-9]"), "")
        }
        if (unique.isEmpty()) throw IllegalStateException("No contact matched '$requestedName'. Check the name in Contacts.")
        if (unique.size > 1) {
            val names = unique.take(6).joinToString(", ") { match ->
                val lastDigits = match.second.filter { it.isDigit() }.takeLast(4)
                if (lastDigits.isBlank()) match.first else "${match.first} (ending $lastDigits)"
            }
            throw IllegalStateException("Multiple contacts found: $names. Which one should I dial? Please say one exact/full name; I will not choose automatically.")
        }
        return unique.single().second
    }

    private fun contactNameMatches(requested: String, stored: String): Boolean =
        ContactNameMatcher.matches(requested, stored)

    private fun resolveAppPackage(label: String): String? {
        if (AppTargetResolver.isPlaceholder(label)) return null
        if (label.equals("settings", ignoreCase = true)) return "com.android.settings"
        val matches = AppTargetResolver.findMentionedApps(label, getInstalledLauncherApps()) ?: return null
        return matches.singleOrNull()?.packageName ?: findPackageByLabel(label)
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

        if (AppTargetResolver.isPlaceholder(label)) return null
        val normalizedLabel = label.lowercase().trim()
        if (normalizedLabel.isBlank()) return null

        val candidates = apps.map { app ->
            app.loadLabel(pm).toString().lowercase().trim() to app.activityInfo.packageName
        }.distinctBy { it.second }

        // Prefer an exact launcher-label match. Never let a short substring such as
        // "app" accidentally select an unrelated installed application.
        candidates.firstOrNull { it.first == normalizedLabel }?.let { return it.second }

        val prefixMatches = candidates.filter { (appLabel, _) ->
            appLabel.startsWith(normalizedLabel) && normalizedLabel.length >= 3
        }
        if (prefixMatches.size == 1) return prefixMatches.first().second

        val containsMatches = candidates.filter { (appLabel, _) ->
            normalizedLabel.length >= 4 &&
                (appLabel.contains(normalizedLabel) || normalizedLabel.contains(appLabel) && appLabel.length >= 4)
        }
        return containsMatches.singleOrNull()?.second
    }
}
