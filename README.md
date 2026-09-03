# Snippet — song guessing for Fire TV

Snippet is a D-pad-first Android TV game: pick one of five difficulty tiers and
play unlimited rounds — every round draws a fresh random song (released 1990 or
later) from that tier's slice of the pool. You hear 0.1s of a 30-second Deezer
preview and have six attempts; every miss or skip unlocks a longer snippet
(0.1s → 0.5s → 1s → 2s → 8s → 15s). Guesses autocomplete on song title or
artist name. Difficulty is popularity: the bundled pool
is sorted by Deezer's frozen `rank` and cut into five percentile buckets, from
global hits (Easy) to deep cuts (Impossible).

A standalone ingestion pipeline lives in `scripts/ingest.ts` (Node 24+, zero
dependencies): it pulls Deezer charts and genre playlists into a SQLite DB,
resolves ISRC → MusicBrainz MBID (cached), fetches ListenBrainz listen counts,
and computes percentile difficulty tiers from real listen data. Raw counts are
stored, so `node scripts/ingest.ts --retier` retunes tiers offline. The pool is
limited to releases from 1990 onward. `node scripts/export-catalog.ts` can
generate `app/src/main/assets/songs.json` from that DB (rank = listen count).

The current catalog flow is `node scripts/kworb-catalog.ts`: it scrapes
kworb.net's all-time Spotify chart, matches each song to a Deezer track id
(cached in the same DB, preview required), and writes songs.json with
rank = Spotify DAILY streams — so difficulty tiers follow what the world
listens to today.

Built for sideloading onto an Amazon Fire TV Stick (Fire OS 7/8): `minSdk 25`,
`targetSdk 34`, landscape-locked, leanback launcher entry, 48dp/27dp overscan
margins, and a custom on-screen D-pad keyboard (the system IME is never shown).

## Stack

Kotlin · Jetpack Compose + `androidx.tv:tv-material` · Media3/ExoPlayer ·
Coroutines/Flow · DataStore · kotlinx-serialization · OkHttp. Single Gradle
module, Kotlin DSL, version catalog. No Play Services, no Firebase, no
analytics, no accounts.

## Requirements

- JDK 17+ (verified with JDK 25; the wrapper runs Gradle 9.1)
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
  (`local.properties` must point at it via `sdk.dir=...`)
- Node 18+ only if you want to regenerate the song list

## Build

```sh
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Unit tests: `./gradlew test`

Without signing properties (below) the release APK is signed with the debug
keystore — fine for sideloading onto your own stick, not for distribution.

## Release signing

Generate a keystore once:

```sh
keytool -genkeypair -v \
  -keystore snippet-release.keystore \
  -alias snippet \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass YOUR_STORE_PASSWORD -keypass YOUR_KEY_PASSWORD \
  -dname "CN=Snippet, OU=Games, O=YourName, L=City, S=State, C=US"
```

Then uncomment and fill in `gradle.properties`:

```properties
SNIPPET_STORE_FILE=/absolute/path/to/snippet-release.keystore
SNIPPET_STORE_PASSWORD=YOUR_STORE_PASSWORD
SNIPPET_KEY_ALIAS=snippet
SNIPPET_KEY_PASSWORD=YOUR_KEY_PASSWORD
```

`./gradlew assembleRelease` now produces a properly signed universal APK.
Keep the keystore out of version control (`.gitignore` already excludes
`*.keystore`/`*.jks`).

## Regenerating the song list (do this before any release)

`app/src/main/assets/songs.json` freezes each track's popularity rank at
generation time — that freeze is what keeps difficulty tiers identical across
devices and days. Ranks drift and previews disappear, so regenerate shortly
before building a release:

```sh
node scripts/kworb-catalog.ts
```

The script scrapes kworb.net's all-time Spotify chart, matches each song to a
Deezer track id (cached in `scripts/data/ingest.db`, so re-runs are fast),
keeps only tracks that still have a 30-second preview, and rewrites
`songs.json` with rank = Spotify daily streams.

Two alternative generators are included: `node tools/build-song-list.js`
(walks Deezer genre charts, samples ~500 tracks across the popularity range)
and the `scripts/ingest.ts` pipeline described above (ListenBrainz
listen-count tiers).

## Sideloading onto a Fire TV Stick

1. On the Fire TV: **Settings → My Fire TV → Developer Options → Install
   unknown apps** (or "Apps from Unknown Sources" on older Fire OS) and enable
   it for **Downloader**. If Developer Options is hidden, go to
   **Settings → My Fire TV → About**, select your device name 7 times.
2. Install **Downloader** (by AFTVnews) from the Amazon Appstore.
3. Host `app-release.apk` at a short, direct HTTPS URL — no redirects, no
   login. A GitHub Release asset link works:
   `https://github.com/YOU/REPO/releases/download/v1.0.0/app-release.apk`.
4. Open Downloader on the stick, enter the URL, and confirm the install when
   Android prompts. Snippet appears in **Your apps & channels** with its
   banner tile.

Updating: bump `versionCode` in `app/build.gradle.kts`, rebuild, re-download in
Downloader, and Android installs it as an update (same signing key required).

## Before distributing to anyone else

Regenerate `songs.json` with the script (stale track ids and ranks are the one
part of this repo that rots), build with your own release keystore instead of
the debug fallback, install once on a real Fire TV Stick and play a round on
each tier to confirm audio focus/D-pad behavior on that Fire OS version, and
review Deezer's API terms of use yourself — this is an unofficial fan project
that streams only official 30-second previews with attribution, but the terms
you ship under are your responsibility, and "Snippet" branding plus the
`dev.snippet.tv` application id are placeholders you may want to make your own.

## License / attribution

Music previews provided by Deezer. Track metadata, artwork and popularity data
come from the public Deezer API. Snippet is an unofficial fan project, not
affiliated with, endorsed by, or sponsored by Deezer, and inspired by the
guess-the-song web game format without reusing any third-party code or assets.
