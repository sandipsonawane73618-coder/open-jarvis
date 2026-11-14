package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

internal data class InstalledAppTarget(val label: String, val packageName: String)

/** Resolves explicit app mentions using installed labels, stable aliases and learned aliases. */
internal object AppTargetResolver {
    private data class Hit(val start: Int, val end: Int, val alias: String, val app: InstalledAppTarget)
    private val placeholders = setOf(
        "", "app", "application", "target", "target app", "app name",
        "application name", "exact installed app label", "app_name"
    )

    // Stable spoken nicknames. Package matching keeps this independent of translated app labels.
    private val packageAliases = mapOf(
        "com.instagram.android" to setOf("insta", "instagram", "इंस्टा", "इन्स्टा", "इंस्टाग्राम"),
        "com.whatsapp" to setOf("wa", "whatsapp", "whats app", "व्हाट्सएप", "व्हॉट्सअॅप", "व्हॉट्सॲप"),
        "com.google.android.youtube" to setOf("yt", "youtube", "you tube", "यूट्यूब"),
        "com.facebook.katana" to setOf("fb", "facebook", "फेसबुक"),
        "com.google.android.apps.nbu.paisa.user" to setOf("gpay", "google pay", "googlepay", "जीपे", "गूगल पे"),
        "com.openai.chatgpt" to setOf("chatgpt", "chat gpt", "chat-gpt", "चैटजीपीटी", "चैट जीपीटी")
    )

    fun isPlaceholder(value: String?): Boolean = normalize(value.orEmpty()) in placeholders

    /**
     * learnedAliases is an injection point for the future LearnedAliases store.
     * It is queried per installed app and must return aliases scoped to that app.
     */
    fun findMentionedApps(
        command: String,
        installedApps: List<InstalledAppTarget>,
        learnedAliases: (InstalledAppTarget) -> Set<String> = { emptySet() }
    ): List<InstalledAppTarget>? {
        val query = normalize(command)
        if (query.isBlank()) return emptyList()
        val hits = installedApps.flatMap { app ->
            aliasesFor(app, learnedAliases(app)).mapNotNull { alias ->
                val phrase = normalize(alias)
                if (phrase.isBlank()) return@mapNotNull null
                val pattern = Regex(
                    "(?<![\\p{L}\\p{N}\\p{M}])" + Regex.escape(phrase) +
                        "(?![\\p{L}\\p{N}\\p{M}])"
                )
                val match = pattern.find(query) ?: return@mapNotNull null
                Hit(match.range.first, match.range.last + 1, phrase, app)
            }
        }.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.alias.length })
        if (hits.isEmpty()) return emptyList()

        val output = mutableListOf<InstalledAppTarget>()
        val seen = mutableSetOf<String>()
        var cursor = 0
        var index = 0
        while (index < hits.size) {
            val first = hits[index]
            if (first.start < cursor) { index++; continue }
            val sameStart = hits.drop(index).takeWhile { it.start == first.start }
            val longest = sameStart.maxOf { it.alias.length }
            val best = sameStart.filter { it.alias.length == longest }.distinctBy { it.app.packageName }
            val chosen = if (best.size > 1 && isYouTubeAlias(first.alias)) {
                val modApps = best.filter { it.app.packageName != OFFICIAL_YOUTUBE_PACKAGE }
                when {
                    modApps.size == 1 -> modApps.single()
                    modApps.size > 1 -> return null
                    else -> best.singleOrNull() ?: return null
                }
            } else {
                if (best.size > 1) return null
                best.first()
            }
            if (seen.add(chosen.app.packageName)) output.add(chosen.app)
            cursor = chosen.end
            index = hits.indexOfFirst { it.start >= cursor }.let { if (it < 0) hits.size else it }
        }
        return output
    }

    private fun aliasesFor(
        app: InstalledAppTarget,
        learned: Set<String>
    ): Set<String> {
        val label = normalize(app.label)
        val aliases = linkedSetOf(label)
        aliases.addAll(packageAliases[app.packageName].orEmpty())
        aliases.addAll(learned)
        if (isYouTubeVariant(app)) aliases.addAll(listOf("youtube", "you tube", "yt", "यूट्यूब"))
        when (label) {
            "chrome", "google chrome" -> aliases.addAll(listOf("chrome", "google chrome"))
            "whatsapp", "whats app" -> aliases.addAll(listOf("whatsapp", "whats app", "व्हाट्सएप", "व्हॉट्सअॅप"))
            "youtube", "you tube" -> aliases.addAll(listOf("youtube", "you tube", "यूट्यूब"))
            "settings", "setting" -> aliases.addAll(listOf("settings", "setting", "सेटिंग", "सेटिंग्स", "सेटिंग्ज", "सेटिंग्ज"))
            "phone", "dialer" -> aliases.addAll(listOf("phone", "dialer", "फोन"))
            "messages", "messaging" -> aliases.addAll(listOf("messages", "messaging", "sms", "संदेश", "मेसेजेस"))
            "contacts" -> aliases.addAll(listOf("contacts", "contact", "संपर्क"))
            "photos", "google photos" -> aliases.addAll(listOf("photos", "google photos", "फोटो"))
            "gallery" -> aliases.addAll(listOf("gallery", "गॅलरी"))
            "files", "file manager", "my files" -> aliases.addAll(listOf("files", "file manager", "my files"))
            "play store", "google play store" -> aliases.addAll(listOf("play store", "google play store", "प्ले स्टोर"))
            "gmail" -> aliases.addAll(listOf("gmail", "जीमेल"))
            "maps", "google maps" -> aliases.addAll(listOf("maps", "google maps", "नकाशे"))
            "camera" -> aliases.addAll(listOf("camera", "कैमरा", "कॅमेरा"))
            "calculator" -> aliases.addAll(listOf("calculator", "कैलकुलेटर"))
            "clock" -> aliases.addAll(listOf("clock", "घड़ी", "घड्याळ"))
            "telegram" -> aliases.addAll(listOf("telegram", "टेलीग्राम"))
            "instagram" -> aliases.addAll(listOf("instagram", "इंस्टाग्राम"))
            "facebook" -> aliases.addAll(listOf("facebook", "फेसबुक"))
        }
        return aliases.filter { normalize(it).length >= 2 }.toSet()
    }

    internal fun youtubeCandidates(installedApps: List<InstalledAppTarget>): List<InstalledAppTarget> =
        installedApps.filter(::isYouTubeVariant).distinctBy { it.packageName }

    private fun isYouTubeVariant(app: InstalledAppTarget): Boolean {
        if (app.packageName == OFFICIAL_YOUTUBE_PACKAGE) return true
        val label = normalize(app.label)
        // Resolve only the actual YouTube app family. Package-name substring matches
        // can incorrectly capture downloaders and unrelated companion apps.
        val excluded = listOf(
            "youtube music", "youtube kids", "youtube tv", "youtube studio",
            "youtube create", "youtube downloader", "youtube go"
        )
        if (excluded.any { label == it || label.startsWith("$it ") }) return false
        return label == "youtube" || label.startsWith("youtube ")
    }

    private fun isYouTubeAlias(alias: String): Boolean =
        normalize(alias) in setOf("youtube", "you tube", "yt", "यूट्यूब")

    private const val OFFICIAL_YOUTUBE_PACKAGE = "com.google.android.youtube"

    private fun normalize(value: String): String =
        Normalizer.normalize(VoiceTextNormalizer.normalize(value), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
