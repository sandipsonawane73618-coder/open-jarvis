package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class ActionVerifierTest {
    private fun screen(pkg: String? = "com.youtube", text: String = "Home Search", elements: String = "Search clickable") =
        ScreenObservation(pkg, text, elements, 1)

    @Test fun appLaunchRequiresExpectedForegroundPackage() {
        val action = Action(action = Action.OPEN_APP, packageName = "com.instagram.android")
        assertTrue(ActionVerifier.verify(action, screen(), screen("com.instagram.android"), true).verified)
        assertFalse(ActionVerifier.verify(action, screen(), screen("com.youtube"), true).verified)
        assertFalse(ActionVerifier.verify(action, screen(), screen("com.instagram.android"), false).verified)
    }

    @Test fun typedTextMustAppearInFreshScreen() {
        val action = Action(action = Action.TYPE_TEXT, value = "SRK")
        assertTrue(ActionVerifier.verify(action, screen(), screen(text = "Search SRK"), true).verified)
        assertFalse(ActionVerifier.verify(action, screen(), screen(text = "Search"), true).verified)
    }

    @Test fun waitRequiresExpectedText() {
        val action = Action(action = Action.WAIT_FOR, text = "Playing")
        assertTrue(ActionVerifier.verify(action, screen(), screen(text = "Playing now"), true).verified)
        assertFalse(ActionVerifier.verify(action, screen(), screen(), true).verified)
    }

    @Test fun clickNeedsScreenChangeOrTargetDisappearance() {
        val action = Action(action = Action.CLICK, text = "Search")
        assertFalse(ActionVerifier.verify(action, screen(), screen(), true).verified)
        assertTrue(ActionVerifier.verify(action, screen(), screen(text = "Search results"), true).verified)
        assertTrue(ActionVerifier.verify(action, screen(), screen(elements = "Video result"), true).verified)
    }

    @Test fun acceptedBackWithUnchangedScreenIsNotVerified() {
        val action = Action(action = Action.BACK)
        assertFalse(ActionVerifier.verify(action, screen(), screen(), true).verified)
        assertTrue(ActionVerifier.verify(action, screen(), screen(text = "Previous page"), true).verified)
    }

    @Test fun failedExecutionAndObservationActionsCannotPass() {
        val action = Action(action = Action.CLICK, text = "Search")
        assertFalse(ActionVerifier.verify(action, screen(), screen(text = "Results"), false).verified)
        assertFalse(ActionVerifier.verify(Action(action = Action.READ_SCREEN), screen(), screen(text = "Results"), true).verified)
    }

    @Test fun sendRequiresWhatsAppMessageVisibleAndComposerCleared() {
        val action = Action(action = Action.CLICK, text = "Send")
        val payload = SendConfirmationPayload("contact-1", "Dad", "3210", "Hello Dad")
        val after = ScreenObservation(
            "com.whatsapp", "Dad\nHello Dad",
            "[#1 editable] hint=Message class=EditText bounds=0,0,1,1", 2
        )
        assertTrue(ActionVerifier.verifySentMessage(action, screen("com.whatsapp"), after, true, payload).verified)
    }

    @Test fun sendTapWithoutMessageEvidenceOrWithComposerStillFilledIsNotSent() {
        val action = Action(action = Action.CLICK, text = "Send")
        val payload = SendConfirmationPayload("contact-1", "Dad", "3210", "Hello Dad")
        val noMessage = ScreenObservation("com.whatsapp", "Dad", "[#1 editable] hint=Message", 2)
        val composerFilled = ScreenObservation(
            "com.whatsapp", "Dad\nHello Dad",
            "[#1 editable] text=Hello Dad hint=Message class=EditText", 2
        )
        assertFalse(ActionVerifier.verifySentMessage(action, screen("com.whatsapp"), noMessage, true, payload).verified)
        assertFalse(ActionVerifier.verifySentMessage(action, screen("com.whatsapp"), composerFilled, true, payload).verified)
        assertFalse(ActionVerifier.verifySentMessage(action, screen("com.example"), noMessage, true, payload).verified)
        val wrongRecipient = ScreenObservation(
            "com.whatsapp", "Mom\nHello Dad",
            "[#1 editable] hint=Message class=EditText bounds=0,0,1,1", 2
        )
        assertFalse(ActionVerifier.verifySentMessage(action, screen("com.whatsapp"), wrongRecipient, true, payload).verified)
        val attachmentPayload = payload.copy(attachmentName = "invoice.pdf")
        val attachmentMissing = ScreenObservation(
            "com.whatsapp", "Dad Hello Dad",
            "[#1 editable] hint=Message class=EditText bounds=0,0,1,1", 2
        )
        assertFalse(ActionVerifier.verifySentMessage(action, screen("com.whatsapp"), attachmentMissing, true, attachmentPayload).verified)
    }
}
