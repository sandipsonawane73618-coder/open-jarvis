package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative Latin/Devanagari contact-name matching for common Hindi name spellings.
 * It does not choose a contact; callers must handle multiple matches explicitly.
 */
internal object ContactNameMatcher {
    fun matches(requested: String, stored: String): Boolean {
        val query = normalize(requested)
        val name = normalize(stored)
        return query.isNotBlank() && (name == query || name.startsWith("$query ") || name.startsWith("$query-"))
    }

    internal fun normalize(value: String): String {
        val source = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
        val consonants = mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'च' to "ch", 'छ' to "chh",
            'ज' to "j", 'झ' to "jh", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh",
            'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'प' to "p",
            'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r",
            'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
            'ण' to "n", 'ङ' to "ng", 'ञ' to "ny"
        )
        val vowels = mapOf(
            'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
            'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
        )
        val matras = mapOf(
            'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
            'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
        )
        val output = StringBuilder()
        var pendingConsonant = false
        for (character in source) {
            when {
                consonants.containsKey(character) -> {
                    if (pendingConsonant) output.append('a')
                    output.append(consonants.getValue(character))
                    pendingConsonant = true
                }
                vowels.containsKey(character) -> {
                    if (pendingConsonant) output.append('a')
                    output.append(vowels.getValue(character))
                    pendingConsonant = false
                }
                matras.containsKey(character) -> {
                    output.append(matras.getValue(character))
                    pendingConsonant = false
                }
                character == '्' -> pendingConsonant = false
                character == 'ं' || character == 'ँ' -> {
                    output.append('m')
                    pendingConsonant = false
                }
                character == ' ' || character == '-' || character == '\'' -> {
                    output.append(character)
                    pendingConsonant = false
                }
                character.code < 128 -> {
                    if (pendingConsonant) output.append('a')
                    output.append(character)
                    pendingConsonant = false
                }
            }
        }
        val latin = output.toString().trim().removeSuffix("a")
        return Normalizer.normalize(latin, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
    }
}
