package com.openjarvis.agent

import java.security.MessageDigest

/** Immutable accessibility snapshot used only by the general UI agent loop. */
internal data class ScreenObservation(
    val packageName: String?,
    val visibleText: String,
    val interactiveElements: String,
    val observedAtMs: Long,
    val fingerprint: String = fingerprintOf(packageName, visibleText, interactiveElements)
) {
    companion object {
        fun fingerprintOf(packageName: String?, visibleText: String, interactiveElements: String): String {
            val source = listOf(packageName.orEmpty(), visibleText, interactiveElements).joinToString("\u001f")
            val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
