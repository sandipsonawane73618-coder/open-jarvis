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
}
