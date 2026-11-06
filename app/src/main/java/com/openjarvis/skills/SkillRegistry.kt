package com.openjarvis.skills

import java.text.Normalizer
import java.util.Locale

/** Pure command-to-skill parsing. Android side effects are delegated to SkillExecutor. */
object SkillRegistry {
    enum class Kind { OPEN_APP, SETTINGS, CALL, SEARCH, WHATSAPP_MESSAGE, SMS_MESSAGE, ALARM, TIMER, TORCH, VOLUME, MAPS }
    data class Request(val kind: Kind, val args: Map<String, String>)

    interface SkillExecutor { suspend fun execute(request: Request): Result<Unit> }

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    fun parse(raw: String): Request? {
        val s = norm(raw)
        if (s.isBlank()) return null

        parseAlarm(s)?.let { return Request(Kind.ALARM, mapOf("hour" to it.first.toString(), "minute" to it.second.toString())) }
        Regex("^(?:set )?(?:a )?timer(?: for)? (\\d+)\\s*(seconds?|secs?|minutes?|mins?)$").matchEntire(s)?.let {
            val seconds = it.groupValues[1].toIntOrNull() ?: return null
            val factor = if (it.groupValues[2].startsWith("s")) 1 else 60
            return Request(Kind.TIMER, mapOf("seconds" to (seconds * factor).toString()))
        }
        if (Regex("^(?:torch|flashlight|फ्लैशलाइट|टॉर्च)\\s+(?:on|चालू|जला दो|चालू करो)$").matches(s) ||
            Regex("^(?:turn on|switch on) (?:the )?(?:torch|flashlight)$").matches(s))
            return Request(Kind.TORCH, mapOf("enabled" to "true"))
        if (Regex("^(?:torch|flashlight|टॉर्च)\\s+(?:off|बंद|बुझा दो|बंद करो)$").matches(s) ||
            Regex("^(?:turn off|switch off) (?:the )?(?:torch|flashlight)$").matches(s))
            return Request(Kind.TORCH, mapOf("enabled" to "false"))
        if (Regex("^(?:volume|आवाज़|आवाज)\\s*(?:up|बढ़ाओ|बढ़ाओ)$|^(?:increase|raise) volume$").matches(s))
            return Request(Kind.VOLUME, mapOf("direction" to "up"))
        if (Regex("^(?:volume|आवाज़|आवाज)\\s*(?:down|कम करो)$|^(?:decrease|lower) volume$").matches(s))
            return Request(Kind.VOLUME, mapOf("direction" to "down"))

        val setting = mapOf(
            "wifi" to listOf("wifi", "wi-fi", "वाईफाई", "वायफाय"),
            "bluetooth" to listOf("bluetooth", "ब्लूटूथ"),
            "display" to listOf("display", "screen", "डिस्प्ले"),
            "sound" to listOf("sound", "audio", "volume", "आवाज़"),
            "location" to listOf("location", "gps", "लोकेशन"),
            "accessibility" to listOf("accessibility", "सुलभता"),
            "apps" to listOf("app settings", "manage apps", "applications"),
            "notifications" to listOf("notification", "notifications", "सूचना"),
            "general" to listOf("settings", "सेटिंग")
        ).entries.firstOrNull { e -> e.value.any { s.contains(it) } && (s.contains("setting") || s.contains("खोल") || s.contains("open") || e.key == "general") }
        if (setting != null) return Request(Kind.SETTINGS, mapOf("screen" to setting.key))

        val wa = Regex("^(?:whatsapp|व्हाट्सएप) (?:ko )?(?:message|msg) (.+?) (?:ko )?(?:bhejo|send karo|लिखो)$").matchEntire(s)
        if (wa != null) return Request(Kind.WHATSAPP_MESSAGE, mapOf("text" to wa.groupValues[1]))
        val sms = Regex("^(?:sms|text message) (.+)$").matchEntire(s)
        if (sms != null) return Request(Kind.SMS_MESSAGE, mapOf("text" to sms.groupValues[1]))
        if (s.contains("map") || s.contains("navigate") || s.contains("रास्ता")) {
            val destination = Regex("(?:to|तक|के लिए) (.+)$").find(s)?.groupValues?.get(1)
            if (!destination.isNullOrBlank()) return Request(Kind.MAPS, mapOf("destination" to destination))
        }
        val app = if (s in setOf("open maps", "launch maps", "maps kholo", "maps खोलो")) null else Regex("^(?:open|launch|start|kholo|खोलो) (.+)$").matchEntire(s)
        if (app != null) {
            val label = app.groupValues[1].trim().split(Regex("\\s+")).toMutableList()
            val trailing = setOf("again", "please", "plz", "pls", "dear", "karo", "kar", "kro", "खोलो", "करो")
            while (label.isNotEmpty() && label.last() in trailing) label.removeAt(label.lastIndex)
            val conjunctions = setOf("and", "aur", "और", "आणि", "then", "phir", "फिर")
            if (label.isNotEmpty() && label.none { it in conjunctions } && label.joinToString(" ").length <= 60) {
                return Request(Kind.OPEN_APP, mapOf("app" to label.joinToString(" ")))
            }
        }
        return null
    }

    private fun parseAlarm(s: String): Pair<Int, Int>? {
        if (!s.contains("alarm") && !s.contains("अलार्म")) return null
        val match = Regex("(subah|सुबह|morning|shaam|शाम|evening|raat|रात)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje|बजे)?").find(s)
            ?: Regex("(\\d{1,2}):(\\d{2})").find(s)
        val hour: Int
        val minute: Int
        if (match == null) return null
        if (match.groupValues[1].firstOrNull()?.isDigit() == true) {
            hour = match.groupValues[1].toInt()
            minute = match.groupValues[2].toInt()
        } else {
            val period = match.groupValues[1]
            val rawHour = match.groupValues[2].toIntOrNull() ?: return null
            minute = match.groupValues[3].toIntOrNull() ?: 0
            hour = when (period) {
                "shaam", "शाम", "evening", "raat", "रात" -> if (rawHour in 1..11) rawHour + 12 else rawHour
                else -> if (rawHour == 12) 0 else rawHour
            }
        }
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }
}
