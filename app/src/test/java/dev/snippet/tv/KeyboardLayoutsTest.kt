package dev.snippet.tv

import dev.snippet.tv.data.KeyboardLayoutOption
import dev.snippet.tv.ui.game.KeyAction
import dev.snippet.tv.ui.game.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutsTest {

    private fun characterKeys(option: KeyboardLayoutOption): List<Char> =
        KeyboardLayouts.rows(option)
            .flatten()
            .mapNotNull { (it.action as? KeyAction.Character)?.char }

    @Test
    fun bothLayoutsIncludeAllLetters() {
        for (option in KeyboardLayoutOption.entries) {
            assertEquals(
                "layout $option letter keys",
                ('A'..'Z').toList(),
                characterKeys(option).filter { it.isLetter() }.sorted(),
            )
        }
    }

    @Test
    fun bothLayoutsIncludeAllDigits() {
        for (option in KeyboardLayoutOption.entries) {
            assertEquals(
                "layout $option digit keys",
                ('0'..'9').toList(),
                characterKeys(option).filter { it.isDigit() }.sorted(),
            )
        }
    }

    @Test
    fun keyLabelsMatchTheirCharacterActions() {
        for (option in KeyboardLayoutOption.entries) {
            KeyboardLayouts.rows(option).flatten().forEach { key ->
                val action = key.action
                if (action is KeyAction.Character) {
                    assertEquals(action.char.toString(), key.label)
                }
            }
        }
    }
}
