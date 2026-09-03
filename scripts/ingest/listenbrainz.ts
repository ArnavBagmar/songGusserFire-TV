/** ListenBrainz popularity (listen/listener counts) per MBID, TTL-cached. */

import type { DatabaseSync } from "node:sqlite";
import { LB_BATCH, LB_MIN_INTERVAL_MS, LISTENBRAINZ_API } from "./config.ts";
import { putStats, statsFetchedAt } from "./db.ts";
import { chunk, fetchJson, RateLimiter } from "./http.ts";
import { asArray, asRecord, numberField, stringField } from "./json.ts";

export interface StatsOptions {
  readonly ttlDays: number;
  readonly force: boolean;
}

interface Counts {
  readonly listenCount: number | null;
  readonly listenerCount: number | null;
}

export class ListenBrainzClient {
  readonly #limiter = new RateLimiter(LB_MIN_INTERVAL_MS);
  readonly #db: DatabaseSync;
  readonly #userAgent: string;

  constructor(db: DatabaseSync, userAgent: string) {
    this.#db = db;
    this.#userAgent = userAgent;
  }

  /**
   * Fetch listen/listener counts for stale or unknown MBIDs and cache them.
   * Counts fresher than `ttlDays` are skipped unless `force` is set — listen
   * counts drift slowly, so reruns inside the window cost zero requests.
   */
  async refreshStats(mbids: readonly string[], options: StatsOptions): Promise<void> {
    const unique = [...new Set(mbids)];
    const fetchedAt = statsFetchedAt(this.#db, unique);
    const cutoff = Date.now() - options.ttlDays * 24 * 60 * 60 * 1000;
    const stale = unique.filter((mbid) => {
      if (options.force) return true;
      const at = fetchedAt.get(mbid);
      return at === undefined || Date.parse(at) < cutoff;
    });
    if (stale.length === 0) {
      console.log(`ListenBrainz: all ${unique.length} MBIDs cached and fresh`);
      return;
    }
    console.log(`ListenBrainz: refreshing ${stale.length}/${unique.length} MBIDs`);
    const batches = chunk(stale, LB_BATCH);
    for (const [index, batch] of batches.entries()) {
      const counts = await this.#popularity(batch);
      // MBIDs absent from the response are cached as nulls: unknown to
      // ListenBrainz today, retried automatically once the TTL lapses.
      for (const mbid of batch) {
        const entry = counts.get(mbid) ?? { listenCount: null, listenerCount: null };
        putStats(this.#db, mbid, entry.listenCount, entry.listenerCount);
      }
      console.log(`ListenBrainz: batch ${index + 1}/${batches.length} stored`);
    }
  }

  async #popularity(batch: readonly string[]): Promise<Map<string, Counts>> {
    const body = await fetchJson(`${LISTENBRAINZ_API}/popularity/recording`, {
      limiter: this.#limiter,
      userAgent: this.#userAgent,
      init: {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ recording_mbids: batch }),
      },
    });
    const results = new Map<string, Counts>();
    for (const item of asArray(body) ?? []) {
      const record = asRecord(item);
      if (!record) continue;
      const mbid = stringField(record, "recording_mbid");
      if (!mbid) continue;
      results.set(mbid, {
        listenCount: numberField(record, "total_listen_count"),
        listenerCount: numberField(record, "total_user_count"),
      });
    }
    return results;
  }
}
