package com.openjarvis.agent

/**
 * Local Kotlin adaptation of the repeat-action guard in AbuZar-Ansarii/Jarvis
 * (MIT; see THIRD_PARTY_NOTICES.md).
 *
 * Blocks a third consecutive identical natural-language pipeline step before
 * it reaches a handler. This is intentionally a stop guard, not an automatic
 * scroll/back recovery: those actions could change user state unexpectedly.
 */
internal class PipelineLoopGuard(private val repeatWindow: Int = 3) {
    init {
        require(repeatWindow >= 2) { "repeatWindow must be at least 2" }
    }

    private val recent = ArrayDeque<String>()

    fun shouldBlock(step: String): Boolean {
        val signature = step.trim().lowercase().replace(Regex("\\s+"), " ")
        if (signature.isEmpty()) return false
        recent.addLast(signature)
        while (recent.size > repeatWindow) recent.removeFirst()
        return recent.size == repeatWindow && recent.all { it == signature }
    }
}
