package com.openjarvis.skills

import java.text.Normalizer
import java.util.Locale

/** Pure command-to-skill parsing. Android side effects are delegated to SkillExecutor. */
object SkillRegistry {
    enum class Kind { OPEN_APP, SETTINGS, CALL, SEARCH, WHATSAPP_MESSAGE, SMS_MESSAGE, ALARM, TIMER, TORCH, VOLUME, MAPS, TIME, DATE }
    data class Request(val kind: Kind, val args: Map<String, String>)

    interface SkillExecutor { suspend fun execute(request: Request): Result<Unit> }

    private fun norm(s: String) = Normalizer.normalize(com.openjarvis.agent.VoiceTextNormalizer.normalize(s), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    private val EXPLICIT_NAVIGATION_WORDS = setOf(
        "navigate", "navigation", "directions", "direction", "route", "map", "maps",
        "raasta", "rasta", "रास्ता", "रस्ता", "नेविगेशन", "नेविगेट", "दिशा", "दिशाएं"
    )

    fun hasExplicitNavigationIntent(raw: String): Boolean {
        val tokens = norm(raw).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        return tokens.any { it in EXPLICIT_NAVIGATION_WORDS }
    }

    fun parse(raw: String): Request? {
        val s = norm(raw)
        if (s.isBlank()) return null

        parseDateTimeQuestion(s)?.let { return it }

        parseAlarm(s)?.let { return Request(Kind.ALARM, mapOf("hour" to it.first.toString(), "minute" to it.second.toString())) }
        Regex("^(?:set )?(?:a )?timer(?: for)? (\\d+)\\s*(seconds?|secs?|minutes?|mins?)$").matchEntire(s)?.let {
            val seconds = it.groupValues[1].toIntOrNull() ?: return null
            val factor = if (it.groupValues[2].startsWith("s")) 1 else 60
            return Request(Kind.TIMER, mapOf("seconds" to (seconds * factor).toString()))
        }
        if (Regex("^(?:torch|flashlight|flash|फ्लैशलाइट|टॉर्च|टार्च)\\s+(?:on|चालू|जला दो|चालू करो)$").matches(s) ||
            Regex("^(?:turn on|switch on) (?:the )?(?:torch|flashlight)$").matches(s))
            return Request(Kind.TORCH, mapOf("enabled" to "true"))
        if (Regex("^(?:torch|flashlight|flash|टॉर्च|टार्च)\\s+(?:off|बंद|बुझा दो|बंद करो)$").matches(s) ||
            Regex("^(?:turn off|switch off) (?:the )?(?:torch|flashlight)$").matches(s))
            return Request(Kind.TORCH, mapOf("enabled" to "false"))
        if (Regex("^(?:(?:volume|sound|आवाज़|आवाज|aawaz|awaaz|aawaj|awaz|avaz|avaj)(?:\\s+(?:thoda|thodi))?\\s*(?:up|बढ़ाओ|बढ़ाओ|बढ़ा दो|बढ़ा दो|badha do|bada do|badhao|vadhav|vadhva|वाढवा|वाढव)|(?:increase|raise) volume)$").matches(s))
            return Request(Kind.VOLUME, mapOf("direction" to "up"))
        if (Regex("^(?:(?:volume|sound|आवाज़|आवाज|aawaz|awaaz|aawaj|awaz|avaz|avaj)(?:\\s+(?:thoda|thodi))?\\s*(?:down|कम करो|कम कर दो|kam karo|kam kar do|kami karo|kami kara|kami kar|ghatao|कमी करा|कमी कर)|(?:decrease|lower) volume)$").matches(s))
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
        if (hasExplicitNavigationIntent(s)) {
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


    fun parseDateTimeQuestion(raw: String): Request? {
        val text = norm(raw)
        if (text.isBlank()) return null
        return when {
            isDateQuestion(text) -> Request(Kind.DATE, emptyMap())
            isTimeQuestion(text) -> Request(Kind.TIME, emptyMap())
            else -> null
        }
    }

    internal fun dateReply(raw: String, clock: java.time.Clock = java.time.Clock.systemDefaultZone()): String {
        val language = responseLanguage(raw)
        val locale = when (language) {
            "mr" -> Locale.forLanguageTag("mr-IN")
            "hi" -> Locale.forLanguageTag("hi-IN")
            else -> Locale.ENGLISH
        }
        val formatted = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", locale)
            .withZone(clock.zone).format(clock.instant())
        return when (language) {
            "mr" -> "आज $formatted आहे."
            "hi" -> "आज $formatted है।"
            else -> "Today is $formatted."
        }
    }

    internal fun timeReply(raw: String, clock: java.time.Clock = java.time.Clock.systemDefaultZone()): String {
        val language = responseLanguage(raw)
        val locale = when (language) {
            "mr" -> Locale.forLanguageTag("mr-IN")
            "hi" -> Locale.forLanguageTag("hi-IN")
            else -> Locale.ENGLISH
        }
        val formatted = java.time.format.DateTimeFormatter.ofPattern("h:mm a", locale)
            .withZone(clock.zone).format(clock.instant())
        return when (language) {
            "mr" -> "आत्ता $formatted वाजले आहेत."
            "hi" -> "अभी $formatted बजे हैं।"
            else -> "It is $formatted."
        }
    }

    private fun responseLanguage(raw: String): String {
        val text = norm(raw)
        val marathi = listOf(
            "आजची", "आज ची", "आज कोणता दिवस", "आज कोणती तारीख", "आहे", "काय", "सांगा", "सांग", "दिवस",
            "aajchi", "aaj chi", "aahe", "sanga", "sang", "kaay"
        ).any { text.contains(it) }
        if (marathi) return "mr"
        val hindi = text.any { it in '\u0900'..'\u097F' } ||
            listOf("aaj", "tarikh", "tareekh", "din", "kya", "hai", "batao", "kaun", "kaunsa").any { text.contains(it) }
        return if (hindi) "hi" else "en"
    }

    internal fun isDateQuestion(s: String): Boolean {
        val text = norm(s).replace(Regex("[?!.,]"), "").trim()
        val patterns = listOf(
            Regex("^(?:what is|what's|tell me) (?:today's |the current )?date$"),
            Regex("^(?:today's date|current date|date today)$"),
            Regex("^(?:aaj ki|aaj ka|aaj) (?:date|tarikh|tareekh) (?:kya hai|batao|bataiye|sanga|sang)$"),
            Regex("^(?:date|tarikh|tareekh) (?:kya hai|batao)$"),
            Regex("^(?:aaj )?(?:kaun si|kya) tarikh hai$"),
            Regex("^(?:आज की|आज का|आज) (?:तारीख|तारीख़|दिनांक|डेट) (?:क्या है|बताओ|बताइए|सांगो|सांगा)$"),
            Regex("^(?:आज कौन सी तारीख है|आज कितनी तारीख है|तारीख क्या है|दिनांक क्या है)$"),
            Regex("^(?:आजची|आज ची) तारीख (?:काय आहे|सांगा|सांग)$"),
            Regex("^(?:आज किती तारीख आहे|आज कोणती तारीख आहे|दिनांक काय आहे)$"),
            Regex("^(?:aajchi|aaj chi) tarikh (?:kay aahe|sanga|sang)$"),
            Regex("^(?:aaj kay tarikh aahe|aajchi date kay aahe)$"),
            Regex("^(?:aaj kaun sa din hai|aaj kya din hai|din kya hai|aaj kaunsa din)$"),
            Regex("^(?:आज कौन सा दिन है|आज कौन-सा दिन है|आज कोणता दिवस)$"),
            Regex("^(?:what day is it|what day is it today)$"),
            Regex("^(?:aaj tarikh|tarikh kya hai)$"),
            Regex("^(?:आज की तारीख|आजची तारीख)$")
        )
        return patterns.any { it.matches(text) }
    }

    internal fun isTimeQuestion(s: String): Boolean {
        val text = norm(s).replace(Regex("[?!.,]"), "").trim()
        val patterns = listOf(
            Regex("^(?:what time is it|what is the time|current time|tell me the time)$"),
            Regex("^(?:time|samay) (?:kya hua(?: hai)?|kya hai|batao|kitna hua hai)$"),
            Regex("^(?:abhi )?(?:kitne baje hain|kitna time hua hai)$"),
            Regex("^(?:aaj ka )?(?:samay|time) (?:kya hai|kaha hai|kitna hua hai)$"),
            Regex("^(?:अभी )?(?:कितने बजे हैं|समय क्या हुआ है|समय क्या है|आज का समय क्या है)$"),
            Regex("^(?:वर्तमान समय क्या है|अभी समय क्या हुआ है)$"),
            Regex("^(?:आत्ता किती वाजले|सध्या किती वाजले|वेळ किती झाली)$"),
            Regex("^(?:what(?:'s| is) the time|what time is it|time now|current time|tell me (?:the )?time|tell me current time)$"),
            Regex("^(?:abhi )?(?:time|samay) (?:kya hua(?: hai)?|kya hai|batao|kitna hua hai)$"),
            Regex("^(?:abhi )?(?:kitne baje hain|kitne baje hue|kitne baje huye|kitna time hua)$"),
            Regex("^(?:आत्ता )?(?:वेळ काय झाली|किती वाजले|वेळ सांगा)$"),
            Regex("^(?:अभी )?(?:समय बताओ|टाइम बताओ|कितने बजे हुए)$")
        )
        return patterns.any { it.matches(text) }
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
