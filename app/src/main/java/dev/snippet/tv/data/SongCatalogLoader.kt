package dev.snippet.tv.data

import android.content.Context
import android.util.Log
import dev.snippet.tv.data.model.SongFile
import dev.snippet.tv.data.model.TieredCatalog
import dev.snippet.tv.game.AutocompleteEngine
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val catalog: TieredCatalog, val autocomplete: AutocompleteEngine) : CatalogState
    data class Failed(val message: String) : CatalogState
}

/** Loads and validates the bundled songs.json, then builds tiers + autocomplete pool. */
class SongCatalogLoader(private val context: Context, private val json: Json) {

    suspend fun load(): CatalogState = withContext(Dispatchers.IO) {
        try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val catalog = parse(json, text)
            CatalogState.Ready(catalog, AutocompleteEngine(catalog.songs))
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read bundled $ASSET_NAME", e)
            CatalogState.Failed("Could not read the bundled song list.")
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse bundled $ASSET_NAME", e)
            CatalogState.Failed("The bundled song list is corrupted.")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Bundled $ASSET_NAME failed validation", e)
            CatalogState.Failed(e.message ?: "The bundled song list is invalid.")
        }
    }

    companion object {
        private const val TAG = "SongCatalogLoader"
        private const val ASSET_NAME = "songs.json"

        fun parse(json: Json, text: String): TieredCatalog =
            TieredCatalog.fromSongs(json.decodeFromString<SongFile>(text).songs)
    }
}
