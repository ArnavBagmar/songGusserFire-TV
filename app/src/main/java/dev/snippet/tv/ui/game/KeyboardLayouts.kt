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
 * Both layouts include digits (some titles contain them) and the round actions
 * (replay/skip/submit) as grid keys, so with wrap-around navigation every key
 * stays within 8 D-pad presses of any other: alphabetical is 6 rows x up to 10
 * columns (3 + 5 = 8 presses worst case), QWERTY is 4 rows x 11 columns
 * (2 + 5 = 7).
 */
object KeyboardLayouts {

    fun rows(option: KeyboardLayoutOption): List<List<KeyDef>> = when (option) {
        KeyboardLayoutOption.ALPHABETICAL -> alphabetical
        KeyboardLayoutOption.QWERTY -> qwerty
    }

    private fun chars(sequence: String): List<KeyDef> =
        sequence.map { KeyDef(it.toString(), KeyAction.Character(it)) }

    private val alphabetical: List<List<KeyDef>> = listOf(
        chars("ABCDEFG"),
        chars("HIJKLMN"),
        chars("OPQRSTU"),
        chars("VWXYZ") + listOf(
            KeyDef("SPACE", KeyAction.Space),
            KeyDef("DEL", KeyAction.Backspace),
        ),
        chars("1234567890"),
        listOf(
            KeyDef("REPLAY", KeyAction.Replay, widthUnits = 2f),
            KeyDef("SKIP", KeyAction.SkipSong, widthUnits = 2f),
            KeyDef("GUESS", KeyAction.Submit, widthUnits = 3f),
        ),
    )

    private val qwerty: List<List<KeyDef>> = listOf(
        chars("1234567890"),
        chars("QWERTYUIOP"),
        chars("ASDFGHJKL") + listOf(
            KeyDef("DEL", KeyAction.Backspace, widthUnits = 1.4f),
        ),
        listOf(
            KeyDef("PLAY", KeyAction.Replay, widthUnits = 1.4f),
            KeyDef("SKIP", KeyAction.SkipSong, widthUnits = 1.4f),
        ) + chars("ZXCVBNM") + listOf(
            KeyDef("SPACE", KeyAction.Space, widthUnits = 1.6f),
            KeyDef("GUESS", KeyAction.Submit, widthUnits = 1.6f),
        ),
    )
}
