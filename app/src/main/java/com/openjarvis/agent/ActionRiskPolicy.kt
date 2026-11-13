package com.openjarvis.agent
internal object ActionRiskPolicy {
    private val irreversibleTarget = Regex(
        "(?i)(?<![\\\\p{L}\\\\p{N}])(?:send(?:\\\\s+message)?|delete|remove|pay|payment|purchase|buy|post\\\\s+(?:update|content)|submit\\\\s+(?:form|order)|uninstall\\\\s+(?:app|application|package)|confirm)(?![\\\\p{L}\\\\p{N}])"
    )
    private val paymentTarget = Regex("(?i)(?<![\\\\p{L}\\\\p{N}])(pay|payment|purchase|buy|transfer)(?![\\\\p{L}\\\\p{N}])")

    fun requiresConfirmation(action: Action): Boolean {
        if (action.action !in setOf(Action.CLICK, Action.TAP)) return false
        val target = listOfNotNull(action.text, action.hint, action.value).joinToString(" ")
        return irreversibleTarget.containsMatchIn(target)
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
