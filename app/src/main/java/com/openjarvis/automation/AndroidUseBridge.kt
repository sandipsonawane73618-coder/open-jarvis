package com.openjarvis.automation

import com.openjarvis.accessibility.JarvisAccessibilityService

/**
 * On-device Android-native bridge inspired by android-use's observe/act loop.
 * It uses Android Accessibility APIs already enabled by the user; it does not
 * embed the upstream Python/ADB host runtime inside the APK.
 */
class AndroidUseBridge(
    private val serviceProvider: () -> JarvisAccessibilityService? = { JarvisAccessibilityService.instance }
) {
    data class Observation(val available: Boolean, val controls: String, val reason: String? = null)

    fun observe(maxItems: Int = 40): Observation {
        val service = serviceProvider() ?: return Observation(false, "", "Enable JARVIS Accessibility Service")
        return runCatching {
            Observation(true, service.describeVisibleInteractiveElements(maxItems.coerceIn(1, 60)))
        }.getOrElse { Observation(false, "", it.message ?: "Screen observation failed") }
    }

    fun tap(target: String): Result<Boolean> = action { it.tapByText(target) }
    fun type(text: String, targetHint: String? = null): Result<Boolean> =
        action { it.typeText(text, targetHint) }
    fun scroll(direction: String): Result<Boolean> = action { it.scrollScreen(direction) }
    fun back(): Result<Boolean> = action { it.pressBack() }
    fun home(): Result<Boolean> = action { it.pressHome() }

    private fun action(block: (JarvisAccessibilityService) -> Boolean): Result<Boolean> {
        val service = serviceProvider()
            ?: return Result.failure(IllegalStateException("Enable JARVIS Accessibility Service"))
        return runCatching { block(service) }
    }
}
