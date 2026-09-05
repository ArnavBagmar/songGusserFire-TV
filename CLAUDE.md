# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Snippet — a D-pad-first song-guessing game for Amazon Fire TV (Android TV). Player hears 0.1s of a 30-second Deezer preview and has six attempts; each miss/skip unlocks a longer snippet (0.1s → 0.5s → 1s → 2s → 8s → 15s). Difficulty tiers are percentile buckets of a popularity-ranked song pool bundled as `app/src/main/assets/songs.json`.

## Commands

```sh
./gradlew assembleRelease            # APK: app/build/outputs/apk/release/app-release.apk
./gradlew test                       # all unit tests (aggregate task — does NOT accept --tests)
./gradlew :app:testDebugUnitTest --tests "dev.snippet.tv.GuessNormalizerTest"   # single test class
node --test scripts/ingest/          # TypeScript pipeline tests (Node built-in runner)
node scripts/kworb-catalog.ts        # regenerate songs.json (required before any release)
```

- Requires JDK 17+ (JDK 25 works; the wrapper runs Gradle 9.1) and an Android SDK with `platforms;android-34` + `build-tools;34.0.0`, pointed at by `sdk.dir` in `local.properties`.
- Without `SNIPPET_*` signing properties in `gradle.properties`, release builds fall back to the debug keystore (fine for sideloading, not distribution).
- Minification is deliberately off in release builds (avoids R8 stripping kotlinx-serialization adapters) — don't enable it casually.

## Architecture

Single Gradle module (`app`), Kotlin DSL, version catalog (`gradle/libs.versions.toml`). Kotlin + Jetpack Compose with `androidx.tv:tv-material`. No DI framework, no navigation library, no ViewModels, no Play Services.

**Wiring** — `di/AppContainer.kt` is a hand-rolled container created once per activity: repositories, OkHttp, DataStore, and a lazily-created `PreviewPlayer` (Media3/ExoPlayer must be constructed on the main thread). `ui/AppRoot.kt` does navigation as a plain back-stack `List<Screen>` (sealed interface); BACK pops one level.

**Game round flow** — `HomeScreen` picks a `DifficultyTier` → `GameScreen` builds a `GameController` (plain class holding a `StateFlow<State>`, not a ViewModel). The controller resumes an unfinished round from `RoundStateRepository` or draws a fresh song via `RoundSelector`, resolves preview/cover files through `TrackRepository` + `data/deezer/DeezerClient` (downloaded to cache), drives playback through `audio/PreviewPlayer`, and persists results to `StatsRepository`. All per-round persistence is DataStore preferences + kotlinx-serialization JSON.

**Pure logic lives in `game/`** — `GameRules` (attempt/snippet constants), `GuessNormalizer` (forgiving matching: case/diacritics/punctuation-insensitive, strips bracketed and " - Radio Edit" suffixes, Levenshtein ≤ 2, optional leading "The"), `AutocompleteEngine`. These are JVM-only and covered by the unit tests in `app/src/test/`.

**Input** — the system IME is never shown. `ui/game/KeyboardLayouts.kt` defines the key grids (letters, digits, and round actions as data) and `DpadKeyboard.kt` renders them with wrap-around focus navigation; each file's header comment documents the worst-case D-pad press-count invariant — keep it true (and updated) when changing layouts.

**Catalog** — `songs.json` freezes each track's popularity rank at generation time; `TieredCatalog` cuts the pool into five percentile buckets from that frozen rank, which is what keeps tiers identical across devices. Ranks drift and Deezer previews disappear, so the file must be regenerated (`node scripts/kworb-catalog.ts`) shortly before a release.

**Ingestion pipeline (`scripts/`)** — standalone, zero-dependency Node 24+ TypeScript, run directly with `node`. Current flow: `kworb-catalog.ts` scrapes kworb.net's all-time Spotify chart, matches tracks to Deezer ids (cached in `scripts/data/ingest.db`, preview required), writes `songs.json` with rank = Spotify daily streams. Alternatives: `ingest.ts` (Deezer → MusicBrainz → ListenBrainz listen-count tiers; `--retier` retunes offline) and `tools/build-song-list.js`. Shared helpers live in `scripts/ingest/`.

**TV constraints** — `minSdk 25` (Fire OS 7), landscape-locked, leanback launcher entry, overscan safe-area padding (48dp/27dp) applied once in `AppRoot`. Everything on screen must be reachable by D-pad focus; hardware key handling (including media play/pause → replay) runs through Compose key events.
