package com.openjarvis.agent

import com.openjarvis.skills.SkillRegistry
import java.text.Normalizer
import java.util.Locale

internal object LlmSkillRouteSafetyPolicy {
    private val keywords = mapOf(
        "TORCH" to setOf("torch", "flashlight", "flash", "टॉर्च", "फ्लैशलाइट", "टार्च"),
        "VOLUME" to setOf("volume", "sound", "awaaz", "aawaz", "awaz", "avaz", "avaj", "आवाज़", "आवाज", "वॉल्यूम"),
        "ALARM" to setOf("alarm", "अलार्म"),
        "TIMER" to setOf("timer", "टाइमर"),
        "CALL" to setOf("call", "dial", "phone", "कॉल", "फोन", "डायल"),
        "MAPS" to setOf("navigate", "navigation", "directions", "direction", "route", "map", "maps", "रास्ता", "रस्ता", "नेविगेट", "नेविगेशन", "दिशा", "ले चलो"),
        "OPEN_APP" to setOf("open", "launch", "start", "खोलो", "खोल", "ओपन", "उघडा", "उघड"),
        "SEARCH" to setOf("search", "find", "look", "lookup", "सर्च", "खोज", "ढूंढ", "ढूँढ", "शोध"),
        "SETTINGS" to setOf("settings", "setting", "सेटिंग", "सेटिंग्स", "सेटिंग्ज")
    )

    private fun tokens(command: String) = Normalizer.normalize(command, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()

    fun acceptedSkill(skill: String, command: String): String? {
        val normalized = skill.uppercase(Locale.ROOT)
        val commandTokens = tokens(command)
        if (normalized == "MAPS") {
            return normalized.takeIf { SkillRegistry.hasExplicitNavigationIntent(command) }
        }
        if (normalized == "OPEN_APP") {
            return normalized.takeIf { commandTokens.any { it in keywords.getValue(normalized) } }
        }
        if (normalized == "SEARCH") {
            return normalized.takeIf { commandTokens.any { it in keywords.getValue(normalized) } }
        }
        if (normalized == "SETTINGS") {
            return normalized.takeIf { commandTokens.any { it in keywords.getValue(normalized) } }
        }
        val required = keywords[normalized] ?: return normalized
        return normalized.takeIf { route -> commandTokens.any { it in required } }
    }

    fun acceptedMainPromptSkill(skill: String, command: String): String? = acceptedSkill(skill, command)
    fun acceptedPipelinePromptSkill(skill: String, command: String): String? = acceptedSkill(skill, command)

    fun fewShotPromptInstructions(): String = """
Few-shot examples:
"flashlight on" -> {"skill":"TORCH","args":{"enabled":true}}
"awaaz badhao" -> {"skill":"VOLUME","args":{"direction":"up"}}
"alarm subah 6 baje" -> {"skill":"ALARM","args":{"hour":6,"minute":0}}
"timer 2 minutes" -> {"skill":"TIMER","args":{"seconds":120}}
"open Chrome" -> {"skill":"OPEN_APP","args":{"app":"Chrome"}}
"kuch bhi" -> {"skill":"NONE","args":{}}
Choose NONE whenever no explicit command evidence supports a native skill. Never infer TORCH from volume or unrelated language.
""".trimIndent()

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
        "OPEN_APP" -> "Kaunsa installed app kholna hai?"
        "SEARCH" -> "Kya search karna hai?"
        "MAPS" -> "Kis jagah ke directions chahiye?"
        "SETTINGS" -> "Kaunsi setting kholni hai?"
        else -> ClarificationWorkflowPolicy.OFFLINE_FALLBACK
    }
}
