package dev.snippet.tv.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.snippet.tv.data.GuessKind
import dev.snippet.tv.data.StoredGuess
import dev.snippet.tv.game.GameRules
import dev.snippet.tv.game.Suggestion
import dev.snippet.tv.ui.components.snippetFocusBorder
import dev.snippet.tv.ui.components.snippetFocusGlow
import dev.snippet.tv.ui.theme.SnippetColors

/** Big circular play/replay control — the game screen's landmark element. */
@Composable
fun PlayControl(
    isPlaying: Boolean,
    onReplay: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    Surface(
        onClick = onReplay,
        modifier = base
            .size(140.dp)
            .onPreviewKeyEvent { event ->
                val exits = event.key == Key.DirectionRight || event.key == Key.DirectionDown
                if (exits && event.type == KeyEventType.KeyDown) {
                    onNavigateToKeyboard()
                    true
                } else {
                    false
                }
            },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SnippetColors.AccentDim,
            contentColor = SnippetColors.Text,
            focusedContainerColor = SnippetColors.Accent,
            focusedContentColor = SnippetColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = snippetFocusBorder(CircleShape),
        glow = snippetFocusGlow(),
    ) {
        val glyphColor = LocalContentColor.current
        Canvas(modifier = Modifier.size(52.dp).align(Alignment.Center)) {
            if (isPlaying) {
                val barWidth = size.width * 0.28f
                drawRect(
                    color = glyphColor,
                    topLeft = Offset(size.width * 0.08f, 0f),
                    size = Size(barWidth, size.height),
                )
                drawRect(
                    color = glyphColor,
                    topLeft = Offset(size.width * 0.64f, 0f),
                    size = Size(barWidth, size.height),
                )
            } else {
                val triangle = Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    lineTo(size.width * 0.18f, size.height)
                    lineTo(size.width, size.height / 2f)
                    close()
                }
                drawPath(triangle, glyphColor)
            }
        }
    }
}

/** One pip per attempt: red = wrong, amber = skip, mint = correct, ring = current. */
@Composable
fun AttemptPips(guesses: List<StoredGuess>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(GameRules.MAX_ATTEMPTS) { index ->
            val guess = guesses.getOrNull(index)
            val color = when (guess?.kind) {
                GuessKind.CORRECT -> SnippetColors.Accent
                GuessKind.WRONG -> SnippetColors.WrongRed
                GuessKind.SKIP -> SnippetColors.Amber
                null -> SnippetColors.PipEmpty
            }
            val isCurrent = index == guesses.size
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isCurrent) Modifier.border(2.dp, SnippetColors.Accent, CircleShape)
                        else Modifier,
                    ),
            )
        }
    }
}

/** Snippet timeline: the full bar is the unlocked length, so it fills completely. */
@Composable
fun SnippetProgressBar(unlockedMs: Long, positionMs: Long, modifier: Modifier = Modifier) {
    val positionFraction =
        if (unlockedMs <= 0L) 0f
        else (positionMs.toFloat() / unlockedMs).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SnippetColors.PipEmpty),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(positionFraction)
                .background(SnippetColors.Accent),
        )
    }
}

/** All six snippet lengths at a glance: spent steps fade, the current one glows. */
@Composable
fun SnippetLadder(attemptIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameRules.SNIPPET_STEPS_MS.forEachIndexed { index, stepMs ->
            val (background, textColor) = when {
                index == attemptIndex -> SnippetColors.Accent to SnippetColors.OnAccent
                index < attemptIndex ->
                    SnippetColors.PipEmpty to SnippetColors.TextDim.copy(alpha = 0.5f)
                else -> SnippetColors.SurfaceBright to SnippetColors.TextDim
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(background)
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    GameRules.formatSnippetSeconds(stepMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Read-only guess field (never summons the system IME — typing happens on the grid). */
@Composable
fun GuessDisplay(typed: String, lastWrongGuess: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(
                containerColor = SnippetColors.Surface,
                contentColor = SnippetColors.Text,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = typed.ifEmpty { "Type your guess with the on-screen keys" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (typed.isEmpty()) SnippetColors.TextDim else SnippetColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .width(3.dp)
                        .height(26.dp)
                        .background(SnippetColors.Accent),
                )
            }
        }
        if (lastWrongGuess != null) {
            Text(
                text = "Not it: “$lastWrongGuess”",
                style = MaterialTheme.typography.bodyMedium,
                color = SnippetColors.WrongRed,
            )
        }
    }
}

/**
 * Autocomplete results to the right of the keyboard. UP/DOWN move within the
 * list (clamped at both ends so focus cannot leak) and scroll it when the
 * focused row leaves the viewport; a slim scrollbar appears on the right edge
 * whenever there are more matches than fit. LEFT returns to the keyboard,
 * select submits the suggestion as an attempt.
 */
@Composable
fun SuggestionColumn(
    suggestions: List<Suggestion>,
    firstItemFocusRequester: FocusRequester,
    onChoose: (Suggestion) -> Unit,
    onExitLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Fresh results restart at the top so the first row is composed and focusable.
    LaunchedEffect(suggestions) { listState.scrollToItem(0) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Matches",
            style = MaterialTheme.typography.labelMedium,
            color = SnippetColors.TextDim,
        )
        Box {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            ) {
                itemsIndexed(suggestions) { index, suggestion ->
                    SuggestionItem(
                        suggestion = suggestion,
                        index = index,
                        lastIndex = suggestions.lastIndex,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onChoose = onChoose,
                        onExitLeft = onExitLeft,
                    )
                }
            }
            ListScrollbar(listState, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: Suggestion,
    index: Int,
    lastIndex: Int,
    firstItemFocusRequester: FocusRequester,
    onChoose: (Suggestion) -> Unit,
    onExitLeft: () -> Unit,
) {
    val itemShape = RoundedCornerShape(10.dp)
    Surface(
        onClick = { onChoose(suggestion) },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (index == 0) Modifier.focusRequester(firstItemFocusRequester)
                else Modifier,
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onExitLeft()
                        true
                    }
                    Key.DirectionUp -> index == 0
                    Key.DirectionDown -> index == lastIndex
                    Key.DirectionRight -> true
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(itemShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SnippetColors.Surface,
            contentColor = SnippetColors.Text,
            focusedContainerColor = SnippetColors.SurfaceBright,
            focusedContentColor = SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = snippetFocusBorder(itemShape),
        glow = snippetFocusGlow(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                suggestion.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                suggestion.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = SnippetColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Track + thumb on the right edge; drawn only when the list overflows. */
@Composable
private fun ListScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total == 0 || visible >= total) return@Canvas
        val barWidth = 4.dp.toPx()
        val left = size.width - barWidth
        val thumbHeight = (size.height * visible / total).coerceAtLeast(24.dp.toPx())
        val progress =
            (listState.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
        val thumbTop = (size.height - thumbHeight) * progress
        val corner = CornerRadius(barWidth / 2f)
        drawRoundRect(
            color = SnippetColors.PipEmpty,
            topLeft = Offset(left, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = SnippetColors.TextDim,
            topLeft = Offset(left, thumbTop),
            size = Size(barWidth, thumbHeight),
            cornerRadius = corner,
        )
    }
}
