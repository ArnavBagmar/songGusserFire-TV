package dev.snippet.tv

import dev.snippet.tv.data.model.Song
import dev.snippet.tv.game.AutocompleteEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocompleteEngineTest {

    private val songs = listOf(
        Song(1, "Yellow", "Coldplay", 900),
        Song(2, "Yellow Submarine", "The Beatles", 800),
        Song(3, "Hello", "Adele", 950),
        Song(4, "Mellow Yellow", "Donovan", 500),
        Song(5, "Yesterday", "The Beatles", 990),
    )
    private val engine = AutocompleteEngine(songs)

    @Test
    fun prefixMatchesComeFirstOrderedByPopularity() {
        val results = engine.suggest("ye")
        assertEquals("Yesterday", results[0].title)
        assertEquals("Yellow", results[1].title)
        assertEquals("Yellow Submarine", results[2].title)
        // "Mellow Yellow" only matches on a word prefix, so it ranks after.
        assertEquals("Mellow Yellow", results[3].title)
    }

    @Test
    fun cappedAtThirtyResults() {
        val many = (1..40).map { Song(it.toLong(), "Song Number $it", "Artist", it * 10L) }
        assertEquals(30, AutocompleteEngine(many).suggest("song").size)
    }

    @Test
    fun prolificArtistSurfacesMoreThanSixSongs() {
        val discography = (1..12).map { Song(it.toLong(), "Album Cut $it", "Prolific Artist", it * 10L) }
        val results = AutocompleteEngine(discography + songs).suggest("prolific")
        assertEquals(12, results.count { it.artist == "Prolific Artist" })
    }

    @Test
    fun emptyQueryYieldsNothing() {
        assertTrue(engine.suggest("").isEmpty())
        assertTrue(engine.suggest("   ").isEmpty())
    }

    @Test
    fun fuzzyMatchesTypos() {
        val results = engine.suggest("yelow submarine")
        assertTrue(results.any { it.title == "Yellow Submarine" })
    }

    @Test
    fun artistNameMatchesSurfaceTheirSongs() {
        val results = engine.suggest("coldplay")
        assertTrue(results.any { it.title == "Yellow" && it.artist == "Coldplay" })
    }

    @Test
    fun artistWordPrefixMatchesOrderedByPopularity() {
        val results = engine.suggest("beatles")
        val beatles = results.filter { it.artist == "The Beatles" }
        assertEquals(listOf("Yesterday", "Yellow Submarine"), beatles.map { it.title })
    }

    @Test
    fun fuzzyMatchesArtistTypos() {
        val results = engine.suggest("coldpaly")
        assertTrue(results.any { it.artist == "Coldplay" })
    }

    @Test
    fun duplicateTitlesCollapseToMostPopular() {
        val duplicated = songs + Song(6, "yellow", "Cover Band", 100)
        val results = AutocompleteEngine(duplicated).suggest("yellow")
        assertEquals(1, results.count { it.title.equals("yellow", ignoreCase = true) })
        assertEquals("Coldplay", results.first { it.title.equals("yellow", ignoreCase = true) }.artist)
    }
}
