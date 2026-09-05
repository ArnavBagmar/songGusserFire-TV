package dev.snippet.tv.ui.game

import dev.snippet.tv.audio.PreviewPlayer
import dev.snippet.tv.data.AlbumArtStore
import dev.snippet.tv.data.AppSettings
import dev.snippet.tv.data.GuessKind
import dev.snippet.tv.data.ResolvedTrack
import dev.snippet.tv.data.RoundSelector
import dev.snippet.tv.data.RoundStateRepository
import dev.snippet.tv.data.SettingsRepository
import dev.snippet.tv.data.SongStatsRepository
import dev.snippet.tv.data.StatsRepository
import dev.snippet.tv.data.StoredGuess
import dev.snippet.tv.data.StoredRound
import dev.snippet.tv.data.TierStats
import dev.snippet.tv.data.TrackRepository
import dev.snippet.tv.data.TrackResolution
import dev.snippet.tv.data.model.DifficultyTier
import dev.snippet.tv.data.model.Song
import dev.snippet.tv.data.model.TieredCatalog
import dev.snippet.tv.game.AutocompleteEngine
import dev.snippet.tv.game.GameRules
import dev.snippet.tv.game.GuessNormalizer
import dev.snippet.tv.game.Suggestion
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the result screen needs, captured at round end. */
data class RoundSummary(
    val tier: DifficultyTier,
    val song: Song,
    val coverFile: File?,
    val previewFile: File?,
    val won: Boolean,
    val attemptsUsed: Int,
    val guesses: List<StoredGuess>,
    val stats: TierStats,
)

/**
 * One round for a tier: resumes an unfinished round or draws a fresh random
 * song, applies guesses and skips, persists progress, and drives playback.
 * Rounds are unlimited — finishing one just frees the tier for the next.
 */
class GameController(
    val tier: DifficultyTier,
    private val catalog: TieredCatalog,
    private val autocomplete: AutocompleteEngine,
    private val trackRepository: TrackRepository,
    private val roundRepository: RoundStateRepository,
    private val statsRepository: StatsRepository,
    private val songStatsRepository: SongStatsRepository,
    private val albumArtStore: AlbumArtStore,
    settingsRepository: SettingsRepository,
    private val player: PreviewPlayer,
) {
    sealed interface Phase {
        data object Loading : Phase
        data class Failed(val message: String) : Phase
        data class Ready(val track: ResolvedTrack) : Phase
        data class Finished(val summary: RoundSummary) : Phase
    }

    data class State(
        val phase: Phase = Phase.Loading,
        val guesses: List<StoredGuess> = emptyList(),
        val typed: String = "",
        val suggestions: List<Suggestion> = emptyList(),
        val settings: AppSettings = AppSettings(),
    ) {
        val attemptIndex: Int get() = guesses.size
        val unlockedMs: Long get() = GameRules.snippetMsForAttempt(attemptIndex)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val playbackError: StateFlow<String?> = player.lastError

    init {
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                player.setVolume(settings.volumePercent)
                _state.update { current ->
                    current.copy(
                        settings = settings,
                        suggestions =
                            if (settings.autocompleteEnabled) autocomplete.suggest(current.typed)
                            else emptyList(),
                    )
                }
            }
        }
        scope.launch { load() }
    }

    private suspend fun load() {
        val stored = roundRepository.round(tier)
        val resumed = stored?.takeIf { !it.finished && it.trackId != 0L }
        // Excluding the last stored track keeps "play again" from repeating it.
        val lastTrackId = stored?.trackId?.takeIf { it != 0L }
        val candidates =
            RoundSelector.candidatesFor(tier, catalog, excludeIds = setOfNotNull(lastTrackId))
        // A resumed round must keep the track it started with; the random picks
        // then only serve as fallbacks if that track is gone from Deezer.
        val ordered = if (resumed != null) {
            (listOfNotNull(catalog.findById(resumed.trackId)) + candidates).distinctBy { it.id }
        } else {
            candidates
        }
        when (val resolution = trackRepository.resolve(ordered)) {
            is TrackResolution.Success -> {
                val guesses = resumed?.guesses
                    ?.takeIf { resolution.track.song.id == resumed.trackId }
                    .orEmpty()
                _state.update { it.copy(phase = Phase.Ready(resolution.track), guesses = guesses) }
                persistProgress(resolution.track, guesses)
                replay()
            }
            is TrackResolution.Failure ->
                _state.update { it.copy(phase = Phase.Failed(resolution.message)) }
        }
    }

    fun retryLoad() {
        if (_state.value.phase !is Phase.Failed) return
        _state.update { it.copy(phase = Phase.Loading) }
        scope.launch { load() }
    }

    fun typeCharacter(char: Char) = updateTyped { current ->
        if (current.length >= MAX_TYPED_LENGTH) current else current + char.uppercaseChar()
    }

    fun typeSpace() = updateTyped { current ->
        if (current.isEmpty() || current.endsWith(' ') || current.length >= MAX_TYPED_LENGTH) current
        else "$current "
    }

    fun backspace() = updateTyped { it.dropLast(1) }

    fun clearTyped() = updateTyped { "" }

    private fun updateTyped(transform: (String) -> String) {
        if (_state.value.phase !is Phase.Ready) return
        _state.update { current ->
            val typed = transform(current.typed)
            current.copy(
                typed = typed,
                suggestions =
                    if (current.settings.autocompleteEnabled) autocomplete.suggest(typed)
                    else emptyList(),
            )
        }
    }

    fun submitTyped() = submit(_state.value.typed)

    fun submitSuggestion(suggestion: Suggestion) = submit(suggestion.title)

    private fun submit(rawGuess: String) {
        val current = _state.value
        val ready = current.phase as? Phase.Ready ?: return
        val guessText = rawGuess.trim()
        if (GuessNormalizer.normalize(guessText).isEmpty()) return
        if (GuessNormalizer.matches(guessText, ready.track.song.title)) {
            finishRound(ready, current.guesses + StoredGuess(GuessKind.CORRECT, guessText))
        } else {
            advance(ready, current.guesses + StoredGuess(GuessKind.WRONG, guessText))
        }
    }

    fun skip() {
        val current = _state.value
        val ready = current.phase as? Phase.Ready ?: return
        advance(ready, current.guesses + StoredGuess(GuessKind.SKIP))
    }

    private fun advance(ready: Phase.Ready, guesses: List<StoredGuess>) {
        if (guesses.size >= GameRules.MAX_ATTEMPTS) {
            finishRound(ready, guesses)
            return
        }
        _state.update { it.copy(guesses = guesses, typed = "", suggestions = emptyList()) }
        scope.launch { persistProgress(ready.track, guesses) }
        // Auto-play the newly unlocked, longer snippet.
        replay()
    }

    private fun finishRound(ready: Phase.Ready, guesses: List<StoredGuess>) {
        player.stop()
        val won = guesses.lastOrNull()?.kind == GuessKind.CORRECT
        scope.launch {
            val stats = statsRepository.recordResult(tier, won, guesses.size)
            songStatsRepository.recordResult(ready.track.song, won)
            // Covers in the track cache get pruned; keep a permanent copy for the album wall.
            albumArtStore.persist(ready.track.song.id, ready.track.coverFile)
            roundRepository.save(
                tier,
                StoredRound(ready.track.song.id, guesses, finished = true, won = won),
            )
            val summary = RoundSummary(
                tier = tier,
                song = ready.track.song,
                coverFile = ready.track.coverFile,
                previewFile = ready.track.previewFile,
                won = won,
                attemptsUsed = guesses.size,
                guesses = guesses,
                stats = stats,
            )
            _state.update {
                it.copy(
                    phase = Phase.Finished(summary),
                    guesses = guesses,
                    typed = "",
                    suggestions = emptyList(),
                )
            }
        }
    }

    private suspend fun persistProgress(track: ResolvedTrack, guesses: List<StoredGuess>) {
        roundRepository.save(tier, StoredRound(track.song.id, guesses))
    }

    fun replay() {
        val ready = _state.value.phase as? Phase.Ready ?: return
        player.playClip(
            ready.track.previewFile,
            _state.value.unlockedMs,
            _state.value.settings.volumePercent,
        )
    }

    fun playbackPositionMs(): Long = player.positionMs()

    fun dispose() {
        player.stop()
        scope.cancel()
    }

    companion object {
        private const val MAX_TYPED_LENGTH = 48
    }
}
