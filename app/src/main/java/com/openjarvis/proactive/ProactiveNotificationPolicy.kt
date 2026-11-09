package com.openjarvis.proactive

/** Runtime notification permission rules shared by UI and notification delivery. */
object ProactiveNotificationPolicy {
    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= 33

    fun mayPost(sdkInt: Int, permissionGranted: Boolean): Boolean =
        !requiresRuntimePermission(sdkInt) || permissionGranted
}
