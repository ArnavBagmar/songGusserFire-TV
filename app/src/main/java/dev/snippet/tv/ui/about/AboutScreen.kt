package dev.snippet.tv.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.snippet.tv.BuildConfig
import dev.snippet.tv.ui.components.SnippetButton
import dev.snippet.tv.ui.theme.SnippetColors

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("About Snippet", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge,
            color = SnippetColors.TextDim,
        )
        Text(
            "Snippet is a song-guessing game. Pick a difficulty and play as many " +
                "rounds as you like — every round draws a fresh song from 1990 to " +
                "today. Difficulty reflects how widely played a song is, from " +
                "global hits down to deep cuts.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Text(
            "Music previews provided by Deezer.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Track metadata, artwork and popularity data are sourced from the public " +
                "Deezer API. Only official 30-second previews are streamed; full songs " +
                "are never downloaded, bundled or stored.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Text(
            "Snippet is an unofficial fan project. It is not affiliated with, endorsed " +
                "by, or sponsored by Deezer.",
            style = MaterialTheme.typography.bodyLarge,
            color = SnippetColors.TextDim,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Spacer(Modifier.weight(1f))
        SnippetButton("Back", onBack, Modifier.focusRequester(backFocus))
    }
}
