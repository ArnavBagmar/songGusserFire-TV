package dev.snippet.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.snippet.tv.ui.theme.SnippetColors

private val ButtonShape = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(16.dp)

@Composable
fun snippetFocusBorder(shape: Shape): ClickableSurfaceBorder =
    ClickableSurfaceDefaults.border(
        focusedBorder = Border(
            border = BorderStroke(3.dp, SnippetColors.Accent),
            inset = 0.dp,
            shape = shape,
        ),
    )

fun snippetFocusGlow(): ClickableSurfaceGlow =
    ClickableSurfaceDefaults.glow(
        focusedGlow = Glow(
            elevationColor = SnippetColors.Accent.copy(alpha = 0.35f),
            elevation = 12.dp,
        ),
    )

/** Standard focusable button: scale + border + glow on focus, per the 10-foot UI rules. */
@Composable
fun SnippetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(ButtonShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) SnippetColors.AccentDim else SnippetColors.Surface,
            contentColor = SnippetColors.Text,
            focusedContainerColor = if (accent) SnippetColors.Accent else SnippetColors.SurfaceBright,
            focusedContentColor = if (accent) SnippetColors.OnAccent else SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = snippetFocusBorder(ButtonShape),
        glow = snippetFocusGlow(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

/** Focusable card container used on Home and Stats. */
@Composable
fun SnippetCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SnippetColors.Surface,
            contentColor = SnippetColors.Text,
            focusedContainerColor = SnippetColors.SurfaceBright,
            focusedContentColor = SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = snippetFocusBorder(CardShape),
        glow = snippetFocusGlow(),
        content = content,
    )
}

/** Full-screen failure state with a focused retry action. */
@Composable
fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = SnippetColors.TextDim,
        )
        SnippetButton(
            text = "Try again",
            onClick = onRetry,
            modifier = Modifier.focusRequester(retryFocus),
            accent = true,
        )
        Text(
            "Press BACK to return",
            style = MaterialTheme.typography.bodyMedium,
            color = SnippetColors.TextDim,
        )
    }
}
