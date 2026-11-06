package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

sealed class StepIntent {
    data class Alarm(val hour: Int, val minute: Int) : StepIntent()
    data class Timer(val seconds: Int) : StepIntent()
    data class Torch(val on: Boolean) : StepIntent()
    data class Volume(val up: Boolean) : StepIntent()
    data class Navigate(val place: String) : StepIntent()
    data class Message(val app: String?, val contact: String?, val text: String?) : StepIntent()
    data class AppLaunch(val label: String) : StepIntent()
}

/**
 * Parses ONE already-split command step (English, Hinglish, Devanagari,
 * Marathi) into a StepIntent. Pure Kotlin, JVM-testable. Returns null if
 * the step is not one of these skills.
 */
object StepIntentParser {

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun words(vararg w: String): Set<String> = w.map { norm(it) }.toSet()

    private val CONNECTORS = words(
        "ko", "se", "ka", "ki", "ke", "to", "pe", "par", "on", "in", "at", "for",
        "liye", "tak", "को", "से", "का", "की", "के", "पर", "पे", "तक", "ला", "वर"
    )
    private val FILLERS = words(
        "karo", "kar", "kro", "kare", "karen", "kardo", "karna", "kijiye",
        "do", "dena", "de", "na", "please", "plz", "pls", "zara", "jara",
        "mujhe", "mera", "meri", "hume", "lagao", "laga", "lagana", "chalu",
        "hai", "hain",
        "करो", "कर", "करा", "करना", "कीजिए", "दो", "दे", "ना", "प्लीज", "कृपया",
        "ज़रा", "जरा", "मुझे", "लगाओ", "लगा", "है", "हैं"
    )
    private val OPEN_VERBS = words(
        "open", "kholo", "khol", "launch", "start", "shuru", "खोलो", "खोल", "शुरू"
    )
    // Suffixes are removed only from app-launch candidates, never from message text.
    private val APP_TRAILING_NOISE: List<List<String>> = listOf(
        listOf("phir", "se"), listOf("ek", "baar"),
        listOf("again"), listOf("please"), listOf("plz"), listOf("pls"),
        listOf("dobara"), listOf("dear"), listOf("app"),
        listOf("karo"), listOf("kar"), listOf("kro"),
        listOf("kholo"), listOf("खोलो"), listOf("करो")
    )
    private val APP_ALIAS_CANONICAL = mapOf(
        "instagram" to "instagram", "insta" to "instagram", "insta app" to "instagram", "ig" to "instagram",
        "whatsapp" to "whatsapp", "wa" to "whatsapp", "व्हाट्सएप" to "whatsapp", "व्हॉट्सऍप" to "whatsapp",
        "chrome" to "chrome",
        "youtube" to "youtube", "yt" to "youtube",
        "maps" to "maps", "google maps" to "google maps",
        "telegram" to "telegram",
        "facebook" to "facebook", "fb" to "facebook",
        "settings" to "settings", "setting" to "settings", "phone" to "phone", "camera" to "camera",
        "gallery" to "gallery", "calculator" to "calculator",
        "gpay" to "google pay", "google pay" to "google pay"
    )
    private val APP_ALIASES = APP_ALIAS_CANONICAL.keys
    private val APP_CONNECTORS = words("and", "aur", "और", "आणि", "then", "phir", "फिर")

    private val MESSAGE_WORDS = words(
        "message", "msg", "sms", "whatsapp", "मैसेज", "मेसेज", "संदेश", "एसएमएस",
        "व्हाट्सएप", "व्हॉट्सऍप"
    )
    private val WHATSAPP_WORDS = words("whatsapp", "व्हाट्सएप", "व्हॉट्सऍप")
    private val SMS_WORDS = words("sms", "एसएमएस")
    private val SEND_WORDS = words(
        "bhejo", "bhej", "send", "saying", "say", "likho", "likh", "bolo", "bol", "keh", "kehdo",
        "भेजो", "भेज", "लिखो", "लिख", "बोलो", "बोल", "कहो", "पाठवा"
    )
    private val TEXT_DELIMS = words("ki", "कि", "that", "saying", "say")
    private val KO_WORDS = words("ko", "को", "ला")
    private val MESSAGE_NOISE: Set<String> =
        MESSAGE_WORDS + SEND_WORDS + CONNECTORS + FILLERS + OPEN_VERBS

    private val ALARM_WORDS = words("alarm", "अलार्म")
    private val MORNING = words("subah", "सुबह", "morning", "am", "सकाळी")
    private val AFTERNOON = words("dopahar", "दोपहर", "afternoon", "दुपारी")
    private val EVENING = words("shaam", "शाम", "evening", "pm", "संध्याकाळी")
    private val NIGHT = words("raat", "रात", "night", "रात्री")
    private val SAADE = words("saade", "sade", "साढ़े", "साढे")
    private val SAVA = words("sava", "सवा")

    private val TIMER_WORDS = words("timer", "टाइमर")
    private val UNIT_MIN = words("minute", "minutes", "min", "mins", "मिनट")
    private val UNIT_SEC = words("second", "seconds", "sec", "secs", "सेकंड", "सेकेंड")
    private val UNIT_HOUR = words("ghanta", "ghante", "hour", "hours", "घंटा", "घंटे", "घण्टे")

    private val TORCH_WORDS = words("torch", "flashlight", "flash", "टॉर्च", "फ्लैशलाइट", "टार्च")
    private val OFF_WORDS = words("off", "band", "बंद", "bujhao", "बुझाओ", "bujha")

    private val VOLUME_WORDS = words("volume", "awaaz", "awaz", "आवाज़", "आवाज", "वॉल्यूम")
    private val UP_WORDS = words(
        "badhao", "increase", "up", "zyada", "tez", "बढ़ाओ", "बढ़ा", "ज्यादा", "तेज़", "तेज"
    )
    private val DOWN_WORDS = words(
        "kam", "kamm", "decrease", "down", "ghatao", "dheema", "कम", "घटाओ", "धीमा"
    )

    private val NAV_WORDS = words(
        "navigate", "navigation", "directions", "direction", "route", "rasta",
        "raasta", "रास्ता", "रस्ता", "नेविगेट", "नेविगेशन"
    )
    private val NAV_NOISE: Set<String> = NAV_WORDS + CONNECTORS + FILLERS + OPEN_VERBS +
        words("maps", "map", "google", "gmaps", "batao", "dikhao", "bata", "dikha",
            "चलो", "बताओ", "दिखाओ")

    private val PUNCT_TO_SPACE = Regex("[\\p{Punct}।॥“”‘’]")
    private val PUNCT_STRIP = Regex("[\\p{Punct}।॥“”‘’]")
    private val SPACES = Regex("\\s+")
    private val CLOCK = Regex("(\\d{1,2}):(\\d{2})")
    private val ATTACHED_MERIDIEM = Regex("(\\d)(am|pm)")
    private val HOUR_MIN = Regex("(\\d{1,2})h(\\d{2})")
    private val SMALL_NUMBER = Regex("\\d{1,2}")
    private val DURATION_NUMBER = Regex("\\d{1,4}")

    fun parse(step: String): StepIntent? {
        val tokens = tokenize(step)
        if (tokens.isEmpty()) return null
        return parseMessage(step)
            ?: parseAlarm(tokens)
            ?: parseTimer(tokens)
            ?: parseTorch(tokens)
            ?: parseVolume(tokens)
            ?: parseNavigate(tokens)
            ?: parseAppLaunch(tokens)
    }

    private fun tokenize(text: String): List<String> {
        val cleaned = norm(text)
            .replace(CLOCK, "$" + "1h$" + "2")
            .replace(ATTACHED_MERIDIEM, "$" + "1 $" + "2")
            .replace(PUNCT_TO_SPACE, " ")
            .replace(SPACES, " ")
            .trim()
        return if (cleaned.isEmpty()) emptyList() else cleaned.split(" ")
    }

    private fun stripEdges(tokens: List<String>, noise: Set<String>): List<String> {
        val list = tokens.toMutableList()
        while (list.isNotEmpty() && list.first() in noise) list.removeAt(0)
        while (list.isNotEmpty() && list.last() in noise) list.removeAt(list.size - 1)
        return list
    }

    private fun parseMessage(step: String): StepIntent.Message? {
        val raw = ArrayList<String>()
        val normed = ArrayList<String>()
        for (piece in step.trim().split(SPACES)) {
            val n = norm(piece).replace(PUNCT_STRIP, "")
            if (n.isNotEmpty()) { raw.add(piece); normed.add(n) }
        }
        if (normed.none { it in MESSAGE_WORDS } || normed.none { it in SEND_WORDS }) return null
        val app = when {
            normed.any { it in WHATSAPP_WORDS } -> "whatsapp"
            normed.any { it in SMS_WORDS } -> "sms"
            else -> null
        }
        val delim = normed.indexOfFirst { it in TEXT_DELIMS }
        val contactTokens: List<String>
        val text: String?
        if (delim >= 0) {
            contactTokens = stripEdges(normed.subList(0, delim), MESSAGE_NOISE)
            text = raw.subList(delim + 1, raw.size).joinToString(" ").trim().ifEmpty { null }
        } else {
            val koIndex = normed.indices.firstOrNull { it > 0 && normed[it] in KO_WORDS } ?: -1
            if (koIndex > 0) {
                contactTokens = stripEdges(normed.subList(0, koIndex), MESSAGE_NOISE)
                val restRaw = raw.subList(koIndex + 1, raw.size).toMutableList()
                val restNorm = normed.subList(koIndex + 1, normed.size).toMutableList()
                while (restRaw.isNotEmpty() && restNorm.first() in MESSAGE_NOISE) { restRaw.removeAt(0); restNorm.removeAt(0) }
                while (restRaw.isNotEmpty() && restNorm.last() in MESSAGE_NOISE) { restRaw.removeAt(restRaw.size - 1); restNorm.removeAt(restNorm.size - 1) }
                text = restRaw.joinToString(" ").trim().ifEmpty { null }
            } else {
                contactTokens = stripEdges(normed, MESSAGE_NOISE)
                text = null
            }
        }
        val contact = contactTokens.joinToString(" ").trim().ifEmpty { null }
        return StepIntent.Message(app, contact, text)
    }

    private fun parseAlarm(tokens: List<String>): StepIntent.Alarm? {
        if (tokens.none { it in ALARM_WORDS }) return null
        var hour = -1
        var minute = 0
        for ((i, t) in tokens.withIndex()) {
            val m = HOUR_MIN.matchEntire(t)
            if (m != null) { hour = m.groupValues[1].toInt(); minute = m.groupValues[2].toInt(); break }
            if (SMALL_NUMBER.matches(t)) {
                hour = t.toInt()
                minute = when { i > 0 && tokens[i - 1] in SAADE -> 30; i > 0 && tokens[i - 1] in SAVA -> 15; else -> 0 }
                break
            }
        }
        if (hour < 0 || hour > 23 || minute > 59) return null
        val seen = tokens.toSet()
        val morning = seen.any { it in MORNING }
        val afternoon = seen.any { it in AFTERNOON }
        val evening = seen.any { it in EVENING }
        val night = seen.any { it in NIGHT }
        var h = hour
        if (h in 1..11) {
            if (afternoon || evening) h += 12 else if (night && h >= 6) h += 12
        } else if (h == 12 && (morning || night) && !(afternoon || evening)) h = 0
        return StepIntent.Alarm(h, minute)
    }

    private fun parseTimer(tokens: List<String>): StepIntent.Timer? {
        if (tokens.none { it in TIMER_WORDS }) return null
        var total = 0
        for (i in 0 until tokens.size - 1) {
            if (!DURATION_NUMBER.matches(tokens[i])) continue
            val value = tokens[i].toInt()
            val unit = tokens[i + 1]
            if (unit in UNIT_MIN) total += value * 60
            else if (unit in UNIT_SEC) total += value
            else if (unit in UNIT_HOUR) total += value * 3600
        }
        return if (total > 0) StepIntent.Timer(total) else null
    }

    private fun parseTorch(tokens: List<String>): StepIntent.Torch? {
        if (tokens.none { it in TORCH_WORDS }) return null
        return StepIntent.Torch(tokens.none { it in OFF_WORDS })
    }

    private fun parseVolume(tokens: List<String>): StepIntent.Volume? {
        if (tokens.none { it in VOLUME_WORDS }) return null
        if (tokens.any { it in DOWN_WORDS }) return StepIntent.Volume(false)
        if (tokens.any { it in UP_WORDS }) return StepIntent.Volume(true)
        return null
    }

    private fun parseAppLaunch(tokens: List<String>): StepIntent.AppLaunch? {
        val hasVerb = tokens.firstOrNull()?.let { it in OPEN_VERBS } == true
        val candidate = if (hasVerb) tokens.drop(1).toMutableList() else tokens.toMutableList()
        while (candidate.isNotEmpty()) {
            val suffix = APP_TRAILING_NOISE.firstOrNull { seq ->
                seq.size <= candidate.size && seq.indices.all { k ->
                    candidate[candidate.size - seq.size + k] == seq[k]
                }
            } ?: break
            repeat(suffix.size) { candidate.removeAt(candidate.lastIndex) }
        }
        if (candidate.isEmpty()) return null
        // A bare app name is accepted only from a conservative alias list. An explicit
        // launch verb may use any installed label, but never swallow a compound sentence.
        val candidateName = candidate.joinToString(" ").trim()
        if (!hasVerb && candidateName !in APP_ALIASES) return null
        // WhatsApp is both an app alias and a message transport. A bare app
        // launch must remain launchable; message intent is recognized above only
        // when an explicit send verb accompanies a message keyword.
        val nonAppMessageWords = MESSAGE_WORDS - APP_ALIASES
        if (candidate.any { it in APP_CONNECTORS || it in TEXT_DELIMS || it in nonAppMessageWords }) return null
        val label = APP_ALIAS_CANONICAL[candidateName] ?: candidateName
        if (label.length !in 2..60) return null
        return StepIntent.AppLaunch(label)
    }

    private fun parseNavigate(tokens: List<String>): StepIntent.Navigate? {
        if (tokens.none { it in NAV_WORDS }) return null
        val place = stripEdges(tokens, NAV_NOISE).joinToString(" ").trim()
        return if (place.isEmpty()) null else StepIntent.Navigate(place)
    }
}
