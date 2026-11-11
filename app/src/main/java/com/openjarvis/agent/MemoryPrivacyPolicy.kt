package com.openjarvis.agent

import com.openjarvis.intelligence.FastCommandParser

/** Personal memory commands must not be copied into task logs or conversational turn history. */
internal object MemoryPrivacyPolicy {
    fun isMemoryCommand(command: FastCommandParser.Command?): Boolean =
        command is FastCommandParser.Command.Remember ||
            command is FastCommandParser.Command.Forget ||
            command is FastCommandParser.Command.ShowMemory
}
