package com.openjarvis.intelligence

import java.text.Normalizer
import java.util.Locale

/**
 * Privacy boundary for notification content. This is applied before a notification
 * enters the in-memory buffer and again before any text is spoken.
 */
internal object NotificationPrivacyPolicy {
    private val otp = Regex(
        "(?i)\\b(?:otp|one[- ]time password|verification code|security code)\\b\\s*(?:is|:|=|-)?\\s*\\d{4,8}\\b"
    )
    // Notifications commonly place the secret before its label: "482913 is your OTP".
    private val otpBeforeLabel = Regex(
        "(?i)\\b\\d{4,8}\\s+(?:is\\s+)?(?:your\\s+)?(?:otp|one[- ]time password|verification code|security code)\\b"
    )
    private val pin = Regex(
        "(?i)\\b(?:upi\\s*)?pin\\b\\s*(?:is|:|=|-)?\\s*\\d{4,8}\\b"
    )
    private val pinBeforeLabel = Regex(
        "(?i)\\b\\d{4,8}\\s+(?:is\\s+)?(?:your\\s+)?(?:upi\\s*)?pin\\b"
    )
    private val card = Regex("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)")
    private val cvv = Regex(
        "(?i)\\b(?:cvv|cvc|security code)\\b\\s*(?:is|:|=|-)?\\s*\\d{3,4}\\b"
    )
    private val password = Regex(
        "(?i)\\b(?:password|passcode|pass phrase)\\b\\s*(?:is|:|=|-)?\\s*[^\\s,;.!?]{4,}"
    )

    fun redact(value: String): String {
        var safe = Normalizer.normalize(value, Normalizer.Form.NFC)
        safe = otp.replace(safe, "[REDACTED]")
        safe = otpBeforeLabel.replace(safe, "[REDACTED]")
        safe = pin.replace(safe, "[REDACTED]")
        safe = pinBeforeLabel.replace(safe, "[REDACTED]")
        safe = cvv.replace(safe, "[REDACTED]")
        safe = password.replace(safe, "[REDACTED]")
        safe = card.replace(safe) { match ->
            if (match.value.count(Char::isDigit) >= 13) "[REDACTED]" else match.value
        }
        return safe
    }

    fun shouldStore(packageName: String): Boolean =
        JarvisNotificationListener.shouldProcessNotification(packageName)
}

/** A pure bounded-history policy. New entries are appended newest-last. */
internal object NotificationBufferPolicy {
    const val MAX_ITEMS = 50
    fun keepLatest(items: List<Long>): List<Long> = items.takeLast(MAX_ITEMS)
}

/** Deterministic route detector; must run before generic chat/search routing. */
internal object ConnectedAppCommandParser {
    private val notificationIntent = Regex(
        "(?i)(koi\\s+naya\\s+message|koi\\s+message|whatsapp\\s+par\\s+kisne|notifications?\\s+padho|sab\\s+padh\\s+ke\\s+suna[o]?|messages?\\s+padho|naye\\s+message)"
    )

    fun route(command: String): String? {
        val normalized = Normalizer.normalize(command, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        return when {
            notificationIntent.containsMatchIn(normalized) || senderFilter(command) != null -> "NOTIFICATION_READ"
            Regex("(?i)\\b(last\\s+sms|sms\\s+kisne|last\\s+message)\\b").containsMatchIn(normalized) -> "SMS_READ"
            Regex("(?i)\\b(aaj|kal|today|tomorrow|आज|कल)\\b.*\\b(events?|meeting|calendar|schedule)\\b|\\b(events?|meeting)\\b.*\\b(aaj|kal|today|tomorrow|आज|कल)\\b").containsMatchIn(normalized) -> "CALENDAR_READ"
            Regex("(?i)^.+\\s+(?:ka|ki)\\s+number[?!.।॥]*$").matches(normalized) -> "CONTACT_READ"
            else -> null
        }
    }

    fun contactName(command: String): String? =
        Regex("(?i)^(.+?)\\s+(?:ka|ki)\\s+number[?!.।॥]*$").matchEntire(command.trim())
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it.length <= 80 }

    fun senderFilter(command: String): String? {
        val cleaned = Normalizer.normalize(command, Normalizer.Form.NFKC).trim()
        val match = Regex("(?i)^(.+?)\\s+(?:ka|ki)\\s+kya\\s+message(?:\\s+hai)?[?!.।॥]*$").matchEntire(cleaned)
            ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 80 }
    }

    fun wantsMoreThanFive(command: String): Boolean {
        val value = command.lowercase(Locale.ROOT)
        return listOf("sab", "saare", "all", "jitne", "more", "aur").any(value::contains)
    }

    fun whatsappOnly(command: String): Boolean = command.contains("whatsapp", ignoreCase = true)
}

internal object CalendarQueryParser {
    fun dayOffset(command: String): Int? {
        val normalized = Normalizer.normalize(command, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        return when {
            Regex("(?i)\\b(aaj|today|आज)\\b").containsMatchIn(normalized) -> 0
            Regex("(?i)\\b(kal|tomorrow|उद्या|कल)\\b").containsMatchIn(normalized) -> 1
            Regex("(?i)\\b(parso|day after tomorrow|परसों|परवा)\\b").containsMatchIn(normalized) -> 2
            else -> null
        }
    }
}

internal object PermissionStatusMessage {
    fun denied(permission: String): String {
        val source = when (permission.uppercase(Locale.ROOT)) {
            "READ_CONTACTS" -> "Contacts"
            "READ_CALENDAR" -> "Calendar"
            "READ_SMS" -> "SMS"
            else -> "This feature"
        }
        return "$source permission is not granted. Allow it in Android app settings, then try again."
    }
}

/** Exact preview binding; execution still belongs to the existing send-safety flow. */
internal object NotificationReplyPolicy {
    fun maySend(
        previewContact: String,
        previewText: String,
        currentContact: String,
        currentText: String,
        explicitlyApproved: Boolean
    ): Boolean = explicitlyApproved &&
        previewContact.trim() == currentContact.trim() &&
        previewText.trim() == currentText.trim() &&
        previewContact.isNotBlank() && previewText.isNotBlank()
}
