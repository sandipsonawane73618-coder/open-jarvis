package com.openjarvis.agent

/**
 * Bounds optional action-planning context before it is added to the system prompt.
 *
 * Priority is deliberate: complete visible interactive targets first, then complete
 * query-relevant memory facts, OCR words, and complete installed-app labels. The user's
 * command is not accepted by this API, so it cannot be truncated or rewritten here.
 */
internal object HeadroomContextBudget {
    const val MAX_TOTAL_CHARS = 1_000

    private const val MAX_SCREEN_ELEMENTS = 450
    private const val MAX_MEMORY = 350
    private const val MAX_SCREEN_TEXT = 150
    private const val MAX_INSTALLED_APPS = 50

    data class Allocation(
        val memory: String,
        val screenText: String,
        val screenElements: String,
        val installedApps: String,
        val inputChars: Int
    ) {
        val outputChars: Int
            get() = memory.length + screenText.length + screenElements.length + installedApps.length
    }

    fun allocate(
        memory: String,
        screenText: String,
        screenElements: String,
        installedApps: String
    ): Allocation {
        var remaining = MAX_TOTAL_CHARS

        fun takeLines(value: String, limit: Int): String {
            if (remaining <= 0 || value.isEmpty()) return ""
            val bounded = fitCompleteLines(value, minOf(limit, remaining))
            remaining -= bounded.length
            return bounded
        }

        fun takeText(value: String, limit: Int): String {
            if (remaining <= 0 || value.isEmpty()) return ""
            val bounded = fitTextAtWordBoundary(value, minOf(limit, remaining))
            remaining -= bounded.length
            return bounded
        }

        fun takeApps(value: String, limit: Int): String {
            if (remaining <= 0 || value.isEmpty()) return ""
            val bounded = fitDelimitedItems(value, ", ", minOf(limit, remaining))
            remaining -= bounded.length
            return bounded
        }

        // Allocate in priority order, independent of argument order.
        val elements = takeLines(screenElements, MAX_SCREEN_ELEMENTS)
        val facts = takeLines(memory, MAX_MEMORY)
        val ocr = takeText(screenText, MAX_SCREEN_TEXT)
        val apps = takeApps(installedApps, MAX_INSTALLED_APPS)

        return Allocation(
            memory = facts,
            screenText = ocr,
            screenElements = elements,
            installedApps = apps,
            inputChars = memory.length + screenText.length + screenElements.length + installedApps.length
        )
    }

    /**
     * Keep only complete newline-delimited records. Interactive element records must
     * never be cut mid-label, resource-id, or bounds tuple.
     */
    fun fitCompleteLines(value: String, limit: Int): String {
        if (limit <= 0 || value.isEmpty()) return ""
        if (value.length <= limit) return value

        val included = ArrayList<String>()
        var used = 0
        for (line in value.split('\n')) {
            val additional = line.length + if (included.isEmpty()) 0 else 1
            if (used + additional > limit) continue
            included += line
            used += additional
        }
        return included.joinToString("\n")
    }

    /** Keep complete comma-delimited app labels instead of cutting an app name in half. */
    fun fitDelimitedItems(value: String, delimiter: String, limit: Int): String {
        if (limit <= 0 || value.isEmpty()) return ""
        if (value.length <= limit) return value

        val included = ArrayList<String>()
        var used = 0
        for (item in value.split(delimiter)) {
            val additional = item.length + if (included.isEmpty()) 0 else delimiter.length
            if (used + additional > limit) continue
            included += item
            used += additional
        }
        return included.joinToString(delimiter)
    }

    /** OCR is descriptive text, so retain whole words and add a clear truncation marker. */
    fun fitTextAtWordBoundary(value: String, limit: Int): String {
        if (value.length <= limit) return value
        if (limit <= 1) return "…".take(limit)

        val candidate = value.take(limit - 1)
        val boundary = candidate.indexOfLast { it.isWhitespace() }
        if (boundary < 0) return ""
        val safePrefix = candidate.substring(0, boundary).trimEnd()
        return if (safePrefix.isEmpty()) "" else safePrefix + "…"
    }
}
