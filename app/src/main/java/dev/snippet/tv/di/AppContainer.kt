package dev.snippet.tv.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dev.snippet.tv.audio.PreviewPlayer
import dev.snippet.tv.data.CatalogState
import dev.snippet.tv.data.RoundStateRepository
import dev.snippet.tv.data.SettingsRepository
import dev.snippet.tv.data.SongCatalogLoader
import dev.snippet.tv.data.StatsRepository
import dev.snippet.tv.data.TrackRepository
import dev.snippet.tv.data.deezer.DeezerClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

private val Context.snippetDataStore: DataStore<Preferences> by preferencesDataStore(name = "snippet")

/** Hand-rolled dependency container; one instance per activity lifetime. */
class AppContainer(appContext: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val dataStore = appContext.snippetDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(dataStore)
    val statsRepository = StatsRepository(dataStore, json)
    val roundStateRepository = RoundStateRepository(dataStore, json)
    val trackRepository = TrackRepository(appContext, DeezerClient(httpClient, json))

    val catalogState: StateFlow<CatalogState> =
        flow { emit(SongCatalogLoader(appContext, json).load()) }
            .stateIn(scope, SharingStarted.Lazily, CatalogState.Loading)

    /** Replay requests from the remote's play/pause media keys. */
    val mediaReplayEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Created lazily so construction happens on the main thread (ExoPlayer requirement).
    private val playerDelegate = lazy { PreviewPlayer(appContext) }
    val player: PreviewPlayer get() = playerDelegate.value

    init {
        scope.launch { trackRepository.pruneOldCache() }
    }

    fun stopPlaybackIfActive() {
        if (playerDelegate.isInitialized()) playerDelegate.value.stop()
    }

    fun release() {
        if (playerDelegate.isInitialized()) playerDelegate.value.release()
        scope.cancel()
    }
}
