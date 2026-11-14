package com.openjarvis.intelligence

/**
 * Deterministic commands that should never depend on an LLM-generated action plan.
 * Keep this parser deliberately conservative: an unrecognized command falls through to Ollama.
 */
internal object FastCommandParser {
    sealed class Command {
        data class OpenApp(val target: String) : Command()
        object CloseCurrentApp : Command()
        object Back : Command()
        object Home : Command()
        object ShowRecents : Command()
        object ShowNotifications : Command()
        data class Scroll(val direction: String) : Command()
        data class OpenSettings(val action: String) : Command()
        data class Dial(val number: String) : Command()
        data class SetTimer(val seconds: Int) : Command()
        data class DialContact(val name: String) : Command()
        data class YoutubeSearch(val query: String) : Command()
        data class Remember(val fact: String) : Command()
        data class Forget(val query: String) : Command()
        object ShowMemory : Command()
    }

    private val englishOpen = Regex("^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val hindiOpen = Regex("^(.+?)\\s+(?:kholo|khol\\s+do|open\\s+karo|chalu\\s+karo|start\\s+karo|खोलो|खोल\\s+दो|खोलें|चालू\\s+करो|उघडा|उघड|उघडा\\s+ना|सुरू\\s+करा|चालू\\s+करा)$", RegexOption.IGNORE_CASE)
    private val close = Regex("^(?:please\\s+)?(?:close|exit|band\\s+karo|band\\s+kar\\s+do)(?:\\s+(?:the\\s+)?app)?$", RegexOption.IGNORE_CASE)
    private val dial = Regex("^(?:call|dial|phone|mujhe\\s+call\\s+karo|call\\s+karo)\\s+([+0-9][+0-9() .-]{4,})$", RegexOption.IGNORE_CASE)
    private val youtubeSearch = Regex("^youtube\\s+(?:par\\s+)?(.+?)\\s+(?:search|dhundo|khojo)(?:\\s+karo)?$", RegexOption.IGNORE_CASE)
    private val youtubeSearchWithOpen = Regex("^youtube\\s+kholo\\s+(?:aur|then)\\s+(.+?)\\s+(?:search|dhundo|khojo)(?:\\s+karo)?$", RegexOption.IGNORE_CASE)
    private val timer = Regex("^(?:(?:please )?set )?(?:a )?timer(?: for)? (\\d{1,4}) (seconds?|secs?|minutes?|mins?|hours?|hrs?)$|^(\\d{1,4}) (seconds?|secs?|minutes?|mins?|hours?|hrs?) timer$", RegexOption.IGNORE_CASE)
    private val contactCall = Regex("^(?:please\\s+)?(?:call|dial|phone)\\s+([\\p{L}][\\p{L}\\p{M} .'-]{1,59})$|^([\\p{L}][\\p{L}\\p{M} .'-]{1,59})\\s+(?:ko\\s+call\\s+karo|ko\\s+phone\\s+karo|ला\\s+कॉल\\s+करा|को\\s+कॉल\\s+करो)$", RegexOption.IGNORE_CASE)
    private val BACK_COMMANDS = setOf(
        "back", "go back", "back screen per jao", "back screen pe jao",
        "pichhe", "pichhe ja", "pichhe jao",
        "peeche", "peeche ja", "peeche jao", "piche jao",
        "wapas", "wapas ja", "wapas jao",
        "mage", "mage ja", "mage jaa",
        "वापस", "वापस जाओ", "पीछे जाओ", "मागे", "मागे जा", "मागे चला"
    )

    fun parseSequence(command: String): List<Command>? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null

        // Keep the supported YouTube compound phrase as one ACTION_VIEW skill.
        if (youtubeSearchWithOpen.matches(normalized) || youtubeSearch.matches(normalized)) {
            return listOf(parse(normalized) ?: return null)
        }

        val parts = normalized.split(Regex("\\s+(?:aur|phir|ani|then)\\s+", RegexOption.IGNORE_CASE))
        if (parts.size > 1) {
            if (parts.size !in 2..5) return null
            return parts.map { parse(it) ?: return null }
        }
        return parse(normalized)?.let(::listOf)
    }

    fun parse(command: String): Command? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null

        timer.matchEntire(normalized)?.let { match ->
            val amount = (match.groupValues[1].ifBlank { match.groupValues[3] }).toIntOrNull() ?: return null
            val unit = (match.groupValues[2].ifBlank { match.groupValues[4] }).lowercase()
            val multiplier = when {
                unit.startsWith("sec") -> 1
                unit.startsWith("min") -> 60
                else -> 3600
            }
            val seconds = amount * multiplier
            if (seconds !in 1..86400) return null
            return Command.SetTimer(seconds)
        }
        youtubeSearchWithOpen.matchEntire(normalized)?.let { return Command.YoutubeSearch(it.groupValues[1].trim()) }
        youtubeSearch.matchEntire(normalized)?.let { return Command.YoutubeSearch(it.groupValues[1].trim()) }

        if (Regex("^(?:show|read|list) (?:my )?memory(?: list)?$|^what do you remember$|^mujhe kya yaad hai(?: list bolo)?$|^मुझे क्या याद है(?: सूची बताओ)?$|^मला काय लक्षात आहे(?: ते सांगा)?$|^माझी आठवण सांगा$", RegexOption.IGNORE_CASE).matches(normalized)) {
            return Command.ShowMemory
        }
        if (Regex("^(?:forget all|forget everything|sab bhool jao|sab kuch bhool jao|bhool jao sab kuch|sab visra|sagla visra|सभी भूल जाओ|सब भूल जाओ|सब कुछ भूल जाओ|सगळं विसर|सर्व विसर)$", RegexOption.IGNORE_CASE).matches(normalized)) {
            return Command.Forget("all")
        }
        val remember = Regex("^(?:remember(?: that)?|yaad rakhna(?: ki)?|yaad rakho(?: ki)?|याद रखना|याद रखो|लक्षात ठेव|लक्षात ठेवा)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(normalized)
        if (remember != null) return Command.Remember(remember.groupValues[1].trim())
        val forget = Regex("^(?:forget|bhool jao|bhul jao|भूल जाओ|विसर|विसरा)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(normalized)
        if (forget != null) return Command.Forget(forget.groupValues[1].trim())

        if (Regex("^(?:show|open|display)\\s+(?:recent apps|recents)$|^(?:recent apps|recents)\\s+(?:dikhao|dikha do|kholo)$|^अलीकडील अॅप्स\\s+(?:दाखवा|उघडा)$", RegexOption.IGNORE_CASE).matches(normalized)) {
            return Command.ShowRecents
        }
        if (Regex("^(?:show|open)\\s+(?:notification panel|notification shade|notifications)$|^(?:notification panel|notifications)\\s+(?:kholo|dikhao|khol do)$|^(?:नोटिफिकेशन पैनल खोलो|सूचना पॅनेल उघडा)$", RegexOption.IGNORE_CASE).matches(normalized)) {
            return Command.ShowNotifications
        }
        if (close.matches(normalized)) return Command.CloseCurrentApp
        if (normalized in BACK_COMMANDS) return Command.Back
        if (normalized.matches(Regex("^(?:home|go home|ghar|होम|होम पर जाओ|घर जाओ|मुख्यपृष्ठ|घरी जा)$", RegexOption.IGNORE_CASE))) return Command.Home

        val scroll = Regex("^(?:scroll|स्क्रॉल)\\s*(up|down|upar|neeche|ऊपर|नीचे|वर|खाली)?$", RegexOption.IGNORE_CASE).matchEntire(normalized)
        if (scroll != null) {
            val direction = when (scroll.groupValues[1].lowercase()) {
                "up", "upar", "ऊपर", "वर" -> "up"
                else -> "down"
            }
            return Command.Scroll(direction)
        }

        val settings = settingsAction(normalized)
        if (settings != null) return Command.OpenSettings(settings)

        val dialMatch = dial.matchEntire(normalized)
        if (dialMatch != null) {
            val number = dialMatch.groupValues[1].replace(Regex("[^+0-9]"), "")
            if (number.count { it.isDigit() } in 5..15) return Command.Dial(number)
        }

        val contact = contactCall.matchEntire(normalized)
        if (contact != null) {
            val name = contact.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
            if (!name.isNullOrBlank()) return Command.DialContact(name)
        }
        val target = openAppTarget(normalized)
        if (target != null) return Command.OpenApp(target)
        return null
    }

    fun openAppTarget(command: String): String? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null
        val match = englishOpen.matchEntire(normalized) ?: hindiOpen.matchEntire(normalized) ?: return null
        val target = match.groupValues[1].trim()
        if (target.isBlank() || target.length > 60) return null
        // Canonicalize common Devanagari app names so package/installed-label
        // resolution receives a stable target instead of a translated display name.
        val canonicalTarget = when (com.openjarvis.agent.VoiceTextNormalizer.canonicalAppLabel(target)) {
            "youtube" -> "YouTube"
            "whatsapp" -> "WhatsApp"
            "instagram" -> "Instagram"
            "telegram" -> "Telegram"
            "chrome" -> "Chrome"
            "camera" -> "Camera"
            "settings" -> "Settings"
            "gallery" -> "Gallery"
            "spotify" -> "Spotify"
            "gmail" -> "Gmail"
            "maps" -> "Maps"
            else -> target
        }
        // A fast-path target must be only an app label. Compound requests
        // (open WhatsApp and send a message...) must reach the AI planner.
        return canonicalTarget.takeIf {
            !Regex(
                "\\b(and|then|also|aur|phir|fir|send|message|text|reply|type|search|play|karo)\\b",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(it)
        }
    }

    private fun settingsAction(command: String): String? {
        val q = command.lowercase()
        return when {
            listOf("wifi", "wi-fi", "वाईफाई", "वायफाय", "वाय-फाय").any { q.contains(it) } && listOf("setting", "settings", "सेटिंग", "सेटिंग्ज", "on karo", "kholo", "khol", "उघडा", "उघड").any { q.contains(it) } -> "wifi"
            listOf("bluetooth", "ब्लूटूथ").any { q.contains(it) } && listOf("setting", "settings", "सेटिंग", "सेटिंग्ज", "kholo", "khol", "उघडा", "उघड").any { q.contains(it) } -> "bluetooth"
            listOf("display settings", "display setting", "screen settings", "brightness settings").any { q.contains(it) } -> "display"
            listOf("sound settings", "sound setting", "volume settings", "audio settings").any { q.contains(it) } -> "sound"
            listOf("location setting", "location settings", "gps setting", "gps settings").any { q.contains(it) } -> "location"
            listOf("accessibility setting", "accessibility settings").any { q.contains(it) } -> "accessibility"
            listOf("notification access", "notification listener", "notification settings", "notifications settings").any { q.contains(it) } -> "notifications"
            listOf("app settings", "applications setting", "manage apps").any { q.contains(it) } -> "apps"
            q in listOf("settings", "open settings", "settings kholo", "settings khol do", "सेटिंग खोलो", "सेटिंग्ज उघडा", "सेटिंग्स उघडा") -> "general"
            else -> null
        }
    }

    private fun normalize(command: String): String =
        com.openjarvis.agent.VoiceTextNormalizer.normalize(command).trim().replace(Regex("\\s+"), " ")
}
