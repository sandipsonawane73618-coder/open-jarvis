package com.openjarvis.agent

/**
 * Offline, Android-native adaptation of Headroom's content-aware compression principles.
 * JSON is minified only after strict JSON validation; quoted strings and values are preserved.
 * Only diagnostic logs use explicit run-length encoding; natural text, UI labels, and policy
 * prompts preserve repeated nonblank lines so memory counts and action targets/rules stay intact.
 * Code, diffs and safety-critical payloads can explicitly opt out.
 *
 * CompressionResult retains the exact original so callers/tests can restore byte-for-byte.
 * No network, daemon, API key, model, or background service is used.
 */
object HeadroomLite {
    enum class ContentKind { AUTO, TEXT, UI_TEXT, POLICY, LOG, JSON, CODE, DIFF, PRESERVE }

    data class CompressionResult(
        val original: String,
        val content: String,
        val kind: ContentKind,
        val inputChars: Int,
        val outputChars: Int,
        val estimatedInputTokens: Int,
        val estimatedOutputTokens: Int
    ) {
        val savedChars: Int get() = inputChars - outputChars
        val savedTokens: Int get() = estimatedInputTokens - estimatedOutputTokens
        val compressionRatio: Double
            get() = if (inputChars == 0) 1.0 else outputChars.toDouble() / inputChars.toDouble()

        /** Exact restoration: the original input is retained byte-for-byte. */
        fun restore(): String = original
    }

    fun compress(input: String, requestedKind: ContentKind = ContentKind.AUTO): CompressionResult {
        val kind = if (requestedKind == ContentKind.AUTO) {
            when {
                isJsonDocument(input) -> ContentKind.JSON
                looksLikeJsonDocument(input) -> ContentKind.PRESERVE
                else -> ContentKind.TEXT
            }
        } else requestedKind

        val compacted = when (kind) {
            ContentKind.JSON -> compactJson(input) ?: input
            ContentKind.TEXT, ContentKind.UI_TEXT, ContentKind.POLICY -> compactLines(input, encodeRepeats = false)
            ContentKind.LOG -> compactLines(input, encodeRepeats = true)
            ContentKind.AUTO -> input
            ContentKind.CODE, ContentKind.DIFF, ContentKind.PRESERVE -> input
        }.let { candidate -> if (candidate.length < input.length) candidate else input }

        return CompressionResult(
            original = input,
            content = compacted,
            kind = kind,
            inputChars = input.length,
            outputChars = compacted.length,
            estimatedInputTokens = estimateTokens(input),
            estimatedOutputTokens = estimateTokens(compacted)
        )
    }

    /**
     * Lightweight, conservative estimate: ASCII word runs average four characters/token;
     * non-ASCII code points average two characters/token; emoji surrogate pairs count once.
     * This is a metric, not a substitute for the model's tokenizer.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var total = 0
        var asciiRun = 0
        var unicodeRun = 0

        fun flushAscii() {
            if (asciiRun > 0) {
                total += (asciiRun + 3) / 4
                asciiRun = 0
            }
        }

        fun flushUnicode() {
            if (unicodeRun > 0) {
                total += (unicodeRun + 1) / 2
                unicodeRun = 0
            }
        }

        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character.isWhitespace() -> {
                    flushAscii()
                    flushUnicode()
                    index++
                }
                character.code < 0x80 && (character.isLetterOrDigit() || character == '_') -> {
                    flushUnicode()
                    asciiRun++
                    index++
                }
                character.code < 0x80 -> {
                    flushAscii()
                    flushUnicode()
                    total++
                    index++
                }
                Character.isHighSurrogate(character) &&
                    index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> {
                    flushAscii()
                    flushUnicode()
                    total++
                    index += 2
                }
                Character.isLowSurrogate(character) -> index++
                else -> {
                    flushAscii()
                    unicodeRun++
                    index++
                }
            }
        }
        flushAscii()
        flushUnicode()
        return total
    }

    private fun isJsonDocument(input: String): Boolean =
        StrictJsonValidator(input.trim()).isValid()

    private fun looksLikeJsonDocument(input: String): Boolean {
        val value = input.trimStart()
        return value.startsWith("{") || value.startsWith("[")
    }

    /** Remove only JSON insignificant whitespace; quoted strings and escapes stay byte-identical. */
    private fun compactJson(input: String): String? {
        if (!isJsonDocument(input)) return null
        val value = input.trim()
        val output = StringBuilder(value.length)
        var inString = false
        var escaped = false

        for (character in value) {
            if (inString) {
                output.append(character)
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when {
                    character == '"' -> {
                        inString = true
                        output.append(character)
                    }
                    character.isWhitespace() -> Unit
                    else -> output.append(character)
                }
            }
        }
        return output.toString().takeIf { isJsonDocument(it) }
    }

    /**
     * Collapse repeated blank-line runs. LOG may encode long adjacent identical nonblank
     * lines with an explicit count. TEXT, UI_TEXT, and POLICY never rewrite nonblank lines.
     */
    private fun compactLines(input: String, encodeRepeats: Boolean): String {
        if (input.isEmpty()) return input
        val lines = input.split('\n')
        val output = ArrayList<String>(lines.size)
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            var end = index + 1
            while (end < lines.size && lines[end] == line) end++
            val count = end - index

            if (line.isEmpty() && count > 1) {
                output += ""
            } else if (encodeRepeats && count > 1) {
                val marker = "$line ⟪repeated $count times⟫"
                val originalLength = count * line.length + (count - 1)
                if (marker.length < originalLength) output += marker
                else repeat(count) { output += line }
            } else {
                repeat(count) { output += line }
            }
            index = end
        }
        return output.joinToString("\n")
    }

    /** Strict JSON grammar validator; avoids JSONTokener's permissive extensions. */
    private class StrictJsonValidator(private val source: String) {
        private var index = 0

        fun isValid(): Boolean {
            skipWhitespace()
            if (!parseValue()) return false
            skipWhitespace()
            return index == source.length
        }

        private fun parseValue(): Boolean {
            skipWhitespace()
            if (index >= source.length) return false
            return when (source[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true")
                'f' -> parseLiteral("false")
                'n' -> parseLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> false
            }
        }

        private fun parseObject(): Boolean {
            index++
            skipWhitespace()
            if (consume('}')) return true
            while (true) {
                skipWhitespace()
                if (!parseString()) return false
                skipWhitespace()
                if (!consume(':')) return false
                if (!parseValue()) return false
                skipWhitespace()
                if (consume('}')) return true
                if (!consume(',')) return false
            }
        }

        private fun parseArray(): Boolean {
            index++
            skipWhitespace()
            if (consume(']')) return true
            while (true) {
                if (!parseValue()) return false
                skipWhitespace()
                if (consume(']')) return true
                if (!consume(',')) return false
            }
        }

        private fun parseString(): Boolean {
            if (!consume('"')) return false
            while (index < source.length) {
                val character = source[index++]
                when {
                    character == '"' -> return true
                    character == '\\' -> {
                        if (index >= source.length) return false
                        when (source[index++]) {
                            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                            'u' -> {
                                if (index + 4 > source.length) return false
                                repeat(4) {
                                    if (source[index++].digitToIntOrNull(16) == null) return false
                                }
                            }
                            else -> return false
                        }
                    }
                    character.code < 0x20 -> return false
                }
            }
            return false
        }

        private fun parseNumber(): Boolean {
            consume('-')
            if (consume('0')) {
                if (index < source.length && source[index] in '0'..'9') return false
            } else {
                if (!consumeDigitOneToNine()) return false
                while (consumeDigit()) Unit
            }
            if (consume('.')) {
                if (!consumeDigit()) return false
                while (consumeDigit()) Unit
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) consume('-')
                if (!consumeDigit()) return false
                while (consumeDigit()) Unit
            }
            return true
        }

        private fun parseLiteral(literal: String): Boolean {
            if (!source.startsWith(literal, index)) return false
            index += literal.length
            return true
        }

        private fun consumeDigitOneToNine(): Boolean {
            if (index >= source.length || source[index] !in '1'..'9') return false
            index++
            return true
        }

        private fun consumeDigit(): Boolean {
            if (index >= source.length || source[index] !in '0'..'9') return false
            index++
            return true
        }

        private fun consume(character: Char): Boolean {
            if (index >= source.length || source[index] != character) return false
            index++
            return true
        }

        private fun skipWhitespace() {
            while (index < source.length) {
                when (source[index]) {
                    ' ', '\t', '\r', '\n' -> index++
                    else -> return
                }
            }
        }
    }
}
