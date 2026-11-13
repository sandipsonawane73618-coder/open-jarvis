package com.openjarvis.commandlab

import com.openjarvis.agent.CommandRouter
import com.openjarvis.agent.ContactCommandParser
import com.openjarvis.agent.ConversationIntentPolicy
import com.openjarvis.agent.MessageCommandParser
import com.openjarvis.agent.SearchCommandParser
import com.openjarvis.agent.StepIntentParser
import com.openjarvis.agent.UnknownCommandPolicy
import com.openjarvis.intelligence.FastCommandParser
import com.openjarvis.skills.SkillRegistry

enum class CommandLabRoute { SKILL, STEP, SEARCH, CHAT, LOOP, UNKNOWN }

data class CommandLabRouteResult(
    val route: CommandLabRoute,
    val parsedIntent: String,
    val reason: String? = null
)

/** Read-only probe over the same deterministic parsers and chat/unknown gates used by AgentCore. */
object CommandLabRouterProbe {
    fun classify(command: String): CommandLabRouteResult {
        val text = command.trim()
        if (text.isBlank()) return CommandLabRouteResult(CommandLabRoute.UNKNOWN, "Empty command", "Command is blank")
        if (text.matches(Regex("(?i)^(mute|unmute|awaaz band|awaaz chalu|आवाज़ बंद|आवाज़ चालू)$"))) {
            return CommandLabRouteResult(CommandLabRoute.SKILL, "Voice mute policy")
        }

        val route = CommandRouter.classify(text)
        when (route) {
            CommandRouter.Route.SKILL_REGISTRY -> {
                val intent = SkillRegistry.parse(text)
                return CommandLabRouteResult(CommandLabRoute.SKILL, intent?.let { "${it.kind}: ${it.args}" } ?: "SkillRegistry")
            }
            CommandRouter.Route.SEARCH -> {
                val query = SearchCommandParser.parseYouTubeQuery(text) ?: SearchCommandParser.parseWebQuery(text)
                return CommandLabRouteResult(CommandLabRoute.SEARCH, "Search(query=${query.orEmpty()})")
            }
            CommandRouter.Route.STEP_INTENT -> {
                val intent = StepIntentParser.parse(text)
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "StepIntent")
            }
            CommandRouter.Route.MESSAGE -> {
                val intent = MessageCommandParser.parse(text)
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "MessageIntent")
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
                return CommandLabRouteResult(CommandLabRoute.STEP, intent?.toString() ?: "FastCommand")
            }
            CommandRouter.Route.NONE -> Unit
        }

        if (ConversationIntentPolicy.isChatRequest(text)) {
            return CommandLabRouteResult(CommandLabRoute.CHAT, "ConversationIntent")
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
