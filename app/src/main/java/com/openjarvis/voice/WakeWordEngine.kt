package com.openjarvis.voice

/** Pluggable local wake-word detector; implementations must not require cloud access. */
interface WakeWordEngine {
    /** Starts capture and returns null on success or a user-safe diagnostic on failure. */
    fun start(): String?
    fun stop()
    fun isRunning(): Boolean
}
