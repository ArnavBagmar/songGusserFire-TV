/**
 * Parser for kworb.net/spotify/songs.html — the all-time most-streamed
 * Spotify songs table. Pure functions, no I/O.
 */

export interface KworbSong {
  readonly artist: string;
  readonly title: string;
  readonly totalStreams: number;
  readonly dailyStreams: number;
  readonly position: number; // 1-based chart position at scrape time
}

const ROW_PATTERN =
  /<tr><td class="text"><div>(.*?)<\/div><\/td><td>([\d,]+)<\/td><td>([\d,]+)<\/td><\/tr>/g;

const ENTITIES: ReadonlyMap<string, string> = new Map([
  ["&amp;", "&"],
  ["&lt;", "<"],
  ["&gt;", ">"],
  ["&quot;", '"'],
  ["&#39;", "'"],
]);

function decodeEntities(text: string): string {
  return text
    .replace(/&#(\d+);/g, (_, code: string) =>
      String.fromCodePoint(Number(code)),
    )
    .replace(/&[a-z]+;|&quot;/g, (entity) => ENTITIES.get(entity) ?? entity);
}

function stripTags(html: string): string {
  return html.replace(/<[^>]*>/g, "");
}

function parseCount(text: string): number {
  return Number(text.replaceAll(",", ""));
}

/** Extract every song row; rows that don't split into "Artist - Title" are dropped. */
export function parseKworbSongs(html: string): KworbSong[] {
  const songs: KworbSong[] = [];
  for (const match of html.matchAll(ROW_PATTERN)) {
    const label = decodeEntities(stripTags(match[1])).trim();
    const separator = label.indexOf(" - ");
    if (separator <= 0) continue;
    const artist = label.slice(0, separator).trim();
    const title = label.slice(separator + 3).trim();
    if (!artist || !title) continue;
    songs.push({
      artist,
      title,
      totalStreams: parseCount(match[2]),
      dailyStreams: parseCount(match[3]),
      position: songs.length + 1,
    });
  }
  return songs;
}

/** Lowercased, diacritic- and punctuation-free form used for matching. */
export function normalizeName(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** Stable cache key for one kworb row: normalized artist + title. */
export function kworbKey(artist: string, title: string): string {
  return `${normalizeName(artist)}|${normalizeName(title)}`;
}
