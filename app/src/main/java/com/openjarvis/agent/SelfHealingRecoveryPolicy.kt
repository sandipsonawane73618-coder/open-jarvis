package com.openjarvis.agent

import org.json.JSONArray

/**
 * Deterministic recovery classification. This layer never executes an action.
 * It separates repeatable observations from actions whose completion may be ambiguous.
 */
internal object SelfHealingRecoveryPolicy {
    enum class FailureArea { OLLAMA_CONNECTIVITY, MODEL_UNAVAILABLE, ACCESSIBILITY, APP_LAUNCH, SPEECH, OVERLAY, ACTION, UNKNOWN }
    enum class RecoveryKind { VERIFY_LOCAL_SERVICE, VERIFY_CONFIGURATION, REFRESH_OBSERVATION, USER_ACTION_REQUIRED, ESCALATE }

    data class Decision(
        val area: FailureArea,
        val recovery: RecoveryKind,
        val mayRetryAutomatically: Boolean,
        val requiresUserConfirmation: Boolean,
        val verifyAfterRecovery: Boolean,
        val explanation: String
    )

    fun classify(action: Action, reason: String): Decision {
        val value = reason.lowercase()
        val isObservation = action.action == Action.WAIT || action.action == Action.WAIT_FOR ||
            action.action == Action.READ_SCREEN || action.action == Action.EXTRACT_TEXT
        val isAppLaunch = action.action == Action.OPEN_APP ||
            contains(value, "app not installed", "package not found", "launch activity", "could not launch")
        val isSideEffect = action.action in setOf(
            Action.TAP, Action.CLICK, Action.TYPE, Action.TYPE_TEXT, Action.OPEN_APP,
            Action.BACK, Action.PRESS_BACK, Action.HOME, Action.PRESS_HOME,
            Action.PRESS_RECENTS, Action.SCROLL, Action.AI_PROMPT
        )
        val area = when {
            // Explicit model-missing signals take precedence even when the diagnostic
            // also names Ollama; otherwise "Ollama model qwen3:1.7b not found" is
            // incorrectly reported as a server-connectivity failure.
            contains(value, "model not found", "unknown model", "model unavailable", "no such model") ||
                (value.contains("qwen3:1.7b") && contains(value, "not found", "missing", "unavailable")) ->
                FailureArea.MODEL_UNAVAILABLE
            contains(value, "ollama", "11434", "connection refused", "failed to connect", "socket timeout") ->
                FailureArea.OLLAMA_CONNECTIVITY
            (isObservation && action.action in setOf(Action.WAIT, Action.WAIT_FOR) &&
                contains(value, "timed out", "timeout", "timed-out")) -> FailureArea.ACCESSIBILITY
            contains(value, "accessibility", "visible text", "node not found", "no accessible text",
                "could not read current screen", "could not extract text from current screen") -> FailureArea.ACCESSIBILITY
            isAppLaunch -> FailureArea.APP_LAUNCH
            contains(value, "speech", "stt", "microphone", "no match") -> FailureArea.SPEECH
            contains(value, "overlay", "floating window") -> FailureArea.OVERLAY
            isSideEffect || contains(value, "tap", "type", "click", "send", "delete", "payment",
                "scroll", "android back action", "android home action", "recent apps action") -> FailureArea.ACTION
            else -> FailureArea.UNKNOWN
        }
        return when (area) {
            FailureArea.OLLAMA_CONNECTIVITY -> Decision(area, RecoveryKind.VERIFY_LOCAL_SERVICE, true, false, true,
                "Check the local Ollama endpoint only; never switch provider or model.")
            FailureArea.MODEL_UNAVAILABLE -> Decision(area, RecoveryKind.VERIFY_CONFIGURATION, false, true, true,
                "Verify the installed local qwen3:1.7b model; do not download or change the default automatically.")
            FailureArea.ACCESSIBILITY -> {
                // Empty screen content is a stale observation; permission/service failures require user action.
                val missingObservation = isObservation && (
                    contains(value, "visible text not found", "node not found", "element not found",
                        "no accessible text", "could not read current screen",
                        "could not extract text from current screen") ||
                    (action.action in setOf(Action.WAIT, Action.WAIT_FOR) &&
                        contains(value, "timed out", "timeout", "timed-out"))
                )
                if (missingObservation) {
                    Decision(area, RecoveryKind.REFRESH_OBSERVATION, true, false, true,
                        "Refresh the read-only screen observation; do not repeat any UI action.")
                } else {
                    Decision(area, RecoveryKind.USER_ACTION_REQUIRED, false, true, true,
                        "Ask the user to check Android accessibility permission/service state, then verify again.")
                }
            }
            FailureArea.OVERLAY, FailureArea.SPEECH ->
                Decision(area, RecoveryKind.USER_ACTION_REQUIRED, false, true, true,
                    "Ask the user to check Android permission/service state, then verify again after the user acts.")
            FailureArea.APP_LAUNCH, FailureArea.ACTION ->
                Decision(area, if (isObservation) RecoveryKind.REFRESH_OBSERVATION else RecoveryKind.USER_ACTION_REQUIRED,
                    isObservation, !isObservation, true,
                    "Observe current state before any retry; never replay a possibly completed side effect.")
            FailureArea.UNKNOWN -> Decision(area, RecoveryKind.ESCALATE, false, true, false,
                "Unknown failure: stop automation and request user review.")
        }
    }

    /** Apply learned recurrence state to the same decision used by retry gating and user guidance. */
    fun applyLearning(
        decision: Decision,
        consecutiveUnresolved: Int,
        threshold: Int = 3
    ): Decision {
        val safeThreshold = threshold.coerceAtLeast(2)
        if (!decision.mayRetryAutomatically || consecutiveUnresolved < safeThreshold) return decision
        return decision.copy(
            recovery = RecoveryKind.ESCALATE,
            mayRetryAutomatically = false,
            requiresUserConfirmation = true,
            explanation = "Repeated unresolved ${decision.area.name.lowercase().replace('_', ' ')} failures reached $safeThreshold occurrences. Stop automatic retries and request user review."
        )
    }

    private fun contains(value: String, vararg terms: String) = terms.any(value::contains)
}

/** Privacy-safe recurring-failure signature; never includes command, screen, or message text. */
internal object SelfHealingPatternCodec {
    private const val MAX_PATTERNS = 12
    private const val MAX_COUNTER = 1_000_000
    private const val MAX_JSON_CHARS = 8_192

    private fun boundedLimit(limit: Int): Int = limit.coerceIn(1, MAX_PATTERNS)
    private fun increment(value: Int): Int = value.coerceIn(0, MAX_COUNTER - 1) + 1

    data class Pattern(
        val area: String,
        val count: Int,
        val lastSeenMillis: Long,
        val verifiedRecoveries: Int = 0,
        val unresolvedFailures: Int = count,
        val consecutiveFailures: Int = count,
        val lastOutcome: String = "UNRESOLVED"
    )

    fun record(existing: List<Pattern>, area: String, now: Long, limit: Int = 12): List<Pattern> {
        val safeArea = area.takeIf { it in SelfHealingRecoveryPolicy.FailureArea.values().map { item -> item.name } }
            ?: SelfHealingRecoveryPolicy.FailureArea.UNKNOWN.name
        val current = existing.firstOrNull { it.area == safeArea }
        val updated = Pattern(
            area = safeArea,
            count = increment(current?.count ?: 0),
            lastSeenMillis = now,
            verifiedRecoveries = (current?.verifiedRecoveries ?: 0).coerceIn(0, MAX_COUNTER),
            unresolvedFailures = increment(current?.unresolvedFailures ?: 0),
            consecutiveFailures = increment(current?.consecutiveFailures ?: 0),
            lastOutcome = "UNRESOLVED"
        )
        return (listOf(updated) + existing.filterNot { it.area == safeArea })
            .sortedByDescending { it.lastSeenMillis }
            .take(boundedLimit(limit))
    }

    /** Record only a verified, read-only recovery outcome; never stores command or screen data. */
    fun recordOutcome(
        existing: List<Pattern>,
        area: String,
        verified: Boolean,
        now: Long,
        limit: Int = 12
    ): List<Pattern> {
        val current = existing.firstOrNull { it.area == area }
        if (current == null) {
            if (!verified || area !in SelfHealingRecoveryPolicy.FailureArea.values().map { it.name }) {
                return existing.take(boundedLimit(limit))
            }
            // A transient failure may recover on its first bounded retry before an
            // unresolved pattern exists. Preserve that verified recovery as a baseline.
            val recovered = Pattern(
                area = area,
                count = 1,
                lastSeenMillis = now,
                verifiedRecoveries = 1,
                unresolvedFailures = 0,
                consecutiveFailures = 0,
                lastOutcome = "VERIFIED_READ_ONLY_RECOVERY"
            )
            return (listOf(recovered) + existing)
                .sortedByDescending { it.lastSeenMillis }
                .take(boundedLimit(limit))
        }
        val updated = current.copy(
            lastSeenMillis = now,
            verifiedRecoveries = if (verified) increment(current.verifiedRecoveries) else current.verifiedRecoveries.coerceIn(0, MAX_COUNTER),
            unresolvedFailures = if (verified) (current.unresolvedFailures - 1).coerceAtLeast(0)
                else current.unresolvedFailures,
            consecutiveFailures = if (verified) 0 else current.consecutiveFailures,
            lastOutcome = if (verified) "VERIFIED_READ_ONLY_RECOVERY" else "UNRESOLVED"
        )
        return (listOf(updated) + existing.filterNot { it.area == area })
            .sortedByDescending { it.lastSeenMillis }
            .take(boundedLimit(limit))
    }

    /**
     * Decode both the original array format and the current additive schema.
     * Missing outcome fields are migrated with the original safe defaults.
     * Unknown fields are ignored; malformed rows are dropped independently.
     */
    fun decodeJson(raw: String?): List<Pattern> = runCatching {
        val safeJson = raw?.takeIf { it.isNotBlank() && it.length <= MAX_JSON_CHARS }
            ?: if (raw.isNullOrBlank()) "[]" else return@runCatching emptyList()
        val array = JSONArray(safeJson)
        val validAreas = SelfHealingRecoveryPolicy.FailureArea.values().map { it.name }.toSet()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val area = item.optString("area")
            val count = item.optInt("count", 0)
            val lastSeen = item.optLong("lastSeen", 0L)
            if (area !in validAreas || count <= 0 || lastSeen <= 0L) return@mapNotNull null
            Pattern(
                area = area,
                count = count,
                lastSeenMillis = lastSeen,
                verifiedRecoveries = item.optInt("verifiedRecoveries", 0).coerceIn(0, MAX_COUNTER),
                unresolvedFailures = item.optInt("unresolvedFailures", count).coerceIn(0, MAX_COUNTER),
                consecutiveFailures = item.optInt("consecutiveFailures", count).coerceIn(0, MAX_COUNTER),
                lastOutcome = item.optString("lastOutcome", "UNRESOLVED")
                    .takeIf { it in setOf("UNRESOLVED", "VERIFIED_READ_ONLY_RECOVERY") } ?: "UNRESOLVED"
            )
        }
            .sortedByDescending { it.lastSeenMillis }
            .distinctBy { it.area }
            .take(MAX_PATTERNS)
    }.getOrDefault(emptyList())

    /** Persist only the explicit allow-listed counters; never serialize caller content. */
    fun encodeJson(patterns: List<Pattern>): String {
        val array = JSONArray()
        patterns.take(MAX_PATTERNS).forEach { item ->
            if (item.area !in SelfHealingRecoveryPolicy.FailureArea.values().map { it.name }) return@forEach
            array.put(org.json.JSONObject()
                .put("area", item.area)
                .put("count", item.count.coerceIn(0, MAX_COUNTER))
                .put("lastSeen", item.lastSeenMillis.coerceAtLeast(0L))
                .put("verifiedRecoveries", item.verifiedRecoveries.coerceIn(0, MAX_COUNTER))
                .put("unresolvedFailures", item.unresolvedFailures.coerceIn(0, MAX_COUNTER))
                .put("consecutiveFailures", item.consecutiveFailures.coerceIn(0, MAX_COUNTER))
                .put("lastOutcome", item.lastOutcome.takeIf {
                    it == "UNRESOLVED" || it == "VERIFIED_READ_ONLY_RECOVERY"
                } ?: "UNRESOLVED"))
        }
        return array.toString()
    }

    fun recurring(pattern: Pattern, threshold: Int = 3): Boolean =
        pattern.consecutiveFailures >= threshold.coerceAtLeast(2)
}
