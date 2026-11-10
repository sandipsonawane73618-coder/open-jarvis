package com.openjarvis.skills

import android.content.Context
import com.openjarvis.agent.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.regex.Pattern

class SkillEngine(private val context: Context) {
    
    private val skillsDir: File
        get() = File(context.filesDir, "skills").also { it.mkdirs() }
    
    private var skillsLoaded = false
    private val skillsCache = mutableListOf<Skill>()
    
    suspend fun loadSkills() = withContext(Dispatchers.IO) {
        if (skillsLoaded) return@withContext
        
        copyBuiltinSkillsIfNeeded()
        
        val jsonFiles = skillsDir.listFiles { _, name -> name.endsWith(".json") }
        
        skillsCache.clear()
        jsonFiles?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                skillsCache.add(parseSkill(json))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        skillsLoaded = true
    }
    
    private fun copyBuiltinSkillsIfNeeded() {
        val assetNames = context.assets.list("skills") ?: return
        assetNames.filter { it.endsWith(".json") }.forEach { assetName ->
            val destFile = File(skillsDir, assetName)
            if (!destFile.exists()) {
                try {
                    context.assets.open("skills/$assetName").use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun parseSkill(json: JSONObject): Skill {
        val triggerPhrases = json.getJSONArray("triggerPhrases").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        
        val variables = json.getJSONArray("variables").let { arr ->
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SkillVariable(
                    key = obj.getString("key"),
                    description = obj.optString("description", ""),
                    extractFrom = obj.optString("extractFrom", "trigger")
                )
            }
        }
        
        val actionTemplate = json.getJSONArray("actionTemplate").let { arr ->
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Action(
                    action = obj.getString("action"),
                    packageName = obj.optString("package", null),
                    label = obj.optString("label", null),
                    text = obj.optString("text", null),
                    value = obj.optString("value", null),
                    hint = obj.optString("hint", null).takeIf { it.isNotBlank() },
                    direction = obj.optString("direction", null).takeIf { it.isNotBlank() },
                    timeoutMs = obj.optLong("timeoutMs", 3000L),
                    message = obj.optString("message", null).takeIf { it.isNotBlank() },
                    prompt = obj.optString("prompt", null).takeIf { it.isNotBlank() },
                    outputKey = obj.optString("outputKey", null).takeIf { it.isNotBlank() },
                    x = if (obj.has("x")) obj.optInt("x") else null,
                    y = if (obj.has("y")) obj.optInt("y") else null,
                    distance = obj.optString("distance", null).takeIf { it.isNotBlank() }
                )
            }
        }
        
        val tags = json.getJSONArray("tags").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        
        return Skill(
            id = json.getString("id"),
            name = json.optString("name", ""),
            version = json.optString("version", "1.0"),
            author = json.optString("author", ""),
            triggerPhrases = triggerPhrases,
            variables = variables,
            llmHint = json.optString("llmHint", ""),
            actionTemplate = actionTemplate,
            successVerification = json.optString("successVerification", ""),
            tags = tags,
            usageCount = json.optInt("usageCount", 0),
            successRate = json.optDouble("successRate", 1.0).toFloat()
        )
    }
    
    private fun triggerPattern(phrase: String): Pattern {
        // Trigger text is user data, not a regular expression. Quote literal characters
        // and retain only the documented '*' capture wildcard.
        val pattern = phrase.split("*").joinToString("(.+?)") { Pattern.quote(it) }
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
    }

    private fun matchesTrigger(command: String, phrase: String): Boolean =
        triggerPattern(phrase).matcher(command).find()

    private fun matchesExactTrigger(command: String, phrase: String): Boolean =
        triggerPattern(phrase).matcher(command.trim()).matches()

    fun findMatch(command: String): Skill? =
        skillsCache.firstOrNull { skill ->
            skill.triggerPhrases.any { phrase -> matchesTrigger(command, phrase) }
        }

    /** Only auto-generated, previously successful skills participate in learned routing. */
    fun findLearnedMatch(command: String): Skill? =
        skillsCache.firstOrNull { skill ->
            skill.author == "generated" &&
                skill.triggerPhrases.any { phrase -> matchesExactTrigger(command, phrase) }
        }

    /** Persist a verified generated skill and make it available to this engine immediately. */
    suspend fun saveGeneratedSkill(skill: Skill) = withContext(Dispatchers.IO) {
        require(skill.author == "generated") { "Only generated skills can be saved as learned skills" }
        require(skill.actionTemplate.size >= 2) { "A learned skill needs at least two verified actions" }
        saveSkill(skill)
        val existing = skillsCache.indexOfFirst { it.id == skill.id }
        if (existing >= 0) skillsCache[existing] = skill else skillsCache.add(skill)
        skillsLoaded = true
    }
    
    fun extractVariables(command: String, skill: Skill): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lower = command.lowercase()
        
        for (skillVar in skill.variables) {
            for (phrase in skill.triggerPhrases) {
                val regex = triggerPattern(phrase)
                val matcher = regex.matcher(lower)
                
                if (matcher.find()) {
                    val group = matcher.group(1)?.trim() ?: continue
                    
                    when (skillVar.key) {
                        "contact", "message", "query", "time", "input" -> {
                            result[skillVar.key] = group
                        }
                    }
                    break
                }
            }
        }
        
        return result
    }
    
    fun buildContext(skill: Skill, vars: Map<String, String>): String {
        var hint = skill.llmHint
        for ((key, value) in vars) {
            hint = hint.replace("{$key}", value)
        }
        
        val actionsJson = skill.actionTemplate.joinToString(", ") { action ->
            val value = action.value?.let { v ->
                vars.entries.find { it.key in v }?.value ?: v
            } ?: ""
            """{"action":"${action.action}","value":"$value"}"""
        }
        
        return """
## Skill: ${skill.name}
$hint
Actions: [$actionsJson]
        """.trimIndent()
    }
    
    suspend fun recordUsage(skillId: String, success: Boolean) = withContext(Dispatchers.IO) {
        val index = skillsCache.indexOfFirst { it.id == skillId }
        if (index < 0) return@withContext
        
        val skill = skillsCache[index]
        val newCount = skill.usageCount + 1
        val newRate = if (success) {
            ((skill.successRate * skill.usageCount) + 1f) / newCount
        } else {
            (skill.successRate * skill.usageCount) / newCount
        }
        
        val updated = skill.copy(usageCount = newCount, successRate = newRate)
        skillsCache[index] = updated
        
        saveSkill(updated)
    }
    
    private fun saveSkill(skill: Skill) {
        val json = JSONObject().apply {
            put("id", skill.id)
            put("name", skill.name)
            put("version", skill.version)
            put("author", skill.author)
            put("triggerPhrases", JSONArray(skill.triggerPhrases))
            put("variables", JSONArray(skill.variables.map {
                JSONObject().put("key", it.key).put("description", it.description).put("extractFrom", it.extractFrom)
            }))
            put("llmHint", skill.llmHint)
            put("actionTemplate", JSONArray(skill.actionTemplate.map {
                JSONObject().apply {
                    put("action", it.action)
                    put("package", it.packageName)
                    put("label", it.label)
                    put("text", it.text)
                    put("value", it.value)
                    put("hint", it.hint)
                    put("direction", it.direction)
                    put("timeoutMs", it.timeoutMs)
                    put("message", it.message)
                    put("prompt", it.prompt)
                    put("outputKey", it.outputKey)
                    put("x", it.x)
                    put("y", it.y)
                    put("distance", it.distance)
                }
            }))
            put("successVerification", skill.successVerification)
            put("tags", JSONArray(skill.tags))
            put("usageCount", skill.usageCount)
            put("successRate", skill.successRate.toDouble())
        }
        
        File(skillsDir, "${skill.id}.json").writeText(json.toString(2))
    }
    
    fun generateSkill(command: String, actions: List<Action>, usedPackage: String?): Skill? {
        if (actions.size < 2) return null
        if (usedPackage == null) return null
        
        return Skill(
            id = "auto-${UUID.randomUUID().toString().take(8)}",
            name = command.take(30),
            version = "1.0",
            author = "generated",
            triggerPhrases = listOf(command.lowercase()),
            variables = listOf(SkillVariable("input", "User input", "trigger")),
            llmHint = "Custom action sequence",
            actionTemplate = actions,
            successVerification = "Task completed",
            tags = listOf("generated", "custom"),
            usageCount = 0,
            successRate = 1f
        )
    }
    
    fun getAllSkills(): List<Skill> = skillsCache.toList()
    
    fun getSkillCount(): Int = skillsCache.size
    
    data class SkillVariable(
        val key: String,
        val description: String,
        val extractFrom: String
    )
}

data class Skill(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val triggerPhrases: List<String>,
    val variables: List<SkillEngine.SkillVariable>,
    val llmHint: String,
    val actionTemplate: List<Action>,
    val successVerification: String,
    val tags: List<String>,
    val usageCount: Int,
    val successRate: Float
)