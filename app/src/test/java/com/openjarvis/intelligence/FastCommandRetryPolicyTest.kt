package com.openjarvis.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FastCommandRetryPolicyTest {
    @Test fun noFastCommandIsAutomaticallyReplayed() {
        val commands = listOf(
            FastCommandParser.Command.OpenApp("WhatsApp"),
            FastCommandParser.Command.CloseCurrentApp,
            FastCommandParser.Command.Back,
            FastCommandParser.Command.Home,
            FastCommandParser.Command.ShowRecents,
            FastCommandParser.Command.ShowNotifications,
            FastCommandParser.Command.Scroll("down"),
            FastCommandParser.Command.OpenSettings("wifi"),
            FastCommandParser.Command.Dial("+919876543210"),
            FastCommandParser.Command.SetTimer(60),
            FastCommandParser.Command.DialContact("Rahul"),
            FastCommandParser.Command.YoutubeSearch("news"),
            FastCommandParser.Command.Remember("I prefer Marathi"),
            FastCommandParser.Command.Forget("old preference"),
            FastCommandParser.Command.ShowMemory
        )

        commands.forEach { command ->
            assertEquals(command.javaClass.simpleName, 1, FastCommandRetryPolicy.maxAttempts(command))
            assertFalse(command.javaClass.simpleName, FastCommandRetryPolicy.shouldRetry(command, 1))
        }
    }
}
