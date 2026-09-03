/** Shared configuration for the ingestion pipeline. */

export const DEEZER_API = "https://api.deezer.com";
export const MUSICBRAINZ_API = "https://musicbrainz.org/ws/2";
export const LISTENBRAINZ_API = "https://api.listenbrainz.org/1";

// MusicBrainz etiquette: identify the client and give a way to reach you.
// Put an email or URL here before running large ingests.
export const CONTACT = "arnavbagmar23@gmail.com";
export const USER_AGENT = `snippet-tv-ingest/1.0${CONTACT ? ` (${CONTACT})` : ""}`;

export const DEFAULTS = {
  dbPath: "scripts/data/ingest.db",
  minYear: 1990,
  perSource: 100, // tracks pulled per chart or playlist
  playlistsPerGenre: 4,
  maxGenres: 25, // Deezer lists ~22 real genres; this covers all of them
  statsTtlDays: 7, // ListenBrainz counts refresh after this many days
} as const;

// Deezer allows ~50 requests per rolling 5 s window; stay safely under it.
export const DEEZER_MIN_INTERVAL_MS = 125;
// MusicBrainz enforces 1 request per second for anonymous clients.
export const MB_MIN_INTERVAL_MS = 1100;
export const LB_MIN_INTERVAL_MS = 300;

export const MB_SEARCH_BATCH = 50; // ISRCs per batched search query
export const LB_BATCH = 100; // MBIDs per popularity request
