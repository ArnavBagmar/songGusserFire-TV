package dev.snippet.tv.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.snippet.tv.data.CatalogState
import dev.snippet.tv.data.GuessKind
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.game.GameRules
import dev.snippet.tv.ui.components.ErrorPanel
import dev.snippet.tv.ui.theme.SnippetColors
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    container: AppContainer,
    tier: DifficultyTier,
    onFinished: (RoundSummary) -> Unit,
) {
    val catalogState by container.catalogState.collectAsState()
    val readyCatalog = catalogState as? CatalogState.Ready
    if (readyCatalog == null) {
        // Home only navigates here once the catalog is ready; this is a guard.
        CenteredMessage("Loading song list…")
        return
    }

    val controller = remember(tier) {
        GameController(
            tier = tier,
            catalog = readyCatalog.catalog,
            autocomplete = readyCatalog.autocomplete,
            trackRepository = container.trackRepository,
            roundRepository = container.roundStateRepository,
            statsRepository = container.statsRepository,
            settingsRepository = container.settingsRepository,
            player = container.player,
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    val state by controller.state.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val playbackError by controller.playbackError.collectAsState()

    LaunchedEffect(state.phase) {
        (state.phase as? GameController.Phase.Finished)?.let { onFinished(it.summary) }
    }
    LaunchedEffect(controller) {
        container.mediaReplayEvents.collect { controller.replay() }
    }

    when (val phase = state.phase) {
        is GameController.Phase.Loading, is GameController.Phase.Finished ->
            CenteredMessage("Loading a ${tier.displayName} song…")
        is GameController.Phase.Failed ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorPanel(message = phase.message, onRetry = controller::retryLoad)
            }
        is GameController.Phase.Ready ->
            GameContent(controller, state, isPlaying, playbackError)
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = SnippetColors.TextDim)
    }
}

@Composable
private fun GameContent(
    controller: GameController,
    state: GameController.State,
    isPlaying: Boolean,
    playbackError: String?,
) {
    val rows = remember(state.settings.keyboardLayout) {
        KeyboardLayouts.rows(state.settings.keyboardLayout)
    }
    val keyboardFocus = rememberKeyboardFocusState(rows)
    val playFocus = remember { FocusRequester() }
    val firstSuggestionFocus = remember { FocusRequester() }

    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialFocusDone) {
            initialFocusDone = true
            playFocus.requestFocus()
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                positionMs = controller.playbackPositionMs()
                delay(80)
            }
        } else {
            positionMs = 0L
        }
    }

    val lastWrongGuess = state.guesses.lastOrNull()
        ?.takeIf { it.kind == GuessKind.WRONG }
        ?.text?.takeIf { it.isNotBlank() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "SNIPPET",
                    style = MaterialTheme.typography.titleLarge,
                    color = SnippetColors.Accent,
                )
                Text(
                    "${controller.tier.displayName} · ${controller.tier.bandLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnippetColors.TextDim,
                )
            }
            AttemptPips(state.guesses)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Column(
                Modifier.width(280.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlayControl(
                    isPlaying = isPlaying,
                    onReplay = controller::replay,
                    onNavigateToKeyboard = keyboardFocus::refocusLast,
                    focusRequester = playFocus,
                )
                Text(
                    text =
                        if (isPlaying) "Playing…"
                        else "Replay the ${GameRules.formatSnippetSeconds(state.unlockedMs)} snippet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnippetColors.TextDim,
                    textAlign = TextAlign.Center,
                )
                SnippetProgressBar(state.unlockedMs, positionMs, Modifier.fillMaxWidth())
                SnippetLadder(state.attemptIndex, Modifier.fillMaxWidth())
                Text(
                    text =
                        if (state.attemptIndex < GameRules.MAX_ATTEMPTS - 1) {
                            val next = GameRules.snippetMsForAttempt(state.attemptIndex + 1)
                            "A miss or skip unlocks ${GameRules.formatSnippetSeconds(next)}"
                        } else {
                            "Final attempt — ${GameRules.formatSnippetSeconds(state.unlockedMs)} unlocked"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnippetColors.TextDim,
                    textAlign = TextAlign.Center,
                )
                if (state.guesses.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.guesses.forEach { guess ->
                            val (label, color) = when (guess.kind) {
                                GuessKind.WRONG -> "✕ ${guess.text}" to SnippetColors.WrongRed
                                GuessKind.SKIP -> "Skipped" to SnippetColors.Amber
                                GuessKind.CORRECT -> "✓ ${guess.text}" to SnippetColors.Accent
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = color,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (playbackError != null) {
                    Text(
                        playbackError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SnippetColors.Danger,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "Music previews provided by Deezer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnippetColors.TextDim,
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GuessDisplay(state.typed, lastWrongGuess, Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DpadKeyboard(
                        rows = rows,
                        focusState = keyboardFocus,
                        onAction = { action -> handleKeyAction(controller, action) },
                        onClearAll = controller::clearTyped,
                        onExitRight = {
                            // The first row may be scrolled out of composition, in
                            // which case its focus requester isn't attached yet.
                            state.suggestions.isNotEmpty() &&
                                runCatching { firstSuggestionFocus.requestFocus() }.isSuccess
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.suggestions.isNotEmpty()) {
                        SuggestionColumn(
                            suggestions = state.suggestions,
                            firstItemFocusRequester = firstSuggestionFocus,
                            onChoose = { suggestion ->
                                // Return focus to the grid before the list disappears.
                                keyboardFocus.refocusLast()
                                controller.submitSuggestion(suggestion)
                            },
                            onExitLeft = keyboardFocus::refocusLast,
                            modifier = Modifier.width(300.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun handleKeyAction(controller: GameController, action: KeyAction) {
    when (action) {
        is KeyAction.Character -> controller.typeCharacter(action.char)
        KeyAction.Space -> controller.typeSpace()
        KeyAction.Backspace -> controller.backspace()
        KeyAction.Submit -> controller.submitTyped()
        KeyAction.Replay -> controller.replay()
        KeyAction.SkipSong -> controller.skip()
    }
}
