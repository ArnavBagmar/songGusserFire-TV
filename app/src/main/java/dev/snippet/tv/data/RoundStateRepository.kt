package dev.snippet.tv.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.snippet.tv.data.model.DifficultyTier
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class GuessKind { WRONG, SKIP, CORRECT }

@Serializable
data class StoredGuess(val kind: GuessKind, val text: String = "")

/**
 * A tier's current round: resumed while unfinished, replaced by a fresh random
 * pick once finished. The last finished round also feeds the home screen's
 * per-tier status line.
 */
@Serializable
data class StoredRound(
    val trackId: Long = 0,
    val guesses: List<StoredGuess> = emptyList(),
    val finished: Boolean = false,
    val won: Boolean = false,
) {
    val attemptsUsed: Int get() = guesses.size
}

class RoundStateRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    val roundsFlow: Flow<Map<DifficultyTier, StoredRound>> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "Round store unreadable", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            DifficultyTier.entries
                .mapNotNull { tier -> decode(prefs[keyFor(tier)])?.let { round -> tier to round } }
                .toMap()
        }

    suspend fun round(tier: DifficultyTier): StoredRound? = try {
        decode(dataStore.data.first()[keyFor(tier)])
    } catch (e: IOException) {
        Log.w(TAG, "Could not read stored round for $tier", e)
        null
    }

    suspend fun save(tier: DifficultyTier, round: StoredRound) {
        dataStore.edit { prefs -> prefs[keyFor(tier)] = json.encodeToString(round) }
    }

    // v2: unlimited-play rounds (v1 daily rounds carried an epochDay and are stale).
    private fun keyFor(tier: DifficultyTier) =
        stringPreferencesKey("round_${tier.name.lowercase()}_v2")

    private fun decode(encoded: String?): StoredRound? {
        if (encoded.isNullOrBlank()) return null
        return try {
            json.decodeFromString<StoredRound>(encoded)
        } catch (e: SerializationException) {
            Log.w(TAG, "Corrupt stored round; ignoring", e)
            null
        }
    }

    companion object {
        private const val TAG = "RoundStateRepository"
    }
}
