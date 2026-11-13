package com.openjarvis.agent

/** Deterministic-first planner. The model is constrained to exactly one JSON action. */
internal class NextActionPlanner {
    suspend fun chooseNextAction(
        command: String,
        observation: ScreenObservation,
        deterministic: (String, ScreenObservation) -> Action?,
        requestOneJsonAction: suspend () -> Result<String>
    ): Result<Action> {
        val direct = deterministic(command, observation)
        if (direct != null) {
            return validateAction(direct, command)
        }
        val raw = requestOneJsonAction().getOrElse { return Result.failure(it) }
        val parsed = LLMResponseValidator.validate(raw)
        if (!parsed.isValid) {
            return Result.failure(IllegalArgumentException(parsed.errors.firstOrNull() ?: "Invalid action JSON"))
        }
        if (parsed.actions.size != 1) {
            return Result.failure(IllegalArgumentException("Planner must return exactly one action"))
        }
        return validateAction(parsed.actions.single(), command)
    }

    private fun validateAction(action: Action, command: String): Result<Action> {
        if (action.action == Action.COMPLETE) return Result.success(action)
        if (ActionRiskPolicy.requiresConfirmation(action)) return Result.success(action)
        if (!AgentPlanPolicy.isExecutablePlan(listOf(action), command)) {
            val rule = AgentPlanPolicy.failureRule(listOf(action), command) ?: "RULE_UNSAFE_ACTION"
            return Result.failure(IllegalArgumentException(AgentPlanPolicy.userGuidance(rule)))
        }
        return Result.success(action)
    }
}

/** Routes only screen/UI requests; deterministic skills and location/search intents stay on old paths. */
internal object AgentLoopCommandPolicy {
    private val locationTerms = Regex(
        "(?<![\\p{L}\\p{N}])(maps?|navigation|navigate|route|directions|location|near me|nearby|रास्ता|लोकेशन|नकाशा|मार्ग)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    private val uiTerms = Regex(
        "(?<![\\p{L}\\p{N}])(screen|button|tap|click|press|scroll|type|enter|first video|pehla video|पहला वीडियो|पहला रिजल्ट|search button)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    private val actionTerms = Regex(
        "(?<![\\p{L}\\p{N}])(open|kholo|launch|search|play|chalao|tap|click|type|likho|scroll|dabao)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    private val crossAppTerms = Regex(
        "(?<![\\p{L}\\p{N}])(banao|bhejo|send\\s+karo|attach\\s+karo|phir|then|after\\s+that|uske\\s+baad|बनाओ|भेजो|फिर)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    private val appTerms = Regex(
        "(?<![\\p{L}\\p{N}])(gemini|whatsapp|youtube|instagram|chrome|telegram|facebook|gallery|photos|files|messages)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    fun isYouTubeFirstVideoGoal(command: String): Boolean {
        if (locationTerms.containsMatchIn(command)) return false
        val video = Regex("(?<![\\p{L}\\p{N}])(youtube|yt|video|song|गाना|वीडियो)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        val first = Regex("(?<![\\p{L}\\p{N}])(first|pehla|पहला|पहली)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        val play = Regex("(?<![\\p{L}\\p{N}])(play|chalao|bajao|चलाओ|चलाओ)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return video.containsMatchIn(command) && first.containsMatchIn(command) && play.containsMatchIn(command)
    }

    fun shouldUseLoop(command: String): Boolean {
        if (command.isBlank() || locationTerms.containsMatchIn(command)) return false
        if (isYouTubeFirstVideoGoal(command)) return true
        val hasUiTarget = uiTerms.containsMatchIn(command)
        val hasAction = actionTerms.containsMatchIn(command)
        val hasSequence = Regex("\\b(and then|then|phir|uske baad|aur)\\b", RegexOption.IGNORE_CASE).containsMatchIn(command)
        val crossAppCount = appTerms.findAll(command).count()
        val crossAppWorkflow = crossAppCount >= 2 && crossAppTerms.containsMatchIn(command)
        return crossAppWorkflow || (hasUiTarget && hasAction) || (hasSequence && hasAction && command.length > 35)
    }
}

/** Small, grounded deterministic UI actions that do not need a model call. */
internal object DeterministicUiActionResolver {
    fun resolve(command: String, observation: ScreenObservation): Action? {
        if (AgentLoopCommandPolicy.isYouTubeFirstVideoGoal(command)) {
            val target = YouTubePlaybackPolicy.firstVideoTarget(observation) ?: return null
            return Action(action = Action.CLICK, text = target)
        }
        val searchTap = Regex(
            "(?i)(?:search|खोज|शोध)\\s*(?:button|बटन)?\\s*(?:tap|click|dabao|दबाओ|टैप|क्लिक)"
        )
        if (searchTap.containsMatchIn(command) &&
            observation.interactiveElements.contains("Search", ignoreCase = true)
        ) return Action(action = Action.CLICK, text = "Search")

        val indexed = Regex(
            "(?i)(?:tap|click|dabao|दबाओ)\\s+(?:the\\s+)?(?:first|pehla|पहला)\\s+(?:button|control|item)"
        )
        if (indexed.containsMatchIn(command) && observation.interactiveElements.isNotBlank()) {
            return Action(action = Action.CLICK, text = "#1")
        }
        return null
    }

    private fun visibleTargetFromLine(line: String): String? {
        val text = Regex("""text=(.+?)(?:\s+(?:desc=|hint=|id=|class=|bounds=)|$)""")
            .find(line)?.groupValues?.get(1)?.trim()
        if (!text.isNullOrBlank()) return text
        return Regex("""desc=(.+?)(?:\s+(?:hint=|id=|class=|bounds=)|$)""")
            .find(line)?.groupValues?.get(1)?.trim()
    }

    private fun isNavigationLabel(value: String): Boolean =
        Regex("(?i)^(home|shorts|subscriptions|library|search|create|notifications|profile|account|settings|back|menu)$")
            .matches(value.trim())
}
