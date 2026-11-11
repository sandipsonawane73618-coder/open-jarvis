package com.openjarvis.voice

import java.text.Normalizer

/**
 * Exact, language-aware voice-session controls.
 *
 * Mute is a command lock: the foreground microphone remains active only so the
 * user can say an explicit Unmute phrase. The overlay/notification must disclose this.
 */
object VoiceControlVocabulary {
    private val muteCommands = setOf(
        "mute", "mute now", "mute please", "mute ho ja", "mute ho jao", "jarvis mute", "mute jarvis",
        "jarvis mute ho ja", "turn voice off", "voice assistant off", "jarvis off",
        "stop listening", "jarvis stop listening", "stop hearing",
        "mic off", "microphone off", "turn off mic", "turn off the microphone",
        "chup ho ja", "chup ho jao", "jarvis chup ho ja", "jarvis chup ho jao", "chup raho",
        "mic band", "mic band karo", "mic bandh karo", "jarvis mic band karo",
        "sunna band karo", "suno band karo", "awaaz band karo", "awaz band karo",
        "mic band kar", "jarvis mic band kar", "aikna band kar", "aikaycha band kar",
        "आवाज बंद करो", "आवाज़ बंद करो", "आवाज बंद कर", "आवाज़ बंद कर", "माइक बंद करो", "माइक बंद कर", "सुनना बंद करो",
        "सुनना बंद कर", "चुप हो जा", "चुप हो जाओ", "चुप रहो", "जार्विस चुप हो जाओ", "गप्प बस", "गप्प बसा",
        "ऐकणं बंद कर", "ऐकायचं बंद कर", "ऐकणं थांबव", "ऐकायचं थांबव",
        "माइक बंद करा", "आवाज बंद करा",
        "shant raha",
        "shant raho",
        "shant ho jao", "shant ho", "be quiet", "quiet jarvis", "gapp raha", "gappa bas",
        "शांत रहा", "शांत रहो", "शांत हो जाओ", "गप्प रहा", "गप्प बसा",
    )

    private val unmuteCommands = setOf(
        "unmute", "unmute now", "unmute please", "unmute karo", "unmute kar", "unmute kar do", "unmute kardo",
        "jarvis unmute", "turn voice on", "voice assistant on", "jarvis on",
        "resume listening", "start listening", "listen again",
        "mic on", "microphone on", "turn on mic", "turn on the microphone",
        "ab bolo", "jarvis ab bolo", "अब बोलो", "जार्विस अब बोलो",
        "mic chalu karo", "mic chalu kar", "mic on karo",
        "voice chalu karo", "voice chalu kar do", "awaaz chalu", "awaz chalu", "awaaz chalu kar", "awaz chalu kar",
        "sunna shuru karo", "phir se suno", "awaaz chalu karo", "awaz chalu karo",
        "listen to me", "jarvis listen",
        "mic chalu kara", "mic suru kara", "punha aika", "aikayla suru kar",
        "aikna suru kar", "punha aik",
        "माइक चालू करो", "माइक चालू कर", "माइक चालू करा",
        "आवाज चालू करो", "आवाज़ चालू करो", "आवाज चालू कर", "आवाज़ चालू कर", "आवाज चालू करा", "आवाज़ चालू करा", "सुनना शुरू करो", "फिर से सुनो",
        "पुन्हा ऐक", "पुन्हा ऐका", "पुन्हा बोल", "पुन्हा बोला", "ऐकायला सुरू कर", "ऐकायला सुरू करा",
        "ऐकणं सुरू कर", "ऐकायचं सुरू कर", "माइक सुरू कर", "माइक सुरू करा",
        "आवाज सुरू कर", "आवाज़ सुरू कर", "आवाज सुरू करा", "आवाज़ सुरू करा",
        "ab suno",
        "ab sunna shuru karo", "listen", "start hearing", "resume voice", "speak again",
        "आता ऐका", "ऐका", "पुन्हा ऐका", "फिर से सुनो", "सुनना शुरू करो",
        "आज ऐका", "पुन्हा ऐकायला सुरू करा",
    )

    val recognitionPhrases: List<String> =
        (muteCommands + unmuteCommands).sorted()

    fun isMuteCommand(command: String): Boolean = stripWakePrefix(normalize(command)) in muteCommands

    fun isUnmuteCommand(command: String): Boolean = stripWakePrefix(normalize(command)) in unmuteCommands

    private fun stripWakePrefix(value: String): String {
        var result = value
        val wakePhrases = listOf("hey jarvis", "hi jarvis", "jarvis", "हे जार्विस", "जार्विस")
        var changed: Boolean
        do {
            changed = false
            val prefix = wakePhrases.firstOrNull { result == it || result.startsWith("$it ") }
            if (prefix != null) {
                result = result.removePrefix(prefix).trim()
                changed = true
            }
            val suffix = wakePhrases.firstOrNull { result == it || result.endsWith(" $it") }
            if (suffix != null) {
                result = if (result == suffix) "" else result.removeSuffix(" $suffix").trim()
                changed = true
            }
        } while (changed)
        return result
    }

    private fun normalize(command: String): String =
        Normalizer.normalize(command, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
