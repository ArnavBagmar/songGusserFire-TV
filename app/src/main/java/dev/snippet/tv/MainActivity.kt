package dev.snippet.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.snippet.tv.di.AppContainer
import dev.snippet.tv.ui.AppRoot
import dev.snippet.tv.ui.theme.SnippetTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)
        setContent {
            SnippetTheme {
                AppRoot(container)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in MEDIA_REPLAY_KEYS) {
            container.mediaReplayEvents.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        // TV apps lose the foreground rarely, but background audio would be wrong.
        container.stopPlaybackIfActive()
        super.onStop()
    }

    override fun onDestroy() {
        container.release()
        super.onDestroy()
    }

    companion object {
        private val MEDIA_REPLAY_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
        )
    }
}
