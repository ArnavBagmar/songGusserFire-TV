/** ISRC -> MusicBrainz recording MBID resolution, cached in SQLite. */

import type { DatabaseSync } from "node:sqlite";
import {
  MB_MIN_INTERVAL_MS,
  MB_SEARCH_BATCH,
  MUSICBRAINZ_API,
} from "./config.ts";
import { cachedMbids, putMbid } from "./db.ts";
import { chunk, fetchJson, NetworkError, RateLimiter } from "./http.ts";
import { asArray, asRecord, stringField } from "./json.ts";

// Country code (2 letters) + registrant (3 alphanumeric) + year + designation
// (7 digits). MusicBrainz rejects anything looser with HTTP 400.
const ISRC_PATTERN = /^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$/;

export class MusicBrainzResolver {
  readonly #limiter = new RateLimiter(MB_MIN_INTERVAL_MS);
  readonly #db: DatabaseSync;
  readonly #userAgent: string;

  constructor(db: DatabaseSync, userAgent: string) {
    this.#db = db;
    this.#userAgent = userAgent;
  }

  /**
   * Resolve every ISRC to an MBID (or null for "no match"). Cached lookups —
   * hits and misses alike — never refetch, so reruns only pay for new tracks.
   * New ISRCs go through batched search queries first (50 per request), with
   * one-at-a-time /isrc lookups only for the stragglers the search missed.
   */
  async resolve(isrcs: readonly string[]): Promise<Map<string, string | null>> {
    const valid = [...new Set(isrcs.map((isrc) => isrc.toUpperCase()))].filter(
      (isrc) => ISRC_PATTERN.test(isrc),
    );
    const resolved = cachedMbids(this.#db, valid);
    const pending = valid.filter((isrc) => !resolved.has(isrc));
    if (pending.length === 0) {
      console.log(`MusicBrainz: all ${valid.length} ISRCs already cached`);
      return resolved;
    }
    console.log(
      `MusicBrainz: ${resolved.size} cached, resolving ${pending.length} new ISRCs`,
    );

    const matched = new Map<string, string>();
    const batches = chunk(pending, MB_SEARCH_BATCH);
    for (const [index, batch] of batches.entries()) {
      const found = await this.#searchBatch(batch);
      // Commit every batch hit immediately: a crash mid-stage loses nothing.
      for (const [isrc, mbid] of found) {
        matched.set(isrc, mbid);
        putMbid(this.#db, isrc, mbid);
        resolved.set(isrc, mbid);
      }
      console.log(
        `MusicBrainz: search batch ${index + 1}/${batches.length}, ` +
          `${matched.size}/${pending.length} matched`,
      );
    }

    const leftovers = pending.filter((isrc) => !matched.has(isrc));
    if (leftovers.length > 0) {
      console.log(
        `MusicBrainz: ${leftovers.length} ISRCs need individual lookups (1/s)`,
      );
    }
    for (const [index, isrc] of leftovers.entries()) {
      const mbid = await this.#lookupSingle(isrc);
      putMbid(this.#db, isrc, mbid);
      resolved.set(isrc, mbid);
      if ((index + 1) % 25 === 0) {
        console.log(
          `MusicBrainz: individual lookups ${index + 1}/${leftovers.length}`,
        );
      }
    }
    return resolved;
  }

  async #searchBatch(batch: readonly string[]): Promise<Map<string, string>> {
    const query = `isrc:(${batch.join(" OR ")})`;
    const url =
      `${MUSICBRAINZ_API}/recording` +
      `?query=${encodeURIComponent(query)}&limit=100&fmt=json`;
    const body = await fetchJson(url, {
      limiter: this.#limiter,
      userAgent: this.#userAgent,
    });
    const wanted = new Set(batch);
    const matches = new Map<string, string>();
    // Results arrive best-score first; the first recording claiming an ISRC wins.
    for (const item of asArray(asRecord(body)?.recordings) ?? []) {
      const recording = asRecord(item);
      if (!recording) continue;
      const mbid = stringField(recording, "id");
      if (!mbid) continue;
      for (const value of asArray(recording.isrcs) ?? []) {
        const isrc = typeof value === "string" ? value.toUpperCase() : null;
        if (isrc && wanted.has(isrc) && !matches.has(isrc))
          matches.set(isrc, mbid);
      }
    }
    return matches;
  }

  async #lookupSingle(isrc: string): Promise<string | null> {
    const url = `${MUSICBRAINZ_API}/isrc/${isrc}?fmt=json`;
    try {
      const body = await fetchJson(url, {
        limiter: this.#limiter,
        userAgent: this.#userAgent,
      });
      if (body === null) return null; // 404 — MusicBrainz has no such ISRC
      const recordings = asArray(asRecord(body)?.recordings) ?? [];
      const first = asRecord(recordings[0]);
      return first ? stringField(first, "id") : null;
    } catch (error) {
      // Offline/socket failures must not be cached as "no match" — rethrow so
      // the run stops and a rerun retries them from the incremental cache.
      if (error instanceof NetworkError) throw error;
      // A rejected ISRC, though, must not kill an hour-long run: it's a miss.
      const message = error instanceof Error ? error.message : String(error);
      console.warn(
        `MusicBrainz: lookup failed for ${isrc}, treating as no match (${message})`,
      );
      return null;
    }
  }
}
