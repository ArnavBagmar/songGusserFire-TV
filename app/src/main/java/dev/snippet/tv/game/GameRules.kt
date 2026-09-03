package dev.snippet.tv.game

/** Core round constants, shared by the engine, UI and stats. */
object GameRules {

    const val MAX_ATTEMPTS = 6
    const val PREVIEW_LENGTH_MS = 30_000L

    /** Snippet length unlocked at each attempt, always played from the start of the preview. */
    val SNIPPET_STEPS_MS = listOf(100L, 500L, 1_000L, 2_000L, 8_000L, 15_000L)

    fun snippetMsForAttempt(attemptIndex: Int): Long =
        SNIPPET_STEPS_MS[attemptIndex.coerceIn(0, SNIPPET_STEPS_MS.lastIndex)]

    fun formatSnippetSeconds(ms: Long): String =
        if (ms % 1000 == 0L) "${ms / 1000}s" else "${ms / 1000.0}s"
}
