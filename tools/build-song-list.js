#!/usr/bin/env node
/**
 * build-song-list.js — regenerates app/src/main/assets/songs.json from the
 * public Deezer API (no key required).
 *
 * The bundled songs.json MUST be regenerated with this script shortly before a
 * release: it freezes each track's Deezer `rank` (popularity) at build time so
 * every install computes identical difficulty tiers, and it guarantees every
 * bundled track id still exists, still has a 30-second preview, and was
 * released in 1990 or later.
 *
 * Pool shape: decade-spanning playlists (90s through today) plus current genre
 * charts, deduped, with a popularity floor so the "deep cuts" tiers stay
 * niche-but-recognizable rather than unguessable.
 *
 * Usage:   node tools/build-song-list.js
 * Output:  app/src/main/assets/songs.json
 *
 * Requires Node 18+ (global fetch). Respects Deezer's ~50 requests / 5 s
 * rate limit and backs off on quota errors.
 */

"use strict";

const fs = require("node:fs");
const path = require("node:path");

// Edit this list to change the pool. `chart` ids are Deezer genre ids
// (https://api.deezer.com/genre); charts skew current, playlists cover eras.
const CHART_SOURCES = [
  { type: "chart", id: 0, name: "All genres" },
  { type: "chart", id: 132, name: "Pop" },
  { type: "chart", id: 116, name: "Rap/Hip Hop" },
  { type: "chart", id: 152, name: "Rock" },
  { type: "chart", id: 113, name: "Dance" },
  { type: "chart", id: 165, name: "R&B" },
  { type: "chart", id: 85, name: "Alternative" },
  { type: "chart", id: 106, name: "Electro" },
  { type: "chart", id: 84, name: "Country" },
  { type: "chart", id: 464, name: "Metal" },
  { type: "chart", id: 169, name: "Soul & Funk" },
];

// Playlist discovery queries, weighted toward the 90s/2000s/2010s so the pool
// is not dominated by whatever charts this week. Top search results per query
// are used; Deezer orders playlist search by relevance/popularity.
const PLAYLIST_QUERIES = [
  "90s hits",
  "90s rock",
  "90s hip hop",
  "90s r&b",
  "90s alternative",
  "2000s hits",
  "2000s rock",
  "2000s hip hop",
  "2000s pop",
  "2010s hits",
  "2010s pop",
  "2010s indie",
  "throwback hits",
  "one hit wonders",
];
const PLAYLISTS_PER_QUERY = 2;
const MIN_PLAYLIST_TRACKS = 20;

const MIN_RELEASE_YEAR = 1990;
// Popularity floor (Deezer rank, 0..~1M): every bundled song must be at least
// this popular so even the deep-cut tiers stay guessable.
const MIN_RANK = 350000;
// Cap on per-track detail lookups (release-date verification), for runtime.
const MAX_DETAIL_LOOKUPS = 1200;

const TARGET_COUNT = 500;
const PAGE_LIMIT = 100;
const TRACKS_PER_SOURCE = 200;
const REQUEST_GAP_MS = 250; // ~4 req/s, well under Deezer's 50 per 5 s
const MAX_RETRIES = 4;
const OUTPUT = path.join(
  __dirname,
  "..",
  "app",
  "src",
  "main",
  "assets",
  "songs.json",
);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function fetchJson(url) {
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt += 1) {
    let response;
    try {
      response = await fetch(url, {
        headers: { "User-Agent": "SnippetTV-songlist/1.0" },
      });
    } catch (err) {
      console.warn(
        `  network error (${err.message}), retry ${attempt}/${MAX_RETRIES}`,
      );
      await sleep(1500 * attempt);
      continue;
    }
    if (response.status === 429) {
      console.warn(`  HTTP 429, backing off (retry ${attempt}/${MAX_RETRIES})`);
      await sleep(3000 * attempt);
      continue;
    }
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} for ${url}`);
    }
    const body = await response.json();
    // Deezer reports quota problems as 200 + {"error":{"code":4}}.
    if (body && body.error) {
      if (body.error.code === 4) {
        console.warn(
          `  Deezer quota error, backing off (retry ${attempt}/${MAX_RETRIES})`,
        );
        await sleep(3000 * attempt);
        continue;
      }
      throw new Error(
        `Deezer error ${body.error.code} (${body.error.message}) for ${url}`,
      );
    }
    return body;
  }
  throw new Error(`Gave up after ${MAX_RETRIES} retries for ${url}`);
}

function firstPageUrl(source) {
  if (source.type === "chart") {
    return `https://api.deezer.com/chart/${source.id}/tracks?limit=${PAGE_LIMIT}`;
  }
  if (source.type === "playlist") {
    return `https://api.deezer.com/playlist/${source.id}/tracks?limit=${PAGE_LIMIT}`;
  }
  throw new Error(`Unknown source type: ${source.type}`);
}

// Mirrors the app's GuessNormalizer closely enough for dedup purposes.
function normalize(text) {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/\(.*?\)|\[.*?\]/g, " ")
    .replace(/\s+-\s+.*$/, " ")
    .replace(/[']/g, "")
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function toEntry(track) {
  if (!track || typeof track.id !== "number") return null;
  const title = typeof track.title === "string" ? track.title.trim() : "";
  const artist =
    track.artist && typeof track.artist.name === "string"
      ? track.artist.name.trim()
      : "";
  const rank = typeof track.rank === "number" ? track.rank : 0;
  const preview = typeof track.preview === "string" ? track.preview : "";
  if (!title || !artist || rank <= 0 || !preview.startsWith("http"))
    return null;
  if (!normalize(title)) return null; // titles with no latin/alphanumeric content are untypeable
  return { id: track.id, title, artist, rank };
}

async function discoverPlaylists() {
  const seen = new Set();
  const sources = [];
  for (const query of PLAYLIST_QUERIES) {
    let body;
    try {
      body = await fetchJson(
        `https://api.deezer.com/search/playlist?q=${encodeURIComponent(query)}&limit=8`,
      );
    } catch (err) {
      console.warn(`  playlist search "${query}" failed: ${err.message}`);
      continue;
    }
    const results = Array.isArray(body.data) ? body.data : [];
    let taken = 0;
    for (const playlist of results) {
      if (taken >= PLAYLISTS_PER_QUERY) break;
      if (!playlist || typeof playlist.id !== "number") continue;
      if ((playlist.nb_tracks || 0) < MIN_PLAYLIST_TRACKS) continue;
      if (seen.has(playlist.id)) continue;
      seen.add(playlist.id);
      sources.push({
        type: "playlist",
        id: playlist.id,
        name: `${query}: ${playlist.title}`,
      });
      taken += 1;
    }
    await sleep(REQUEST_GAP_MS);
  }
  console.log(`Discovered ${sources.length} era playlists`);
  return sources;
}

async function collect(sources) {
  const byKey = new Map();
  for (const source of sources) {
    let url = firstPageUrl(source);
    let fetched = 0;
    while (url && fetched < TRACKS_PER_SOURCE) {
      let body;
      try {
        body = await fetchJson(url);
      } catch (err) {
        console.warn(
          `  skipping ${source.type} ${source.id} (${source.name}): ${err.message}`,
        );
        break;
      }
      const tracks = Array.isArray(body.data) ? body.data : [];
      for (const track of tracks) {
        const entry = toEntry(track);
        if (!entry) continue;
        const key = `${normalize(entry.title)}|${normalize(entry.artist)}`;
        const existing = byKey.get(key);
        if (!existing || entry.rank > existing.rank) byKey.set(key, entry);
      }
      fetched += tracks.length;
      url = typeof body.next === "string" ? body.next : null;
      await sleep(REQUEST_GAP_MS);
    }
    console.log(
      `${source.type} ${source.id} (${source.name}): pool now ${byKey.size} unique tracks`,
    );
  }
  return [...byKey.values()];
}

function releaseYear(detail) {
  const date =
    typeof detail.release_date === "string" ? detail.release_date : "";
  const year = Number.parseInt(date.slice(0, 4), 10);
  return Number.isFinite(year) && year > 0 ? year : null;
}

/**
 * Verifies each candidate against the track endpoint: still exists, still has
 * a preview, and was released in MIN_RELEASE_YEAR or later. List endpoints do
 * not carry release dates, so this is one request per candidate.
 */
async function filterByEra(candidates) {
  const capped = candidates.slice(0, MAX_DETAIL_LOOKUPS);
  if (capped.length < candidates.length) {
    console.log(
      `Verifying top ${capped.length} of ${candidates.length} candidates (lookup cap)`,
    );
  }
  const kept = [];
  let tooOld = 0;
  let noDate = 0;
  let unavailable = 0;
  for (const [index, entry] of capped.entries()) {
    let detail;
    try {
      detail = await fetchJson(`https://api.deezer.com/track/${entry.id}`);
    } catch (err) {
      unavailable += 1;
      await sleep(REQUEST_GAP_MS);
      continue;
    }
    const preview = typeof detail.preview === "string" ? detail.preview : "";
    if (!preview.startsWith("http") || detail.readable === false) {
      unavailable += 1;
    } else {
      const year = releaseYear(detail);
      if (year === null) noDate += 1;
      else if (year < MIN_RELEASE_YEAR) tooOld += 1;
      else
        kept.push({
          ...entry,
          rank: typeof detail.rank === "number" ? detail.rank : entry.rank,
        });
    }
    if ((index + 1) % 100 === 0) {
      console.log(
        `  verified ${index + 1}/${capped.length} (kept ${kept.length})`,
      );
    }
    await sleep(REQUEST_GAP_MS);
  }
  console.log(
    `Era filter: kept ${kept.length}, dropped ${tooOld} pre-${MIN_RELEASE_YEAR}, ` +
      `${noDate} undated, ${unavailable} unavailable`,
  );
  return kept;
}

// Evenly sample across the rank-sorted pool so the popularity spread survives
// the cap (taking the top N would delete every deep cut).
function sampleEvenly(sorted, target) {
  if (sorted.length <= target) return sorted;
  const picked = [];
  for (let i = 0; i < target; i += 1) {
    picked.push(sorted[Math.floor((i * sorted.length) / target)]);
  }
  return picked;
}

async function main() {
  const playlistSources = await discoverPlaylists();
  const pool = await collect([...CHART_SOURCES, ...playlistSources]);
  const floored = pool.filter((entry) => entry.rank >= MIN_RANK);
  console.log(
    `Popularity floor (rank >= ${MIN_RANK}): ${floored.length} of ${pool.length} remain`,
  );
  const sortedCandidates = [...floored].sort((a, b) => b.rank - a.rank);
  const verified = await filterByEra(sortedCandidates);
  if (verified.length < 100) {
    throw new Error(
      `Only ${verified.length} usable tracks — refusing to write a degenerate songs.json`,
    );
  }
  const sorted = [...verified].sort((a, b) => b.rank - a.rank);
  const songs = sampleEvenly(sorted, TARGET_COUNT);
  const payload = {
    _note:
      "Generated by tools/build-song-list.js. Regenerate before release: ranks are frozen at " +
      `generation time and drive the difficulty tiers. All tracks released ${MIN_RELEASE_YEAR}+.`,
    version: 1,
    generatedAtUtc: new Date().toISOString(),
    songs,
  };
  fs.mkdirSync(path.dirname(OUTPUT), { recursive: true });
  fs.writeFileSync(OUTPUT, `${JSON.stringify(payload, null, 1)}\n`);
  const ranks = songs.map((s) => s.rank);
  console.log(`Wrote ${songs.length} songs to ${OUTPUT}`);
  console.log(
    `Rank spread: max ${Math.max(...ranks)}, min ${Math.min(...ranks)}`,
  );
}

main().catch((err) => {
  console.error(err.message);
  process.exitCode = 1;
});
