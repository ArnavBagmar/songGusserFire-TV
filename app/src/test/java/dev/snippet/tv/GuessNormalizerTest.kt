package dev.snippet.tv

import dev.snippet.tv.game.GuessNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuessNormalizerTest {

    @Test
    fun normalizesCaseAndPunctuation() {
        assertEquals("dont stop me now", GuessNormalizer.normalize("Don't Stop Me Now!"))
    }

    @Test
    fun stripsDiacritics() {
        assertEquals("deja vu", GuessNormalizer.normalize("Déjà Vu"))
    }

    @Test
    fun stripsBracketedAndDashSuffixes() {
        assertEquals("deja vu", GuessNormalizer.normalize("Déjà Vu (Remastered 2011)"))
        assertEquals("blinding lights", GuessNormalizer.normalize("Blinding Lights - Radio Edit"))
        assertEquals("song title", GuessNormalizer.normalize("Song Title [Live at Wembley]"))
    }

    @Test
    fun matchesWithinEditDistanceTwo() {
        assertTrue(GuessNormalizer.matches("blinding lightz", "Blinding Lights"))
        assertTrue(GuessNormalizer.matches("BLINDING LIGHTS", "Blinding Lights - Radio Edit"))
    }

    @Test
    fun matchesIgnoringLeadingThe() {
        assertTrue(GuessNormalizer.matches("Chain", "The Chain"))
        assertTrue(GuessNormalizer.matches("The Chain", "Chain"))
    }

    @Test
    fun rejectsDistantOrEmptyGuesses() {
        assertFalse(GuessNormalizer.matches("Something Else", "Blinding Lights"))
        assertFalse(GuessNormalizer.matches("", "Blinding Lights"))
        assertFalse(GuessNormalizer.matches("...", "Blinding Lights"))
    }

    @Test
    fun levenshteinBoundsAndEarlyExit() {
        assertEquals(0, GuessNormalizer.levenshtein("same", "same", 2))
        assertEquals(1, GuessNormalizer.levenshtein("cat", "cut", 2))
        assertEquals(3, GuessNormalizer.levenshtein("abcdefgh", "zzzzzzzz", 2))
        assertEquals(3, GuessNormalizer.levenshtein("short", "muchlongerstring", 2))
    }
}
