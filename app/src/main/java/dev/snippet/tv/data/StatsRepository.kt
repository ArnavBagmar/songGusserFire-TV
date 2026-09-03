package dev.snippet.tv.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.game.GameRules
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TierStats(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val attemptDistribution: List<Int> = List(GameRules.MAX_ATTEMPTS) { 0 },
) {
    val losses: Int get() = gamesPlayed - wins
    val winRatePercent: Int get() = if (gamesPlayed == 0) 0 else wins * 100 / gamesPlayed
}

/** Per-tier streaks, win rate and attempt distribution, persisted as one JSON blob. */
class StatsRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val statsKey = stringPreferencesKey("tier_stats_v1")

    val statsFlow: Flow<Map<DifficultyTier, TierStats>> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "Stats store unreadable; showing empty stats", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> decode(prefs[statsKey]) }

    /** Applies a finished round and returns the tier's updated stats. */
    suspend fun recordResult(tier: DifficultyTier, won: Boolean, attemptsUsed: Int): TierStats {
        var updated = TierStats()
        dataStore.edit { prefs ->
            val all = decode(prefs[statsKey])
            updated = applyResult(all[tier] ?: TierStats(), won, attemptsUsed)
            val merged = (all + (tier to updated)).mapKeys { (key, _) -> key.name }
            prefs[statsKey] = json.encodeToString(merged)
        }
        return updated
    }

    suspend fun resetAll() {
        dataStore.edit { prefs -> prefs.remove(statsKey) }
    }

    private fun decode(encoded: String?): Map<DifficultyTier, TierStats> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, TierStats>>(encoded)
                .mapNotNull { (name, stats) ->
                    DifficultyTier.entries.find { it.name == name }?.let { tier -> tier to stats }
                }
                .toMap()
        } catch (e: SerializationException) {
            Log.w(TAG, "Corrupt stats blob; starting fresh", e)
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "StatsRepository"

        /** Pure so it is unit-testable: streaks are consecutive wins within a tier. */
        fun applyResult(old: TierStats, won: Boolean, attemptsUsed: Int): TierStats {
            val attempts = attemptsUsed.coerceIn(1, GameRules.MAX_ATTEMPTS)
            val padded = List(GameRules.MAX_ATTEMPTS) { old.attemptDistribution.getOrElse(it) { 0 } }
            val distribution =
                if (won) padded.mapIndexed { i, count -> if (i == attempts - 1) count + 1 else count }
                else padded
            val streak = if (won) old.currentStreak + 1 else 0
            return TierStats(
                gamesPlayed = old.gamesPlayed + 1,
                wins = old.wins + if (won) 1 else 0,
                currentStreak = streak,
                maxStreak = max(old.maxStreak, streak),
                attemptDistribution = distribution,
            )
        }
    }
}
