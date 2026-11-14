package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

internal data class MessageRequest(
    val contactName: String,
    val channel: Channel,
    val message: String,
    val sendNow: Boolean
) {
    enum class Channel { WHATSAPP, SMS }
}

/** Parses explicit contact + channel + message requests without involving the LLM. */
internal object MessageCommandParser {
    private val commandPattern = Regex(
        "^(.+?)\\s+(?:ko|la|को|ला)\\s+(whatsapp|wa|व्हाट्सएप|व्हॉट्सअॅप|व्हॉट्सॲप|sms|message|संदेश)\\s*(?:(?:pe|par|पर|वर)\\s*)?(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val sendSuffix = Regex(
        "(?:^|\\s+)(?:bhejo|bhej do|send(?:\\s+karo)?|भेजो|भेज दो|पाठवा|पाठव|" +
            "dhad|dhada|dhadva|pathva|pathav|pathvaa|धाड|धाडा|पाठवून\\s+दे)(?:\\s*:\\s*|\\s*$)",
        RegexOption.IGNORE_CASE
    )
    private val nonMessageSuffix = Regex(
        "(?:^|\\s+)(?:likho|type karo|लिखो|लिहा)\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun parse(command: String): MessageRequest? {
        val cleaned = Normalizer.normalize(VoiceTextNormalizer.normalize(command), Normalizer.Form.NFKC).trim()
        val replyMatch = Regex("^(.+?)\\s+(?:ko|को|ला)\\s+reply(?:\\s+karo)?\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (replyMatch != null) {
            val name = replyMatch.groupValues[1].trim()
            val body = replyMatch.groupValues[2].trim()
            if (name.isNotBlank() && body.isNotBlank()) {
                return MessageRequest(name, MessageRequest.Channel.WHATSAPP, body, true)
            }
        }
        val match = commandPattern.matchEntire(cleaned) ?: return null
        val name = match.groupValues[1].trim()
            .replace(Regex("(?i)^(please|plz|zara|jara|mujhe|mera|meri|मुझे|मेरा|मेरी|मला|ज़रा|जरा)\\s+"), "")
            .replace(Regex("(?i)\\s+(karo|kar|kro|kare|karen|kardo|karna|please|plz|na|ज़रा|जरा)$"), "")
            .trim()
        var body = match.groupValues[3].trim()
            .replace(Regex("(?i)^message\\s+"), "")
        val sendNow = sendSuffix.containsMatchIn(body)
        if (sendNow) body = sendSuffix.replace(body, "").trim().removePrefix(":").trim()
        else body = nonMessageSuffix.replace(body, "").trim()
        if (name.isBlank() || body.isBlank()) return null
        val channel = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "sms", "संदेश" -> MessageRequest.Channel.SMS
            else -> MessageRequest.Channel.WHATSAPP
        }
        return MessageRequest(name, channel, body, sendNow)
    }
}
