package com.openjarvis.agent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class AgentLoopRunnerTest {
    private val runner = AgentLoopRunner()
    private fun screen(text: String) = ScreenObservation("com.example.app", text, text, 1)
    private val sendPayload = SendConfirmationPayload("contact-1", "Dad", "3210", "Hello Dad", null)
    private fun whatsappScreen(text: String) = ScreenObservation(
        "com.whatsapp", text,
        "[#1 editable] hint=Message class=EditText bounds=0,0,1,1",
        2
    )
    private fun verifier(a: Action, b: ScreenObservation, c: ScreenObservation, ok: Boolean) = ActionVerifier.verify(a, b, c, ok, a.packageName)

    @Test fun observesVerifiesAndReplansOneActionAtATime() = runBlocking {
        var plans = 0; var actions = 0; var observation = screen("Home")
        val result = runner.run(AgentTaskSession("open screen and tap", 0), nowMs = { 1 },
            observe = { observation },
            plan = { _, _ -> plans++; if (plans == 1) Result.success(Action(action = Action.CLICK, text = "Search")) else Result.success(Action(action = Action.COMPLETE)) },
            execute = { actions++; observation = screen("Search results"); true }, verify = ::verifier)
        assertEquals(AgentLoopStatus.COMPLETED, result.status); assertEquals(2, plans); assertEquals(1, actions)
        assertEquals(1, result.session.completedActions.size)
    }
    @Test fun sendAndDeletePauseWithoutExecutingUntilConfirmed() = runBlocking {
        var executions = 0
        val initial = runner.run(AgentTaskSession("send this", 0), nowMs = { 1 }, observe = { whatsappScreen("Composer: Hello Dad") },
            plan = { _, _ -> Result.success(Action(action = Action.CLICK, text = "Send")) },
            execute = { executions++; true },
            resolveSendPayload = { _, _ -> sendPayload }, verify = ::verifier)
        assertEquals(AgentLoopStatus.NEEDS_CONFIRMATION, initial.status); assertEquals(0, executions)
        assertEquals(sendPayload, initial.session.awaitingConfirmationPayload)
        val resumed = runner.run(initial.session, approvedConfirmation = true, nowMs = { 2 }, observe = { whatsappScreen("Hello Dad") },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { executions++; true },
            resolveSendPayload = { _, _ -> sendPayload }, verify = ::verifier)
        assertEquals(1, executions); assertEquals(AgentLoopStatus.COMPLETED, resumed.status)
    }
    @Test fun sendIsBlockedWhenRecipientPayloadCannotBeResolved() = runBlocking {
        var executions = 0
        val result = runner.run(
            AgentTaskSession("send to Dad", 0), nowMs = { 1 }, observe = { whatsappScreen("Dad") },
            plan = { _, _ -> Result.success(Action(action = Action.CLICK, text = "Send")) },
            execute = { executions++; true }, verify = ::verifier
        )
        assertEquals(AgentLoopStatus.FAILED, result.status)
        assertTrue(result.message.contains("exact recipient"))
        assertEquals(0, executions)
    }

    @Test fun changedContactInvalidatesApprovalAndRequiresFreshConfirmation() = runBlocking {
        var executions = 0
        val pending = AgentTaskSession("send to Dad", 0).requireConfirmation(
            Action(action = Action.CLICK, text = "Send"), sendPayload
        )
        val changed = sendPayload.copy(recipientContactId = "contact-2", recipientName = "Dad Work", phoneLastFour = "9999")
        val result = runner.run(
            pending, approvedConfirmation = true, nowMs = { 1 }, observe = { whatsappScreen("Dad Work") },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { executions++; true }, resolveSendPayload = { _, _ -> changed }, verify = ::verifier
        )
        assertEquals(AgentLoopStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(changed, result.session.awaitingConfirmationPayload)
        assertEquals(0, executions)
    }

    @Test fun changedMessageOrAttachmentInvalidatesApproval() = runBlocking {
        val pending = AgentTaskSession("send to Dad", 0).requireConfirmation(
            Action(action = Action.CLICK, text = "Send"), sendPayload
        )
        val changed = sendPayload.copy(message = "Different text", attachmentName = "other.png")
        var executions = 0
        val result = runner.run(
            pending, approvedConfirmation = true, nowMs = { 1 }, observe = { whatsappScreen("Dad") },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { executions++; true }, resolveSendPayload = { _, _ -> changed }, verify = ::verifier
        )
        assertEquals(AgentLoopStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(0, executions)
    }

    @Test fun sentIsNotReportedWithoutMessageAndClearedComposerEvidence() = runBlocking {
        val pending = AgentTaskSession("send to Dad", 0).requireConfirmation(
            Action(action = Action.CLICK, text = "Send"), sendPayload
        )
        val result = runner.run(
            pending, approvedConfirmation = true, nowMs = { 1 },
            observe = { ScreenObservation(
                "com.whatsapp", "Hello Dad",
                "[#1 editable] text=Hello Dad hint=Message class=EditText bounds=0,0,1,1", 3
            ) },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { true }, resolveSendPayload = { _, _ -> sendPayload }, verify = ::verifier
        )
        assertEquals(AgentLoopStatus.FAILED, result.status)
        assertEquals("send tap kiya, confirm nahi hua", result.message)
    }

    @Test fun declinedConfirmationNeverExecutesPendingAction() = runBlocking {
        var executions = 0
        val pending = AgentTaskSession("delete item", 0).requireConfirmation(Action(action = Action.CLICK, text = "Delete")).resolveConfirmation(false)
        val result = runner.run(pending, nowMs = { 1 }, observe = { screen("Item") },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { executions++; true }, verify = ::verifier)
        assertEquals(0, executions); assertEquals(AgentLoopStatus.FAILED, result.status)
    }
    @Test fun payIsNeverExecutedEvenAfterConfirmation() = runBlocking {
        var executions = 0
        val pending = AgentTaskSession("pay now", 0).requireConfirmation(Action(action = Action.CLICK, text = "Pay"))
        val result = runner.run(pending, approvedConfirmation = true, nowMs = { 1 }, observe = { screen("Payment") },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { executions++; true }, verify = ::verifier)
        assertEquals(AgentLoopStatus.FAILED, result.status); assertEquals(0, executions)
    }
    @Test fun unchangedScreenStopsAndDoesNotRetryClick() = runBlocking {
        var executions = 0
        val result = runner.run(AgentTaskSession("tap Search", 0), nowMs = { 1 }, observe = { screen("Search") },
            plan = { _, _ -> Result.success(Action(action = Action.CLICK, text = "Search")) },
            execute = { executions++; true }, verify = ::verifier)
        assertEquals(AgentLoopStatus.FAILED, result.status); assertEquals(1, executions)
    }
    @Test fun eightActionLimitNeverReportsCompletion() = runBlocking {
        var executions = 0; var plans = 0; var observation = screen("0")
        val result = runner.run(AgentTaskSession("perform many actions", 0), nowMs = { 1 }, observe = { observation },
            plan = { _, _ -> plans++; Result.success(if (plans <= 8) Action(action = Action.BACK) else Action(action = Action.COMPLETE)) },
            execute = { executions++; observation = screen(executions.toString()); true }, verify = ::verifier)
        assertEquals(AgentLoopStatus.LIMIT_REACHED, result.status); assertEquals(8, executions)
    }
    @Test fun expiredSessionStopsBeforeObservationOrAction() = runBlocking {
        var observed = false
        val result = runner.run(AgentTaskSession("task", 0, deadlineAtMs = 0), nowMs = { 0 },
            observe = { observed = true; screen("Home") }, plan = { _, _ -> Result.success(Action(action = Action.BACK)) },
            execute = { true }, verify = ::verifier)
        assertEquals(AgentLoopStatus.FAILED, result.status); assertFalse(observed)
    }
    @Test fun plannerCompletionBeforeAnyVerifiedActionIsNotSuccess() = runBlocking {
        val result = runner.run(AgentTaskSession("task", 0), nowMs = { 1 }, observe = { screen("Home") },
            plan = { _, _ -> Result.success(Action(action = Action.COMPLETE)) },
            execute = { true }, verify = ::verifier)
        assertEquals(AgentLoopStatus.FAILED, result.status)
    }


    @Test fun plannerCompleteRequiresIndependentGoalVerificationFromFreshScreen() = runBlocking {
        var goalChecks = 0
        val result = runner.run(
            AgentTaskSession("show Display settings", 0),
            nowMs = { 1 },
            observe = { if (goalChecks == 0) screen("Home") else screen("Search results") },
            plan = { _, _ -> if (goalChecks == 0) Result.success(Action(action = Action.CLICK, text = "Search")) else Result.success(Action(action = Action.COMPLETE)) },
            execute = { goalChecks++; true },
            verify = ::verifier,
            verifyGoal = { goal, latest ->
                assertEquals("show Display settings", goal)
                GoalVerification(false, "Display settings are not visible on ${latest.visibleText}")
            }
        )
        assertEquals(AgentLoopStatus.FAILED, result.status)
        assertTrue(result.message.contains("Display settings are not visible"))
        assertTrue(goalChecks >= 1)
    }

    @Test fun plannerCannotDeclareCompleteWhenGoalVerifierRejectsEvidence() = runBlocking {
        var plans = 0
        var observation = screen("Settings home")
        val result = runner.run(
            AgentTaskSession("open Settings", 0),
            nowMs = { 1 },
            observe = { observation },
            plan = { _, _ -> plans++; Result.success(if (plans == 1) Action(action = Action.BACK) else Action(action = Action.COMPLETE)) },
            execute = { observation = screen("Home"); true },
            verify = ::verifier,
            verifyGoal = { _, _ -> GoalVerification(false, "Settings screen not observed") }
        )
        assertEquals(AgentLoopStatus.FAILED, result.status)
        assertTrue(result.message.contains("Settings screen not observed"))
    }
}
