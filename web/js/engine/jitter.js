// Port of domain/GlyphJitter.kt — deterministic per-glyph "imperfect type" (64-bit splitmix, BigInt).
const M64 = (1n << 64n) - 1n;
const toS64 = (x) => BigInt.asIntN(64, x);
function mix(x) {
  let z = BigInt.asUintN(64, x + 0x9E3779B97F4A7C15n);
  z = ((z ^ (z >> 30n)) * 0xBF58476D1CE4E5B9n) & M64;
  z = ((z ^ (z >> 27n)) * 0x94D049BB133111EBn) & M64;
  return z ^ (z >> 31n);
}
const cache = new Map();
function unit(index, seed, salt) {
  const key = `${seed}|${index}|${salt}`;
  let v = cache.get(key);
  if (v === undefined) {
    const x = toS64(BigInt(seed) * 1000003n + BigInt(index) * 31n + BigInt(salt));
    v = Number(mix(BigInt.asUintN(64, x)) >> 40n) / 16777216;
    if (cache.size > 60000) cache.clear();
    cache.set(key, v);
  }
  return v;
}
export const MAX_ROTATION = 0.5, MIN_ALPHA = 0.74, GHOST_SHARE = 0.18;
export const rotation = (i, seed) => (unit(i, seed, 11) * 2 - 1) * MAX_ROTATION;
export function alpha(i, seed) { const u = unit(i, seed, 23); return MIN_ALPHA + (1 - MIN_ALPHA) * (1 - u * u); }
export const offset = (i, seed) => unit(i, seed, 37) * 2 - 1;
export const strikeTilt = (i, seed) => unit(i, seed, 53) * 2 - 1;
export function ghostAlpha(i, seed) { const u = unit(i, seed, 71); return u < GHOST_SHARE ? 0.22 + 0.20 * (u / GHOST_SHARE) : 0; }
export const ghostDx = (i, seed) => unit(i, seed, 83) * 2 - 1;
export const ghostDy = (i, seed) => unit(i, seed, 97) * 2 - 1;
export const _unit = unit;
