package com.openjarvis.ui

/** Pure WindowManager sizing and flag policy; never applies display blur or dim. */
data class OverlayWindowSpec(
    val width: Int,
    val height: Int,
    val flags: Int,
    val dimAmount: Float = 0f
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
            flags = commonFlags or if (expanded) 0 else notFocusableFlag,
            dimAmount = 0f
        )
    }
}
