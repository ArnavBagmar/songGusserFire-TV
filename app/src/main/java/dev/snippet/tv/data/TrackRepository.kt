package dev.snippet.tv.data

import android.content.Context
import android.util.Log
import dev.snippet.tv.data.deezer.DeezerClient
import dev.snippet.tv.data.model.Song
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A song whose 30-second preview (and, when available, cover art) is cached locally. */
data class ResolvedTrack(
    val song: Song,
    val previewFile: File,
    val coverFile: File?,
)

sealed interface TrackResolution {
    data class Success(val track: ResolvedTrack) : TrackResolution
    data class Failure(val message: String) : TrackResolution
}

/**
 * Resolves the round's track: local cache first, then one Deezer metadata call
 * and a preview/cover download. Once cached, a round survives a network drop.
 */
class TrackRepository(context: Context, private val client: DeezerClient) {

    private val trackCacheDir = File(context.cacheDir, "tracks")

    fun cached(song: Song): ResolvedTrack? {
        val preview = previewFile(song)
        if (!preview.isFile || preview.length() == 0L) return null
        return ResolvedTrack(song, preview, existingCover(song))
    }

    /**
     * Tries each deterministic candidate in order. A track that is permanently
     * gone from Deezer falls through to the next candidate; a network-level
     * failure aborts so the UI can show a retry state.
     */
    suspend fun resolve(candidates: List<Song>): TrackResolution {
        withContext(Dispatchers.IO) { trackCacheDir.mkdirs() }
        var failureMessage: String? = null
        for (song in candidates) {
            cached(song)?.let { return TrackResolution.Success(it) }
            when (val fetched = client.fetchTrack(song.id)) {
                is DeezerClient.TrackResult.Success -> {
                    val previewOk = client.downloadToFile(
                        fetched.track.preview, previewFile(song), MAX_PREVIEW_BYTES,
                    )
                    if (!previewOk) {
                        failureMessage = "The audio preview could not be downloaded."
                        break
                    }
                    val coverUrl = fetched.track.album?.coverBig?.takeIf { it.startsWith("http") }
                    if (coverUrl != null) {
                        // Cover art is best-effort; the round works without it.
                        client.downloadToFile(coverUrl, coverFile(song), MAX_COVER_BYTES)
                    }
                    return TrackResolution.Success(
                        ResolvedTrack(song, previewFile(song), existingCover(song)),
                    )
                }
                is DeezerClient.TrackResult.NotAvailable -> {
                    Log.w(TAG, "Track ${song.id} unavailable (${fetched.reason}); trying fallback")
                    failureMessage = "This song is no longer available on Deezer."
                }
                is DeezerClient.TrackResult.TransientFailure -> {
                    failureMessage = "Deezer could not be reached. Check the connection and retry."
                    break
                }
            }
        }
        return TrackResolution.Failure(failureMessage ?: "The song could not be loaded.")
    }

    /** Previews/covers from previous days are transient; drop anything older than a week. */
    suspend fun pruneOldCache(maxAgeMs: Long = SEVEN_DAYS_MS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val stale = trackCacheDir.listFiles().orEmpty().filter { it.lastModified() < cutoff }
        for (file in stale) {
            if (!file.delete()) Log.w(TAG, "Could not delete stale cache file ${file.name}")
        }
    }

    private fun previewFile(song: Song) = File(trackCacheDir, "preview_${song.id}.mp3")

    private fun coverFile(song: Song) = File(trackCacheDir, "cover_${song.id}.jpg")

    private fun existingCover(song: Song): File? =
        coverFile(song).takeIf { it.isFile && it.length() > 0 }

    companion object {
        private const val TAG = "TrackRepository"
        private const val MAX_PREVIEW_BYTES = 4L * 1024 * 1024
        private const val MAX_COVER_BYTES = 2L * 1024 * 1024
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
