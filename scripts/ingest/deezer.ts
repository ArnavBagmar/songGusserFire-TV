/** Deezer API client: charts, genre playlists, and per-track detail. */

import { DEEZER_API, DEEZER_MIN_INTERVAL_MS } from "./config.ts";
import { fetchJson, RateLimiter } from "./http.ts";
import { asArray, asRecord, numberField, stringField } from "./json.ts";

export interface GenreRef {
  readonly id: number;
  readonly name: string;
}

export interface SearchHit {
  readonly id: number;
  readonly title: string;
  readonly artistName: string;
  readonly hasPreview: boolean;
}

export interface TrackDetail {
  readonly id: number;
  readonly title: string;
  readonly artist: string;
  readonly album: string;
  readonly isrc: string | null;
  readonly deezerRank: number;
  readonly coverUrl: string | null;
  readonly releaseDate: string | null;
}

function errorCode(body: unknown): number | null {
  const error = asRecord(asRecord(body)?.error);
  return error ? numberField(error, "code") : null;
}

// Deezer signals "quota exceeded" as a 200 response with error code 4.
function isQuotaError(body: unknown): boolean {
  return errorCode(body) === 4;
}

export class DeezerClient {
  readonly #limiter = new RateLimiter(DEEZER_MIN_INTERVAL_MS);
  readonly #userAgent: string;

  constructor(userAgent: string) {
    this.#userAgent = userAgent;
  }

  async #get(path: string): Promise<unknown> {
    return fetchJson(`${DEEZER_API}${path}`, {
      limiter: this.#limiter,
      userAgent: this.#userAgent,
      retryOnBody: isQuotaError,
    });
  }

  async listGenres(): Promise<GenreRef[]> {
    const body = await this.#get("/genre");
    return listData(body, "genre list").flatMap((item) => {
      const record = asRecord(item);
      if (!record) return [];
      const id = numberField(record, "id");
      const name = stringField(record, "name");
      // Genre 0 is "All", covered separately by the all-genres chart.
      return id !== null && id > 0 && name ? [{ id, name }] : [];
    });
  }

  /** Track ids from a genre chart (genre 0 = all genres). */
  async chartTrackIds(genreId: number, limit: number): Promise<number[]> {
    const body = await this.#get(`/chart/${genreId}/tracks?limit=${limit}`);
    return idsFrom(body, `chart ${genreId} tracks`);
  }

  async chartPlaylistIds(genreId: number, limit: number): Promise<number[]> {
    const body = await this.#get(`/chart/${genreId}/playlists?limit=${limit}`);
    return idsFrom(body, `chart ${genreId} playlists`);
  }

  async playlistTrackIds(playlistId: number, limit: number): Promise<number[]> {
    const collected: number[] = [];
    let index = 0;
    while (collected.length < limit) {
      const pageSize = Math.min(100, limit - collected.length);
      const body = await this.#get(
        `/playlist/${playlistId}/tracks?limit=${pageSize}&index=${index}`,
      );
      const ids = idsFrom(body, `playlist ${playlistId} tracks`);
      collected.push(...ids);
      if (ids.length < pageSize) break;
      index += ids.length;
    }
    return collected;
  }

  /** Best-effort track search; used to match external charts to Deezer ids. */
  async searchTracks(query: string, limit: number): Promise<SearchHit[]> {
    const body = await this.#get(
      `/search?q=${encodeURIComponent(query)}&limit=${limit}`,
    );
    return listData(body, "search").flatMap((item) => {
      const record = asRecord(item);
      if (!record) return [];
      const id = numberField(record, "id");
      const title = stringField(record, "title");
      const artist = asRecord(record.artist);
      const artistName = artist ? stringField(artist, "name") : null;
      const hasPreview = stringField(record, "preview") !== null;
      return id !== null && title && artistName
        ? [{ id, title, artistName, hasPreview }]
        : [];
    });
  }

  /** Full track detail; null when Deezer no longer knows the track. */
  async trackDetail(id: number): Promise<TrackDetail | null> {
    const body = await this.#get(`/track/${id}`);
    const code = errorCode(body);
    if (code === 800) return null; // "no data" — track deleted from Deezer
    if (code !== null) throw new Error(`Deezer error ${code} for track ${id}`);
    const record = asRecord(body);
    if (!record) throw new Error(`unexpected Deezer response for track ${id}`);
    const album = asRecord(record.album);
    const artist = asRecord(record.artist);
    const releaseDate = stringField(record, "release_date");
    return {
      id,
      title: stringField(record, "title") ?? "",
      artist: (artist && stringField(artist, "name")) ?? "",
      album: (album && stringField(album, "title")) ?? "",
      isrc: stringField(record, "isrc"),
      deezerRank: numberField(record, "rank") ?? 0,
      coverUrl: album ? stringField(album, "cover_xl") : null,
      releaseDate: releaseDate !== "0000-00-00" ? releaseDate : null,
    };
  }
}

function listData(body: unknown, label: string): unknown[] {
  // Some genres have no chart/playlists; Deezer answers with error 800.
  if (errorCode(body) === 800) return [];
  const array = asArray(asRecord(body)?.data);
  if (!array) throw new Error(`Deezer ${label}: response has no data array`);
  return array;
}

function idsFrom(body: unknown, label: string): number[] {
  return listData(body, label).flatMap((item) => {
    const record = asRecord(item);
    const id = record ? numberField(record, "id") : null;
    return id !== null ? [id] : [];
  });
}
