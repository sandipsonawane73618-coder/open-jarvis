package com.openjarvis.agent

/** Minimal, immutable identity and content snapshot shown before an external message is sent. */
internal data class SendConfirmationPayload(
    val recipientContactId: String,
    val recipientName: String,
    val phoneLastFour: String,
    val message: String,
    val attachmentName: String? = null
) {
    init {
        require(recipientContactId.isNotBlank())
        require(recipientName.isNotBlank())
        require(phoneLastFour.matches(Regex("\\d{4}")))
        require(message.isNotBlank())
        require(attachmentName == null || attachmentName.isNotBlank())
    }

    /** Stable approval binding; full phone numbers are deliberately never retained here. */
    fun approvalFingerprint(): String = listOf(
        recipientContactId.trim(),
        recipientName.trim().lowercase(),
        phoneLastFour,
        message,
        attachmentName.orEmpty()
    ).joinToString("\u001f")

    fun matches(other: SendConfirmationPayload?): Boolean =
        other != null && approvalFingerprint() == other.approvalFingerprint()

    fun confirmationText(): String = buildString {
        append("Send confirmation required. Recipient: ")
        append(recipientName)
        append(", number ending ")
        append(phoneLastFour)
        append(". Message: ")
        append(message)
        attachmentName?.let { append(". Attachment: "); append(it) }
        append(". Say exactly haan to send, or no cancel.")
    }
}

internal data class ContactCandidate(
    val contactId: String,
    val displayName: String,
    val phoneNumber: String
)

internal sealed class ExactContactMatch {
    data class Unique(val contactId: String, val displayName: String, val phoneLastFour: String) : ExactContactMatch()
    data class Ambiguous(val exactMatches: Int) : ExactContactMatch()
    object NotFound : ExactContactMatch()
}

/** Exact-name only. Similar names and multiple contact/number rows are never auto-selected. */
internal object SendConfirmationPolicy {
    fun resolveExactContact(requestedName: String, candidates: List<ContactCandidate>): ExactContactMatch {
        val requested = normalizeName(requestedName)
        if (requested.isBlank()) return ExactContactMatch.NotFound
        val exact = candidates.filter { normalizeName(it.displayName) == requested }
            .filter { it.contactId.isNotBlank() && it.phoneNumber.filter(Char::isDigit).length >= 4 }
        if (exact.isEmpty()) return ExactContactMatch.NotFound
        val uniqueRows = exact.distinctBy { it.contactId to it.phoneNumber.filter(Char::isDigit) }
        val contactIds = uniqueRows.map { it.contactId }.distinct()
        val numbers = uniqueRows.map { it.phoneNumber.filter(Char::isDigit) }.distinct()
        if (contactIds.size != 1 || numbers.size != 1) return ExactContactMatch.Ambiguous(uniqueRows.size)
        val candidate = uniqueRows.single()
        return ExactContactMatch.Unique(
            contactId = candidate.contactId,
            displayName = candidate.displayName.trim(),
            phoneLastFour = candidate.phoneNumber.filter(Char::isDigit).takeLast(4)
        )
    }

    fun approvalStillValid(approved: SendConfirmationPayload, current: SendConfirmationPayload?): Boolean =
        approved.matches(current)

    /** Require a standalone exact chat-header line; substring matches such as "Dad Work" are unsafe. */
    fun hasExactVisibleRecipientHeader(visibleText: String, requestedName: String): Boolean =
        visibleText.lineSequence().any { normalizeName(it) == normalizeName(requestedName) }

    /** Outbound sends require the exact spoken Haan; generic yes/confirm phrases do not qualify. */
    fun isExplicitHaan(command: String): Boolean =
        command.trim().lowercase(java.util.Locale.ROOT)
            .replace(Regex("[.!?।॥]+$"), "")
            .trim() in setOf("haan", "हाँ", "हां")

    fun confirmationText(payload: SendConfirmationPayload): String = payload.confirmationText()

    fun requestedRecipientName(command: String): String? {
        val withoutNumberSelector = command.replace(
            Regex("""(?i)\s+(?:(?:phone|number)\s+)?(?:ending|last four|last digits)\s+\d{4}"""),
            ""
        )
        val match = Regex(
            """(?i)\b(?:me|par|to|for)\s+([\p{L}][\p{L}\p{N} .'-]{0,30}?)\s+ko\b"""
        ).findAll(withoutNumberSelector).lastOrNull() ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    fun requestedPhoneLastFour(command: String): String? =
        Regex("""(?i)(?:(?:phone|number)\s+)?(?:ending|last four|last digits)\s+(\d{4})""")
            .find(command)?.groupValues?.get(1)

    fun composerMessage(interactiveElements: String): String? {
        val values = interactiveElements.lineSequence()
            .filter { it.contains("editable", ignoreCase = true) }
            .mapNotNull { line ->
                Regex("""(?:^|\s)text=(.+?)(?:\s+(?:hint=|id=|class=|bounds=)|$)""")
                    .find(line)?.groupValues?.get(1)?.trim()
            }
            .filter { it.isNotBlank() }
            .toList()
        return values.singleOrNull()
    }

    fun attachmentName(visibleText: String): String? =
        Regex("""(?i)\b[\p{L}\p{N}_-]+(?: [\p{L}\p{N}_-]+)*\.(?:png|jpe?g|webp|gif|pdf)\b""")
            .find(visibleText)?.value?.trim()

    fun hasAttachmentEvidence(visibleText: String): Boolean =
        attachmentName(visibleText) != null ||
            Regex("(?i)(remove attachment|image attached|photo attached|attachment preview|selected image|file attached)")
                .containsMatchIn(visibleText)

    private fun normalizeName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase()
}
