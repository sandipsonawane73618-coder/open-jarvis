package com.openjarvis.headroom

/**
 * Conservative, deterministic context compression for on-device JARVIS prompts.
 *
 * This first implementation deliberately avoids summarization or paraphrasing:
 * user commands, action JSON, and safety instructions are never rewritten.
 */
data class HeadroomCompressionResult(
    val original: String,
    val compressed: String,
    val originalCharacters: Int,
    val compressedCharacters: Int,
    val estimatedOriginalTokens: Int,
    val estimatedCompressedTokens: Int
) {
    val charactersSaved: Int get() = (originalCharacters - compressedCharacters).coerceAtLeast(0)
    val estimatedTokensSaved: Int get() =
        (estimatedOriginalTokens - estimatedCompressedTokens).coerceAtLeast(0)
    val reductionPercent: Int get() =
        if (originalCharacters == 0) 0
        else ((charactersSaved * 100f) / originalCharacters).toInt()
}

/**
 * Lightweight first-stage compressor. It removes only redundant formatting and
 * immediately repeated tool/log lines. It does not summarize or reorder content.
 */
object HeadroomCompressor {
    private val repeatableLinePrefixes = listOf(
        "tool:", "[tool]", "log:", "[log]", "result:"
    )

    fun compressContext(input: String): HeadroomCompressionResult {
        val output = mutableListOf<String>()
        var previousRepeatableLine: String? = null
        var blankLinePending = false

        input.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                if (output.isNotEmpty()) blankLinePending = true
                previousRepeatableLine = null
                return@forEach
            }

            val candidate = line.trimStart()
            val isRepeatable = repeatableLinePrefixes.any {
                candidate.startsWith(it, ignoreCase = true)
            }
            if (isRepeatable && previousRepeatableLine == line) {
                return@forEach
            }

            if (blankLinePending && output.isNotEmpty()) output.add("")
            blankLinePending = false
            output.add(line)
            previousRepeatableLine = line.takeIf { isRepeatable }
        }

        val compressed = output.joinToString("\n")
        return HeadroomCompressionResult(
            original = input,
            compressed = compressed,
            originalCharacters = input.length,
            compressedCharacters = compressed.length,
            estimatedOriginalTokens = estimateTokens(input),
            estimatedCompressedTokens = estimateTokens(compressed)
        )
    }

    /** Rough telemetry only; never use this estimate as an LLM tokenizer. */
    fun estimateTokens(text: String): Int =
        if (text.isEmpty()) 0 else (text.length + 3) / 4
}
