package com.openjarvis.agent

import java.util.Locale

/** Deterministic, narrowly-scoped recognition and confirmation for full-device power off. */
internal object PowerOffCommandPolicy {
    private val exactPowerOffCommands = setOf(
        "shutdown ho jao",
        "shutdown kar do",
        "shut down ho jao",
        "phone band kar do",
        "phone ko band kar do",
        "mobile band kar do",
        "mobile ko band kar do",
        "phone off kar do",
        "mobile off kar do",
        "power off kar do",
        "phone shutdown kar do",
        "mobile shutdown kar do",
        "फोन बंद कर दो",
        "फोन को बंद कर दो",
        "मोबाइल बंद कर दो",
        "मोबाइल को बंद कर दो",
        "फोन बंद करो",
        "मोबाइल बंद करो",
        "फोन शटडाउन कर दो",
        "पावर ऑफ कर दो",
        "फोन बंद करा",
        "फोन बंद करा ना",
        "मोबाईल बंद करा",
        "मोबाईल बंद करून टाका"
    )

    private val englishPowerOff = Regex(
        "^(?:please\\s+)?(?:power\\s+off|shut\\s+down|shutdown|turn\\s+off)\\s+(?:the\\s+)?(?:phone|mobile|device)$",
        RegexOption.IGNORE_CASE
    )

    fun isPowerOffCommand(command: String): Boolean {
        val normalized = normalize(command)
        return normalized in exactPowerOffCommands || englishPowerOff.matches(normalized)
    }

    /** Null means this utterance is not a clear answer to a pending power-off prompt. */
    fun confirmationDecision(command: String): Boolean? {
        val normalized = normalize(command)
        return when (normalized) {
            "yes", "yes confirm", "confirm", "confirm shutdown", "yes shutdown",
            "haan", "han", "ha", "haan power off", "haan shutdown", "haan kar do",
            "हाँ", "हां", "हाँ बंद करो", "हां बंद करो", "हो", "होय", "हो बंद करा",
            "होय बंद करा", "कर दो", "करो", "बंद कर दो", "बंद करा" -> true

            "no", "no cancel", "cancel", "nahi", "nahin", "nahi karo",
            "नहीं", "नही", "मत करो", "रद्द करो", "नको" -> false

            else -> null
        }
    }

    private fun normalize(command: String): String =
        command.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[.!?।॥,;]+$"), "")
            .replace(Regex("\\s+"), " ")
}
