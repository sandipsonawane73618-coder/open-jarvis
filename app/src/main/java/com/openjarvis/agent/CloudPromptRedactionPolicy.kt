package com.openjarvis.agent

/**
 * Last-line privacy boundary for every outbound hybrid-cloud prompt.
 * User-approved memory/context sharing does not waive secret or banking redaction.
 */
internal object CloudPromptRedactionPolicy {
    private val assignmentSecret = Regex(
        """(?i)\b(password|passwd|passcode|api[_ -]?key|secret[_ -]?key|access[_ -]?token|bearer)\s*[:=]\s*[^\s,;]+"""
    )
    private val labelledCode = Regex(
        """(?i)\b(otp|one[ -]?time password|verification code|upi pin|pin|cvv|cvc)\s*[:#=-]?\s*\d{3,8}\b"""
    )
    private val cardNumber = Regex("""(?<!\d)(?:\d[ -]?){13,19}(?!\d)""")
    private val apiToken = Regex(
        """\b(?:sk-[A-Za-z0-9_-]{12,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})\b"""
    )
    private val labelledSecret = Regex(
        """(?i)\b(password|passwd|passcode|api[_ -]?key|secret[_ -]?key|access[_ -]?token|bearer)\b"""
    )

    fun redact(value: String): String {
        if (value.isBlank()) return value
        // BankingSafetyPolicy is authoritative: redact the whole sensitive command/context
        // instead of trying to preserve an amount, recipient, or partial transaction.
        if (BankingSafetyPolicy.isBlockedCommand(value)) return "[REDACTED]"
        var safe = assignmentSecret.replace(value) { "[REDACTED]" }
        safe = labelledCode.replace(safe) { "[REDACTED]" }
        safe = apiToken.replace(safe) { "[REDACTED]" }
        safe = cardNumber.replace(safe) { "[REDACTED]" }
        safe = proseSecret.replace(safe) { "[REDACTED]" }
        return safe
    }
}
