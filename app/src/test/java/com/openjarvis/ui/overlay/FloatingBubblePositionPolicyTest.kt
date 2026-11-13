package com.openjarvis.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBubblePositionPolicyTest {
    @Test
    fun dragToLeftHalfSnapsToLeftEdge() {
        val result = snapFloatingBubblePosition(1080, 2400, 192, 192, 36, 800, 500)
        assertEquals(984, result.xFromEndPx)
    }

    @Test
    fun dragToRightHalfSnapsToRightEdge() {
        val result = snapFloatingBubblePosition(1080, 2400, 192, 192, 36, 100, 500)
        assertEquals(-96, result.xFromEndPx)
    }

    @Test
    fun verticalPositionStaysInsideScreenMargins() {
        val top = snapFloatingBubblePosition(1080, 2400, 192, 192, 36, 100, -200).topPx
        val bottom = snapFloatingBubblePosition(1080, 2400, 192, 192, 36, 100, 5000).topPx
        assertEquals(36, top)
        assertEquals(2172, bottom)
    }
}
