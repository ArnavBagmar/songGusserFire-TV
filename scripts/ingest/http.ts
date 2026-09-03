/** HTTP plumbing shared by the API clients: pacing, retries, concurrency. */

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Connectivity-level failure (DNS, socket, offline) — retryable by rerunning. */
export class NetworkError extends Error {}

/** Serializes callers so requests to one host stay `minIntervalMs` apart. */
export class RateLimiter {
  readonly #minIntervalMs: number;
  #nextSlot = 0;

  constructor(minIntervalMs: number) {
    this.#minIntervalMs = minIntervalMs;
  }

  async wait(): Promise<void> {
    const now = Date.now();
    const slot = Math.max(now, this.#nextSlot);
    this.#nextSlot = slot + this.#minIntervalMs;
    if (slot > now) await sleep(slot - now);
  }
}

export interface FetchJsonOptions {
  readonly limiter: RateLimiter;
  readonly userAgent: string;
  readonly init?: RequestInit;
  readonly retries?: number;
  /** Return true when a 200 body still signals a retryable error (Deezer quota). */
  readonly retryOnBody?: (body: unknown) => boolean;
}

/**
 * Rate-limited fetch with exponential backoff on 429/5xx/network errors.
 * Returns null on 404 (a meaningful miss for MusicBrainz ISRC lookups);
 * throws on any other non-retryable failure.
 */
export async function fetchJson(
  url: string,
  options: FetchJsonOptions,
): Promise<unknown> {
  const retries = options.retries ?? 4;
  let lastError = new Error(`fetch never attempted for ${url}`);
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    await options.limiter.wait();
    try {
      const response = await fetch(url, {
        ...options.init,
        headers: {
          "User-Agent": options.userAgent,
          Accept: "application/json",
          ...(options.init?.headers ?? {}),
        },
      });
      if (response.ok) {
        const body = (await response.json()) as unknown;
        if (options.retryOnBody?.(body)) {
          lastError = new Error(`retryable API error from ${url}`);
        } else {
          return body;
        }
      } else if (response.status === 404) {
        return null;
      } else if (response.status === 429 || response.status >= 500) {
        lastError = new Error(`HTTP ${response.status} from ${url}`);
      } else {
        const detail = (await response.text()).slice(0, 200);
        throw new Error(`HTTP ${response.status} from ${url}: ${detail}`);
      }
    } catch (error) {
      // fetch signals network-level failures as TypeError; anything else is ours.
      if (!(error instanceof TypeError)) throw error;
      lastError = new NetworkError(
        `network error fetching ${url}: ${error.message}`,
      );
    }
    await sleep(500 * 2 ** attempt);
  }
  throw lastError;
}

/** Run `fn` over `items` with at most `limit` in flight; preserves order. */
export async function mapLimit<T, R>(
  items: readonly T[],
  limit: number,
  fn: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const results = new Array<R>(items.length);
  let nextIndex = 0;
  const workers = Array.from(
    { length: Math.min(limit, items.length) },
    async () => {
      while (true) {
        const index = nextIndex;
        nextIndex += 1;
        if (index >= items.length) return;
        results[index] = await fn(items[index], index);
      }
    },
  );
  await Promise.all(workers);
  return results;
}

export function chunk<T>(items: readonly T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let i = 0; i < items.length; i += size)
    chunks.push(items.slice(i, i + size));
  return chunks;
}
