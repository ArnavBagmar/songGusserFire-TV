package dev.snippet.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.snippet.tv.data.CatalogState
import dev.snippet.tv.data.StoredRound
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.game.GameRules
import dev.snippet.tv.ui.components.SnippetButton
import dev.snippet.tv.ui.components.SnippetCard
import dev.snippet.tv.ui.theme.SnippetColors

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (DifficultyTier) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val catalogState by container.catalogState.collectAsState()
    val rounds by container.roundStateRepository.roundsFlow.collectAsState(initial = emptyMap())

    val firstCardFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(catalogState) {
        if (catalogState is CatalogState.Ready && !focusedOnce) {
            focusedOnce = true
            firstCardFocus.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column {
            Text(
                "SNIPPET",
                style = MaterialTheme.typography.headlineLarge,
                color = SnippetColors.Accent,
            )
            Text(
                "Guess the song from a snippet — pick a difficulty and play as many " +
                    "rounds as you like.",
                style = MaterialTheme.typography.bodyLarge,
                color = SnippetColors.TextDim,
            )
        }
        Spacer(Modifier.height(28.dp))
        when (val catalog = catalogState) {
            is CatalogState.Loading ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Loading song list…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SnippetColors.TextDim,
                    )
                }
            is CatalogState.Failed ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        catalog.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SnippetColors.Danger,
                    )
                }
            is CatalogState.Ready ->
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DifficultyTier.entries.forEachIndexed { index, tier ->
                        TierCard(
                            tier = tier,
                            lastRound = rounds[tier],
                            onClick = { onPlay(tier) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstCardFocus)
                                    else Modifier,
                                ),
                        )
                    }
                }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnippetButton("Stats", onStats)
            SnippetButton("Settings", onSettings)
            SnippetButton("About", onAbout)
            Spacer(Modifier.weight(1f))
            Text(
                "Music previews provided by Deezer",
                style = MaterialTheme.typography.bodyMedium,
                color = SnippetColors.TextDim,
            )
        }
    }
}

private data class TierStatus(val label: String, val color: Color)

private fun statusFor(round: StoredRound?): TierStatus = when {
    round == null -> TierStatus("Not played yet", SnippetColors.TextDim)
    !round.finished ->
        TierStatus("In progress · ${round.attemptsUsed}/${GameRules.MAX_ATTEMPTS}", SnippetColors.Amber)
    round.won -> TierStatus("Last round · won in ${round.attemptsUsed}", SnippetColors.Accent)
    else -> TierStatus("Last round · lost", SnippetColors.WrongRed)
}

@Composable
private fun TierCard(
    tier: DifficultyTier,
    lastRound: StoredRound?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = statusFor(lastRound)
    SnippetCard(onClick = onClick, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tier.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    tier.bandLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnippetColors.TextDim,
                )
            }
            Text(
                status.label,
                style = MaterialTheme.typography.labelMedium,
                color = status.color,
            )
        }
    }
}
