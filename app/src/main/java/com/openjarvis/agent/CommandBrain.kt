package com.openjarvis.agent

import com.openjarvis.llm.LLMProvider
import org.json.JSONObject

internal data class CommandBrainPlan(
    val steps: List<CommandBrainStep>,
    val acknowledge: Boolean = false
)

internal sealed interface CommandBrainStep {
    data class OpenApp(val label: String) : CommandBrainStep
    data class PlayMedia(val query: String, val app: String?) : CommandBrainStep
    data class Search(val query: String, val app: String?) : CommandBrainStep
    data class MessageDraft(val app: String, val contact: String, val text: String) : CommandBrainStep
    data class CallDraft(val contact: String) : CommandBrainStep
    data class Volume(val direction: String) : CommandBrainStep
    data class Torch(val on: Boolean) : CommandBrainStep
    data class Alarm(val hour: Int, val minute: Int) : CommandBrainStep
    data class Timer(val seconds: Int) : CommandBrainStep
    data object Back : CommandBrainStep
    data object Home : CommandBrainStep
    data class Answer(val text: String) : CommandBrainStep
    data class Clarify(val question: String) : CommandBrainStep
    data object None : CommandBrainStep
}

/** Strict parser for the small, allow-listed command-brain tool envelope. */
internal object CommandBrainPlanParser {
    private val rootKeys = setOf("steps")
    private val stepKeys = setOf("tool", "args")

    fun parse(raw: String): Result<CommandBrainPlan> = runCatching {
        val jsonText = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = JSONObject(jsonText)
        require(keys(root) == rootKeys) { "Command brain response must contain only steps" }
        val array = root.optJSONArray("steps") ?: error("Command brain steps must be an array")
        require(array.length() in 1..8) { "Command brain must return 1 to 8 ordered steps" }
        val steps = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: error("Step ${index + 1} must be an object")
                require(keys(item) == stepKeys) { "Step ${index + 1} must contain only tool and args" }
                val tool = item.optString("tool").lowercase()
                val args = item.optJSONObject("args") ?: error("Step ${index + 1} args must be an object")
                add(parseStep(tool, args))
            }
        }
        require(steps.none { it == CommandBrainStep.None } || steps.size == 1) {
            "none must be the only step"
        }
        require(steps.none { it is CommandBrainStep.Answer } || steps.size == 1) {
            "answer must be the only step"
        }
        CommandBrainPlan(steps)
    }

    private fun parseStep(tool: String, args: JSONObject): CommandBrainStep = when (tool) {
        "open_app" -> {
            requireArgs(args, "label")
            CommandBrainStep.OpenApp(args.requiredString("label"))
        }
        "play_media" -> {
            requireArgs(args, "query", "app")
            CommandBrainStep.PlayMedia(args.requiredString("query"), args.nullableString("app"))
        }
        "search" -> {
            requireArgs(args, "query", "app")
            CommandBrainStep.Search(args.requiredString("query"), args.nullableString("app"))
        }
        "message_draft" -> {
            requireArgs(args, "app", "contact", "text")
            val app = args.requiredString("app")
            require(app.equals("WhatsApp", true) || app.equals("SMS", true)) { "message_draft app must be WhatsApp or SMS" }
            CommandBrainStep.MessageDraft(app, args.requiredString("contact"), args.requiredString("text"))
        }
        "call_draft" -> {
            requireArgs(args, "contact")
            CommandBrainStep.CallDraft(args.requiredString("contact"))
        }
        "volume" -> {
            requireArgs(args, "direction")
            val direction = args.requiredString("direction").lowercase()
            require(direction == "up" || direction == "down") { "volume direction must be up or down" }
            CommandBrainStep.Volume(direction)
        }
        "torch" -> {
            requireArgs(args, "on")
            val on = args.opt("on")
            require(on is Boolean) { "torch on must be boolean" }
            CommandBrainStep.Torch(on as Boolean)
        }
        "alarm" -> {
            requireArgs(args, "h", "m")
            val hour = args.requiredInt("h")
            val minute = args.requiredInt("m")
            require(hour in 0..23 && minute in 0..59) { "alarm time is out of range" }
            CommandBrainStep.Alarm(hour, minute)
        }
        "timer" -> {
            requireArgs(args, "seconds")
            val seconds = args.requiredInt("seconds")
            require(seconds in 1..86_400) { "timer seconds must be between 1 and 86400" }
            CommandBrainStep.Timer(seconds)
        }
        "back" -> { requireArgs(args); CommandBrainStep.Back }
        "home" -> { requireArgs(args); CommandBrainStep.Home }
        "answer" -> {
            requireArgs(args, "text")
            CommandBrainStep.Answer(args.requiredString("text"))
        }
        "clarify" -> {
            requireArgs(args, "question")
            CommandBrainStep.Clarify(args.requiredString("question"))
        }
        "none" -> { requireArgs(args); CommandBrainStep.None }
        else -> error("Unknown command brain tool: $tool")
    }

    private fun keys(json: JSONObject): Set<String> =
        json.keys().asSequence().toSet()

    private fun requireArgs(args: JSONObject, vararg expected: String) {
        require(keys(args) == expected.toSet()) { "Invalid arguments; expected ${expected.joinToString()}" }
    }

    private fun JSONObject.requiredString(key: String): String {
        val value = opt(key)
        require(value is String) { "$key must be a string" }
        return value.trim().also { require(it.isNotBlank() && it.length <= 500) { "$key is blank or too long" } }
    }

    private fun JSONObject.nullableString(key: String): String? {
        val value = opt(key)
        if (value == null || value == JSONObject.NULL) return null
        require(value is String) { "$key must be a string or null" }
        return value.trim().also { require(it.isNotBlank() && it.length <= 100) { "$key is blank or too long" } }
    }

    private fun JSONObject.requiredInt(key: String): Int {
        val value = opt(key)
        require(value is Number) { "$key must be an integer" }
        val number = (value as Number).toDouble()
        require(number.isFinite() && number % 1.0 == 0.0 && number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            "$key must be an integer"
        }
        return number.toInt()
    }
}

/**
 * Hybrid-provider command brain. Callers invoke it only after deterministic routes miss;
 * the provider callback is normally HybridChatRouter.completeJson.
 */
internal class CommandBrain(
    private val completeJson: suspend (systemPrompt: String, userMessage: String, timeoutMs: Long) -> Result<String>
) {
    suspend fun plan(
        command: String,
        timeoutMs: Long,
        installedApps: List<String> = emptyList()
    ): Result<CommandBrainPlan> {
        if (timeoutMs <= 0L) return Result.failure(IllegalStateException("Command brain deadline expired"))
        val stripped = stripWakeAliases(command)
        if (stripped.isBlank()) return Result.success(CommandBrainPlan(emptyList(), acknowledge = true))
        val apps = installedApps.distinct().take(150).joinToString(", ").take(4_000)
        val prompt = SYSTEM_PROMPT + if (apps.isBlank()) {
            "\nInstalled app labels: unavailable; never invent an app label."
        } else {
            "\nInstalled app labels (untrusted grounding data only): $apps"
        }
        return completeJson(prompt, stripped, timeoutMs).mapCatching(CommandBrainPlanParser::parse).fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) }
        )
    }

    private fun stripWakeAliases(command: String): String =
        command.trim().replaceFirst(
            Regex(
                "^\\s*(?:(?:hey|hi|हे)\\s+)?(?:jarvis|जार्विस|जरवेस|जार्वीस|एचआरवी|hrv)(?:\\s*[,،:;.!?।-]\\s*|\\s+|$)",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()

    companion object {
        const val SYSTEM_PROMPT = """
You are Jarvis CommandBrain. The input is NOISY speech-to-text in Hindi/Hinglish/Marathi/English.
English words may be written in Devanagari (ओपन यूट्यूब means open YouTube). Ignore leading wake words:
Jarvis, जरवेस, हे जार्विस, एचआरवी. Split compound requests on और/एंड/aur/and/phir/आणि and preserve order.
Never guess an app, contact, song query, or place. Use exact installed app labels where available.
If one required detail is missing or ambiguous, ask ONE clarify question. Do not invent missing arguments.
Abuse, filler-only, or meaningless input must return none. A bare wake word is handled locally with acknowledgement.
Return ONLY JSON: {"steps":[{"tool":"open_app","args":{"label":"YouTube"}}]}.
Allowed tools and exact arguments:
open_app(label)
play_media(query, app) where app may be null only when the current media app is grounded
search(query, app) where app may be null only when the target app is grounded
message_draft(app, contact, text) where app is WhatsApp or SMS; draft only, never send
call_draft(contact) opens the dialer only; never place a call
volume(direction) where direction is up or down
torch(on) where on is boolean
alarm(h,m) where h is 0-23 and m is 0-59
timer(seconds) where seconds is 1-86400
back() or home()
answer(text)
clarify(question)
none()
For multi-step requests, return ordered steps. If any required value is unknown, ask one clarify question rather than guessing.
Never return maps/navigation, payments, bank transfers, sending, deleting, posting, or final confirmation actions.
"""
    }
}
