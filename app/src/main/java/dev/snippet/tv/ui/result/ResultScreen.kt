package dev.snippet.tv.ui.result

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.snippet.tv.data.GuessKind
import dev.snippet.tv.data.StoredGuess
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.game.GameRules
import dev.snippet.tv.ui.components.SnippetButton
import dev.snippet.tv.ui.game.RoundSummary
import dev.snippet.tv.ui.theme.SnippetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(
    container: AppContainer,
    summary: RoundSummary,
    onPlayAgain: () -> Unit,
    onBackToHome: () -> Unit,
) {
    val playAgainFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playAgainFocus.requestFocus() }

    // Reward reveal: the round is over, so play the whole preview uninterrupted.
    LaunchedEffect(summary.previewFile) {
        val previewFile = summary.previewFile ?: return@LaunchedEffect
        val volume = container.settingsRepository.settingsFlow.first().volumePercent
        container.player.playClip(previewFile, GameRules.PREVIEW_LENGTH_MS, volume)
    }
    DisposableEffect(Unit) {
        onDispose { container.stopPlaybackIfActive() }
    }

    var cover by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(summary.coverFile) {
        cover = summary.coverFile?.let { file ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                    .getOrNull()
            }
        }
    }

    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SnippetColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            val artwork = cover
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = "Album artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "No artwork",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SnippetColors.TextDim,
                )
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (summary.won) "You got it!" else "Out of guesses",
                style = MaterialTheme.typography.headlineMedium,
                color = if (summary.won) SnippetColors.Accent else SnippetColors.WrongRed,
            )
            Text(summary.song.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                summary.song.artist,
                style = MaterialTheme.typography.titleMedium,
                color = SnippetColors.TextDim,
            )
            Text(
                text =
                    if (summary.won) {
                        "${summary.tier.displayName} · solved in ${summary.attemptsUsed} of " +
                            "${GameRules.MAX_ATTEMPTS}"
                    } else {
                        "${summary.tier.displayName} · not solved this time"
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
            ResultGrid(summary.guesses)
            Text(
                "Current streak ${summary.stats.currentStreak} · " +
                    "Best ${summary.stats.maxStreak} · " +
                    "Win rate ${summary.stats.winRatePercent}%",
                style = MaterialTheme.typography.bodyLarge,
                color = SnippetColors.TextDim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SnippetButton(
                    text = "Play again",
                    onClick = onPlayAgain,
                    modifier = Modifier.focusRequester(playAgainFocus),
                    accent = true,
                )
                SnippetButton(
                    text = "Back to home",
                    onClick = onBackToHome,
                )
            }
        }
    }
}

/** Wordle-style share grid, rendered on screen (no share sheet on a TV). */
@Composable
private fun ResultGrid(guesses: List<StoredGuess>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(GameRules.MAX_ATTEMPTS) { index ->
            val color = when (guesses.getOrNull(index)?.kind) {
                GuessKind.CORRECT -> SnippetColors.Accent
                GuessKind.WRONG -> SnippetColors.WrongRed
                GuessKind.SKIP -> SnippetColors.Amber
                null -> SnippetColors.PipEmpty
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
            )
        }
    }
}
