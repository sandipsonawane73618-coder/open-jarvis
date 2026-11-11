package com.openjarvis.voice

/** Metadata supplied by a separately audited on-device ASR model package. */
data class IndicModelProfile(val languageCode: String, val modelId: String, val minimumApi: Int, val requiredAbi: String, val modelBytes: Long?, val peakMemoryBytes: Long?, val licenseSpdx: String?)
enum class IndicModelRejection { UNSUPPORTED_LANGUAGE, API_TOO_LOW, ABI_MISMATCH, MODEL_SIZE_UNKNOWN, MEMORY_ESTIMATE_UNKNOWN, LICENSE_UNVERIFIED, STORAGE_INSUFFICIENT, MEMORY_BUDGET_EXCEEDED }
data class IndicModelDecision(val compatible: Boolean, val rejection: IndicModelRejection? = null)
object IndicModelCompatibility {
    private val supportedLanguages = setOf("hi", "mr", "kn", "ml", "ta", "te")
    fun evaluate(profile: IndicModelProfile, sdkInt: Int, deviceAbi: String, availableStorageBytes: Long, availableMemoryBytes: Long): IndicModelDecision {
        fun reject(reason: IndicModelRejection) = IndicModelDecision(false, reason)
        if (profile.languageCode.lowercase() !in supportedLanguages) return reject(IndicModelRejection.UNSUPPORTED_LANGUAGE)
        if (sdkInt < profile.minimumApi) return reject(IndicModelRejection.API_TOO_LOW)
        if (!deviceAbi.equals(profile.requiredAbi, true)) return reject(IndicModelRejection.ABI_MISMATCH)
        val modelBytes = profile.modelBytes?.takeIf { it > 0 } ?: return reject(IndicModelRejection.MODEL_SIZE_UNKNOWN)
        val peakBytes = profile.peakMemoryBytes?.takeIf { it > 0 } ?: return reject(IndicModelRejection.MEMORY_ESTIMATE_UNKNOWN)
        val license = profile.licenseSpdx?.trim().orEmpty()
        if (license.isBlank() || license.equals("unknown", true) || license.equals("unverified", true)) return reject(IndicModelRejection.LICENSE_UNVERIFIED)
        if (availableStorageBytes < modelBytes) return reject(IndicModelRejection.STORAGE_INSUFFICIENT)
        val safeBudget = (availableMemoryBytes.coerceAtLeast(0L) * 0.50).toLong()
        if (peakBytes > safeBudget) return reject(IndicModelRejection.MEMORY_BUDGET_EXCEEDED)
        return IndicModelDecision(true)
    }
}