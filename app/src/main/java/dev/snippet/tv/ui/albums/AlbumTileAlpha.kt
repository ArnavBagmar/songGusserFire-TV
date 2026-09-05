package dev.snippet.tv.ui.albums

/**
 * Cover opacity encodes how often the song gets guessed: barely-there below
 * [LOW_PERCENT] accuracy, fully visible from [HIGH_PERCENT] up, linear ramp
 * in between. Pure so it is unit-testable.
 */
object AlbumTileAlpha {

    const val MIN_ALPHA = 0.15f
    const val LOW_PERCENT = 10
    const val HIGH_PERCENT = 85

    fun alphaFor(winRatePercent: Int): Float {
        val percent = winRatePercent.coerceIn(0, 100)
        return when {
            percent < LOW_PERCENT -> MIN_ALPHA
            percent >= HIGH_PERCENT -> 1f
            else ->
                MIN_ALPHA +
                    (1f - MIN_ALPHA) * (percent - LOW_PERCENT) / (HIGH_PERCENT - LOW_PERCENT)
        }
    }
}
