/** SQLite persistence via node:sqlite — no external dependencies. */

import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { chunk } from "./http.ts";
import type { Tier } from "./tiers.ts";

export interface RawTrack {
  readonly id: number;
  readonly title: string;
  readonly artist: string;
  readonly album: string;
  readonly isrc: string | null;
  readonly deezerRank: number;
  readonly coverUrl: string | null;
  readonly releaseDate: string | null; // ISO yyyy-mm-dd
  readonly source: string; // e.g. "chart:Pop" or "playlist:Rock:12345"
}

export interface PoolRow {
  readonly trackId: number;
  readonly isrc: string | null;
  readonly mbid: string | null;
  readonly listenCount: number | null;
  readonly listenerCount: number | null;
}

export interface TierRow {
  readonly trackId: number;
  readonly mbid: string | null;
  readonly listenCount: number | null;
  readonly listenerCount: number | null;
  readonly percentile: number | null;
  readonly tier: Tier | null;
}

const SCHEMA = `
CREATE TABLE IF NOT EXISTS raw_tracks (
  id INTEGER PRIMARY KEY,
  title TEXT NOT NULL,
  artist TEXT NOT NULL,
  album TEXT NOT NULL,
  isrc TEXT,
  deezer_rank INTEGER NOT NULL,
  cover_url TEXT,
  release_date TEXT,
  source TEXT NOT NULL,
  fetched_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mbid_cache (
  isrc TEXT PRIMARY KEY,
  mbid TEXT,
  fetched_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS listen_stats (
  mbid TEXT PRIMARY KEY,
  listen_count INTEGER,
  listener_count INTEGER,
  fetched_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS track_tiers (
  track_id INTEGER PRIMARY KEY REFERENCES raw_tracks(id),
  mbid TEXT,
  listen_count INTEGER,
  listener_count INTEGER,
  percentile REAL,
  tier TEXT,
  computed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_raw_tracks_release ON raw_tracks(release_date);
CREATE TABLE IF NOT EXISTS kworb_songs (
  key TEXT PRIMARY KEY,
  artist TEXT NOT NULL,
  title TEXT NOT NULL,
  total_streams INTEGER NOT NULL,
  daily_streams INTEGER NOT NULL,
  position INTEGER NOT NULL,
  scraped_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS kworb_match (
  key TEXT PRIMARY KEY,
  deezer_id INTEGER,
  matched_at TEXT NOT NULL
);
`;

// SQLite caps bound variables; stay well below the limit when chunking IN().
const IN_CHUNK = 500;

export function openDb(path: string): DatabaseSync {
  mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec("PRAGMA journal_mode = WAL;");
  db.exec(SCHEMA);
  return db;
}

/** Ids already hydrated with full detail — these skip the Deezer refetch. */
export function storedTrackIds(db: DatabaseSync): Set<number> {
  const rows = db.prepare("SELECT id FROM raw_tracks").all();
  return new Set(rows.map((row) => Number(row.id)));
}

export function upsertTrack(db: DatabaseSync, track: RawTrack): void {
  db.prepare(
    `INSERT INTO raw_tracks
       (id, title, artist, album, isrc, deezer_rank, cover_url, release_date, source, fetched_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET
       deezer_rank = excluded.deezer_rank,
       cover_url = excluded.cover_url,
       fetched_at = excluded.fetched_at`,
  ).run(
    track.id,
    track.title,
    track.artist,
    track.album,
    track.isrc,
    track.deezerRank,
    track.coverUrl,
    track.releaseDate,
    track.source,
    new Date().toISOString(),
  );
}

export function trackCount(db: DatabaseSync): number {
  const row = db.prepare("SELECT COUNT(*) AS n FROM raw_tracks").get();
  return Number(row?.n ?? 0);
}

/**
 * The tierable pool: tracks released `minYear` or later, joined with whatever
 * MBID and listen data the caches hold so far.
 */
export function poolWithStats(db: DatabaseSync, minYear: number): PoolRow[] {
  const rows = db
    .prepare(
      `SELECT t.id AS track_id, t.isrc AS isrc, c.mbid AS mbid,
              s.listen_count AS listen_count, s.listener_count AS listener_count
       FROM raw_tracks t
       LEFT JOIN mbid_cache c ON c.isrc = t.isrc
       LEFT JOIN listen_stats s ON s.mbid = c.mbid
       WHERE t.release_date >= ?`,
    )
    .all(`${minYear}-01-01`);
  return rows.map((row) => ({
    trackId: Number(row.track_id),
    isrc: row.isrc === null ? null : String(row.isrc),
    mbid: row.mbid === null ? null : String(row.mbid),
    listenCount: row.listen_count === null ? null : Number(row.listen_count),
    listenerCount:
      row.listener_count === null ? null : Number(row.listener_count),
  }));
}

/** Cached ISRC lookups; a null value means "looked up before, no match". */
export function cachedMbids(
  db: DatabaseSync,
  isrcs: readonly string[],
): Map<string, string | null> {
  const result = new Map<string, string | null>();
  for (const batch of chunk(isrcs, IN_CHUNK)) {
    const placeholders = batch.map(() => "?").join(",");
    const rows = db
      .prepare(
        `SELECT isrc, mbid FROM mbid_cache WHERE isrc IN (${placeholders})`,
      )
      .all(...batch);
    for (const row of rows) {
      result.set(String(row.isrc), row.mbid === null ? null : String(row.mbid));
    }
  }
  return result;
}

export function putMbid(
  db: DatabaseSync,
  isrc: string,
  mbid: string | null,
): void {
  db.prepare(
    "INSERT OR REPLACE INTO mbid_cache (isrc, mbid, fetched_at) VALUES (?, ?, ?)",
  ).run(isrc, mbid, new Date().toISOString());
}

export function statsFetchedAt(
  db: DatabaseSync,
  mbids: readonly string[],
): Map<string, string> {
  const result = new Map<string, string>();
  for (const batch of chunk(mbids, IN_CHUNK)) {
    const placeholders = batch.map(() => "?").join(",");
    const rows = db
      .prepare(
        `SELECT mbid, fetched_at FROM listen_stats WHERE mbid IN (${placeholders})`,
      )
      .all(...batch);
    for (const row of rows)
      result.set(String(row.mbid), String(row.fetched_at));
  }
  return result;
}

export function putStats(
  db: DatabaseSync,
  mbid: string,
  listenCount: number | null,
  listenerCount: number | null,
): void {
  db.prepare(
    `INSERT OR REPLACE INTO listen_stats (mbid, listen_count, listener_count, fetched_at)
     VALUES (?, ?, ?, ?)`,
  ).run(mbid, listenCount, listenerCount, new Date().toISOString());
}

/** Replaces all tier rows in one transaction — tiers are always a full recompute. */
export function saveTierRows(db: DatabaseSync, rows: readonly TierRow[]): void {
  const computedAt = new Date().toISOString();
  const insert = db.prepare(
    `INSERT INTO track_tiers (track_id, mbid, listen_count, listener_count, percentile, tier, computed_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  );
  db.exec("BEGIN");
  try {
    db.exec("DELETE FROM track_tiers");
    for (const row of rows) {
      insert.run(
        row.trackId,
        row.mbid,
        row.listenCount,
        row.listenerCount,
        row.percentile,
        row.tier,
        computedAt,
      );
    }
    db.exec("COMMIT");
  } catch (error) {
    db.exec("ROLLBACK");
    throw error;
  }
}

export interface KworbStored {
  readonly key: string;
  readonly artist: string;
  readonly title: string;
  readonly totalStreams: number;
  readonly dailyStreams: number;
  readonly position: number;
}

/** Full refresh of the kworb chart snapshot — daily counts change every scrape. */
export function replaceKworbSongs(
  db: DatabaseSync,
  rows: readonly KworbStored[],
): void {
  const scrapedAt = new Date().toISOString();
  const insert = db.prepare(
    `INSERT OR REPLACE INTO kworb_songs
       (key, artist, title, total_streams, daily_streams, position, scraped_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  );
  db.exec("BEGIN");
  try {
    db.exec("DELETE FROM kworb_songs");
    for (const row of rows) {
      insert.run(
        row.key,
        row.artist,
        row.title,
        row.totalStreams,
        row.dailyStreams,
        row.position,
        scrapedAt,
      );
    }
    db.exec("COMMIT");
  } catch (error) {
    db.exec("ROLLBACK");
    throw error;
  }
}

/** Cached kworb->Deezer matches; null value = searched before, no match found. */
export function cachedKworbMatches(
  db: DatabaseSync,
  keys: readonly string[],
): Map<string, number | null> {
  const result = new Map<string, number | null>();
  for (const batch of chunk(keys, IN_CHUNK)) {
    const placeholders = batch.map(() => "?").join(",");
    const rows = db
      .prepare(
        `SELECT key, deezer_id FROM kworb_match WHERE key IN (${placeholders})`,
      )
      .all(...batch);
    for (const row of rows) {
      result.set(
        String(row.key),
        row.deezer_id === null ? null : Number(row.deezer_id),
      );
    }
  }
  return result;
}

export function putKworbMatch(
  db: DatabaseSync,
  key: string,
  deezerId: number | null,
): void {
  db.prepare(
    "INSERT OR REPLACE INTO kworb_match (key, deezer_id, matched_at) VALUES (?, ?, ?)",
  ).run(key, deezerId, new Date().toISOString());
}

export interface KworbExportRow {
  readonly deezerId: number;
  readonly title: string; // Deezer's title — matches the preview actually played
  readonly artist: string;
  readonly dailyStreams: number;
}

/** Matched kworb songs joined to hydrated Deezer tracks released `minYear`+. */
export function kworbExportRows(
  db: DatabaseSync,
  minYear: number,
): KworbExportRow[] {
  const rows = db
    .prepare(
      `SELECT m.deezer_id AS deezer_id, t.title AS title, t.artist AS artist,
              k.daily_streams AS daily_streams
       FROM kworb_songs k
       JOIN kworb_match m ON m.key = k.key AND m.deezer_id IS NOT NULL
       JOIN raw_tracks t ON t.id = m.deezer_id
       WHERE t.release_date >= ? AND k.daily_streams > 0`,
    )
    .all(`${minYear}-01-01`);
  return rows.map((row) => ({
    deezerId: Number(row.deezer_id),
    title: String(row.title),
    artist: String(row.artist),
    dailyStreams: Number(row.daily_streams),
  }));
}

export function tierDistribution(db: DatabaseSync): Map<string, number> {
  const rows = db
    .prepare(
      "SELECT COALESCE(tier, 'UNRATED') AS tier, COUNT(*) AS n FROM track_tiers GROUP BY 1",
    )
    .all();
  return new Map(rows.map((row) => [String(row.tier), Number(row.n)]));
}
