package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class Stage3ActionSafetyPolicyTest {
    @Test fun whatsappAlwaysCreatesPreviewBeforeAnySendApproval() {
        val preview = MessageSendApprovalPolicy.createPreview("Amit", "+919876543210", "mi yetoy")
        assertNotNull(preview)
        assertEquals("Amit", preview!!.contactName)
        assertEquals("mi yetoy", preview.messageText)
        assertFalse(preview.isApproved)
    }

    @Test fun whatsappNoConfirmationNeverAuthorizesSend() {
        val preview = MessageSendApprovalPolicy.createPreview("Amit", "+919876543210", "mi yetoy")!!
        assertFalse(MessageSendApprovalPolicy.isApproved(preview, "Amit", "+919876543210", "mi yetoy", null))
        assertFalse(MessageSendApprovalPolicy.isApproved(preview, "Amit", "+919876543210", "mi yetoy", "maybe"))
    }

    @Test fun whatsappApprovalIsBoundToExactContactAndText() {
        val preview = MessageSendApprovalPolicy.createPreview("Amit", "+919876543210", "mi yetoy")!!
        assertTrue(MessageSendApprovalPolicy.isApproved(preview, "Amit", "+919876543210", "mi yetoy", "haan"))
        assertFalse(MessageSendApprovalPolicy.isApproved(preview, "Amit", "+919876543210", "mi yetoy", "yes"))
        assertFalse(MessageSendApprovalPolicy.isApproved(preview, "Amit", "+919876543210", "mi yetoy", "confirm"))
    }

    @Test fun changedWhatsappTextInvalidatesApproval() {
        val preview = MessageSendApprovalPolicy.createPreview("Amit", "+919876543210", "mi yetoy")!!
        assertFalse(MessageSendApprovalPolicy.isApproved(preview, "Amit", "+919876543210", "mi yetoy kal", "haan"))
    }

    @Test fun changedWhatsappContactInvalidatesApproval() {
        val preview = MessageSendApprovalPolicy.createPreview("Amit", "+919876543210", "mi yetoy")!!
        assertFalse(MessageSendApprovalPolicy.isApproved(preview, "Amita", "+919876543211", "mi yetoy", "haan"))
    }

    @Test fun genericUiSendTapIsBlockedForWhatsApp() {
        assertTrue(MessageSendApprovalPolicy.isWhatsAppSendControl("com.whatsapp", "Send"))
        assertTrue(MessageSendApprovalPolicy.isWhatsAppSendControl("com.whatsapp", "Send message"))
        assertFalse(MessageSendApprovalPolicy.isWhatsAppSendControl("com.whatsapp", "Search"))
        assertFalse(MessageSendApprovalPolicy.isWhatsAppSendControl("com.google.android.youtube", "Send"))
    }

    @Test fun sentEvidenceRequiresExactOutgoingContactAndMessageOnScreen() {
        assertTrue(MessageSendApprovalPolicy.isSentEvidenceVerified(
            visibleText = "Amit\nmi yetoy\nSent",
            expectedContact = "Amit",
            expectedMessage = "mi yetoy",
            outgoingMessageVisible = true
        ))
    }

    @Test fun composerOrIncomingMessageIsNotSentEvidence() {
        assertFalse(MessageSendApprovalPolicy.isSentEvidenceVerified(
            visibleText = "Amit\nmi yetoy\nType a message",
            expectedContact = "Amit",
            expectedMessage = "mi yetoy",
            outgoingMessageVisible = false
        ))
    }

    @Test fun youtubeFirstVideoTargetSkipsNavigationAndShorts() {
        val screen = ScreenObservation(
            "com.google.android.youtube",
            "Home",
            "[#1 clickable] text=Home\n[#2 clickable] text=Shorts\n[#3 clickable] text=Arijit Singh - Tum Hi Ho",
            1
        )
        assertEquals(
            "Arijit Singh - Tum Hi Ho",
            YouTubePlaybackPolicy.firstVideoTarget(screen)
        )
    }

    @Test fun youtubeSuccessRequiresPlayerScreenEvidence() {
        assertTrue(YouTubePlaybackPolicy.isPlayerVerified(
            packageName = "com.google.android.youtube",
            visibleText = "Arijit Singh - Tum Hi Ho\nPause",
            playerControlsVisible = true
        ))
        assertFalse(YouTubePlaybackPolicy.isPlayerVerified(
            packageName = "com.google.android.youtube",
            visibleText = "Search results for Arijit Singh",
            playerControlsVisible = false
        ))
    }

    @Test fun youtubeNoResultsReturnsHonestFailure() {
        assertEquals(
            "YouTube par koi matching video nahi mila.",
            YouTubePlaybackPolicy.noResultsReply()
        )
    }

    @Test fun youtubeSearchAndPlayCommandRequestsFirstResultPlayback() {
        assertTrue(YouTubePlaybackPolicy.shouldPlayFirstResult(
            "YouTube par arijit ke gaane search karke play karo"
        ))
        assertFalse(YouTubePlaybackPolicy.shouldPlayFirstResult(
            "YouTube par arijit ke gaane search karo"
        ))
    }

    @Test fun youtubeNoResultsScreenIsDetectedWithoutClaimingPlayback() {
        assertTrue(YouTubePlaybackPolicy.isNoResultsScreen("No results found"))
        assertFalse(YouTubePlaybackPolicy.isNoResultsScreen("Arijit Singh - Tum Hi Ho"))
    }

    @Test fun localFallbackHintAppearsWhenHybridChatIsOff() {
        assertTrue(LocalModelFallbackHintPolicy.shouldShow(
            onlineChatEnabled = false,
            selectedProviderKeyAvailable = true,
            localModelUsed = true,
            alreadyShown = false
        ))
    }

    @Test fun localFallbackHintAppearsWhenSelectedProviderKeyIsMissing() {
        assertTrue(LocalModelFallbackHintPolicy.shouldShow(
            onlineChatEnabled = true,
            selectedProviderKeyAvailable = false,
            localModelUsed = true,
            alreadyShown = false
        ))
    }

    @Test fun localFallbackHintIsOneTimeAndOnlyForLocalModelReplies() {
        assertFalse(LocalModelFallbackHintPolicy.shouldShow(
            onlineChatEnabled = false,
            selectedProviderKeyAvailable = false,
            localModelUsed = true,
            alreadyShown = true
        ))
        assertFalse(LocalModelFallbackHintPolicy.shouldShow(
            onlineChatEnabled = true,
            selectedProviderKeyAvailable = true,
            localModelUsed = false,
            alreadyShown = false
        ))
    }
}
