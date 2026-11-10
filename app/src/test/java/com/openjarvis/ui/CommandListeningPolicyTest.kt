package com.openjarvis.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandListeningPolicyTest {
    @Test fun activeModeExecutesCommandsAndInterceptsMultilingualMute() {
        assertEquals(CommandListeningPolicy.Action.MUTE, CommandListeningPolicy.classify("Chup ho jao", muted = false))
        assertEquals(CommandListeningPolicy.Action.MUTE, CommandListeningPolicy.classify("Mute ho ja Jarvis", muted = false))
        assertEquals(CommandListeningPolicy.Action.MUTE, CommandListeningPolicy.classify("Chup ho ja", muted = false))
        assertEquals(CommandListeningPolicy.Action.MUTE, CommandListeningPolicy.classify("Mic band karo", muted = false))
        assertEquals(CommandListeningPolicy.Action.MUTE, CommandListeningPolicy.classify("ऐकणं बंद कर", muted = false))
        assertEquals(CommandListeningPolicy.Action.EXECUTE_COMMAND, CommandListeningPolicy.classify("YouTube kholo", muted = false))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Awaaz chalu karo", muted = false))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Unmute karo", muted = false))
        assertEquals(CommandListeningPolicy.Action.EXECUTE_COMMAND, CommandListeningPolicy.classify("Ab bolo kya hua", muted = false))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("   ", muted = false))
    }

    @Test fun mutedModeAcceptsOnlyExplicitMultilingualUnmuteCommands() {
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Ab bolo", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("अब बोलो", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Unmute", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Unmute karo", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("Awaaz chalu karo", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("माइक चालू करा", muted = true))
        assertEquals(CommandListeningPolicy.Action.UNMUTE, CommandListeningPolicy.classify("पुन्हा ऐका", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("YouTube kholo", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("Chup ho jao", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("ab bolo kya hua", muted = true))
        assertEquals(CommandListeningPolicy.Action.IGNORE, CommandListeningPolicy.classify("", muted = true))
    }
}
