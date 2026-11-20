// Port of domain/Strike.kt — one keystroke, timed from the reference recording (see README).
import { isNewline, isSpace, sameGlyph } from './glyph.js';
export const RISE_MS = 32, STEP_DELAY_MS = 70, STEP_MS = 90, TYPING_LED_MS = 350;
export const Phase = Object.freeze({ LEVER_UP: 'LEVER_UP', IMPACT_STAMP: 'IMPACT_STAMP', LEVER_DOWN: 'LEVER_DOWN', CARRIAGE_STEP: 'CARRIAGE_STEP' });
export const timeline = Object.freeze([
  [Phase.LEVER_UP, 0], [Phase.IMPACT_STAMP, RISE_MS], [Phase.LEVER_DOWN, RISE_MS], [Phase.CARRIAGE_STEP, RISE_MS + STEP_DELAY_MS],
]);
export function struckCell(prev, prevCursor, next, nextCursor) {
  if (nextCursor !== prevCursor + 1) return -1;
  const k = prevCursor;
  if (k < 0 || k >= next.length) return -1;
  const g = next[k];
  if (isNewline(g) || (isSpace(g) && !g.s)) return -1;
  if (next.length === prev.length + 1) return k === next.length - 1 && sameUpTo(prev, next, k) ? k : -1;
  if (next.length === prev.length) return sameExcept(prev, next, k) ? k : -1;
  return -1;
}
function sameUpTo(a, b, n) {
  if (a.length < n || b.length < n) return false;
  for (let i = 0; i < n; i++) if (!sameGlyph(a[i], b[i])) return false;
  return true;
}
function sameExcept(a, b, k) {
  for (let i = 0; i < a.length; i++) if (i !== k && !sameGlyph(a[i], b[i])) return false;
  return true;
}
