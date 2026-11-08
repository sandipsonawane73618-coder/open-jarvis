package com.openjarvis.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandListeningPolicyTest {
    @Test fun activeModeExecutesCommandsAndInterceptsMuteOnly() {
        assertEquals(CommandListeningPolicy.Action.MUTE, CommandListeningPolicy.classify("Chup ho jao", muted = false))
        assertEquals(CommandListeningPolicy.Action.EXECUTE_COMMAND, CommandListeningPolicy.classify("YouTube kholo", muted = false))
        assertEquals(CommandListeningPolicy.Action.EXECUTE_COMMAND, CommandListeningPolicy.classify("Ab bolo", muted = false))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("   ", muted = false))
    }

    @Test fun mutedModeAcceptsOnlyExactUnmuteCommand() {
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Ab bolo", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("अब बोलो", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("YouTube kholo", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("Chup ho jao", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("", muted = true))
    }
}
