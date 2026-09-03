/**
 * Pure difficulty-tier math: a track's tier is the percentile of its
 * ListenBrainz listen_count within the rated pool, cut into the same five 20%
 * bands the app uses (DifficultyTier.kt). Raw counts live in the DB, so tiers
 * can be recomputed at any time without touching the network (--retier).
 */

export const TIERS = [
  "EASY",
  "MEDIUM",
  "HARD",
  "EXPERT",
  "IMPOSSIBLE",
] as const;
export type Tier = (typeof TIERS)[number];

export interface TierInput {
  readonly trackId: number;
  readonly listenCount: number | null;
}

export interface TierResult {
  readonly trackId: number;
  /** 0 = most played of the rated pool, approaching 1 = least played. */
  readonly percentile: number | null;
  /** null = no listen data, track cannot be rated. */
  readonly tier: Tier | null;
}

export function tierForPercentile(percentile: number): Tier {
  if (!Number.isFinite(percentile) || percentile < 0 || percentile > 1) {
    throw new RangeError(`percentile must be in [0, 1], got ${percentile}`);
  }
  // Multiply rather than divide by the band width: 0.6 / 0.2 is 2.999…96 in
  // IEEE 754 and would drop the exact 60% boundary into the wrong band.
  const index = Math.min(
    Math.floor(percentile * TIERS.length),
    TIERS.length - 1,
  );
  return TIERS[index];
}

/**
 * Percentile = fraction of the rated pool with a strictly higher listen count,
 * so tied counts always share a percentile (and therefore a tier).
 */
export function computeTiers(inputs: readonly TierInput[]): TierResult[] {
  const rated = inputs.filter((input) => input.listenCount !== null);
  const descending = [...rated].sort(
    (a, b) => (b.listenCount ?? 0) - (a.listenCount ?? 0),
  );
  const firstIndexOfCount = new Map<number, number>();
  descending.forEach((input, index) => {
    const count = input.listenCount ?? 0;
    if (!firstIndexOfCount.has(count)) firstIndexOfCount.set(count, index);
  });
  const poolSize = rated.length;
  return inputs.map((input) => {
    if (input.listenCount === null) {
      return { trackId: input.trackId, percentile: null, tier: null };
    }
    const percentile =
      poolSize === 0
        ? 0
        : (firstIndexOfCount.get(input.listenCount) ?? 0) / poolSize;
    return {
      trackId: input.trackId,
      percentile,
      tier: tierForPercentile(percentile),
    };
  });
}
