package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class AgentLoopAcceptanceTest {
    @Test fun youtubeFirstVideoCommandUsesGeneralLoop() {
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("YouTube pe pehla video play karo"))
        assertTrue(AgentLoopCommandPolicy.isYouTubeFirstVideoGoal("YouTube pe pehla video play karo"))
    }

    @Test fun firstYoutubeVideoSkipsNavigationControlsAndKeepsFullTitle() {
        val screen = ScreenObservation(
            "com.google.android.youtube", "Home",
            "[#1 clickable] text=Home bounds=0,0,1,1\n[#2 clickable] text=Shorts bounds=0,0,1,1\n[#3 clickable] text=First Video Song bounds=0,0,1,1",
            1
        )
        val action = DeterministicUiActionResolver.resolve("pehla video play karo", screen)
        assertEquals("First Video Song", action?.text)
    }

    @Test fun mapsAndLocationQueriesNeverBecomeYoutubePlaybackGoals() {
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("Maps par pehla video play karo"))
        assertFalse(AgentLoopCommandPolicy.isYouTubeFirstVideoGoal("Maps par pehla video play karo"))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("Pune ka route Maps par dikhao"))
    }

    @Test fun plainInstagramLaunchRemainsOnDeterministicPath() {
        assertEquals(CommandRouter.Route.STEP_INTENT, CommandRouter.classify("Instagram kholo"))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("Instagram kholo"))
    }

    @Test fun whatsappMessageComposerRemainsOnExistingDeterministicPath() {
        val command = "WhatsApp par Rahul ko message bhejo ki main aa raha hoon"
        assertNotEquals(CommandRouter.Route.NONE, CommandRouter.classify(command))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop(command))
    }

    @Test fun explicitSearchButtonTapUsesGeneralLoopAndGroundedAction() {
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("Search button tap karo"))
        val screen = ScreenObservation("com.example.app", "Home", "[#2 clickable] text=Search bounds=0,0,1,1", 1)
        assertEquals("Search", DeterministicUiActionResolver.resolve("Search button tap karo", screen)?.text)
    }

    @Test fun compoundUiTaskUsesGeneralLoop() {
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("Instagram kholo aur Search button tap karo"))
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("Chrome kholo, Search par tap karo aur Pune type karo"))
    }

    @Test fun explicitCrossAppCreationAndSendWordsTriggerGeneralLoop() {
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("Gemini me anime image banao phir WhatsApp me Dad ko bhejo"))
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("YouTube kholo phir WhatsApp me message send karo"))
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("Instagram me photo banao phir WhatsApp par attach karo"))
    }

    @Test fun deterministicSingleAppAndNativeCommandsStayOnExistingPath() {
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("time kya hua"))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("volume badhao"))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("Instagram kholo"))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("volume badhao phir Instagram kholo"))
        assertEquals(CommandRouter.Route.SKILL_REGISTRY, CommandRouter.classify("volume badhao"))
    }

    @Test fun deterministicVolumeAndAppSequenceStillSplitsForStepPipeline() {
        val steps = CommandSplitter.split("volume badhao phir Instagram kholo") {
            CommandRouter.classify(it) != CommandRouter.Route.NONE
        }
        assertEquals(2, steps.size)
        assertEquals(CommandRouter.Route.SKILL_REGISTRY, CommandRouter.classify("volume badhao"))
        assertEquals(CommandRouter.Route.STEP_INTENT, CommandRouter.classify("Instagram kholo"))
    }

    @Test fun sendAndDeleteControlsRequireApprovalBeforeExecution() {
        assertTrue(ActionRiskPolicy.requiresConfirmation(Action(action = Action.CLICK, text = "Send")))
        assertTrue(ActionRiskPolicy.requiresConfirmation(Action(action = Action.CLICK, text = "Delete")))
        assertEquals(true, ActionRiskPolicy.approvalDecision("yes confirm"))
        assertEquals(false, ActionRiskPolicy.approvalDecision("no cancel"))
    }

    @Test fun paymentControlsAreConfirmedButNeverAutomated() {
        val pay = Action(action = Action.CLICK, text = "Pay now")
        assertTrue(ActionRiskPolicy.requiresConfirmation(pay))
        assertTrue(ActionRiskPolicy.isPaymentAction(pay))
    }
}
