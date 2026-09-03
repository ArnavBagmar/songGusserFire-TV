package dev.snippet.tv.data.model

/**
 * Difficulty is a popularity percentile of the bundled pool, not a snippet-length
 * change.
 */
enum class DifficultyTier(
    val displayName: String,
    val bandLabel: String,
) {
    EASY("Easy", "Top 20% most-played"),
    MEDIUM("Medium", "Top 20–40% band"),
    HARD("Hard", "Middle 40–60% band"),
    EXPERT("Expert", "Lower 60–80% band"),
    IMPOSSIBLE("Impossible", "Bottom 20% — deep cuts"),
}
