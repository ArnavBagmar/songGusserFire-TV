package dev.snippet.tv.data

import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.data.model.Song
import dev.snippet.tv.data.model.TieredCatalog
import kotlin.random.Random

/**
 * Random song selection for unlimited play: every round draws fresh from the
 * tier's bucket. Callers pass recently played ids to avoid immediate repeats;
 * the exclusion is dropped rather than failing when it would empty the bucket.
 */
object RoundSelector {

    private const val DEFAULT_CANDIDATE_COUNT = 3

    /**
     * The round's pick for a tier, plus fallbacks used only when a track can
     * no longer be resolved on Deezer (deleted or preview withdrawn).
     */
    fun candidatesFor(
        tier: DifficultyTier,
        catalog: TieredCatalog,
        excludeIds: Set<Long> = emptySet(),
        count: Int = DEFAULT_CANDIDATE_COUNT,
        random: Random = Random.Default,
    ): List<Song> {
        val bucket = catalog.bucket(tier)
        require(bucket.isNotEmpty()) { "Empty bucket for tier $tier" }
        val fresh = bucket.filter { it.id !in excludeIds }
        val pool = if (fresh.size >= count) fresh else bucket
        return pool.shuffled(random).take(minOf(count, pool.size))
    }
}
