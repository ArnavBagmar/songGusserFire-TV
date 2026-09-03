package dev.snippet.tv.data.model

/**
 * The validated song pool cut into five popularity buckets. Bucket boundaries are
 * computed once from the bundled list, so tiers stay balanced for any pool size.
 */
class TieredCatalog private constructor(
    val songs: List<Song>,
    private val buckets: Map<DifficultyTier, List<Song>>,
    private val byId: Map<Long, Song>,
) {
    fun bucket(tier: DifficultyTier): List<Song> = buckets.getValue(tier)

    fun findById(id: Long): Song? = byId[id]

    companion object {
        const val MIN_POOL_SIZE = 25

        fun fromSongs(raw: List<Song>): TieredCatalog {
            val valid = raw
                .filter { it.id > 0 && it.title.isNotBlank() && it.artist.isNotBlank() && it.rank > 0 }
                .distinctBy { it.id }
            require(valid.size >= MIN_POOL_SIZE) {
                "Song pool too small: ${valid.size} valid entries (need at least $MIN_POOL_SIZE)"
            }
            // Rank descending; id breaks ties so every install sorts identically.
            val sorted = valid.sortedWith(compareByDescending<Song> { it.rank }.thenBy { it.id })
            val tiers = DifficultyTier.entries
            val buckets = tiers.withIndex().associate { (index, tier) ->
                val from = index * sorted.size / tiers.size
                val to = (index + 1) * sorted.size / tiers.size
                tier to sorted.subList(from, to).toList()
            }
            return TieredCatalog(sorted, buckets, sorted.associateBy { it.id })
        }
    }
}
