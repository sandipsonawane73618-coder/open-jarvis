package com.openjarvis.agent

import com.openjarvis.intelligence.FastCommandParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPrivacyPolicyTest {
    @Test fun excludesAllPersonalMemoryCommandsFromHistory() {
        assertTrue(MemoryPrivacyPolicy.isMemoryCommand(FastCommandParser.Command.Remember("I like tea")))
        assertTrue(MemoryPrivacyPolicy.isMemoryCommand(FastCommandParser.Command.Forget("tea")))
        assertTrue(MemoryPrivacyPolicy.isMemoryCommand(FastCommandParser.Command.ShowMemory))
    }

    @Test fun keepsOrdinaryCommandsInHistory() {
        assertFalse(MemoryPrivacyPolicy.isMemoryCommand(FastCommandParser.Command.Back))
        assertFalse(MemoryPrivacyPolicy.isMemoryCommand(null))
    }
}
