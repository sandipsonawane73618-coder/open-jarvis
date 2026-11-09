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
        "mute", "mute now", "mute please", "jarvis mute", "mute jarvis", "turn voice off", "voice assistant off", "jarvis off",
        "stop listening", "jarvis stop listening", "stop hearing",
        "mic off", "microphone off", "turn off mic", "turn off the microphone",
        "chup ho jao", "jarvis chup ho jao", "chup raho",
        "mic band", "mic band karo", "mic bandh karo", "jarvis mic band karo",
        "sunna band karo", "suno band karo", "awaaz band karo", "awaz band karo",
        "mic band kar", "jarvis mic band kar", "aikna band kar", "aikaycha band kar",
        "आवाज बंद करो", "माइक बंद करो", "माइक बंद कर", "सुनना बंद करो",
        "सुनना बंद कर", "चुप हो जाओ", "जार्विस चुप हो जाओ",
        "ऐकणं बंद कर", "ऐकायचं बंद कर", "ऐकणं थांबव", "ऐकायचं थांबव",
        "माइक बंद करा", "आवाज बंद करा"
    )

    private val unmuteCommands = setOf(
        "unmute", "unmute now", "unmute please", "jarvis unmute", "turn voice on", "voice assistant on", "jarvis on",
        "resume listening", "start listening", "listen again",
        "mic on", "microphone on", "turn on mic", "turn on the microphone",
        "ab bolo", "jarvis ab bolo", "अब बोलो", "जार्विस अब बोलो",
        "mic chalu karo", "mic chalu kar", "mic on karo",
        "sunna shuru karo", "phir se suno", "awaaz chalu karo", "awaz chalu karo",
        "listen to me", "jarvis listen",
        "mic chalu kara", "mic suru kara", "punha aika", "aikayla suru kar",
        "aikna suru kar", "punha aik",
        "माइक चालू करो", "माइक चालू कर", "माइक चालू करा",
        "आवाज चालू करो", "आवाज चालू करा", "सुनना शुरू करो", "फिर से सुनो",
        "पुन्हा ऐक", "पुन्हा ऐका", "ऐकायला सुरू कर", "ऐकायला सुरू करा",
        "ऐकणं सुरू कर", "ऐकायचं सुरू कर", "माइक सुरू कर", "माइक सुरू करा",
        "आवाज सुरू कर", "आवाज सुरू करा"
    )

    val recognitionPhrases: List<String> =
        (muteCommands + unmuteCommands).sorted()

    fun isMuteCommand(command: String): Boolean = stripWakePrefix(normalize(command)) in muteCommands

    fun isUnmuteCommand(command: String): Boolean = stripWakePrefix(normalize(command)) in unmuteCommands

    private fun stripWakePrefix(value: String): String {
        var result = value
        val prefixes = listOf("hey jarvis", "hi jarvis", "jarvis", "हे जार्विस", "जार्विस")
        while (true) {
            val prefix = prefixes.firstOrNull { result == it || result.startsWith("$it ") } ?: break
            result = result.removePrefix(prefix).trim()
        }
        return result
    }

    private fun normalize(command: String): String =
        Normalizer.normalize(command, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
