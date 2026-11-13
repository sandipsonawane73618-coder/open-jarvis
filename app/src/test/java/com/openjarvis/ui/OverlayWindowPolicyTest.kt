package com.openjarvis.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowPolicyTest {
    private companion object {
        const val WRAP_CONTENT = -2
        const val MATCH_PARENT = -1
        const val FLAG_NOT_FOCUSABLE = 0x00000008
        const val FLAG_NOT_TOUCH_MODAL = 0x00000020
        const val FLAG_WATCH_OUTSIDE_TOUCH = 0x00040000
        const val FLAG_LAYOUT_IN_SCREEN = 0x00000100
        const val FLAG_DIM_BEHIND = 0x00000002
        const val FLAG_BLUR_BEHIND = 0x00000004
        const val FORBIDDEN_FLAGS = FLAG_DIM_BEHIND or FLAG_BLUR_BEHIND
    }

    @Test
    fun collapsedWindowIsWrapContentAndNeverBlursOrDims() {
        val spec = OverlayWindowPolicy.create(
            expanded = false,
            expandedWidthPx = 304,
            wrapContent = WRAP_CONTENT,
            notFocusableFlag = FLAG_NOT_FOCUSABLE,
            notTouchModalFlag = FLAG_NOT_TOUCH_MODAL,
            watchOutsideTouchFlag = FLAG_WATCH_OUTSIDE_TOUCH,
            layoutInScreenFlag = FLAG_LAYOUT_IN_SCREEN
        )

        assertEquals(WRAP_CONTENT, spec.width)
        assertEquals(WRAP_CONTENT, spec.height)
        assertEquals(0f, spec.dimAmount, 0f)
        assertEquals(0, spec.blurBehindRadius)
        assertEquals(0, spec.flags and FORBIDDEN_FLAGS)
        assertFalse(spec.width == MATCH_PARENT || spec.height == MATCH_PARENT)
        assertTrue(spec.flags and FLAG_NOT_FOCUSABLE != 0)
    }

    @Test
    fun expandedWindowIs304DpWideAndNeverBlursOrDims() {
        val spec = OverlayWindowPolicy.create(
            expanded = true,
            expandedWidthPx = 912,
            wrapContent = WRAP_CONTENT,
            notFocusableFlag = FLAG_NOT_FOCUSABLE,
            notTouchModalFlag = FLAG_NOT_TOUCH_MODAL,
            watchOutsideTouchFlag = FLAG_WATCH_OUTSIDE_TOUCH,
            layoutInScreenFlag = FLAG_LAYOUT_IN_SCREEN
        )

        assertEquals(912, spec.width)
        assertEquals(WRAP_CONTENT, spec.height)
        assertEquals(0f, spec.dimAmount, 0f)
        assertEquals(0, spec.blurBehindRadius)
        assertEquals(0, spec.flags and FORBIDDEN_FLAGS)
        assertFalse(spec.width == MATCH_PARENT || spec.height == MATCH_PARENT)
        assertFalse(spec.flags and FLAG_NOT_FOCUSABLE != 0)
    }
}
