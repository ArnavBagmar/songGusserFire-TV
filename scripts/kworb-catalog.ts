#!/usr/bin/env node
/**
 * Build the game catalog from kworb.net's all-time Spotify chart, with
 * difficulty driven by DAILY streams: songs.json `rank` = kworb daily count,
 * so the app's five percentile tiers split by how much the world listens
 * to each song *today*.
 *
 * Stages (each cached in the ingest DB, reruns only pay for new songs):
 *   1. Scrape https://kworb.net/spotify/songs.html  -> kworb_songs
 *   2. Match each song to a Deezer track id (search) -> kworb_match
 *   3. Hydrate matched tracks for release dates      -> raw_tracks
 *   4. Export app/src/main/assets/songs.json (released >= --min-year)
 *
 * Usage:  node scripts/kworb-catalog.ts [options]
 *   --html <path>       parse a saved copy instead of fetching the live page
 *   --min-year <year>   earliest release year (default 1990)
 *   --db <path>         cache DB (default scripts/data/ingest.db)
 *   --out <path>        output (default app/src/main/assets/songs.json)
 */

import { readFileSync, writeFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { USER_AGENT } from "./ingest/config.ts";
import {
  cachedKworbMatches,
  kworbExportRows,
  openDb,
  putKworbMatch,
  replaceKworbSongs,
  storedTrackIds,
  upsertTrack,
} from "./ingest/db.ts";
import { DeezerClient } from "./ingest/deezer.ts";
import type { SearchHit } from "./ingest/deezer.ts";
import { mapLimit } from "./ingest/http.ts";
import { kworbKey, normalizeName, parseKworbSongs } from "./ingest/kworb.ts";
import type { KworbSong } from "./ingest/kworb.ts";

const KWORB_URL = "https://kworb.net/spotify/songs.html";
// Must match TieredCatalog.MIN_POOL_SIZE in the app.
const MIN_POOL_SIZE = 25;
const MATCH_CONCURRENCY = 4;

const { values } = parseArgs({
  options: {
    html: { type: "string" },
    "min-year": { type: "string", default: "1990" },
    db: { type: "string", default: "scripts/data/ingest.db" },
    out: { type: "string", default: "app/src/main/assets/songs.json" },
  },
});

const minYear = Number(values["min-year"]);
if (!Number.isInteger(minYear) || minYear < 1900 || minYear > 2100) {
  throw new Error(`--min-year must be a year, got "${values["min-year"]}"`);
}

async function loadChartHtml(): Promise<string> {
  if (values.html) return readFileSync(values.html, "utf8");
  const response = await fetch(KWORB_URL, { headers: { "User-Agent": USER_AGENT } });
  if (!response.ok) throw new Error(`kworb fetch failed: HTTP ${response.status}`);
  return response.text();
}

function stripParens(text: string): string {
  return text.replace(/\(.*?\)|\[.*?\]/g, " ");
}

function titlesMatch(kworbTitle: string, deezerTitle: string): boolean {
  const a = normalizeName(stripParens(kworbTitle));
  const b = normalizeName(stripParens(deezerTitle));
  return a.length > 0 && b.length > 0 && (a === b || a.startsWith(b) || b.startsWith(a));
}

function artistsMatch(kworbArtist: string, deezerArtist: string): boolean {
  const a = normalizeName(kworbArtist);
  const b = normalizeName(deezerArtist);
  return a.length > 0 && b.length > 0 && (a.includes(b) || b.includes(a));
}

function pickMatch(song: KworbSong, hits: readonly SearchHit[]): SearchHit | null {
  return (
    hits.find(
      (hit) =>
        hit.hasPreview &&
        artistsMatch(song.artist, hit.artistName) &&
        titlesMatch(song.title, hit.title),
    ) ?? null
  );
}

async function findDeezerId(deezer: DeezerClient, song: KworbSong): Promise<number | null> {
  const quoted =
    `artist:"${song.artist.replaceAll('"', "")}" ` +
    `track:"${song.title.replaceAll('"', "")}"`;
  const first = pickMatch(song, await deezer.searchTracks(quoted, 10));
  if (first) return first.id;
  const second = pickMatch(song, await deezer.searchTracks(`${song.artist} ${song.title}`, 10));
  return second?.id ?? null;
}

async function main(): Promise<void> {
  const db = openDb(values.db);
  const deezer = new DeezerClient(USER_AGENT);
  try {
    // Stage 1: scrape.
    const songs = parseKworbSongs(await loadChartHtml());
    if (songs.length < 100) {
      throw new Error(`kworb parse looks broken: only ${songs.length} rows found`);
    }
    replaceKworbSongs(
      db,
      songs.map((song) => ({ ...song, key: kworbKey(song.artist, song.title) })),
    );
    console.log(`kworb: ${songs.length} chart rows stored`);

    // Stage 2: match to Deezer ids (cache pays for reruns).
    const matches = cachedKworbMatches(db, songs.map((s) => kworbKey(s.artist, s.title)));
    const unmatched = songs.filter((s) => !matches.has(kworbKey(s.artist, s.title)));
    console.log(`Deezer: ${matches.size} matches cached, searching ${unmatched.length} new songs`);
    let searched = 0;
    let found = 0;
    await mapLimit(unmatched, MATCH_CONCURRENCY, async (song) => {
      const id = await findDeezerId(deezer, song);
      putKworbMatch(db, kworbKey(song.artist, song.title), id);
      searched += 1;
      if (id !== null) found += 1;
      if (searched % 100 === 0) {
        console.log(`Deezer: searched ${searched}/${unmatched.length} (${found} matched)`);
      }
    });
    if (unmatched.length > 0) {
      console.log(`Deezer: matched ${found}/${unmatched.length} new songs`);
    }

    // Stage 3: hydrate matched tracks we've never fetched (release dates).
    const allMatches = cachedKworbMatches(db, songs.map((s) => kworbKey(s.artist, s.title)));
    const matchedIds = [...new Set([...allMatches.values()].flatMap((id) => (id ? [id] : [])))];
    const known = storedTrackIds(db);
    const toHydrate = matchedIds.filter((id) => !known.has(id));
    console.log(`Deezer: hydrating ${toHydrate.length} of ${matchedIds.length} matched tracks`);
    let hydrated = 0;
    await mapLimit(toHydrate, MATCH_CONCURRENCY, async (id) => {
      const detail = await deezer.trackDetail(id);
      if (detail && detail.title && detail.artist) {
        upsertTrack(db, { ...detail, source: "kworb" });
      }
      hydrated += 1;
      if (hydrated % 200 === 0) {
        console.log(`Deezer: hydrated ${hydrated}/${toHydrate.length}`);
      }
    });

    // Stage 4: export, deduped, rank = daily streams.
    const rows = kworbExportRows(db, minYear);
    const byId = new Map(rows.map((row) => [row.deezerId, row]));
    const byName = new Map<string, (typeof rows)[number]>();
    for (const row of byId.values()) {
      const key = `${normalizeName(row.title)}|${normalizeName(row.artist)}`;
      const existing = byName.get(key);
      if (!existing || row.dailyStreams > existing.dailyStreams) byName.set(key, row);
    }
    const catalog = [...byName.values()]
      .sort((a, b) => b.dailyStreams - a.dailyStreams)
      .map((row) => ({
        id: row.deezerId,
        title: row.title,
        artist: row.artist,
        rank: row.dailyStreams,
      }));
    if (catalog.length < MIN_POOL_SIZE) {
      throw new Error(
        `Only ${catalog.length} songs survived matching/filters (need >= ${MIN_POOL_SIZE}).`,
      );
    }
    const file = {
      _note:
        "Generated by scripts/kworb-catalog.ts from kworb.net/spotify/songs.html. " +
        `rank = Spotify DAILY streams at scrape time; tiers derive from it. ` +
        `All tracks released ${minYear}+, matched to Deezer ids for previews.`,
      version: 1,
      generatedAtUtc: new Date().toISOString(),
      songs: catalog,
    };
    writeFileSync(values.out, `${JSON.stringify(file, null, 1)}\n`);
    console.log(`\nWrote ${catalog.length} songs to ${values.out}`);
    console.log(
      `Most-listened today: ${catalog[0].title} — ${catalog[0].artist} ` +
        `(${catalog[0].rank.toLocaleString()} daily streams)`,
    );
  } finally {
    db.close();
  }
}

main().catch((error: unknown) => {
  console.error(
    `kworb-catalog failed: ${error instanceof Error ? error.message : String(error)}`,
  );
  process.exitCode = 1;
});
