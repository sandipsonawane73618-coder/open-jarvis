package com.openjarvis.agent

import android.content.Context

/** Local-only bounded failure-pattern counters. Stores category names and counts, never user content. */
internal class SelfHealingPatternStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jarvis_self_healing_patterns", Context.MODE_PRIVATE)

    @Synchronized
    fun record(area: String, now: Long = System.currentTimeMillis()): SelfHealingPatternCodec.Pattern {
        val updatedPatterns = SelfHealingPatternCodec.record(read(), area, now)
        val updated = updatedPatterns.first()
        persist(updatedPatterns)
        return updated
    }

    @Synchronized
    fun recordOutcome(area: String, verified: Boolean, now: Long = System.currentTimeMillis()): SelfHealingPatternCodec.Pattern? {
        val updatedPatterns = SelfHealingPatternCodec.recordOutcome(read(), area, verified, now)
        val updated = updatedPatterns.firstOrNull { it.area == area } ?: return null
        persist(updatedPatterns)
        return updated
    }

    private fun persist(patterns: List<SelfHealingPatternCodec.Pattern>) {
        prefs.edit().putString(KEY_PATTERNS, SelfHealingPatternCodec.encodeJson(patterns)).apply()
    }

    fun patterns(): List<SelfHealingPatternCodec.Pattern> = read()

    private fun read(): List<SelfHealingPatternCodec.Pattern> =
        SelfHealingPatternCodec.decodeJson(prefs.getString(KEY_PATTERNS, "[]"))

    companion object { private const val KEY_PATTERNS = "patterns_json" }
}
