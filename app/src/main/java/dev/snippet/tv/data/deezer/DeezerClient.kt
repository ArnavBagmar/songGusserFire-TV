package dev.snippet.tv.data.deezer

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal Deezer API client. One metadata call per round plus two small file
 * downloads — far inside Deezer's ~50 requests / 5 s limit — but 429 and quota
 * errors still back off and retry.
 */
class DeezerClient(private val httpClient: OkHttpClient, private val json: Json) {

    sealed interface TrackResult {
        data class Success(val track: DeezerTrackDto) : TrackResult

        /** Permanent for this track (deleted, no preview): fall back to the next candidate. */
        data class NotAvailable(val reason: String) : TrackResult

        /** Network/quota trouble: worth retrying later. */
        data class TransientFailure(val reason: String) : TrackResult
    }

    suspend fun fetchTrack(trackId: Long): TrackResult = withContext(Dispatchers.IO) {
        var lastTransient: TrackResult.TransientFailure? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            when (val result = fetchTrackOnce(trackId)) {
                is TrackResult.Success, is TrackResult.NotAvailable -> return@withContext result
                is TrackResult.TransientFailure -> {
                    Log.w(TAG, "Attempt $attempt/$MAX_ATTEMPTS for track $trackId failed: ${result.reason}")
                    lastTransient = result
                    if (attempt < MAX_ATTEMPTS) delay(BACKOFF_BASE_MS * attempt)
                }
            }
        }
        lastTransient ?: TrackResult.TransientFailure("Could not reach Deezer")
    }

    private fun fetchTrackOnce(trackId: Long): TrackResult {
        val request = Request.Builder()
            .url("$API_BASE/track/$trackId")
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> TrackResult.TransientFailure("Deezer rate limit (HTTP 429)")
                    response.code in 500..599 ->
                        TrackResult.TransientFailure("Deezer server error (HTTP ${response.code})")
                    !response.isSuccessful -> TrackResult.NotAvailable("Deezer returned HTTP ${response.code}")
                    else -> parseTrackBody(response.body?.string().orEmpty())
                }
            }
        } catch (e: IOException) {
            TrackResult.TransientFailure("Network error: ${e.message ?: "unknown"}")
        }
    }

    private fun parseTrackBody(body: String): TrackResult = try {
        val error = json.decodeFromString<DeezerEnvelopeDto>(body).error
        when {
            error != null && error.code == QUOTA_ERROR_CODE ->
                TrackResult.TransientFailure("Deezer quota exceeded")
            error != null -> TrackResult.NotAvailable("Deezer error ${error.code}: ${error.message}")
            else -> {
                val track = json.decodeFromString<DeezerTrackDto>(body)
                when {
                    track.id <= 0 -> TrackResult.NotAvailable("Deezer returned an empty track")
                    !track.preview.startsWith("http") ->
                        TrackResult.NotAvailable("No preview available for this track")
                    else -> TrackResult.Success(track)
                }
            }
        }
    } catch (e: SerializationException) {
        TrackResult.NotAvailable("Unexpected Deezer response: ${e.message ?: "parse error"}")
    }

    /**
     * Streams a URL into [destination] via a temp file so a torn download never
     * leaves a corrupt cache entry. Returns false on any failure.
     */
    suspend fun downloadToFile(url: String, destination: File, maxBytes: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!url.startsWith("http")) return@withContext false
            val temp = File(destination.parentFile, "${destination.name}.tmp")
            try {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                val complete = httpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        false
                    } else {
                        temp.outputStream().use { output ->
                            copyBounded(body.byteStream(), output, maxBytes)
                        }
                    }
                }
                complete && temp.length() > 0 && temp.renameTo(destination)
            } catch (e: IOException) {
                Log.w(TAG, "Download failed for $url", e)
                false
            } finally {
                if (temp.exists() && !temp.delete()) {
                    Log.w(TAG, "Could not remove temp file ${temp.name}")
                }
            }
        }

    private fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long): Boolean {
        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return true
            total += read
            if (total > maxBytes) return false
            output.write(buffer, 0, read)
        }
    }

    companion object {
        private const val TAG = "DeezerClient"
        private const val API_BASE = "https://api.deezer.com"
        private const val USER_AGENT = "SnippetTV/1.0"
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_BASE_MS = 1_500L
        private const val QUOTA_ERROR_CODE = 4
        private const val DOWNLOAD_BUFFER_BYTES = 16 * 1024
    }
}
