package com.openjarvis.ui

import com.openjarvis.voice.VoiceControlVocabulary

/** Exact multilingual voice controls; intercepted before command planning. */
object MuteCommandPolicy {
    fun isMuteCommand(command: String): Boolean =
        VoiceControlVocabulary.isMuteCommand(command)

    fun isUnmuteCommand(command: String): Boolean =
        VoiceControlVocabulary.isUnmuteCommand(command)
}
