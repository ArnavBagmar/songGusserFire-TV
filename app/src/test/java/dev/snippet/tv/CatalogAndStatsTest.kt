package dev.snippet.tv

import dev.snippet.tv.data.SongCatalogLoader
import dev.snippet.tv.data.StatsRepository
import dev.snippet.tv.data.TierStats
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.data.model.Song
import dev.snippet.tv.data.model.TieredCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndStatsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseIgnoresUnknownKeysAndFiltersInvalidEntries() {
        val validEntries = (1..30).joinToString(",") {
            """{"id":$it,"title":"Song $it","artist":"Artist","rank":${it * 100},"extra":true}"""
        }
        val text = """
            {"_note":"generated","version":1,"generatedAtUtc":"2026-09-01T00:00:00Z","songs":[
              $validEntries,
              {"id":0,"title":"Bad Id","artist":"X","rank":10},
              {"id":97,"title":"","artist":"X","rank":10},
              {"id":98,"title":"No Rank","artist":"X","rank":0}
            ]}
        """.trimIndent()
        val catalog = SongCatalogLoader.parse(json, text)
        assertEquals(30, catalog.songs.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsTinyPools() {
        SongCatalogLoader.parse(json, """{"songs":[{"id":1,"title":"A","artist":"B","rank":5}]}""")
    }

    @Test
    fun tiersAreEqualPercentileBucketsSortedByRank() {
        val songs = (1..50).map { Song(it.toLong(), "S$it", "A", (1000 - it).toLong()) }
        val catalog = TieredCatalog.fromSongs(songs)
        for (tier in DifficultyTier.entries) {
            assertEquals(10, catalog.bucket(tier).size)
        }
        val easyMin = catalog.bucket(DifficultyTier.EASY).minOf { it.rank }
        val impossibleMax = catalog.bucket(DifficultyTier.IMPOSSIBLE).maxOf { it.rank }
        assertTrue("Easy bucket must outrank Impossible bucket", easyMin > impossibleMax)
    }

    @Test
    fun applyResultTracksStreaksWinsAndDistribution() {
        var stats = TierStats()
        stats = StatsRepository.applyResult(stats, won = true, attemptsUsed = 3)
        stats = StatsRepository.applyResult(stats, won = true, attemptsUsed = 1)
        stats = StatsRepository.applyResult(stats, won = false, attemptsUsed = 5)
        stats = StatsRepository.applyResult(stats, won = true, attemptsUsed = 3)
        assertEquals(4, stats.gamesPlayed)
        assertEquals(3, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(1, stats.currentStreak)
        assertEquals(2, stats.maxStreak)
        assertEquals(listOf(1, 0, 2, 0, 0, 0), stats.attemptDistribution)
        assertEquals(75, stats.winRatePercent)
    }
}
