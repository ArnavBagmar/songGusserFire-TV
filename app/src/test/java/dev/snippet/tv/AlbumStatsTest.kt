package dev.snippet.tv

import dev.snippet.tv.data.SongPlayStats
import dev.snippet.tv.data.model.Song
import dev.snippet.tv.ui.albums.AlbumTileAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumStatsTest {

    private val song = Song(1, "Yellow", "Coldplay", 900)

    @Test
    fun applyResultCountsWinsAndLossesPerSong() {
        var stats = SongPlayStats.applyResult(null, song, won = true)
        stats = SongPlayStats.applyResult(stats, song, won = false)
        stats = SongPlayStats.applyResult(stats, song, won = true)
        assertEquals(2, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(3, stats.plays)
        assertEquals(66, stats.winRatePercent)
        assertEquals("Yellow", stats.title)
        assertEquals("Coldplay", stats.artist)
    }

    @Test
    fun applyResultRefreshesTitleAndArtistFromCatalog() {
        val old = SongPlayStats(1, "Old Name", "Old Artist", wins = 1, losses = 0)
        val updated = SongPlayStats.applyResult(old, song, won = false)
        assertEquals("Yellow", updated.title)
        assertEquals("Coldplay", updated.artist)
        assertEquals(1, updated.wins)
        assertEquals(1, updated.losses)
    }

    @Test
    fun accuracyBelowTenPercentIsBarelyVisible() {
        assertEquals(AlbumTileAlpha.MIN_ALPHA, AlbumTileAlpha.alphaFor(0), 0.001f)
        assertEquals(AlbumTileAlpha.MIN_ALPHA, AlbumTileAlpha.alphaFor(9), 0.001f)
    }

    @Test
    fun accuracyAtOrAboveEightyFivePercentIsFullyVisible() {
        assertEquals(1f, AlbumTileAlpha.alphaFor(85), 0.001f)
        assertEquals(1f, AlbumTileAlpha.alphaFor(100), 0.001f)
    }

    @Test
    fun alphaGrowsMonotonicallyBetweenThresholds() {
        val alphas = (10..85).map { AlbumTileAlpha.alphaFor(it) }
        assertTrue(alphas.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(AlbumTileAlpha.MIN_ALPHA, alphas.first(), 0.001f)
        assertEquals(1f, alphas.last(), 0.001f)
    }

    @Test
    fun alphaToleratesOutOfRangePercents() {
        assertEquals(AlbumTileAlpha.MIN_ALPHA, AlbumTileAlpha.alphaFor(-5), 0.001f)
        assertEquals(1f, AlbumTileAlpha.alphaFor(150), 0.001f)
    }
}
