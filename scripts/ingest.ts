#!/usr/bin/env node
/**
 * Phase 1 ingestion pipeline — standalone; the TV app never runs this.
 *
 * 1. Pulls Deezer charts + genre playlists into the raw_tracks table
 *    (id, title, artist, album, isrc, deezer_rank, cover_url, release_date).
 * 2. Resolves ISRC -> MBID against MusicBrainz, cached so reruns don't refetch.
 * 3. Fetches listen_count + listener_count from ListenBrainz per MBID (TTL cache).
 * 4. Computes difficulty tiers by listen-count percentile within the pool.
 *    Raw counts stay in the DB, so thresholds can be retuned offline: --retier
 *    recomputes tiers from stored numbers without touching the network.
 *
 * The pool only admits tracks released 1990 or later (--min-year).
 *
 * Usage:  node scripts/ingest.ts [options]      (Node >= 24, zero dependencies)
 *   --retier                recompute tiers from cached data, no network
 *   --refresh-stats         ignore the ListenBrainz cache TTL
 *   --db <path>             SQLite location (default scripts/data/ingest.db)
 *   --min-year <year>       earliest release year admitted (default 1990)
 *   --per-source <n>        tracks per chart/playlist (default 100)
 *   --playlists-per-genre <n>  playlists pulled per genre (default 2)
 *   --max-genres <n>        genres beyond the all-genres chart (default 12)
 */

import { parseArgs } from "node:util";
import { DEFAULTS, USER_AGENT } from "./ingest/config.ts";
import {
  openDb,
  poolWithStats,
  saveTierRows,
  storedTrackIds,
  tierDistribution,
  trackCount,
  upsertTrack,
} from "./ingest/db.ts";
import type { PoolRow } from "./ingest/db.ts";
import { DeezerClient } from "./ingest/deezer.ts";
import { mapLimit } from "./ingest/http.ts";
import { ListenBrainzClient } from "./ingest/listenbrainz.ts";
import { MusicBrainzResolver } from "./ingest/musicbrainz.ts";
import { computeTiers, TIERS } from "./ingest/tiers.ts";
import type { DatabaseSync } from "node:sqlite";

const HYDRATE_CONCURRENCY = 4;

interface Options {
  readonly retier: boolean;
  readonly refreshStats: boolean;
  readonly dbPath: string;
  readonly minYear: number;
  readonly perSource: number;
  readonly playlistsPerGenre: number;
  readonly maxGenres: number;
}

function parseOptions(): Options | null {
  const { values } = parseArgs({
    options: {
      retier: { type: "boolean", default: false },
      "refresh-stats": { type: "boolean", default: false },
      db: { type: "string", default: DEFAULTS.dbPath },
      "min-year": { type: "string", default: String(DEFAULTS.minYear) },
      "per-source": { type: "string", default: String(DEFAULTS.perSource) },
      "playlists-per-genre": { type: "string", default: String(DEFAULTS.playlistsPerGenre) },
      "max-genres": { type: "string", default: String(DEFAULTS.maxGenres) },
      help: { type: "boolean", default: false },
    },
  });
  if (values.help) return null;
  return {
    retier: values.retier,
    refreshStats: values["refresh-stats"],
    dbPath: values.db,
    minYear: intOption("min-year", values["min-year"], 1900, 2100),
    perSource: intOption("per-source", values["per-source"], 1, 500),
    playlistsPerGenre: intOption("playlists-per-genre", values["playlists-per-genre"], 0, 10),
    maxGenres: intOption("max-genres", values["max-genres"], 0, 50),
  };
}

function intOption(name: string, raw: string, min: number, max: number): number {
  const value = Number(raw);
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`--${name} must be an integer between ${min} and ${max}, got "${raw}"`);
  }
  return value;
}

/** Stage 1: charts + genre playlists -> map of track id to first-seen source. */
async function collectTrackSources(
  deezer: DeezerClient,
  options: Options,
): Promise<Map<number, string>> {
  const sources = new Map<number, string>();
  const claim = (ids: readonly number[], label: string) => {
    for (const id of ids) if (!sources.has(id)) sources.set(id, label);
  };

  claim(await deezer.chartTrackIds(0, options.perSource), "chart:all");
  const genres = (await deezer.listGenres()).slice(0, options.maxGenres);
  console.log(`Deezer: covering ${genres.length} genres`);
  for (const genre of genres) {
    claim(await deezer.chartTrackIds(genre.id, options.perSource), `chart:${genre.name}`);
    const playlistIds = await deezer.chartPlaylistIds(genre.id, options.playlistsPerGenre);
    for (const playlistId of playlistIds) {
      claim(
        await deezer.playlistTrackIds(playlistId, options.perSource),
        `playlist:${genre.name}:${playlistId}`,
      );
    }
  }
  return sources;
}

/** Stage 1b: hydrate detail for tracks the DB has never seen. */
async function hydrateTracks(
  db: DatabaseSync,
  deezer: DeezerClient,
  sources: ReadonlyMap<number, string>,
): Promise<void> {
  const known = storedTrackIds(db);
  const fresh = [...sources].filter(([id]) => !known.has(id));
  console.log(`Deezer: ${sources.size} unique tracks, ${fresh.length} need a detail fetch`);
  let processed = 0;
  let dropped = 0;
  await mapLimit(fresh, HYDRATE_CONCURRENCY, async ([id, source]) => {
    const detail = await deezer.trackDetail(id);
    processed += 1;
    if (!detail || !detail.title || !detail.artist) {
      dropped += 1;
    } else {
      upsertTrack(db, { ...detail, source });
    }
    if (processed % 200 === 0) {
      console.log(`Deezer: hydrated ${processed}/${fresh.length}`);
    }
  });
  if (dropped > 0) console.log(`Deezer: dropped ${dropped} deleted/nameless tracks`);
}

/** Stages 2 + 3: ISRC -> MBID, then MBID -> listen counts. */
async function resolveAndFetchStats(db: DatabaseSync, options: Options): Promise<void> {
  const pool = poolWithStats(db, options.minYear);
  const isrcs = pool.flatMap((row) => (row.isrc ? [row.isrc] : []));
  const resolver = new MusicBrainzResolver(db, USER_AGENT);
  const mbidByIsrc = await resolver.resolve(isrcs);
  const mbids = [...new Set([...mbidByIsrc.values()].flatMap((mbid) => (mbid ? [mbid] : [])))];
  const listenBrainz = new ListenBrainzClient(db, USER_AGENT);
  await listenBrainz.refreshStats(mbids, {
    ttlDays: DEFAULTS.statsTtlDays,
    force: options.refreshStats,
  });
}

/** Stage 4: percentile tiers from stored listen counts — pure, offline. */
function retier(db: DatabaseSync, options: Options): PoolRow[] {
  const pool = poolWithStats(db, options.minYear);
  const results = computeTiers(
    pool.map((row) => ({ trackId: row.trackId, listenCount: row.listenCount })),
  );
  const byTrackId = new Map(pool.map((row) => [row.trackId, row]));
  saveTierRows(
    db,
    results.map((result) => {
      const row = byTrackId.get(result.trackId);
      return {
        trackId: result.trackId,
        mbid: row?.mbid ?? null,
        listenCount: row?.listenCount ?? null,
        listenerCount: row?.listenerCount ?? null,
        percentile: result.percentile,
        tier: result.tier,
      };
    }),
  );
  return pool;
}

function printSummary(db: DatabaseSync, pool: readonly PoolRow[], options: Options): void {
  const distribution = tierDistribution(db);
  const tierLine = [...TIERS, "UNRATED"]
    .map((tier) => `${tier} ${distribution.get(tier) ?? 0}`)
    .join(" · ");
  console.log("\n=== Ingest summary ===");
  console.log(`raw tracks stored:            ${trackCount(db)}`);
  console.log(`pool (released >= ${options.minYear}): ${pool.length}`);
  console.log(`  with ISRC:                  ${pool.filter((row) => row.isrc).length}`);
  console.log(`  with MBID:                  ${pool.filter((row) => row.mbid).length}`);
  console.log(`  with listen stats:          ${pool.filter((row) => row.listenCount !== null).length}`);
  console.log(`tiers: ${tierLine}`);
  console.log(`db: ${options.dbPath}`);
}

async function main(): Promise<void> {
  const options = parseOptions();
  if (options === null) {
    console.log("See the header comment in scripts/ingest.ts for usage.");
    return;
  }
  const db = openDb(options.dbPath);
  try {
    if (!options.retier) {
      const deezer = new DeezerClient(USER_AGENT);
      const sources = await collectTrackSources(deezer, options);
      await hydrateTracks(db, deezer, sources);
      await resolveAndFetchStats(db, options);
    } else {
      console.log("Retier only: recomputing tiers from cached data (no network).");
    }
    const pool = retier(db, options);
    printSummary(db, pool, options);
  } finally {
    db.close();
  }
}

main().catch((error: unknown) => {
  console.error(`ingest failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
});
