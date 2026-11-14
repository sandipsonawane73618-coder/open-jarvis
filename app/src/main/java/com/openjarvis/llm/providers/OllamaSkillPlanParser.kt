package com.openjarvis.llm.providers

import org.json.JSONArray
import org.json.JSONObject

internal data class OllamaSkillPlan(val skill: String, val args: JSONObject)

internal object OllamaSkillPlanParser {
    private val allowedSkills = setOf(
        "OPEN_APP", "SETTINGS", "CALL", "SEARCH", "WHATSAPP_MESSAGE", "SMS_MESSAGE",
        "ALARM", "TIMER", "TORCH", "VOLUME", "MAPS", "TIME", "ANDROID_UI_ACTIONS", "NONE", "UNKNOWN"
    )
    private val allowedArgs = setOf(
        "app", "screen", "contact", "text", "hour", "minute", "seconds",
        "direction", "destination", "enabled", "actions"
    )

    fun parse(raw: String): OllamaSkillPlan {
        val clean = Regex("(?is)<think>.*?</think>").replace(raw, "").trim()
        require(clean.startsWith("{") && clean.endsWith("}")) {
            "Ollama skill response must be one JSON object"
        }
        val root = JSONObject(clean)
        require(root.length() == 2 && root.has("skill") && root.has("args")) {
            "Ollama skill response must contain only skill and args"
        }
        val skill = root.optString("skill").trim()
        require(skill in allowedSkills && skill != "UNKNOWN") {
            "Unsupported or unknown skill: ${skill.ifBlank { "(empty)" }}"
        }
        val args = root.opt("args") as? JSONObject
            ?: throw IllegalArgumentException("Skill args must be a JSON object")
        val keys = args.keys().asSequence().toSet()
        require(keys.all { it in allowedArgs }) { "Unsupported skill argument: ${keys.first { it !in allowedArgs }}" }

        if (skill == "ANDROID_UI_ACTIONS") {
            val actions = args.opt("actions") as? JSONArray
                ?: throw IllegalArgumentException("ANDROID_UI_ACTIONS requires args.actions array")
            require(actions.length() in 1..20) { "UI skill must contain 1 to 20 actions" }
            for (index in 0 until actions.length()) {
                val item = actions.optJSONObject(index)
                    ?: throw IllegalArgumentException("UI action $index must be an object")
                require(item.optString("action").isNotBlank()) {
                    "UI action $index is missing action"
                }
            }
        } else {
            require(!args.has("actions")) { "Native skill cannot contain UI actions" }
        }
        return OllamaSkillPlan(skill, args)
    }
}
