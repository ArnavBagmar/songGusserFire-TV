package dev.snippet.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.ui.about.AboutScreen
import dev.snippet.tv.ui.albums.AlbumsScreen
import dev.snippet.tv.ui.game.GameScreen
import dev.snippet.tv.ui.game.RoundSummary
import dev.snippet.tv.ui.home.HomeScreen
import dev.snippet.tv.ui.result.ResultScreen
import dev.snippet.tv.ui.settings.SettingsScreen
import dev.snippet.tv.ui.stats.StatsScreen
import dev.snippet.tv.ui.theme.SnippetColors

sealed interface Screen {
    data object Home : Screen
    data class Game(val tier: DifficultyTier) : Screen
    data class Result(val summary: RoundSummary) : Screen
    data object Stats : Screen
    data object Albums : Screen
    data object Settings : Screen
    data object About : Screen
}

// Overscan safe area for TV panels.
private val OVERSCAN_HORIZONTAL = 48.dp
private val OVERSCAN_VERTICAL = 27.dp

/**
 * Screen switching via a simple back stack — BACK pops one level and exits the
 * app from Home (no handler registered there, so the system default applies).
 */
@Composable
fun AppRoot(container: AppContainer) {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Home)) }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
        colors = SurfaceDefaults.colors(
            containerColor = SnippetColors.Background,
            contentColor = SnippetColors.Text,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OVERSCAN_HORIZONTAL, vertical = OVERSCAN_VERTICAL),
        ) {
            when (val screen = current) {
                Screen.Home -> HomeScreen(
                    container = container,
                    onPlay = { tier -> stack = stack + Screen.Game(tier) },
                    onStats = { stack = stack + Screen.Stats },
                    onSettings = { stack = stack + Screen.Settings },
                    onAbout = { stack = stack + Screen.About },
                )
                is Screen.Game -> GameScreen(
                    container = container,
                    tier = screen.tier,
                    onFinished = { summary -> stack = stack.dropLast(1) + Screen.Result(summary) },
                )
                is Screen.Result -> ResultScreen(
                    container = container,
                    summary = screen.summary,
                    onPlayAgain = {
                        stack = stack.dropLast(1) + Screen.Game(screen.summary.tier)
                    },
                    onBackToHome = { stack = listOf(Screen.Home) },
                )
                Screen.Stats -> StatsScreen(
                    container = container,
                    onAlbums = { stack = stack + Screen.Albums },
                    onBack = { stack = stack.dropLast(1) },
                )
                Screen.Albums -> AlbumsScreen(
                    container = container,
                    onBack = { stack = stack.dropLast(1) },
                )
                Screen.Settings -> SettingsScreen(
                    container = container,
                    onBack = { stack = stack.dropLast(1) },
                )
                Screen.About -> AboutScreen(
                    onBack = { stack = stack.dropLast(1) },
                )
            }
        }
    }
}
