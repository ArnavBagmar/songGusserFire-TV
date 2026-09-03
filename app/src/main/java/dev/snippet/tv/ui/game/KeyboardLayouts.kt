package dev.snippet.tv.ui.game

import dev.snippet.tv.data.KeyboardLayoutOption

sealed interface KeyAction {
    data class Character(val char: Char) : KeyAction
    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Submit : KeyAction
    data object Replay : KeyAction
    data object SkipSong : KeyAction
}

data class KeyDef(
    val label: String,
    val action: KeyAction,
    val widthUnits: Float = 1f,
)

/**
 * Both layouts include the round actions (replay/skip/submit) as grid keys, so
 * with wrap-around navigation every key stays within 6 D-pad presses of any
 * other: alphabetical is 5 rows x 7 columns (3 + 2 = 5 presses worst case),
 * QWERTY is 3 rows x 11 columns (5 + 1 = 6).
 */
object KeyboardLayouts {

    fun rows(option: KeyboardLayoutOption): List<List<KeyDef>> = when (option) {
        KeyboardLayoutOption.ALPHABETICAL -> alphabetical
        KeyboardLayoutOption.QWERTY -> qwerty
    }

    private fun letters(sequence: String): List<KeyDef> =
        sequence.map { KeyDef(it.toString(), KeyAction.Character(it)) }

    private val alphabetical: List<List<KeyDef>> = listOf(
        letters("ABCDEFG"),
        letters("HIJKLMN"),
        letters("OPQRSTU"),
        letters("VWXYZ") + listOf(
            KeyDef("SPACE", KeyAction.Space),
            KeyDef("DEL", KeyAction.Backspace),
        ),
        listOf(
            KeyDef("REPLAY", KeyAction.Replay, widthUnits = 2f),
            KeyDef("SKIP", KeyAction.SkipSong, widthUnits = 2f),
            KeyDef("GUESS", KeyAction.Submit, widthUnits = 3f),
        ),
    )

    private val qwerty: List<List<KeyDef>> = listOf(
        letters("QWERTYUIOP"),
        letters("ASDFGHJKL") + listOf(
            KeyDef("DEL", KeyAction.Backspace, widthUnits = 1.4f),
        ),
        listOf(
            KeyDef("PLAY", KeyAction.Replay, widthUnits = 1.4f),
            KeyDef("SKIP", KeyAction.SkipSong, widthUnits = 1.4f),
        ) + letters("ZXCVBNM") + listOf(
            KeyDef("SPACE", KeyAction.Space, widthUnits = 1.6f),
            KeyDef("GUESS", KeyAction.Submit, widthUnits = 1.6f),
        ),
    )
}
