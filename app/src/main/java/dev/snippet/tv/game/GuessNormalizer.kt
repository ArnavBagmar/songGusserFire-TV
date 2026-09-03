package dev.snippet.tv.game

import java.text.Normalizer
import kotlin.math.abs

/**
 * Forgiving title matching: case-insensitive, diacritics stripped, punctuation
 * ignored, bracketed suffixes and " - Radio Edit"-style suffixes removed, and a
 * Levenshtein distance of up to [MAX_EDIT_DISTANCE] accepted on the result.
 */
object GuessNormalizer {

    const val MAX_EDIT_DISTANCE = 2

    private val combiningMarks = Regex("\\p{Mn}+")
    private val bracketed = Regex("\\(.*?\\)|\\[.*?\\]")
    private val dashSuffix = Regex("\\s+[-\u2013\u2014]\\s+.*$")
    private val apostrophes = Regex("['\u2019\u02BC]")
    private val nonAlphanumeric = Regex("[^a-z0-9 ]")
    private val whitespace = Regex("\\s+")

    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(combiningMarks, "")
            .replace(apostrophes, "")
            .replace(bracketed, " ")
            .replace(dashSuffix, " ")
            .replace(nonAlphanumeric, " ")
            .replace(whitespace, " ")
            .trim()
    }

    fun matches(guess: String, answer: String): Boolean {
        val g = normalize(guess)
        val a = normalize(answer)
        if (g.isEmpty() || a.isEmpty()) return false
        if (g == a || stripLeadingThe(g) == stripLeadingThe(a)) return true
        if (levenshtein(g, a, MAX_EDIT_DISTANCE) <= MAX_EDIT_DISTANCE) return true
        return levenshtein(stripLeadingThe(g), stripLeadingThe(a), MAX_EDIT_DISTANCE) <= MAX_EDIT_DISTANCE
    }

    private fun stripLeadingThe(normalized: String): String =
        normalized.removePrefix("the ").trim()

    /**
     * Bounded edit distance; returns max + 1 as soon as the distance provably
     * exceeds [max], so autocomplete can call this per keystroke cheaply.
     */
    fun levenshtein(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > max) return max + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMinimum = current[0]
            for (j in 1..b.length) {
                val substitution = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + substitution,
                )
                rowMinimum = minOf(rowMinimum, current[j])
            }
            if (rowMinimum > max) return max + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
