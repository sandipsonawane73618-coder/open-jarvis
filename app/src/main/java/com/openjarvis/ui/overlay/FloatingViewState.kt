package com.openjarvis.ui.overlay

enum class FloatingPanelMode {
    COLLAPSED,
    EXPANDED
}

enum class FloatingVoiceState {
    OFF,
    LISTENING,
    THINKING,
    SPEAKING
}

data class FloatingViewStyle(
    val colorArgb: Int,
    val label: String
)

/** Pure visual mapping only; it never changes voice or overlay lifecycle state. */
fun mapFloatingViewStyle(
    mode: FloatingPanelMode,
    voiceState: FloatingVoiceState
): FloatingViewStyle {
    val color = when (voiceState) {
        FloatingVoiceState.OFF -> 0xFF87909F.toInt()
        FloatingVoiceState.LISTENING -> 0xFF31D8E8.toInt()
        FloatingVoiceState.THINKING -> 0xFFFFB454.toInt()
        FloatingVoiceState.SPEAKING -> 0xFFE879F9.toInt()
    }
    val stateLabel = when (voiceState) {
        FloatingVoiceState.OFF -> "OFF"
        FloatingVoiceState.LISTENING -> "LISTENING"
        FloatingVoiceState.THINKING -> "THINKING"
        FloatingVoiceState.SPEAKING -> "SPEAKING"
    }
    val label = when (mode) {
        FloatingPanelMode.COLLAPSED -> "JARVIS · $stateLabel"
        FloatingPanelMode.EXPANDED -> "JARVIS $stateLabel"
    }
    return FloatingViewStyle(colorArgb = color, label = label)
}
