package com.openjarvis.agent

/** Verifies an action against a fresh screen observation; action acceptance alone is insufficient. */
internal object ActionVerifier {
    data class Verification(val verified: Boolean, val reason: String)

    fun verifySentMessage(
        action: Action,
        before: ScreenObservation,
        after: ScreenObservation,
        executionAccepted: Boolean,
        payload: SendConfirmationPayload
    ): Verification {
        if (!executionAccepted) return Verification(false, "Send tap was not accepted; send tap kiya, confirm nahi hua.")
        val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
        if (before.packageName !in whatsappPackages || after.packageName !in whatsappPackages) {
            return Verification(false, "WhatsApp is not the foreground app; send tap kiya, confirm nahi hua.")
        }
        val recipientVisible = SendConfirmationPolicy.hasExactVisibleRecipientHeader(after.visibleText, payload.recipientName)
        val messageVisible = after.visibleText.contains(payload.message)
        val attachmentVisible = payload.attachmentName?.let { after.visibleText.contains(it, ignoreCase = true) } ?: true
        val composerCleared = after.interactiveElements.lineSequence().any { line ->
            line.contains("editable", ignoreCase = true) &&
                (line.contains("hint=Message", ignoreCase = true) ||
                    line.contains("id=com.whatsapp:id/entry", ignoreCase = true)) &&
                !line.contains("text=${payload.message}", ignoreCase = true)
        }
        val deliveryMarker = Regex("(?i)\\b(sent|delivered|read)\\b|भेजा गया|पहुंचा दिया").containsMatchIn(after.visibleText)
        return if (recipientVisible && messageVisible && attachmentVisible && (composerCleared || deliveryMarker)) {
            Verification(true, "Message is visible in the WhatsApp conversation and the composer is clear.")
        } else {
            Verification(false, "send tap kiya, confirm nahi hua")
        }
    }

    fun verify(
        action: Action,
        before: ScreenObservation,
        after: ScreenObservation,
        executionAccepted: Boolean,
        expectedPackage: String? = action.packageName
    ): Verification {
        if (!executionAccepted) return Verification(false, "Android did not accept the action")
        val changed = before.fingerprint != after.fingerprint
        return when (action.action) {
            Action.OPEN_APP -> {
                val expected = expectedPackage
                if (expected.isNullOrBlank()) Verification(false, "Target app package is unresolved")
                else if (after.packageName == expected) Verification(true, "Target app is foreground")
                else Verification(false, "Target app did not become foreground")
            }
            Action.TYPE, Action.TYPE_TEXT -> {
                val value = action.value.orEmpty()
                if (value.isNotEmpty() && after.visibleText.contains(value)) {
                    Verification(true, "Typed text is visible")
                } else Verification(false, "Typed text could not be verified on screen")
            }
            Action.SCROLL_UNTIL_TARGET -> {
                val target = action.text.orEmpty()
                if (target.isNotBlank() &&
                    (after.visibleText.contains(target, ignoreCase = true) ||
                        after.interactiveElements.contains(target, ignoreCase = true))) {
                    Verification(true, "Scroll target is visible in the fresh screen observation")
                } else Verification(false, "Target '$target' was not visible after scrolling")
            }
            Action.WAIT, Action.WAIT_FOR -> {
                val expected = action.text.orEmpty()
                if (expected.isNotBlank() && after.visibleText.contains(expected, ignoreCase = true)) {
                    Verification(true, "Expected text is visible")
                } else Verification(false, "Expected text is not visible")
            }
            Action.CLICK, Action.TAP -> {
                val target = action.text.orEmpty()
                val targetStillVisible = target.isNotBlank() &&
                    (after.visibleText.contains(target, true) || after.interactiveElements.contains(target, true))
                if (changed || !targetStillVisible) Verification(true, "Screen changed after tap")
                else Verification(false, "Tap was accepted but the screen did not visibly change")
            }
            Action.SCROLL, Action.BACK, Action.PRESS_BACK, Action.HOME, Action.PRESS_HOME,
            Action.PRESS_RECENTS -> if (changed) {
                Verification(true, "Screen changed after navigation action")
            } else Verification(false, "Screen did not change after navigation action")
            Action.READ_SCREEN, Action.EXTRACT_TEXT -> Verification(false, "Observation is not an executable action")
            Action.ERROR -> Verification(false, action.message ?: "Planner returned an error")
            else -> if (changed) Verification(true, "Fresh screen differs from previous observation")
                else Verification(false, "No observable result after action")
        }
    }
}
