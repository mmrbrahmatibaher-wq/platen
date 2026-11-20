// Port of audio/SoundManager.kt for the browser: the same CC0 WAVs through Web Audio, with the same
// per-event volume / pitch randomisation (SoundMix) and the key sound delayed to the moment of
// impact. Browsers only allow audio after a user gesture, so the AudioContext is created (or
// resumed) on the first pointer or key event; samples are fetched early and decoded on unlock.
import { TypeEvent as E } from './engine/glyph.js';
import { RISE_MS } from './engine/strike.js';

export const SOUND_MIX = {
  KEY: [[0.81, 0.99], [0.96, 1.04]], STRIKE: [[0.81, 0.99], [0.96, 1.04]],
  BACKSPACE: [[0.60, 0.74], [0.96, 1.04]], ERASE: [[0.50, 0.62], [0.96, 1.04]],
  SPACE: [[0.81, 0.99], [0.96, 1.04]], RETURN: [[0.92, 1.0], [0.98, 1.02]],
  BELL: [[0.72, 0.82], [0.995, 1.005]], LIMIT: [[0.95, 1.0], [0.9, 1.0]],
};
export function mixFor(event, rnd = Math.random) {
  const [[v0, v1], [r0, r1]] = SOUND_MIX[event];
  return { volume: v0 + rnd() * (v1 - v0), rate: r0 + rnd() * (r1 - r0) };
}

const KEYS = Array.from({ length: 10 }, (_, i) => `key_${String(i + 1).padStart(2, '0')}`);
const FILES = [...KEYS, 'backspace', 'space', 'carriage_return', 'carriage_return_2', 'bell', 'bell_2', 'bell_3', 'jam'];

export class SoundPlayer {
  constructor(base = 'assets/sounds/') {
    this.base = base;
    this.ctx = null;
    this.raw = new Map();
    this.buffers = new Map();
    this.enabled = true;
    this.lastKey = -1;
    this.lastJam = 0;
    this.played = []; // for tests
    this._prefetch = Promise.all(FILES.map((f) => fetch(this.base + f + '.wav').then((r) => r.arrayBuffer()).then((b) => this.raw.set(f, b)).catch(() => {})));
    const unlock = () => this.unlock();
    window.addEventListener('pointerdown', unlock, { capture: true });
    window.addEventListener('keydown', unlock, { capture: true });
    window.addEventListener('touchend', unlock, { capture: true });
  }
  configure(enabled) { this.enabled = enabled; }
  get unlocked() { return !!this.ctx && this.ctx.state === 'running'; }

  async unlock() {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    if (!this.ctx) {
      this.ctx = new AC({ latencyHint: 'interactive' });
      this.master = this.ctx.createGain();
      this.master.connect(this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') { try { await this.ctx.resume(); } catch { /* ignore */ } }
    if (!this._decoding) {
      this._decoding = this._prefetch.then(() => Promise.all(FILES.map(async (f) => {
        const raw = this.raw.get(f);
        if (!raw || this.buffers.has(f)) return;
        try { this.buffers.set(f, await this.ctx.decodeAudioData(raw.slice(0))); } catch { /* ignore */ }
      })));
    }
  }

  _play(name, volume, rate, delayMs = 0) {
    const ctx = this.ctx, buf = this.buffers.get(name);
    if (!ctx || !buf || ctx.state !== 'running') return;
    const src = ctx.createBufferSource();
    src.buffer = buf;
    src.playbackRate.value = rate;
    const gain = ctx.createGain();
    gain.gain.value = volume;
    src.connect(gain).connect(this.master);
    src.start(ctx.currentTime + delayMs / 1000);
  }

  play(event) {
    this.played.push(event);
    if (!this.enabled) return;
    const p = mixFor(event);
    switch (event) {
      case E.KEY: case E.STRIKE: {
        let k;
        do { k = Math.floor(Math.random() * KEYS.length); } while (k === this.lastKey && KEYS.length > 1);
        this.lastKey = k;
        // Sounded when the type bar hits the paper on screen, not when the key went down.
        this._play(KEYS[k], p.volume, p.rate, RISE_MS);
        if (event === E.STRIKE) {
          const k2 = (k + 1 + Math.floor(Math.random() * (KEYS.length - 1))) % KEYS.length;
          this._play(KEYS[k2], p.volume * 0.45, p.rate, RISE_MS + 22);
        }
        break;
      }
      case E.BACKSPACE: case E.ERASE: this._play('backspace', p.volume, p.rate); break;
      case E.SPACE: this._play('space', p.volume, p.rate); break;
      case E.RETURN: this._play(Math.random() < 0.5 ? 'carriage_return' : 'carriage_return_2', p.volume, p.rate); break;
      case E.BELL: this._play(['bell', 'bell_2', 'bell_3'][Math.floor(Math.random() * 3)], p.volume, p.rate); break;
      case E.LIMIT: {
        const now = performance.now();
        if (now - this.lastJam > 250) { this.lastJam = now; this._play('jam', p.volume, p.rate); }
        break;
      }
      default: break;
    }
  }
}
