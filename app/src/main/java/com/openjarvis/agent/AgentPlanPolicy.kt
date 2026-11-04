package com.openjarvis.agent

/**
 * Bounded plan policy. Replans are only requested for structural safety failures
 * or when an action-bearing compound request was reduced to a single app launch.
 */
internal object AgentPlanPolicy {
    const val MAX_ACTIONS = 10

    fun hasUnresolvedAppLaunch(actions: List<Action>): Boolean =
        actions.any { it.action == Action.OPEN_APP && AppTargetResolver.isPlaceholder(it.label) }

    fun needsCompactReplan(actions: List<Action>): Boolean =
        actions.size > MAX_ACTIONS ||
            (actions.any { it.action == Action.ERROR } && actions.any { it.action != Action.ERROR }) ||
            hasUnresolvedAppLaunch(actions)

    private val multiStepConnector = Regex(
        "(?<![\\p{L}\\p{N}\\p{M}])(and|then|after|also|aur|phir|fir|uske baad|iske baad|baad me|baad mein)(?![\\p{L}\\p{N}\\p{M}])",
        RegexOption.IGNORE_CASE
    )
    private val actionableIntent = Regex(
        "(?<![\\p{L}\\p{N}\\p{M}])(search|find|type|write|send|reply|message|click|tap|scroll|likho|bhejo|khojo|खोजो|लिखो|भेजो|टाइप|क्लिक|टैप|स्क्रोल|शोधा|लिहा|पाठवा)(?![\\p{L}\\p{N}\\p{M}])",
        RegexOption.IGNORE_CASE
    )

    fun isLikelyUnderPlanned(command: String, actions: List<Action>): Boolean {
        if (actions.size != 1 || actions.single().action != Action.OPEN_APP) return false
        val hasConnector = multiStepConnector.containsMatchIn(command) ||
            listOf("और", "आणि", "त्यानंतर").any { command.contains(it) }
        val actionableCount = actionableIntent.findAll(command).count()
        return (hasConnector && actionableCount >= 1) || actionableCount >= 2
    }

    fun needsCompactReplan(actions: List<Action>, command: String): Boolean =
        needsCompactReplan(actions) || isLikelyUnderPlanned(command, actions)

    fun isExecutablePlan(actions: List<Action>): Boolean =
        actions.isNotEmpty() &&
            actions.size <= MAX_ACTIONS &&
            !hasUnresolvedAppLaunch(actions) &&
            actions.none {
                it.action == Action.ERROR ||
                    it.action == Action.READ_SCREEN ||
                    it.action == Action.EXTRACT_TEXT
            }

    fun isExecutablePlan(actions: List<Action>, command: String): Boolean =
        isExecutablePlan(actions) && !isLikelyUnderPlanned(command, actions)

    /** A single resolved launch is safe even when the current Accessibility tree is blank. */
    fun canLaunchResolvedAppWithoutScreen(actions: List<Action>): Boolean =
        actions.size == 1 &&
            actions.single().action == Action.OPEN_APP &&
            !AppTargetResolver.isPlaceholder(actions.single().label) &&
            !actions.single().packageName.isNullOrBlank() &&
            isExecutablePlan(actions)
    fun failureRule(actions: List<Action>, command: String): String? = when {
        actions.size > MAX_ACTIONS -> "RULE_MAX_ACTIONS"
        actions.any { it.action == Action.ERROR } && actions.any { it.action != Action.ERROR } -> "RULE_MIXED_ERROR"
        hasUnresolvedAppLaunch(actions) -> "RULE_UNRESOLVED_APP"
        isLikelyUnderPlanned(command, actions) -> "RULE_UNDER_PLANNED"
        actions.isEmpty() -> "RULE_EMPTY_PLAN"
        actions.any { it.action == Action.READ_SCREEN || it.action == Action.EXTRACT_TEXT } -> "RULE_SCREEN_OBSERVATION"
        else -> null
    }

    fun userGuidance(rule: String): String = when (rule) {
        "RULE_MAX_ACTIONS" -> "Plan mein 10 se zyada steps hain; command ko chhote parts mein bolo."
        "RULE_MIXED_ERROR" -> "Plan mein action aur error mix hain; ek clear command do."
        "RULE_UNRESOLVED_APP" -> "App ka installed naam nahi mila; app ka exact naam bolo."
        "RULE_UNDER_PLANNED" -> "Command ke kuch steps plan nahi hue; ek baar mein ek chhota task bolo."
        "RULE_SCREEN_OBSERVATION" -> "Accessibility screen read nahi kar pa rahi; Jarvis Accessibility enable karke target app kholo."
        "RULE_EMPTY_PLAN" -> "Koi action plan nahi bana; command ko specific tarike se bolo."
        else -> "Safe action plan nahi bana; command ko chhote aur clear steps mein bolo."
    }

}
