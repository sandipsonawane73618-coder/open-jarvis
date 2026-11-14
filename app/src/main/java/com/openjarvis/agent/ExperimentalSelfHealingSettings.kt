package com.openjarvis.agent

import android.content.Context

/** Experimental helpers remain disabled unless the user explicitly enables them in Settings. */
object ExperimentalSelfHealingSettings {
    const val PREFS_NAME = "jarvis_experimental_self_healing"
    const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        isEnabled(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false))

    /** Pure overload used by JVM tests; null represents an unset preference. */
    fun isEnabled(storedValue: Boolean?): Boolean = storedValue == true

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
