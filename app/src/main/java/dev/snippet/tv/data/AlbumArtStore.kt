package dev.snippet.tv.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps one cover per played song under filesDir (never pruned), because the
 * track cache in cacheDir drops files after a week — the albums stats grid
 * needs artwork to survive long after the round was played.
 */
class AlbumArtStore(context: Context) {

    private val coversDir = File(context.filesDir, "covers")

    fun coverFor(songId: Long): File? =
        coverFile(songId).takeIf { it.isFile && it.length() > 0 }

    /** Best-effort copy; the grid falls back to a text tile when art is missing. */
    suspend fun persist(songId: Long, source: File?) = withContext(Dispatchers.IO) {
        if (source == null || !source.isFile || source.length() == 0L) return@withContext
        val target = coverFile(songId)
        if (target.isFile && target.length() > 0) return@withContext
        try {
            coversDir.mkdirs()
            source.copyTo(target, overwrite = true)
        } catch (e: IOException) {
            Log.w(TAG, "Could not persist cover for song $songId", e)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        for (file in coversDir.listFiles().orEmpty()) {
            if (!file.delete()) Log.w(TAG, "Could not delete cover ${file.name}")
        }
    }

    private fun coverFile(songId: Long) = File(coversDir, "cover_$songId.jpg")

    companion object {
        private const val TAG = "AlbumArtStore"
    }
}
