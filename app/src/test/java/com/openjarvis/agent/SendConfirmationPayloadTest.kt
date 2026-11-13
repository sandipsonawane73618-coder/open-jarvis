package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class SendConfirmationPayloadTest {
    @Test fun exactContactMatchReturnsOnlyOneExactContactAndPhone() {
        val result = SendConfirmationPolicy.resolveExactContact(
            "Dad",
            listOf(
                ContactCandidate("1", "Dad", "+91 98765 43210"),
                ContactCandidate("2", "Dad Work", "+91 98765 43211")
            )
        )
        assertEquals(ExactContactMatch.Unique("1", "Dad", "3210"), result)
    }

    @Test fun duplicateExactDadContactsRequireUserDisambiguation() {
        val result = SendConfirmationPolicy.resolveExactContact(
            "Dad",
            listOf(
                ContactCandidate("1", "Dad", "+91 98765 43210"),
                ContactCandidate("2", "Dad", "+91 98765 43211")
            )
        )
        assertTrue(result is ExactContactMatch.Ambiguous)
    }

    @Test fun multipleNumbersForSameExactContactAreAmbiguous() {
        val result = SendConfirmationPolicy.resolveExactContact(
            "Dad",
            listOf(
                ContactCandidate("1", "Dad", "+91 98765 43210"),
                ContactCandidate("1", "Dad", "+91 98765 43211")
            )
        )
        assertTrue(result is ExactContactMatch.Ambiguous)
    }

    @Test fun similarContactNameIsNotAnExactMatch() {
        assertEquals(
            ExactContactMatch.NotFound,
            SendConfirmationPolicy.resolveExactContact(
                "Dad",
                listOf(ContactCandidate("2", "Dad Work", "+91 98765 43211"))
            )
        )
    }

    @Test fun approvalFingerprintInvalidatesWhenRecipientMessageOrAttachmentChanges() {
        val approved = SendConfirmationPayload("contact-1", "Dad", "3210", "Hello", "anime.png")
        assertTrue(SendConfirmationPolicy.approvalStillValid(approved, approved.copy()))
        assertFalse(SendConfirmationPolicy.approvalStillValid(approved, approved.copy(recipientContactId = "contact-2")))
        assertFalse(SendConfirmationPolicy.approvalStillValid(approved, approved.copy(recipientName = "Dad Work")))
        assertFalse(SendConfirmationPolicy.approvalStillValid(approved, approved.copy(phoneLastFour = "9999")))
        assertFalse(SendConfirmationPolicy.approvalStillValid(approved, approved.copy(message = "Different text")))
        assertFalse(SendConfirmationPolicy.approvalStillValid(approved, approved.copy(attachmentName = "other.png")))
        assertFalse(SendConfirmationPolicy.approvalStillValid(approved, null))
    }

    @Test fun confirmationTextIncludesExactRecipientLastFourMessageAndAttachment() {
        val text = SendConfirmationPayload("contact-1", "Dad", "3210", "Hello", "anime.png").confirmationText()
        assertTrue(text.contains("Dad"))
        assertTrue(text.contains("3210"))
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("anime.png"))
    }

    @Test fun extractsRecipientFromCrossAppCommandAndComposerMessage() {
        assertEquals(
            "Dad",
            SendConfirmationPolicy.requestedRecipientName("Gemini me anime image banao phir WhatsApp me Dad ko bhejo")
        )
        assertEquals(
            "Hello Dad",
            SendConfirmationPolicy.composerMessage("[#2 editable] text=Hello Dad hint=Message class=EditText bounds=0,0,1,1")
        )
        assertEquals("anime.png", SendConfirmationPolicy.attachmentName("Preview anime.png Remove"))
    }

    @Test fun visibleRecipientHeaderMustBeAnExactStandaloneLine() {
        assertTrue(SendConfirmationPolicy.hasExactVisibleRecipientHeader("Dad\\nHello", "Dad"))
        assertFalse(SendConfirmationPolicy.hasExactVisibleRecipientHeader("Dad Work\\nHello", "Dad"))
        assertFalse(SendConfirmationPolicy.hasExactVisibleRecipientHeader("Mom\\nDad Work\\nHello", "Dad"))
    }

    @Test fun sendApprovalRequiresExactHaanNotGenericYesOrConfirm() {
        assertTrue(SendConfirmationPolicy.isExplicitHaan("haan"))
        assertTrue(SendConfirmationPolicy.isExplicitHaan("हाँ"))
        assertFalse(SendConfirmationPolicy.isExplicitHaan("yes"))
        assertFalse(SendConfirmationPolicy.isExplicitHaan("confirm"))
        assertFalse(SendConfirmationPolicy.isExplicitHaan("haan please"))
    }

    @Test fun composerMessageMustBeUnambiguous() {
        assertNull(SendConfirmationPolicy.composerMessage(
            "[#1 editable] text=Hello hint=Message\\n[#2 editable] text=Other hint=Search"
        ))
    }
}
