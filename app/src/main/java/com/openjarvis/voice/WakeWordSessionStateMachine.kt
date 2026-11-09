package com.openjarvis.voice

/**
 * Small, deterministic lifecycle for a wake-word-triggered STT session.
 * The restart callback is supplied by the Android service; tests can use a fake engine.
 */
class WakeWordSessionStateMachine(
    private val watchdogMs: Long = DEFAULT_WATCHDOG_MS
) {
    var commandPending: Boolean = false
        private set
    var responsePending: Boolean = false
        private set
    var transcriptReceived: Boolean = false
        private set
    var pendingSinceMs: Long = 0L
        private set

    fun beginCommand(nowMs: Long = System.currentTimeMillis()) {
        commandPending = true
        responsePending = true
        transcriptReceived = false
        pendingSinceMs = nowMs
    }

    fun markResponsePending() {
        responsePending = true
    }

    fun onTranscriptReceived() {
        commandPending = false
        pendingSinceMs = 0L
        transcriptReceived = true
    }

    /** Clears both pending flags on Idle; only an empty STT completion re-arms here. */
    fun onIdle(engine: WakeWordEngine?, restart: () -> Unit): Boolean {
        val shouldRestart = commandPending && !transcriptReceived
        clearPending()
        if (shouldRestart) {
            engine?.stop()
            restart()
        }
        return shouldRestart
    }

    /** Returns true and re-arms if STT has been stuck beyond the configured deadline. */
    fun onWatchdog(
        nowMs: Long,
        engine: WakeWordEngine?,
        restart: () -> Unit
    ): Boolean {
        if (!commandPending || pendingSinceMs <= 0L || nowMs - pendingSinceMs < watchdogMs) {
            return false
        }
        reset()
        engine?.stop()
        restart()
        return true
    }

    fun clearPending() {
        commandPending = false
        responsePending = false
        pendingSinceMs = 0L
    }

    fun finishResponse() {
        reset()
    }

    fun reset() {
        clearPending()
        transcriptReceived = false
    }

    companion object {
        const val DEFAULT_WATCHDOG_MS = 15_000L
    }
}
