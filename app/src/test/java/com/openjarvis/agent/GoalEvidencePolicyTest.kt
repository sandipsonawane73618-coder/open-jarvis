package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class GoalEvidencePolicyTest {
    private fun screen(text: String, packageName: String? = "com.android.settings") =
        ScreenObservation(packageName, text, text, 1)

    @Test fun settingsGoalRequiresVisibleSettingsEvidence() {
        val result = GoalEvidencePolicy.verify("open Settings", screen("Settings\nNetwork & internet"))
        assertTrue(result.verified)
    }

    @Test fun displaySettingsGoalDoesNotPassOnUnrelatedChangedScreen() {
        val result = GoalEvidencePolicy.verify("show Display settings", screen("Home\nRecent apps"))
        assertFalse(result.verified)
        assertTrue(result.reason.isNotBlank())
    }

    @Test fun goalWithNoDistinctiveVisibleEvidenceFailsClosed() {
        val result = GoalEvidencePolicy.verify("finish the task", screen("Home"))
        assertFalse(result.verified)
    }

    @Test fun evidenceMustComeFromFreshScreenObservation() {
        val stale = screen("Display settings")
        val fresh = screen("Home")
        val result = GoalEvidencePolicy.verify("show Display settings", fresh)
        assertFalse(result.verified)
        assertNotSame(stale, fresh)
    }

    @Test fun emptyScreenCannotVerifyAnyGoal() {
        val result = GoalEvidencePolicy.verify("open Settings", screen(""))
        assertFalse(result.verified)
    }
}
