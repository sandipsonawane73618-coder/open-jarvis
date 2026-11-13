package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

internal object LlmSkillRouteSafetyPolicy {
    private val keywords = mapOf(
        "TORCH" to setOf("torch", "flashlight", "flash", "टॉर्च", "फ्लैशलाइट", "टार्च"),
        "VOLUME" to setOf("volume", "sound", "awaaz", "aawaz", "awaz", "avaz", "avaj", "आवाज़", "आवाज", "वॉल्यूम"),
        "ALARM" to setOf("alarm", "अलार्म"),
        "TIMER" to setOf("timer", "टाइमर"),
        "CALL" to setOf("call", "dial", "phone", "कॉल", "फोन", "डायल")
    )
    private fun tokens(command: String) = Normalizer.normalize(command, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()
    fun acceptedSkill(skill: String, command: String): String? {
        val normalized = skill.uppercase(Locale.ROOT)
        val required = keywords[normalized] ?: return normalized
        return normalized.takeIf { route -> tokens(command).any { it in required } }
    }
    fun skillFor(intent: StepIntent): String? = when (intent) {
        is StepIntent.Torch -> "TORCH"
        is StepIntent.Volume -> "VOLUME"
        is StepIntent.Alarm -> "ALARM"
        is StepIntent.Timer -> "TIMER"
        else -> null
    }
    fun clarificationQuestion(skill: String): String = when (skill.uppercase(Locale.ROOT)) {
        "TORCH" -> "Kya aap torch/flashlight on ya off karna chahte hain?"
        "VOLUME" -> "Aap volume badhana chahte hain ya kam karna?"
        "ALARM" -> "Kya alarm set karna hai? Time bataiye."
        "TIMER" -> "Kya timer lagana hai? Duration bataiye."
        "CALL" -> "Kya aap kisi ko call karna chahte hain? Contact ka naam bataiye."
        else -> ClarificationWorkflowPolicy.OFFLINE_FALLBACK
    }
}