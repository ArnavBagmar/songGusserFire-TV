package dev.snippet.tv.ui.albums

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.snippet.tv.data.SongPlayStats
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.ui.components.SnippetButton
import dev.snippet.tv.ui.components.snippetFocusBorder
import dev.snippet.tv.ui.components.snippetFocusGlow
import dev.snippet.tv.ui.theme.SnippetColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GRID_COLUMNS = 6
private val TileShape = RoundedCornerShape(10.dp)

/** Album wall: every finished song, its cover fading with how rarely it gets guessed. */
@Composable
fun AlbumsScreen(container: AppContainer, onBack: () -> Unit) {
    val songStats by container.songStatsRepository.statsFlow.collectAsState(initial = emptyMap())
    val entries = remember(songStats) {
        songStats.values.sortedWith(
            compareByDescending<SongPlayStats> { it.plays }.thenBy { it.title },
        )
    }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Albums", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Every song you've finished a round on — the clearer the cover, " +
                "the more often you guess it right.",
            style = MaterialTheme.typography.bodyMedium,
            color = SnippetColors.TextDim,
        )
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No albums yet — finish a round and it will show up here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SnippetColors.TextDim,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(entries, key = { it.songId }) { entry ->
                    AlbumTile(entry, container.albumArtStore.coverFor(entry.songId))
                }
            }
        }
        SnippetButton("Back", onBack, Modifier.focusRequester(backFocus))
    }
}

@Composable
private fun AlbumTile(entry: SongPlayStats, coverFile: File?) {
    var focused by remember { mutableStateOf(false) }
    val cover = rememberCoverBitmap(coverFile)
    val artAlpha = AlbumTileAlpha.alphaFor(entry.winRatePercent)
    Surface(
        onClick = {},
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SnippetColors.Surface,
            contentColor = SnippetColors.Text,
            focusedContainerColor = SnippetColors.SurfaceBright,
            focusedContentColor = SnippetColors.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = snippetFocusBorder(TileShape),
        glow = snippetFocusGlow(),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = "${entry.title} album artwork",
                        modifier = Modifier.fillMaxSize().alpha(artAlpha),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SnippetColors.TextDim,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp).alpha(artAlpha),
                    )
                }
                // Focus reveals which song a faint tile belongs to.
                if (focused) {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(SnippetColors.Background.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            entry.artist,
                            style = MaterialTheme.typography.labelMedium,
                            color = SnippetColors.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Text(
                    "✓${entry.wins}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SnippetColors.Accent,
                )
                Text(
                    "✕${entry.losses}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SnippetColors.WrongRed,
                )
            }
        }
    }
}

@Composable
private fun rememberCoverBitmap(file: File?): ImageBitmap? {
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = file?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    // Covers are ~500px; half resolution is plenty for a grid tile.
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(it.absolutePath, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}
