package com.openjarvis.agent

/** Exact-payload approval gate for outbound messages. It never performs a send itself. */
internal object MessageSendApprovalPolicy {
    data class Preview(
        val contactName: String,
        val contactNumber: String,
        val messageText: String,
        val isApproved: Boolean = false
    )

    fun createPreview(contactName: String, contactNumber: String, messageText: String): Preview? {
        val name = contactName.trim()
        val number = contactNumber.filter { it.isDigit() || it == '+' }
        val text = messageText.trim()
        if (name.isBlank() || number.count(Char::isDigit) < 7 || text.isBlank()) return null
        return Preview(name, number, text, false)
    }

    fun isApproved(
        preview: Preview,
        contactName: String,
        contactNumber: String,
        messageText: String,
        spokenConfirmation: String?
    ): Boolean {
        val exactPayload = preview.contactName == contactName.trim() &&
            normalizeNumber(preview.contactNumber) == normalizeNumber(contactNumber) &&
            preview.messageText == messageText.trim()
        return exactPayload && isExplicitYes(spokenConfirmation)
    }

    fun isExplicitYes(utterance: String?): Boolean =
        utterance?.trim()?.lowercase(java.util.Locale.ROOT) in setOf(
            "haan", "ha", "yes", "confirm", "yes confirm", "ji haan", "हाँ", "हां", "हो"
        )

    fun isExplicitNo(utterance: String?): Boolean =
        utterance?.trim()?.lowercase(java.util.Locale.ROOT) in setOf(
            "nahi", "nahin", "no", "cancel", "no cancel", "नहीं", "नही"
        )

    fun isSentEvidenceVerified(
        visibleText: String,
        expectedContact: String,
        expectedMessage: String,
        outgoingMessageVisible: Boolean
    ): Boolean {
        if (!outgoingMessageVisible || expectedContact.isBlank() || expectedMessage.isBlank()) return false
        val screen = visibleText.lowercase(java.util.Locale.ROOT)
        val contactVisible = screen.contains(expectedContact.trim().lowercase(java.util.Locale.ROOT))
        val messageVisible = screen.contains(expectedMessage.trim().lowercase(java.util.Locale.ROOT))
        val sentStateVisible = listOf("sent", "delivered", "read", "भेजा गया", "पहुंचा दिया")
            .any { screen.contains(it) }
        return contactVisible && messageVisible && sentStateVisible
    }

    private fun normalizeNumber(value: String): String = value.filter(Char::isDigit)
}
