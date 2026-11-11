package com.openjarvis.proactive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveNotificationPolicyTest {
    @Test fun preAndroid13DoesNotRequireRuntimeNotificationPermission() {
        assertFalse(ProactiveNotificationPolicy.requiresRuntimePermission(32))
        assertTrue(ProactiveNotificationPolicy.mayPost(32, permissionGranted = false))
    }

    @Test fun android13AndNewerRequireGrantedPermission() {
        assertTrue(ProactiveNotificationPolicy.requiresRuntimePermission(33))
        assertFalse(ProactiveNotificationPolicy.mayPost(33, permissionGranted = false))
        assertTrue(ProactiveNotificationPolicy.mayPost(33, permissionGranted = true))
        assertFalse(ProactiveNotificationPolicy.mayPost(35, permissionGranted = false))
        assertTrue(ProactiveNotificationPolicy.mayPost(35, permissionGranted = true))
    }
}
