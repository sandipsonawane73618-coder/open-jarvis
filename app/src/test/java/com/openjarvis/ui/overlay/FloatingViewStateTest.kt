package com.openjarvis.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingViewStateTest {
    @Test
    fun collapsedStateMapsVoiceStatesToDistinctColorsAndLabels() {
        assertEquals(FloatingViewStyle(0xFF87909F.toInt(), "JARVIS · OFF"), mapFloatingViewStyle(FloatingPanelMode.COLLAPSED, FloatingVoiceState.OFF))
        assertEquals(FloatingViewStyle(0xFF31D8E8.toInt(), "JARVIS · LISTENING"), mapFloatingViewStyle(FloatingPanelMode.COLLAPSED, FloatingVoiceState.LISTENING))
        assertEquals(FloatingViewStyle(0xFFFFB454.toInt(), "JARVIS · THINKING"), mapFloatingViewStyle(FloatingPanelMode.COLLAPSED, FloatingVoiceState.THINKING))
        assertEquals(FloatingViewStyle(0xFFE879F9.toInt(), "JARVIS · SPEAKING"), mapFloatingViewStyle(FloatingPanelMode.COLLAPSED, FloatingVoiceState.SPEAKING))
    }

    @Test
    fun expandedStateKeepsVoiceColorAndUsesPanelLabel() {
        assertEquals(FloatingViewStyle(0xFF87909F.toInt(), "JARVIS OFF"), mapFloatingViewStyle(FloatingPanelMode.EXPANDED, FloatingVoiceState.OFF))
        assertEquals(FloatingViewStyle(0xFF31D8E8.toInt(), "JARVIS LISTENING"), mapFloatingViewStyle(FloatingPanelMode.EXPANDED, FloatingVoiceState.LISTENING))
        assertEquals(FloatingViewStyle(0xFFFFB454.toInt(), "JARVIS THINKING"), mapFloatingViewStyle(FloatingPanelMode.EXPANDED, FloatingVoiceState.THINKING))
        assertEquals(FloatingViewStyle(0xFFE879F9.toInt(), "JARVIS SPEAKING"), mapFloatingViewStyle(FloatingPanelMode.EXPANDED, FloatingVoiceState.SPEAKING))
    }
}
