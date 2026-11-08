package com.openjarvis.agent

/**
 * Offline, Android-native adaptation of Headroom's content-aware compression principles.
 * JSON is minified only after strict JSON validation; quoted strings and values are preserved.
 * Text/log output uses conservative run-length encoding for adjacent identical lines.
 * UI labels and policy prompts preserve every nonblank line to avoid changing action targets/rules.
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
            ContentKind.TEXT, ContentKind.LOG -> compactLines(input, encodeRepeats = true)
            ContentKind.UI_TEXT, ContentKind.POLICY -> compactLines(input, encodeRepeats = false)
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

    fun estimateTokens(text: String): Int = (text.length + 3) / 4

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
     * Collapse repeated blank-line runs. For TEXT/LOG only, encode long adjacent identical
     * nonblank-line runs as line + count. UI_TEXT and POLICY never rewrite nonblank lines.
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
                val marker = "$line ⟪x$count⟫"
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
