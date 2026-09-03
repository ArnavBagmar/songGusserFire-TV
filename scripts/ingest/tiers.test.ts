import assert from "node:assert/strict";
import { test } from "node:test";
import { computeTiers, tierForPercentile, TIERS } from "./tiers.ts";

test("tierForPercentile maps 20% bands to the app's five tiers", () => {
  assert.equal(tierForPercentile(0), "EASY");
  assert.equal(tierForPercentile(0.19), "EASY");
  assert.equal(tierForPercentile(0.2), "MEDIUM");
  assert.equal(tierForPercentile(0.59), "HARD");
  assert.equal(tierForPercentile(0.79), "EXPERT");
  assert.equal(tierForPercentile(0.8), "IMPOSSIBLE");
  assert.equal(tierForPercentile(1), "IMPOSSIBLE");
});

test("tierForPercentile rejects out-of-range input", () => {
  assert.throws(() => tierForPercentile(-0.01), RangeError);
  assert.throws(() => tierForPercentile(1.01), RangeError);
  assert.throws(() => tierForPercentile(Number.NaN), RangeError);
});

test("computeTiers splits a distinct pool into five equal bands", () => {
  const inputs = Array.from({ length: 50 }, (_, i) => ({
    trackId: i + 1,
    listenCount: 5000 - i * 10,
  }));
  const results = computeTiers(inputs);
  const byTier = new Map<string, number>();
  for (const r of results) {
    const key = r.tier ?? "NONE";
    byTier.set(key, (byTier.get(key) ?? 0) + 1);
  }
  for (const tier of TIERS) assert.equal(byTier.get(tier), 10, tier);
  assert.equal(results.find((r) => r.trackId === 1)?.tier, "EASY");
  assert.equal(results.find((r) => r.trackId === 50)?.tier, "IMPOSSIBLE");
});

test("tied listen counts share a percentile and tier", () => {
  const inputs = [
    { trackId: 1, listenCount: 100 },
    { trackId: 2, listenCount: 100 },
    { trackId: 3, listenCount: 1 },
  ];
  const [a, b] = computeTiers(inputs);
  assert.equal(a.percentile, b.percentile);
  assert.equal(a.tier, b.tier);
});

test("null listen counts stay unrated and are excluded from the pool", () => {
  const inputs = [
    { trackId: 1, listenCount: 10 },
    { trackId: 2, listenCount: null },
  ];
  const results = computeTiers(inputs);
  assert.deepEqual(results.find((r) => r.trackId === 2), {
    trackId: 2,
    percentile: null,
    tier: null,
  });
  assert.equal(results.find((r) => r.trackId === 1)?.tier, "EASY");
});

test("computeTiers does not mutate its input", () => {
  const inputs = [
    { trackId: 2, listenCount: 5 },
    { trackId: 1, listenCount: 9 },
  ];
  const copy = structuredClone(inputs);
  computeTiers(inputs);
  assert.deepEqual(inputs, copy);
});
