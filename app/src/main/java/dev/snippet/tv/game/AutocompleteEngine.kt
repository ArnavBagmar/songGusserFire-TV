package dev.snippet.tv.game

import dev.snippet.tv.data.model.Song

data class Suggestion(val title: String, val artist: String)

/**
 * Autocomplete over the full bundled pool, matching on title or artist: exact
 * prefix first, then word-prefix, then substring, then fuzzy (edit distance
 * <= 2), each group ordered by popularity. Capped at [MAX_SUGGESTIONS] results.
 */
class AutocompleteEngine(songs: List<Song>) {

    private data class Entry(
        val title: String,
        val artist: String,
        val normalizedTitle: String,
        val titleWords: List<String>,
        val normalizedArtist: String,
        val artistWords: List<String>,
        val rank: Long,
    )

    private val entries: List<Entry> = songs
        .map { song ->
            val normalizedTitle = GuessNormalizer.normalize(song.title)
            val normalizedArtist = GuessNormalizer.normalize(song.artist)
            Entry(
                title = song.title,
                artist = song.artist,
                normalizedTitle = normalizedTitle,
                titleWords = normalizedTitle.split(' '),
                normalizedArtist = normalizedArtist,
                artistWords = normalizedArtist.split(' '),
                rank = song.rank,
            )
        }
        .filter { it.normalizedTitle.isNotEmpty() }
        .groupBy { it.normalizedTitle }
        .map { (_, duplicates) -> duplicates.maxBy { it.rank } }

    fun suggest(query: String, limit: Int = MAX_SUGGESTIONS): List<Suggestion> {
        val q = GuessNormalizer.normalize(query)
        if (q.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry -> scoreFor(entry, q)?.let { score -> entry to score } }
            .sortedWith(compareBy({ it.second }, { -it.first.rank }, { it.first.normalizedTitle }))
            .take(limit)
            .map { (entry, _) -> Suggestion(entry.title, entry.artist) }
    }

    private fun scoreFor(entry: Entry, q: String): Int? {
        val titleScore = fieldScore(entry.normalizedTitle, entry.titleWords, q)
        val artistScore = fieldScore(entry.normalizedArtist, entry.artistWords, q)
        return listOfNotNull(titleScore, artistScore).minOrNull()
    }

    private fun fieldScore(normalized: String, words: List<String>, q: String): Int? = when {
        normalized.startsWith(q) -> 0
        words.any { it.startsWith(q) } -> 1
        q.length >= SUBSTRING_MIN_QUERY && normalized.contains(q) -> 2
        q.length >= FUZZY_MIN_QUERY &&
            GuessNormalizer.levenshtein(q, normalized, GuessNormalizer.MAX_EDIT_DISTANCE) <=
            GuessNormalizer.MAX_EDIT_DISTANCE -> 3
        else -> null
    }

    companion object {
        const val MAX_SUGGESTIONS = 30
        private const val SUBSTRING_MIN_QUERY = 3
        private const val FUZZY_MIN_QUERY = 4
    }
}
