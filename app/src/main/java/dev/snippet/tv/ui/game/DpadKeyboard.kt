package dev.snippet.tv.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.snippet.tv.ui.components.snippetFocusBorder
import dev.snippet.tv.ui.components.snippetFocusGlow
import dev.snippet.tv.ui.theme.SnippetColors

private val KeyShape = RoundedCornerShape(8.dp)
private val KEY_GAP = 8.dp
private val KEY_HEIGHT = 52.dp

/** Holding select on Backspace this many auto-repeats clears the whole field. */
private const val LONG_PRESS_REPEATS = 5

/** Focus bookkeeping for the grid: one requester per key plus the last focused cell. */
class KeyboardFocusState(rows: List<List<KeyDef>>) {
    val requesters: List<List<FocusRequester>> = rows.map { row -> row.map { FocusRequester() } }

    var lastRow = 0
        private set
    var lastCol = 0
        private set

    fun noteFocused(row: Int, col: Int) {
        lastRow = row
        lastCol = col
    }

    fun focus(row: Int, col: Int) {
        val r = row.coerceIn(0, requesters.lastIndex)
        val c = col.coerceIn(0, requesters[r].lastIndex)
        requesters[r][c].requestFocus()
    }

    fun refocusLast() = focus(lastRow, lastCol)
}

@Composable
fun rememberKeyboardFocusState(rows: List<List<KeyDef>>): KeyboardFocusState =
    remember(rows) { KeyboardFocusState(rows) }

private enum class Direction { LEFT, RIGHT, UP, DOWN }

/**
 * D-pad grid keyboard. Movement wraps on both axes (so every key is reachable
 * within 6 presses), except that RIGHT from the last column offers focus to the
 * autocomplete list first via [onExitRight]. Hardware key auto-repeat drives
 * hold-to-move for free because navigation runs on every KeyDown.
 */
@Composable
fun DpadKeyboard(
    rows: List<List<KeyDef>>,
    focusState: KeyboardFocusState,
    onAction: (KeyAction) -> Unit,
    onClearAll: () -> Unit,
    onExitRight: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
            ) {
                row.forEachIndexed { colIndex, key ->
                    KeyboardKeyCell(
                        key = key,
                        modifier = Modifier.weight(key.widthUnits),
                        focusRequester = focusState.requesters[rowIndex][colIndex],
                        onFocused = { focusState.noteFocused(rowIndex, colIndex) },
                        onNavigate = { direction ->
                            navigate(rows, focusState, rowIndex, colIndex, direction, onExitRight)
                        },
                        onPress = { onAction(key.action) },
                        onLongPress = if (key.action == KeyAction.Backspace) onClearAll else null,
                    )
                }
            }
        }
    }
}

private fun navigate(
    rows: List<List<KeyDef>>,
    focusState: KeyboardFocusState,
    row: Int,
    col: Int,
    direction: Direction,
    onExitRight: () -> Boolean,
): Boolean {
    val rowCount = rows.size
    when (direction) {
        Direction.LEFT ->
            focusState.focus(row, if (col == 0) rows[row].lastIndex else col - 1)
        Direction.RIGHT ->
            if (col == rows[row].lastIndex) {
                if (!onExitRight()) focusState.focus(row, 0)
            } else {
                focusState.focus(row, col + 1)
            }
        Direction.UP -> focusState.focus((row - 1 + rowCount) % rowCount, col)
        Direction.DOWN -> focusState.focus((row + 1) % rowCount, col)
    }
    return true
}

private fun isSelectKey(key: Key): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

@Composable
private fun KeyboardKeyCell(
    key: KeyDef,
    modifier: Modifier,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onNavigate: (Direction) -> Boolean,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    // Set while a long-press clear has fired, so the eventual click is swallowed.
    var longPressFired by remember { mutableStateOf(false) }
    val isActionKey = key.action !is KeyAction.Character
    val isSubmit = key.action == KeyAction.Submit
    Surface(
        onClick = {
            if (longPressFired) longPressFired = false else onPress()
        },
        modifier = modifier
            .height(KEY_HEIGHT)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> Direction.LEFT
                    Key.DirectionRight -> Direction.RIGHT
                    Key.DirectionUp -> Direction.UP
                    Key.DirectionDown -> Direction.DOWN
                    else -> null
                }
                when {
                    direction != null && event.type == KeyEventType.KeyDown -> onNavigate(direction)
                    direction != null -> true
                    onLongPress != null && isSelectKey(event.key) &&
                        event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount >= LONG_PRESS_REPEATS -> {
                        if (!longPressFired) {
                            longPressFired = true
                            onLongPress()
                        }
                        true
                    }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(KeyShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isSubmit -> SnippetColors.AccentDim
                isActionKey -> SnippetColors.SurfaceBright
                else -> SnippetColors.Surface
            },
            contentColor = SnippetColors.Text,
            focusedContainerColor = if (isSubmit) SnippetColors.Accent else SnippetColors.SurfaceBright,
            focusedContentColor = if (isSubmit) SnippetColors.OnAccent else SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = snippetFocusBorder(KeyShape),
        glow = snippetFocusGlow(),
    ) {
        Text(
            text = key.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 2.dp),
            maxLines = 1,
        )
    }
}
