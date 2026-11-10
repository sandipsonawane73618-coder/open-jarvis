package com.openjarvis.agent

import com.openjarvis.intelligence.FastCommandParser
import com.openjarvis.skills.SkillRegistry

/**
 * Shared deterministic route classifier used by AgentCore and parser/splitter tests.
 * Keep precedence aligned with AgentCore's direct-command pipeline.
 */
internal object CommandRouter {
    enum class Route {
        SKILL_REGISTRY,
        STEP_INTENT,
        MESSAGE,
        MESSAGE_CONTINUATION,
        SEARCH,
        CONTACT,
        FAST,
        NONE
    }

    fun classify(step: String): Route {
        if (SkillRegistry.parse(step) != null) return Route.SKILL_REGISTRY
        if (StepIntentParser.parse(step) != null) return Route.STEP_INTENT
        if (MessageCommandParser.parse(step) != null) return Route.MESSAGE
        if (messageContinuationContact(step) != null) return Route.MESSAGE_CONTINUATION
        if (SearchCommandParser.parseYouTubeQuery(step) != null ||
            SearchCommandParser.parseWebQuery(step) != null
        ) return Route.SEARCH
        if (ContactCommandParser.parse(step) != null) return Route.CONTACT
        if (FastCommandParser.parse(step) != null) return Route.FAST

        return Route.NONE
    }

    fun messageContinuationContact(step: String): String? {
        val cleaned = step.trim().replace(Regex("[\\p{Punct}।॥]+$"), "").trim()
        val match = Regex(
            "^(.+?)\\s+(?:ko|को|ला)\\s+(?:bhi|also|too|भी|सुद्धा)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(cleaned) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 80 }
    }
}
