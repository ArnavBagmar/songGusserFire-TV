package dev.snippet.tv.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snippet playback over the locally cached 30-second preview: always from the
 * start of the file, clipped to the currently unlocked length. Main-thread
 * only, like ExoPlayer itself.
 */
class PreviewPlayer(context: Context) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Snippet playback failed", error)
                _isPlaying.value = false
                _lastError.value = "Playback failed — press replay to try again."
            }
        })
    }

    fun playClip(file: File, clipEndMs: Long, volumePercent: Int) {
        if (!file.isFile || file.length() == 0L) {
            _lastError.value = "The cached preview is missing — retry the round."
            return
        }
        _lastError.value = null
        player.volume = volumePercent.coerceIn(0, 100) / 100f
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(clipEndMs)
                    .build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.seekTo(0)
        player.playWhenReady = true
    }

    fun setVolume(volumePercent: Int) {
        player.volume = volumePercent.coerceIn(0, 100) / 100f
    }

    /** Position within the clip (the clip starts at 0). */
    fun positionMs(): Long = player.currentPosition.coerceAtLeast(0)

    fun stop() {
        player.playWhenReady = false
        player.stop()
    }

    fun release() {
        player.release()
    }

    companion object {
        private const val TAG = "PreviewPlayer"
    }
}
