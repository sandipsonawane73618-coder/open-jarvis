package com.openjarvis.ui

/**
 * Pure WindowManager sizing/flag policy for the floating assistant.
 * The spec deliberately exposes zero blur and dim values so both states are testable
 * without creating a real Android window.
 */
data class OverlayWindowSpec(
    val width: Int,
    val height: Int,
    val flags: Int,
    val dimAmount: Float = 0f,
    val blurBehindRadius: Int = 0
)

object OverlayWindowPolicy {
    fun create(
        expanded: Boolean,
        expandedWidthPx: Int,
        wrapContent: Int,
        notFocusableFlag: Int,
        notTouchModalFlag: Int,
        watchOutsideTouchFlag: Int,
        layoutInScreenFlag: Int
    ): OverlayWindowSpec {
        val commonFlags = notTouchModalFlag or watchOutsideTouchFlag or layoutInScreenFlag
        return OverlayWindowSpec(
            width = if (expanded) expandedWidthPx else wrapContent,
            height = wrapContent,
            flags = commonFlags or if (expanded) 0 else notFocusableFlag
        )
    }
}
