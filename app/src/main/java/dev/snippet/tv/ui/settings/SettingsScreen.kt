package dev.snippet.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import dev.snippet.tv.data.AppSettings
import dev.snippet.tv.data.KeyboardLayoutOption
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.ui.components.SnippetButton
import dev.snippet.tv.ui.components.snippetFocusBorder
import dev.snippet.tv.ui.components.snippetFocusGlow
import dev.snippet.tv.ui.theme.SnippetColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settingsRepository.settingsFlow
        .collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    var confirmingReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    var confirmShownOnce by remember { mutableStateOf(false) }

    val firstRowFocus = remember { FocusRequester() }
    val resetRowFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRowFocus.requestFocus() }
    LaunchedEffect(confirmingReset) {
        // The confirm row replaces the reset row; move focus with it so it is never lost.
        if (confirmingReset) {
            confirmShownOnce = true
            cancelFocus.requestFocus()
        } else if (confirmShownOnce) {
            resetRowFocus.requestFocus()
        }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        SettingRow(
            label = "Keyboard layout",
            value = settings.keyboardLayout.displayName,
            modifier = Modifier.focusRequester(firstRowFocus),
        ) {
            val next =
                if (settings.keyboardLayout == KeyboardLayoutOption.ALPHABETICAL) {
                    KeyboardLayoutOption.QWERTY
                } else {
                    KeyboardLayoutOption.ALPHABETICAL
                }
            scope.launch { container.settingsRepository.setKeyboardLayout(next) }
        }
        SettingRow(
            label = "Autocomplete",
            value = if (settings.autocompleteEnabled) "On" else "Off",
        ) {
            scope.launch {
                container.settingsRepository.setAutocompleteEnabled(!settings.autocompleteEnabled)
            }
        }
        VolumeRow(settings.volumePercent) { newPercent ->
            scope.launch { container.settingsRepository.setVolumePercent(newPercent) }
        }
        if (!confirmingReset) {
            SettingRow(
                label = "Reset local stats",
                value = if (resetDone) "Done" else "",
                modifier = Modifier.focusRequester(resetRowFocus),
            ) {
                confirmingReset = true
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Erase all local stats?", style = MaterialTheme.typography.bodyLarge)
                SnippetButton(
                    text = "Cancel",
                    onClick = { confirmingReset = false },
                    modifier = Modifier.focusRequester(cancelFocus),
                )
                SnippetButton(
                    text = "Reset everything",
                    onClick = {
                        scope.launch {
                            container.statsRepository.resetAll()
                            container.songStatsRepository.resetAll()
                            container.albumArtStore.clear()
                        }
                        resetDone = true
                        confirmingReset = false
                    },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        SnippetButton("Back", onBack)
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SnippetColors.Surface,
            contentColor = SnippetColors.Text,
            focusedContainerColor = SnippetColors.SurfaceBright,
            focusedContentColor = SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = snippetFocusBorder(shape),
        glow = snippetFocusGlow(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = SnippetColors.Accent,
            )
        }
    }
}

@Composable
private fun VolumeRow(volumePercent: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { /* Adjusted with LEFT/RIGHT while focused. */ },
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onChange((volumePercent - AppSettings.VOLUME_STEP).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight -> {
                        onChange((volumePercent + AppSettings.VOLUME_STEP).coerceAtMost(100))
                        true
                    }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SnippetColors.Surface,
            contentColor = SnippetColors.Text,
            focusedContainerColor = SnippetColors.SurfaceBright,
            focusedContentColor = SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = snippetFocusBorder(shape),
        glow = snippetFocusGlow(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Volume (LEFT / RIGHT)", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(10) { index ->
                    Box(
                        Modifier
                            .width(14.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index < volumePercent / 10) SnippetColors.Accent
                                else SnippetColors.PipEmpty,
                            ),
                    )
                }
                Text(
                    "  $volumePercent%",
                    style = MaterialTheme.typography.titleMedium,
                    color = SnippetColors.Accent,
                )
            }
        }
    }
}
