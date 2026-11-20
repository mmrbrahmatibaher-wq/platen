// A tiny animation system with the same semantics as Compose's Animatable: tween (cubic-bezier easing,
// optional delay) and spring (damping ratio + stiffness, unit mass). Starting a new animation on a
// value cancels the running one; every animateTo() returns a promise that resolves to true when the
// animation finished and false when it was interrupted.

export function cubicBezier(x1, y1, x2, y2) {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  const sx = (t) => ((ax * t + bx) * t + cx) * t;
  const sy = (t) => ((ay * t + by) * t + cy) * t;
  const dx = (t) => (3 * ax * t + 2 * bx) * t + cx;
  return (x) => {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    let t = x;
    for (let i = 0; i < 8; i++) {
      const e = sx(t) - x;
      if (Math.abs(e) < 1e-6) break;
      const d = dx(t);
      if (Math.abs(d) < 1e-6) break;
      t -= e / d;
    }
    let lo = 0, hi = 1;
    if (Math.abs(sx(t) - x) > 1e-5) {
      t = x;
      for (let i = 0; i < 30; i++) { const v = sx(t); if (Math.abs(v - x) < 1e-6) break; if (v < x) lo = t; else hi = t; t = (lo + hi) / 2; }
    }
    return sy(t);
  };
}
export const FastOutSlowIn = cubicBezier(0.4, 0, 0.2, 1);
export const LinearOutSlowIn = cubicBezier(0, 0, 0.2, 1);
export const Linear = (x) => x;

export const tween = (duration, easing = FastOutSlowIn, delay = 0) => ({ type: 'tween', duration, easing, delay });
export const spring = (dampingRatio = 1, stiffness = 1500, threshold = 0.01) => ({ type: 'spring', dampingRatio, stiffness, threshold });

export class AnimValue {
  constructor(value, clock) {
    this.value = value;
    this.velocity = 0;
    this.clock = clock;
    this.job = null;
  }
  get running() { return this.job != null; }
  snapTo(v) { this._cancel(); this.value = v; this.velocity = 0; this.clock.invalidate(); }
  stop() { this._cancel(); }
  _cancel() { if (this.job) { const j = this.job; this.job = null; j.resolve(false); } }
  animateTo(target, spec) {
    this._cancel();
    return new Promise((resolve) => {
      const job = { target, spec, resolve, start: this.clock.now(), from: this.value, v0: this.velocity, last: this.clock.now() };
      this.job = job;
      this.clock.add(this);
    });
  }
  /** Advances to time t (ms). Returns true while still running. */
  step(t) {
    const j = this.job;
    if (!j) return false;
    const s = j.spec;
    if (s.type === 'tween') {
      const el = t - j.start - (s.delay || 0);
      if (el < 0) return true;
      const p = s.duration <= 0 ? 1 : Math.min(1, el / s.duration);
      const prev = this.value;
      this.value = j.from + (j.target - j.from) * s.easing(p);
      this.velocity = (this.value - prev) / Math.max(1, t - j.last) * 1000;
      j.last = t;
      if (p >= 1) { this.value = j.target; this.velocity = 0; this.job = null; j.resolve(true); return false; }
      return true;
    }
    // spring: x'' = -k (x - target) - c x', integrated in 1 ms sub-steps (semi-implicit Euler).
    const k = s.stiffness, c = 2 * s.dampingRatio * Math.sqrt(k);
    let dt = Math.min(64, t - j.last);
    j.last = t;
    let x = this.value - j.target, v = this.velocity;
    while (dt > 0) {
      const h = Math.min(1, dt) / 1000;
      v += (-k * x - c * v) * h;
      x += v * h;
      dt -= 1;
    }
    this.value = j.target + x; this.velocity = v;
    if (Math.abs(x) < s.threshold && Math.abs(v) < s.threshold * 10) {
      this.value = j.target; this.velocity = 0; this.job = null; j.resolve(true); return false;
    }
    return true;
  }
}

/** Frame clock driving every AnimValue; calls onFrame each animation frame while anything moves. */
export class FrameClock {
  constructor(onFrame) {
    this.onFrame = onFrame;
    this.active = new Set();
    this.raf = 0;
    this.dirty = true;
    this._loop = this._loop.bind(this);
  }
  now() { return performance.now(); }
  add(av) { this.active.add(av); this.invalidate(); }
  invalidate() { this.dirty = true; if (!this.raf) this.raf = requestAnimationFrame(this._loop); }
  delay(ms) { return new Promise((r) => setTimeout(r, ms)); }
  _loop(t) {
    this.raf = 0;
    const now = this.now();
    for (const av of [...this.active]) if (!av.step(now)) this.active.delete(av);
    const wasDirty = this.dirty || this.active.size > 0;
    this.dirty = false;
    if (wasDirty) this.onFrame(now);
    if (this.active.size > 0 || this.dirty) this.raf = requestAnimationFrame(this._loop);
  }
}
