package com.openjarvis.agent

internal object ActionRiskPolicy {
    private val exactIrreversibleTargets = setOf(
        "send", "send message", "delete", "remove", "pay", "payment",
        "purchase", "buy", "post", "submit", "uninstall", "confirm"
    )
    private val irreversiblePhrases = listOf(
        Regex("(?i)^pay\\s+now$"),
        Regex("(?i)^post\\s+(update|content)$"),
        Regex("(?i)^submit\\s+(form|order)$"),
        Regex("(?i)^uninstall\\s+(app|application|package)$")
    )
    private val paymentTarget = Regex("(?i)(?<![\\p{L}\\p{N}])(pay|payment|purchase|buy|transfer)(?![\\p{L}\\p{N}])")

    private fun targetIsIrreversible(value: String): Boolean {
        val normalized = value.trim().replace(Regex("\\s+"), " ").lowercase()
        return normalized in exactIrreversibleTargets ||
            irreversiblePhrases.any { it.matches(normalized) }
    }

    fun requiresConfirmation(action: Action): Boolean {
        if (action.action !in setOf(Action.CLICK, Action.TAP)) return false
        return listOfNotNull(action.text, action.hint, action.value).any(::targetIsIrreversible)
    }

    fun isPaymentAction(action: Action): Boolean {
        if (action.action !in setOf(Action.CLICK, Action.TAP, Action.TYPE, Action.TYPE_TEXT)) return false
        val target = listOfNotNull(action.text, action.hint, action.value).joinToString(" ")
        return paymentTarget.containsMatchIn(target) || BankingSafetyPolicy.isFinancialPackage(action.packageName)
    }

    fun approvalDecision(command: String): Boolean? = when (command.trim().lowercase().replace(Regex("[.!?।॥]+$"), "").trim()) {
        "yes", "yes confirm", "confirm", "approve", "haan", "han", "ha", "हाँ", "हां", "हो", "होय", "करो", "कर दीजिए" -> true
        "no", "no cancel", "cancel", "decline", "nahi", "nahin", "नहीं", "मत करो", "रद्द करो", "नको" -> false
        else -> null
    }
}
