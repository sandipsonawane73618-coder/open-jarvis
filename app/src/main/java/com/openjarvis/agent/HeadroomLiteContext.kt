package com.openjarvis.agent

/**
 * Android-native structural compaction for repeated screen/OCR lines.
 * Only adjacent, byte-identical, non-empty, plain display-text lines are candidates.
 * Structured values and diagnostic lines remain byte-for-byte unchanged.
 */
internal object HeadroomLiteContext {
    data class Result(
        val text: String,
        val originalChars: Int,
        val compactedChars: Int,
        val collapsedLines: Int
    ) {
        val charsSaved: Int get() = (originalChars - compactedChars).coerceAtLeast(0)
    }

    private val importantLine = Regex(
        """(?i)^\s*(error|warning|fatal|exception|failed|failure)\b"""
    )
    private val urlLine = Regex("""(?i).*(https?://|www\.).*""")
    private val uuidLine = Regex(
        """(?i).*\b[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b.*"""
    )
    private val identifierLine = Regex("""(?i).*\b[a-z][a-z0-9_-]*[0-9][a-z0-9_-]*\b.*""")
    private val numericIdLine = Regex(""".*\b[0-9]{5,}\b.*""")
    private val structuredLine = Regex(
        """(?i)^\s*(package|import|class|interface|object|fun|val|var|const|return)\b.*|.*[{};].*|^[a-z_][a-z0-9_.-]*\s*[:=]\s*\S+.*"""
    )

    private fun isSafeDisplayText(line: String): Boolean =
        line.isNotBlank() &&
            !importantLine.containsMatchIn(line) &&
            !urlLine.matches(line) &&
            !uuidLine.matches(line) &&
            !identifierLine.matches(line) &&
            !numericIdLine.matches(line) &&
            !structuredLine.matches(line)

    fun compressRepeatedLines(input: String): Result {
        if (input.isEmpty()) return Result(input, 0, 0, 0)

        // Keep the trailing empty element so a final newline is preserved.
        val lines = ArrayList<String>()
        var start = 0
        for (position in input.indices) {
            if (input[position] == '\n') {
                lines.add(input.substring(start, position))
                start = position + 1
            }
        }
        lines.add(input.substring(start))

        val output = StringBuilder(input.length)
        var collapsed = 0
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            var end = index + 1
            val candidate = isSafeDisplayText(line)
            if (candidate) {
                while (end < lines.size && lines[end] == line) end++
            }

            val count = end - index
            val marker = " ×$count"
            val sourceLength = count * line.length + (count - 1)
            val compactLength = line.length + marker.length
            val shouldCollapse = candidate && count > 1 && compactLength < sourceLength

            if (shouldCollapse) {
                output.append(line).append(marker)
                collapsed += count - 1
                index = end
            } else {
                output.append(line)
                index++
            }
            if (index < lines.size) output.append('\n')
        }

        val compacted = output.toString()
        return Result(compacted, input.length, compacted.length, collapsed)
    }
    /**
     * Replaces only the two explicitly designated screen-context slots.
     * The current request, confirmations, action schema, and all other prompt text
     * remain outside the compaction boundary and byte-for-byte unchanged.
     */
    fun applyScreenContext(
        promptTemplate: String,
        ocrText: String,
        interactiveElements: String,
        maxOcrChars: Int = 150,
        maxElementChars: Int = 650
    ): String = promptTemplate
        .replace("{SCREEN_OCR}", compressRepeatedLines(ocrText).text.take(maxOcrChars))
        .replace("{SCREEN_ELEMENTS}", interactiveElements.take(maxElementChars))

}