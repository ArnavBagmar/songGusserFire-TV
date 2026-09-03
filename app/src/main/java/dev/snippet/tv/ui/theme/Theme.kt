package dev.snippet.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Ink-navy background with a mint accent — high contrast for a 10-foot UI. */
object SnippetColors {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF141B29)
    val SurfaceBright = Color(0xFF1D2740)
    val Border = Color(0xFF33405C)
    val Accent = Color(0xFF4EE1A0)
    val OnAccent = Color(0xFF06281B)
    val AccentDim = Color(0xFF2A8F68)
    val Amber = Color(0xFFFFB454)
    val OnAmber = Color(0xFF33200A)
    val Text = Color(0xFFF2F5F9)
    val TextDim = Color(0xFF97A3B6)
    val Danger = Color(0xFFFF6B6B)
    val WrongRed = Color(0xFFE4536B)
    val PipEmpty = Color(0xFF2A3346)
}

@Composable
fun SnippetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SnippetColors.Accent,
            onPrimary = SnippetColors.OnAccent,
            secondary = SnippetColors.Amber,
            onSecondary = SnippetColors.OnAmber,
            background = SnippetColors.Background,
            onBackground = SnippetColors.Text,
            surface = SnippetColors.Surface,
            onSurface = SnippetColors.Text,
            surfaceVariant = SnippetColors.SurfaceBright,
            onSurfaceVariant = SnippetColors.TextDim,
            border = SnippetColors.Border,
            error = SnippetColors.Danger,
        ),
        typography = SnippetTypography,
        content = content,
    )
}
