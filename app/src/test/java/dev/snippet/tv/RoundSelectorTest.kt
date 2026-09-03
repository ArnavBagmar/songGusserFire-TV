package dev.snippet.tv

import dev.snippet.tv.data.RoundSelector
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.data.model.Song
import dev.snippet.tv.data.model.TieredCatalog
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundSelectorTest {

    private val catalog = TieredCatalog.fromSongs(
        (1..50).map { Song(it.toLong(), "Song $it", "Artist $it", (100 - it).toLong()) },
    )

    @Test
    fun candidatesComeFromTheTierBucketAndAreDistinct() {
        for (tier in DifficultyTier.entries) {
            val bucket = catalog.bucket(tier)
            val candidates = RoundSelector.candidatesFor(tier, catalog)
            assertEquals(candidates.size, candidates.distinctBy { it.id }.size)
            assertTrue(candidates.all { it in bucket })
        }
    }

    @Test
    fun excludedIdsAreNotPickedWhenEnoughSongsRemain() {
        val tier = DifficultyTier.EASY
        val excluded = catalog.bucket(tier).take(3).map { it.id }.toSet()
        repeat(20) { seed ->
            val candidates = RoundSelector.candidatesFor(
                tier,
                catalog,
                excludeIds = excluded,
                random = Random(seed),
            )
            assertTrue(candidates.none { it.id in excluded })
        }
    }

    @Test
    fun exclusionIsDroppedRatherThanFailingWhenBucketWouldRunDry() {
        val tier = DifficultyTier.EASY
        val allIds = catalog.bucket(tier).map { it.id }.toSet()
        val candidates = RoundSelector.candidatesFor(tier, catalog, excludeIds = allIds)
        assertEquals(3, candidates.size)
    }

    @Test
    fun repeatedDrawsVaryThePick() {
        val picks = (0..29).map { seed ->
            RoundSelector.candidatesFor(DifficultyTier.EASY, catalog, random = Random(seed))
                .first()
        }
        assertTrue(picks.distinct().size > 1)
    }
}
