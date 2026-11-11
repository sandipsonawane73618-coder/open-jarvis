package com.openjarvis.ui

/**
 * Prevents concurrent service/watchdog callbacks from attaching more than one
 * WindowManager overlay. The caller still checks the actual View attachment state.
 */
internal class OverlayAttachmentGate {
    private var attaching = false

    @Synchronized
    fun begin(isAlreadyAttached: Boolean): Boolean {
        if (isAlreadyAttached || attaching) return false
        attaching = true
        return true
    }

    @Synchronized
    fun finish() {
        attaching = false
    }
}
