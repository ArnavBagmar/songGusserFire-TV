package dev.snippet.tv.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.snippet.tv.data.TierStats
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.game.GameRules
import dev.snippet.tv.ui.components.SnippetButton
import dev.snippet.tv.ui.components.SnippetCard
import dev.snippet.tv.ui.theme.SnippetColors

private val BAR_MAX_HEIGHT = 64.dp

@Composable
fun StatsScreen(container: AppContainer, onAlbums: () -> Unit, onBack: () -> Unit) {
    val stats by container.statsRepository.statsFlow.collectAsState(initial = emptyMap())
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium)
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DifficultyTier.entries.forEach { tier ->
                TierStatsCard(
                    tier = tier,
                    stats = stats[tier] ?: TierStats(),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SnippetButton("Albums", onAlbums)
            SnippetButton("Back", onBack, Modifier.focusRequester(backFocus))
        }
    }
}

@Composable
private fun TierStatsCard(tier: DifficultyTier, stats: TierStats, modifier: Modifier = Modifier) {
    // Cards are focusable (not just decorative) so the D-pad can browse them.
    SnippetCard(onClick = {}, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tier.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${stats.gamesPlayed} played · ${stats.winRatePercent}% won",
                style = MaterialTheme.typography.bodyMedium,
                color = SnippetColors.TextDim,
            )
            Text(
                "Streak ${stats.currentStreak} · best ${stats.maxStreak}",
                style = MaterialTheme.typography.bodyMedium,
                color = SnippetColors.TextDim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Attempts to win",
                style = MaterialTheme.typography.bodyMedium,
                color = SnippetColors.TextDim,
            )
            AttemptBarChart(stats, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AttemptBarChart(stats: TierStats, modifier: Modifier = Modifier) {
    val counts =
        List(GameRules.MAX_ATTEMPTS) { stats.attemptDistribution.getOrElse(it) { 0 } } +
            stats.losses
    val maxCount = maxOf(1, counts.max())
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { index, count ->
            val isLossBar = index >= GameRules.MAX_ATTEMPTS
            val barColor = when {
                count == 0 -> SnippetColors.PipEmpty
                isLossBar -> SnippetColors.WrongRed
                else -> SnippetColors.Accent
            }
            val barHeight = if (count == 0) 4.dp else 8.dp + (BAR_MAX_HEIGHT * count / maxCount)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
                Text(
                    text = if (isLossBar) "✕" else "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SnippetColors.TextDim,
                )
            }
        }
    }
}
