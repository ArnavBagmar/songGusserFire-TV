package dev.snippet.tv.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.snippet.tv.data.model.Song
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SongPlayStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val wins: Int = 0,
    val losses: Int = 0,
) {
    val plays: Int get() = wins + losses
    val winRatePercent: Int get() = if (plays == 0) 0 else wins * 100 / plays

    companion object {
        /** Pure so it is unit-testable; title/artist refresh from the current catalog. */
        fun applyResult(old: SongPlayStats?, song: Song, won: Boolean): SongPlayStats {
            val base = old ?: SongPlayStats(song.id, song.title, song.artist)
            return base.copy(
                title = song.title,
                artist = song.artist,
                wins = base.wins + if (won) 1 else 0,
                losses = base.losses + if (won) 0 else 1,
            )
        }
    }
}

/** Per-song win/loss counts across all tiers, persisted as one JSON blob. */
class SongStatsRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val statsKey = stringPreferencesKey("song_stats_v1")

    val statsFlow: Flow<Map<Long, SongPlayStats>> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "Song stats store unreadable; showing empty stats", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> decode(prefs[statsKey]) }

    suspend fun recordResult(song: Song, won: Boolean) {
        dataStore.edit { prefs ->
            val all = decode(prefs[statsKey])
            val updated = all + (song.id to SongPlayStats.applyResult(all[song.id], song, won))
            prefs[statsKey] = json.encodeToString(updated.mapKeys { (id, _) -> id.toString() })
        }
    }

    suspend fun resetAll() {
        dataStore.edit { prefs -> prefs.remove(statsKey) }
    }

    private fun decode(encoded: String?): Map<Long, SongPlayStats> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, SongPlayStats>>(encoded)
                .mapNotNull { (id, stats) -> id.toLongOrNull()?.let { it to stats } }
                .toMap()
        } catch (e: SerializationException) {
            Log.w(TAG, "Corrupt song stats blob; starting fresh", e)
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "SongStatsRepository"
    }
}
