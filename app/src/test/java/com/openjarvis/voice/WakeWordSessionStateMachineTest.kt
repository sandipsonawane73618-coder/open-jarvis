package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordSessionStateMachineTest {
    private class FakeWakeWordEngine : WakeWordEngine {
        var running = false
        var starts = 0
        var stops = 0
        override fun start(): String? {
            starts++
            running = true
            return null
        }
        override fun stop() {
            stops++
            running = false
        }
        override fun isRunning(): Boolean = running
    }

    @Test fun idleWithoutTranscriptClearsBothFlagsAndRestartsEngine() {
        val engine = FakeWakeWordEngine().apply { start() }
        val state = WakeWordSessionStateMachine()
        state.beginCommand(nowMs = 1_000L)
        var restarts = 0

        val restarted = state.onIdle(engine) {
            restarts++
            engine.start()
        }

        assertTrue(restarted)
        assertFalse(state.commandPending)
        assertFalse(state.responsePending)
        assertEquals(1, engine.stops)
        assertEquals(2, engine.starts)
        assertEquals(1, restarts)
        assertTrue(engine.isRunning())
    }

    @Test fun idleAfterTranscriptClearsFlagsButWaitsForResponseCallback() {
        val engine = FakeWakeWordEngine()
        val state = WakeWordSessionStateMachine()
        state.beginCommand(nowMs = 1_000L)
        state.onTranscriptReceived()
        var restarts = 0

        val restarted = state.onIdle(engine) { restarts++ }

        assertFalse(restarted)
        assertFalse(state.commandPending)
        assertFalse(state.responsePending)
        assertEquals(0, restarts)
        assertTrue(state.transcriptReceived)
        state.finishResponse()
        assertFalse(state.transcriptReceived)
    }

    @Test fun watchdogResetsStuckSttAndRestartsFakeEngineAtFifteenSeconds() {
        val engine = FakeWakeWordEngine().apply { start() }
        val state = WakeWordSessionStateMachine(watchdogMs = 15_000L)
        state.beginCommand(nowMs = 10_000L)
        var restarts = 0

        assertFalse(state.onWatchdog(24_999L, engine) { restarts++ })
        assertTrue(state.onWatchdog(25_000L, engine) {
            restarts++
            engine.start()
        })

        assertFalse(state.commandPending)
        assertFalse(state.responsePending)
        assertFalse(state.transcriptReceived)
        assertEquals(1, engine.stops)
        assertEquals(2, engine.starts)
        assertEquals(1, restarts)
    }

    @Test fun newCommandResetsOldTranscriptAndStartsWatchdogWindow() {
        val state = WakeWordSessionStateMachine()
        state.beginCommand(nowMs = 100L)
        state.onTranscriptReceived()
        state.finishResponse()
        state.beginCommand(nowMs = 500L)

        assertTrue(state.commandPending)
        assertTrue(state.responsePending)
        assertFalse(state.transcriptReceived)
        assertEquals(500L, state.pendingSinceMs)
    }
}
