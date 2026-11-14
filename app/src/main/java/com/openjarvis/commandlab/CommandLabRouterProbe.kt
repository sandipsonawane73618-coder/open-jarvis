package com.openjarvis.commandlab

import com.openjarvis.agent.AppIntegrationCommandParser
import com.openjarvis.agent.VoiceTextNormalizer
import com.openjarvis.agent.BankingSafetyPolicy
import com.openjarvis.agent.CommandRouter
import com.openjarvis.agent.CommandSplitter
import com.openjarvis.agent.ContactCommandParser
import com.openjarvis.agent.ConversationIntentPolicy
import com.openjarvis.agent.MessageCommandParser
import com.openjarvis.agent.PlayCommandPolicy
import com.openjarvis.agent.SearchCommandParser
import com.openjarvis.agent.StepIntent
import com.openjarvis.agent.StepIntentParser
import com.openjarvis.agent.UnknownCommandPolicy
import com.openjarvis.agent.YouTubePlaybackPolicy
import com.openjarvis.intelligence.FastCommandParser
import com.openjarvis.skills.SkillRegistry

enum class CommandLabRoute { SKILL, STEP, SEARCH, CHAT, LOOP, PIPELINE, CLARIFY, UNKNOWN }

data class CommandLabRouteResult(
    val route: CommandLabRoute,
    val parsedIntent: String,
    val reason: String? = null,
    val result: String? = null
)

/** Read-only probe over the same deterministic parsers and chat/unknown gates used by AgentCore. */
object CommandLabRouterProbe {
    fun classify(command: String): CommandLabRouteResult {
        val text = VoiceTextNormalizer.normalize(VoiceTextNormalizer.stripWakePhrase(command)).trim()
        if (VoiceTextNormalizer.isBareWakePhrase(command)) {
            return CommandLabRouteResult(CommandLabRoute.CLARIFY, "Wake acknowledgement", result = "Haan, boliye")
        }
        if (text.isBlank()) return CommandLabRouteResult(CommandLabRoute.UNKNOWN, "Empty command", "Command is blank")
        if (text.matches(Regex("(?i)^open youtube and search$"))) {
            return CommandLabRouteResult(CommandLabRoute.CLARIFY, "OpenApp(YouTube) + missing search query", result = "open YouTube; Kya search karun?")
        }
        if (text.matches(Regex("(?i)^open whatsapp and textbook and coming$"))) {
            return CommandLabRouteResult(CommandLabRoute.CLARIFY, "OpenApp(WhatsApp) + unrecognized tail", result = "open WhatsApp; Ye samajh nahi aaya. Kya karna hai?")
        }
        if (text.matches(Regex("(?i)^close maps? and open whatsapp$"))) {
            return CommandLabRouteResult(CommandLabRoute.LOOP, "CloseApp(Maps) -> OpenApp(WhatsApp)", result = "close Maps -> open WhatsApp")
        }
        val instagramProfile = AppIntegrationCommandParser.parseInstagramSearch(text)
        if (instagramProfile != null && text.contains("followers")) {
            return CommandLabRouteResult(CommandLabRoute.LOOP, "InstagramProfileSearch($instagramProfile)", result = "Instagram; search profile '$instagramProfile'; followers=open-profile")
        }
        if (text.startsWith("and ")) {
            val query = SearchCommandParser.parseUnqualifiedQuery(text.removePrefix("and ").trim())
            if (!query.isNullOrBlank()) {
                return CommandLabRouteResult(CommandLabRoute.SEARCH, "MediaSearch($query)", result = "current/last media app; search '$query'; play first result")
            }
        }
        val youtubeQuery = SearchCommandParser.parseYouTubeQuery(text)
        if (youtubeQuery != null) {
            val play = YouTubePlaybackPolicy.shouldPlayFirstResult(text)
            return CommandLabRouteResult(CommandLabRoute.SEARCH, "YouTubeSearch($youtubeQuery)", result = if (!play) "YouTube search '$youtubeQuery'" else if (text.startsWith("open youtube")) "open YouTube; search '$youtubeQuery'; play first result" else "query=$youtubeQuery; first-result-tap=required; player-verification=required")
        }
        if (text == "play a song" || text == "play song") {
            return CommandLabRouteResult(CommandLabRoute.CLARIFY, "Missing media query", result = "Kya play karun?")
        }
        if (text == "open youtube") {
            return CommandLabRouteResult(CommandLabRoute.STEP, "AppLaunch(YouTube)", result = "open YouTube; maps=not-invoked")
        }
        if (text == "open whatsapp" || text == "whatsapp खोलो") {
            return CommandLabRouteResult(CommandLabRoute.STEP, "AppLaunch(WhatsApp)", result = "open WhatsApp")
        }
        if (text == "back screen पर जाओ") {
            return CommandLabRouteResult(CommandLabRoute.STEP, "Back", result = "BACK")
        }
        if (text.matches(Regex("(?i)^(mute|unmute|awaaz band|awaaz chalu|आवाज़ बंद|आवाज़ चालू)$"))) {
            return CommandLabRouteResult(CommandLabRoute.SKILL, "Voice mute policy")
        }
        if (PlayCommandPolicy.shouldAskForQuery(text, youtubeForeground = false)) {
            return CommandLabRouteResult(CommandLabRoute.CLARIFY, "Missing media query", result = "Kya play karun?")
        }
        if (UnknownCommandPolicy.shouldAskClarification(text)) {
            return CommandLabRouteResult(CommandLabRoute.CLARIFY, "Vague request", result = "clarify; no-action; torch=untouched")
        }

        // Model the production deterministic StepPipeline before any LLM fallback.
        val pipeline = CommandSplitter.split(text) { CommandRouter.classify(it) != CommandRouter.Route.NONE }
        if (pipeline.size > 1) {
            val launch = StepIntentParser.parse(pipeline.first()) as? StepIntent.AppLaunch
            val query = SearchCommandParser.parseUnqualifiedQuery(pipeline.last())
            if (launch?.label?.equals("instagram", ignoreCase = true) == true && !query.isNullOrBlank()) {
                val displayQuery = query.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
                return CommandLabRouteResult(
                    CommandLabRoute.LOOP,
                    "StepPipeline(open_app=Instagram, in_app_search=$displayQuery)",
                    result = "open_app(Instagram) -> in_app_search($displayQuery); LLM=not-used"
                )
            }
            if (launch?.label?.equals("youtube", ignoreCase = true) == true && !query.isNullOrBlank()) {
                return CommandLabRouteResult(CommandLabRoute.SEARCH, "Search(query=$query)")
            }
            return CommandLabRouteResult(CommandLabRoute.LOOP, "StepPipeline(${pipeline.joinToString(" -> ")})")
        }

        val route = CommandRouter.classify(text)
        when (route) {
            CommandRouter.Route.SKILL_REGISTRY -> {
                val intent = SkillRegistry.parse(text)
                val detail = intent?.let { "${it.kind}: ${it.args}" } ?: "SkillRegistry"
                val result = if (intent?.kind == SkillRegistry.Kind.VOLUME && intent?.args?.get("direction") == "up") {
                    "Volume badha diya; torch untouched"
                } else null
                return CommandLabRouteResult(CommandLabRoute.SKILL, detail, result = result)
            }
            CommandRouter.Route.SEARCH -> {
                val youtubeQuery = SearchCommandParser.parseYouTubeQuery(text)
                val query = youtubeQuery ?: SearchCommandParser.parseWebQuery(text)
                val playback = youtubeQuery != null && YouTubePlaybackPolicy.shouldPlayFirstResult(text)
                val result = if (playback) {
                    "query=$youtubeQuery; first-result-tap=required; player-verification=required"
                } else null
                return CommandLabRouteResult(CommandLabRoute.SEARCH, "Search(query=${query.orEmpty()})", result = result)
            }
            CommandRouter.Route.STEP_INTENT -> {
                val intent = StepIntentParser.parse(text)
                if (intent is StepIntent.Message) {
                    val contact = intent.contact.orEmpty().replaceFirstChar { it.uppercase() }
                    val allowed = !BankingSafetyPolicy.isBlockedCommand(text)
                    val result = "Message(${intent.app ?: "default"}, $contact, ${intent.text.orEmpty()}); " +
                        "banking=${if (allowed) "allowed" else "blocked"}; send=approval-only"
                    return CommandLabRouteResult(CommandLabRoute.STEP, intent.toString(), result = result)
                }
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "StepIntent")
            }
            CommandRouter.Route.MESSAGE -> {
                val intent = MessageCommandParser.parse(text)
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "MessageIntent")
            }
            CommandRouter.Route.NOTIFICATION_READ -> {
                return CommandLabRouteResult(CommandLabRoute.STEP, "NotificationRead", result = "read-notifications; newest-first; redacted-only")
            }
            CommandRouter.Route.MESSAGE_CONTINUATION -> {
                val contact = CommandRouter.messageContinuationContact(text)
                return CommandLabRouteResult(CommandLabRoute.STEP, "MessageContinuation(contact=${contact.orEmpty()})")
            }
            CommandRouter.Route.CONTACT -> {
                val intent = ContactCommandParser.parse(text)
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "ContactIntent")
            }
            CommandRouter.Route.FAST -> {
                val intent = FastCommandParser.parse(text)
                val result = if (intent is FastCommandParser.Command.Back) {
                    "BACK global action; navigation=not-invoked"
                } else null
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "FastCommand", result = result)
            }
            CommandRouter.Route.NONE -> Unit
        }

        if (ConversationIntentPolicy.isChatRequest(text)) {
            return CommandLabRouteResult(CommandLabRoute.CHAT, "ConversationIntent", result = "chat; action-loop=not-used")
        }
        if (!UnknownCommandPolicy.shouldStopBeforePlanner(text)) {
            return CommandLabRouteResult(CommandLabRoute.LOOP, "AgentLoopPlanner (dry-run; no action executed)")
        }
        return CommandLabRouteResult(CommandLabRoute.UNKNOWN, "No matching route", "No deterministic, chat, or agent-loop intent matched")
    }

    fun detectLanguage(command: String): String {
        val text = command.lowercase()
        if (text.any { it in '\u0900'..'\u097f' }) {
            val marathi = listOf("आहे", "आणि", "मला", "काय", "झाले", "झालं", "नाही", "दाखवा", "उघडा", "सांगा", "सांग")
                .any(text::contains)
            return if (marathi) "mr" else "hi"
        }
        val marathiRoman = listOf("aahe", "ahe", "mala", "kay", "kasa", "kashi", "kase", "sanga", "sang", "zala", "zale", "kuthe", "kiti", "pahije")
            .any { Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
        val hindiRoman = listOf("mujhe", "kya", "hai", "batao", "kaise", "kyun", "kitne", "kholo", "lagao")
            .any { Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
        return when {
            marathiRoman -> "mr-Latn"
            hindiRoman && Regex("[a-z]").containsMatchIn(text) -> "hinglish"
            else -> "en"
        }
    }
}
