// Port of ui/stage/TypewriterStage.kt — the machine: dark stage, the arched sheet rolling over the
// drum, the steel rod with its rubber stops, and the carriage pill with its T-lever under the red
// typing cursor. Strike choreography (lever up -> stamp at impact -> lever drops with a dip ->
// carriage steps), Return with a springy slam, LEDs, blink, review drag and the night lamp.
import { stageGeometry } from './geometry.js';
import { AnimValue, FrameClock, tween, spring, FastOutSlowIn, LinearOutSlowIn } from './anim.js';
import { sheetLayout } from '../engine/layout.js';
import { nearMargin } from '../engine/engine.js';
import * as Strike from '../engine/strike.js';
import * as Jitter from '../engine/jitter.js';
import * as Title from '../engine/title.js';
import { rgba, lerpColor, PORTRAIT_VOID } from '../palette.js';

export const GLYPH_FONT = '"Special Elite", Tahoma, "Noto Naskh Arabic", "Noto Sans Arabic", "Segoe UI", sans-serif';
const ASCENT = 1440 / 2048; // Special Elite hhea ascent: Compose's firstBaseline for a one-line layout
const PLACEHOLDER = 'Begin here.';
const RTL_RE = /[\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFF]|\uD802[\uDC00-\uDFFF]|\uD83A[\uDC00-\uDFFF]/;
const RED = '#D9352B';
const LED = { green: '#55D86A', red: '#F0402E', offGreen: '#3B4A35', offDark: '#3C1C15', offPink: '#C9918A', amber: '#F0912E' };
const ROD_STOPS = [[0, '#2F3535'], [0.12, '#3A3F43'], [0.25, '#5C6165'], [0.36, '#A5AAAD'], [0.44, '#B4B9BD'], [0.53, '#707579'],
  [0.62, '#52575D'], [0.72, '#40454B'], [0.82, '#292E31'], [0.92, '#1A1C1E'], [1, '#131315']];

export function rtlParagraphs(glyphs) {
  const out = new Array(glyphs.length).fill(false);
  let start = 0;
  for (let i = 0; i <= glyphs.length; i++) {
    if (i === glyphs.length || glyphs[i].c === '\n') {
      let rtl = false;
      for (let k = start; k < i && !rtl; k++) if (RTL_RE.test(glyphs[k].c)) rtl = true;
      if (rtl) for (let k = start; k < i; k++) out[k] = true;
      start = i + 1;
    }
  }
  return out;
}
export function readableText(glyphs) {
  let s = '';
  for (const g of glyphs) { if (g.s && g.o != null) s += g.o; else if (!g.s) s += g.c; }
  return s;
}
export function ledColors(near, busy = false) {
  return [
    [near ? LED.offGreen : busy ? LED.amber : LED.green, !near],
    [near ? LED.red : LED.offDark, near],
    [LED.offPink, false],
  ];
}

export class TypewriterStage {
  /**
   * @param canvas the stage canvas
   * @param images map of name -> HTMLImageElement (paper_*, carriage_*, lever_t, rod_stop, charm_*)
   */
  constructor(canvas, images, { onColumns = () => {}, onGeometry = () => {} } = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.images = images;
    this.onColumns = onColumns;
    this.onGeometry = onGeometry;
    this.clock = new FrameClock(() => this.draw());
    this.lever = new AnimValue(0, this.clock);
    this.carX = new AnimValue(0, this.clock);
    this.row = new AnimValue(0, this.clock);
    this.drop = new AnimValue(0, this.clock);
    this.review = 0;
    this.reviewAnim = new AnimValue(0, this.clock);
    this.lampFade = new AnimValue(0, this.clock);
    this.palette = null;
    this.lamp = { on: false, warmth: 0.6, intensity: 0.7, beam: 0.5 };
    this.charms = true;
    this.blink = true;
    this.caretOn = true;
    this.busy = false;
    this.seed = 1;
    this.strokes = 0;
    this.shown = { glyphs: [], cursor: 0 };
    this.lastReal = { glyphs: [], cursor: 0 };
    this.effect = 0;
    this.g = null;
    this.dpr = 1;
    this.portrait = null;
    this.measureCache = new Map();
    this.textCache = null;
    this.patterns = new Map();
    this.lampCanvas = document.createElement('canvas');
    this._blinkTimer = 0;
    this._busyTimer = 0;
  }

  // ------------------------------------------------------------------------------- inputs
  setPalette(p) { if (this.palette !== p) { this.palette = p; this.textCache = null; this.patterns.clear(); this.clock.invalidate(); } }
  setCharms(on) { this.charms = on; this.clock.invalidate(); }
  setBlink(on) { this.blink = on; this._restartBlink(); }
  setLamp(l) {
    const was = this.lamp.on;
    this.lamp = { ...l };
    if (l.on && !was) { this.lampFade.snapTo(0); this.lampFade.animateTo(1, tween(450)); }
    this.clock.invalidate();
  }

  resize(cssW, cssH, portrait, dpr = window.devicePixelRatio || 1) {
    this.dpr = dpr;
    this.canvas.width = Math.max(1, Math.round(cssW * dpr));
    this.canvas.height = Math.max(1, Math.round(cssH * dpr));
    this.canvas.style.width = cssW + 'px';
    this.canvas.style.height = cssH + 'px';
    this.lampCanvas.width = this.canvas.width; this.lampCanvas.height = this.canvas.height;
    const old = this.g;
    this.g = stageGeometry(cssW, cssH, 1, 1, portrait);
    this.portrait = portrait;
    this.measureCache.clear();
    this.textCache = null;
    if (!old || old.columns !== this.g.columns) this.onColumns(this.g.columns);
    this.onGeometry(this.g);
    // Same as the LaunchedEffect keyed on the geometry: no hammer blow, the carriage just snaps.
    this.effect++;
    this.shown = this.lastReal;
    this.lever.snapTo(0);
    this.carX.snapTo(this._cursorX(this.shown.glyphs, this.shown.cursor));
    this.row.snapTo(this._layout(this.shown).cursorRow);
    this.drop.snapTo(this.shown.glyphs.length ? 1 : 0);
    this.clock.invalidate();
  }

  /** New editor state (same contract as the composable's parameters). */
  setState(glyphs, cursor, strokes, seed, { initial = false } = {}) {
    this.seed = seed;
    const keyedStroke = strokes !== this.strokes;
    if (keyedStroke) this._onStroke(strokes);
    this.strokes = strokes;
    if (initial || !this.g) {
      this.lastReal = this.shown = { glyphs, cursor };
      this.textCache = null;
      if (this.g) {
        this.carX.snapTo(this._cursorX(glyphs, cursor));
        this.row.snapTo(this._layout(this.shown).cursorRow);
        this.drop.snapTo(glyphs.length ? 1 : 0);
      }
      this.clock.invalidate();
      return;
    }
    const prev = this.lastReal;
    if (prev.glyphs === glyphs && prev.cursor === cursor) return;
    const next = { glyphs, cursor };
    this.lastReal = next;
    this._runEffect(prev, next, keyedStroke);
  }

  _onStroke(strokes) {
    if (strokes === 0) return;
    // amber "busy" LED for TYPING_LED_MS after each key
    this.busy = true;
    clearTimeout(this._busyTimer);
    this._busyTimer = setTimeout(() => { this.busy = false; this.clock.invalidate(); }, Strike.TYPING_LED_MS);
    // the next keystroke rolls a reviewed sheet back
    this.review = 0; this.reviewAnim.animateTo(0, tween(200));
    this._restartBlink();
  }

  _restartBlink() {
    clearTimeout(this._blinkTimer);
    this.caretOn = true;
    this.clock.invalidate();
    if (!this.blink) return;
    const tick = (ms) => { this._blinkTimer = setTimeout(() => { this.caretOn = !this.caretOn; this.clock.invalidate(); tick(530); }, ms); };
    tick(700);
  }

  async _runEffect(prev, next, keyed) {
    const id = ++this.effect;
    const live = () => id === this.effect;
    const g = this.g;
    const k = keyed ? Strike.struckCell(prev.glyphs, prev.cursor, next.glyphs, next.cursor) : -1;
    if (k < 0) {
      // No hammer blow: space, Return, erase, paste, load. The carriage just moves.
      const returned = sheetLayout(prev.glyphs, g.columns, prev.cursor).cursorRow !== sheetLayout(next.glyphs, g.columns, next.cursor).cursorRow;
      this._setShown(next);
      if (this.lever.value !== 0) this.lever.animateTo(0, tween(60));
      const x = this._cursorX(next.glyphs, next.cursor);
      if (!keyed) this.carX.snapTo(x);
      else await this.carX.animateTo(x, returned ? spring(0.42, 380) : tween(Strike.STEP_MS, FastOutSlowIn));
      return;
    }
    // 1. The carriage must stand under cell k (it may still be finishing the last step).
    this._setShown({ glyphs: prev.glyphs, cursor: k });
    const atK = this._cursorX(prev.glyphs, k);
    let settle = null;
    if (this.carX.value !== atK) settle = this.carX.animateTo(atK, tween(Strike.RISE_MS));
    // 2. Lever shoots up to the paper.
    await this.lever.animateTo(1, tween(Strike.RISE_MS, LinearOutSlowIn));
    if (!live()) return;
    if (settle) this.carX.stop();
    this.carX.snapTo(atK);
    // 3. Impact: the letter is on the paper.
    this._setShown(next);
    // 4. Lever drops back with a little dip below rest; the carriage steps once it is down.
    this.lever.animateTo(0, spring(0.5, 1100, 0.002));
    const x = this._cursorX(next.glyphs, next.cursor);
    await this.carX.animateTo(x, tween(Strike.STEP_MS, FastOutSlowIn, Strike.STEP_DELAY_MS));
  }

  _setShown(v) {
    this.shown = v;
    this.textCache = null;
    // animateFloatAsState(cursorRow) and LaunchedEffect(onDrum): animate whenever the target changes.
    const rowTarget = this._layout(v).cursorRow;
    if (rowTarget !== (this.row.running ? this.row.job.target : this.row.value)) this.row.animateTo(rowTarget, tween(260, FastOutSlowIn));
    const onDrum = v.glyphs.length > 0;
    const dropTarget = onDrum ? 1 : 0;
    if (dropTarget !== (this.drop.running ? this.drop.job.target : this.drop.value)) this.drop.animateTo(dropTarget, tween(onDrum ? 460 : 320, FastOutSlowIn));
    this.clock.invalidate();
  }

  // ----------------------------------------------------------------------------- review drag
  dragBy(dy) {
    const lay = this._layout(this.shown);
    this.review = Math.min(Math.max(0, this.review + dy), Math.max(0, lay.cursorRow * this.g.lineHeight));
    this.reviewAnim.animateTo(this.review, tween(200));
  }

  // ------------------------------------------------------------------------------- helpers
  _layout(v) {
    if (!this.g) return { cursorRow: 0, cursorCol: 0, rows: [], cols: [], widths: [] };
    if (v._lay && v._layCols === this.g.columns) return v._lay;
    v._lay = sheetLayout(v.glyphs, this.g.columns, v.cursor);
    v._layCols = this.g.columns;
    return v._lay;
  }
  _font(px = this.g.fontPx) { return `${px}px ${GLYPH_FONT}`; }
  measure(t) {
    let w = this.measureCache.get(t);
    if (w === undefined) {
      const c = this.ctx; c.save(); c.font = this._font(); c.direction = 'ltr'; w = c.measureText(t).width; c.restore();
      this.measureCache.set(t, w);
    }
    return w;
  }
  measureRtl(s) { const c = this.ctx; c.save(); c.font = this._font(); c.direction = 'rtl'; const w = c.measureText(s).width; c.restore(); return w; }

  _cursorX(glyphs, cursor, lay = sheetLayout(glyphs, this.g.columns, cursor)) {
    const g = this.g;
    const rtl = rtlParagraphs(glyphs);
    let rowRtl = false;
    if (cursor < glyphs.length) rowRtl = rtl[cursor];
    else if (cursor > 0 && glyphs[cursor - 1].c !== '\n') rowRtl = rtl[cursor - 1];
    if (!rowRtl) return g.cellCenterX(lay.cursorCol);
    let sb = '';
    for (let i = 0; i < Math.min(cursor, glyphs.length); i++) if (lay.rows[i] === lay.cursorRow && glyphs[i].c !== '\n') sb += glyphs[i].c;
    const w = sb ? this.measureRtl(sb) : 0;
    return Math.max(g.textRight - w - g.cell / 2, g.textLeft);
  }

  get targetX() { return this._cursorX(this.shown.glyphs, this.shown.cursor, this._layout(this.shown)); }
  get near() { return this.g ? nearMargin(this._layout(this.shown).cursorCol, this.g.columns) : false; }
  /** Test hook, like the composable's "strikeState" semantics node. */
  get strikeState() { return { inked: readableText(this.shown.glyphs), lever: this.lever.value, carX: this.carX.value, near: this.near, busy: this.busy }; }

  _pattern(name) {
    let p = this.patterns.get(name);
    if (!p) {
      const img = this.images[name];
      p = this.ctx.createPattern(img, 'repeat');
      this.patterns.set(name, p);
    }
    return p;
  }

  // ------------------------------------------------------------------------------- drawing
  draw() {
    const g = this.g, p = this.palette;
    if (!g || !p) return;
    const c = this.ctx;
    c.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    c.clearRect(0, 0, g.width, g.height);
    const d = this.drop.value;
    const hidden = -(g.cornerRy + 6 * g.unit);
    const paperTop = hidden + (g.drumTop - hidden) * d;
    const corner = 0.35 + 0.65 * d;
    const voidC = g.portrait ? lerpColor(p.stageSide, PORTRAIT_VOID, 0.8) : p.stageVoid;
    this._drawStageBack(c, g, p.stageSide, voidC);
    this._drawPaper(c, g, paperTop, g.cornerRx * corner, g.cornerRy * corner, p, d);
    c.save();
    this._sheetPath(c, g, paperTop, g.cornerRx * corner, g.cornerRy * corner);
    c.clip();
    this._drawText(c, g, p, d);
    if (this.shown.glyphs.length === 0) {
      c.font = this._font(); c.fillStyle = rgba(p.ink, 0.45); c.textBaseline = 'alphabetic'; c.direction = 'ltr';
      [...PLACEHOLDER].forEach((ch, i) => {
        if (ch === ' ') return;
        const w = this.measure(ch);
        c.fillText(ch, g.textLeft + i * g.cell + (g.cell - w) / 2, g.baseline);
      });
    }
    this._drawRodShadowOnPaper(c, g);
    c.restore();
    if (this.caretOn) {
      const cw = g.cell * 0.95, ch = Math.max(g.cell * 0.15, 1.5);
      c.fillStyle = RED;
      c.fillRect(this.targetX - cw / 2, g.cursorY - ch / 2, cw, ch);
    }
    this._drawRod(c, g);
    this._drawStops(c, g);
    const near = this.near;
    this._drawCarriage(c, g, this.carX.value, near, this.busy, this.lever.value, p);
    if (this.lamp.on) this._drawLamp(c, g, this.carX.value, near);
  }

  _drawStageBack(c, g, side, voidC) {
    if (g.portrait) { c.fillStyle = voidC; c.fillRect(0, 0, g.width, g.height); return; }
    c.fillStyle = side; c.fillRect(0, 0, g.width, g.height);
    let gr = c.createLinearGradient(0, 0, g.paperLeft, 0);
    gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,0.18)');
    c.fillStyle = gr; c.fillRect(0, 0, g.paperLeft, g.height);
    gr = c.createLinearGradient(g.paperRight, 0, g.width, 0);
    gr.addColorStop(0, 'rgba(0,0,0,0.18)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    c.fillStyle = gr; c.fillRect(g.paperRight, 0, g.width - g.paperRight, g.height);
    c.fillStyle = voidC; c.fillRect(g.paperLeft, 0, g.paperWidth, g.height);
  }

  _sheetPath(c, g, top, rx, ry) {
    const l = g.paperLeft, r = g.paperRight, b = g.height + 4;
    c.beginPath();
    c.moveTo(l, b);
    c.lineTo(l, top + ry);
    c.ellipse(l + rx, top + ry, rx, ry, 0, Math.PI, Math.PI * 1.5);
    c.lineTo(r - rx, top);
    c.ellipse(r - rx, top + ry, rx, ry, 0, Math.PI * 1.5, Math.PI * 2);
    c.lineTo(r, b);
    c.closePath();
  }

  _drawPaper(c, g, top, rx, ry, p, onDrum) {
    for (const [dy, a] of [[2, 0.30], [5, 0.16], [10, 0.08]]) {
      this._sheetPath(c, g, top - dy * g.unit * 0.6, rx, ry);
      c.fillStyle = `rgba(0,0,0,${a})`; c.fill();
    }
    // Texture stays on the sheet; drawn at device pixels like the Android ImageShader.
    const pat = this._pattern(p.paperTex);
    pat.setTransform(new DOMMatrix().translateSelf(g.paperLeft, top).scaleSelf(1 / this.dpr, 1 / this.dpr));
    this._sheetPath(c, g, top, rx, ry);
    c.fillStyle = pat; c.fill();
    c.save();
    this._sheetPath(c, g, top, rx, ry); c.clip();
    if (onDrum > 0) {
      const gr = c.createLinearGradient(0, g.drumTop, 0, g.bendStart);
      gr.addColorStop(0, `rgba(0,0,0,${(p.isDark ? 0.22 : 0.07) * onDrum})`); gr.addColorStop(1, 'rgba(0,0,0,0)');
      c.fillStyle = gr; c.fillRect(g.paperLeft, g.drumTop, g.paperWidth, g.bendStart - g.drumTop);
    }
    const e = p.isDark ? 0.26 : 0.05;
    const gr = c.createLinearGradient(g.paperLeft, 0, g.paperRight, 0);
    gr.addColorStop(0, `rgba(0,0,0,${e})`); gr.addColorStop(0.04, 'rgba(0,0,0,0)'); gr.addColorStop(0.96, 'rgba(0,0,0,0)'); gr.addColorStop(1, `rgba(0,0,0,${e})`);
    c.fillStyle = gr; c.fillRect(g.paperLeft, top, g.paperWidth, g.height - top + 4);
    this._sheetPath(c, g, top, rx, ry);
    c.strokeStyle = `rgba(255,255,255,${p.isDark ? 0.05 : 0.18})`; c.lineWidth = 1.2 * g.unit; c.stroke();
    c.restore();
  }

  _drawRodShadowOnPaper(c, g) {
    const top = g.rodCenter - g.rodThickness / 2, bot = g.rodCenter + g.rodThickness / 2;
    let gr = c.createLinearGradient(0, top - g.rodThickness * 0.6, 0, top);
    gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,0.10)');
    c.fillStyle = gr; c.fillRect(0, top - g.rodThickness * 0.6, g.width, g.rodThickness * 0.6);
    gr = c.createLinearGradient(0, bot, 0, bot + g.rodThickness * 0.9);
    gr.addColorStop(0, 'rgba(0,0,0,0.32)'); gr.addColorStop(0.5, 'rgba(0,0,0,0.10)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    c.fillStyle = gr; c.fillRect(0, bot, g.width, g.rodThickness * 0.9);
  }

  _drawRod(c, g) {
    const top = g.rodCenter - g.rodThickness / 2;
    const gr = c.createLinearGradient(0, top, 0, top + g.rodThickness);
    for (const [o, col] of ROD_STOPS) gr.addColorStop(o, col);
    c.fillStyle = gr; c.fillRect(0, top, g.width, g.rodThickness);
    const sh = c.createLinearGradient(0, 0, g.width, 0);
    sh.addColorStop(0, 'rgba(255,255,255,0.30)'); sh.addColorStop(0.5, 'rgba(255,255,255,0.08)'); sh.addColorStop(1, 'rgba(255,255,255,0.18)');
    c.fillStyle = sh; c.fillRect(0, top + g.rodThickness * 0.38, g.width, g.rodThickness * 0.05);
  }

  _drawStops(c, g) {
    const img = this.images.rod_stop;
    const u = g.stopWidth / 26, w = 42 * u, h = 60 * u;
    for (const x of [g.paperLeft + 7 * g.unit, g.paperRight - 7 * g.unit - g.stopWidth]) {
      c.drawImage(img, Math.round(x - 8 * u), Math.round(g.rodCenter - g.stopHeight / 2 - 8 * u), Math.round(w), Math.round(h));
    }
  }

  _drawLeds(c, g, cx, near, glowBoost = 1, busy = false) {
    const u = g.unit, x0 = cx - g.carriageWidth / 2, y0 = g.carriageTop;
    ledColors(near, busy).forEach(([col, lit], i) => {
      const x = x0 + (24 + 19 * i) * u, y = y0 + 36 * u;
      if (lit) {
        const gr = c.createRadialGradient(x, y, 0, x, y, 12 * u);
        gr.addColorStop(0, rgba(col, Math.min(1, 0.45 * glowBoost))); gr.addColorStop(1, rgba(col, 0));
        c.fillStyle = gr; c.beginPath(); c.arc(x, y, 12 * u, 0, Math.PI * 2); c.fill();
      }
      c.fillStyle = col; c.beginPath(); c.arc(x, y, 4.6 * u, 0, Math.PI * 2); c.fill();
      c.fillStyle = `rgba(255,255,255,${lit ? 0.45 : 0.18})`; c.beginPath(); c.arc(x - 1.4 * u, y - 1.6 * u, 1.6 * u, 0, Math.PI * 2); c.fill();
    });
  }

  _drawCarriage(c, g, cx, near, busy, strike, p) {
    const u = g.unit;
    this._drawLever(c, g, cx, strike * g.strikeRise);
    c.drawImage(this.images[p.carriageTex], Math.round(cx - g.carriageWidth / 2 - 22 * u), Math.round(g.carriageTop - 22 * u), Math.round(208 * u), Math.round(114 * u));
    this._drawLeds(c, g, cx, near, 1, busy);
    if (this.charms) {
      const x0 = cx - g.carriageWidth / 2;
      p.charms.slice(0, 2).forEach((name, i) => {
        const s = 20 * u, x = x0 + (112 + 24 * i) * u, y = g.carriageTop + 36 * u;
        c.drawImage(this.images[name], Math.round(x - s / 2), Math.round(y - s / 2), Math.round(s), Math.round(s));
      });
    }
  }

  _drawLever(c, g, cx, rise) {
    const img = this.images.lever_t, u = g.unit;
    const left = Math.round(cx - 20 * u), w = Math.round(40 * u);
    const top = g.leverTop - 2 * u, bottom = top + 47 * u;
    const pxPerUnit = img.naturalHeight / 47;
    const barSrcH = Math.round(10.5 * pxPerUnit);
    const barTop = top - rise, barH = 10.5 * u;
    c.drawImage(img, 0, barSrcH, img.naturalWidth, img.naturalHeight - barSrcH, left, Math.round(barTop + barH), w, Math.max(1, Math.round(bottom - barTop - barH)));
    c.drawImage(img, 0, 0, img.naturalWidth, barSrcH, left, Math.round(barTop), w, Math.round(barH));
  }

  // ---------------------------------------------------------------------------- sheet text
  _drawText(c, g, p, onDrum) {
    // The sheet text only changes with the inked glyphs, the line feed, the review drag and the roll
    // onto the drum; between those (every strike frame) a cached layer is reused.
    const key = `${this.row.value.toFixed(4)}|${this.reviewAnim.value.toFixed(2)}|${onDrum.toFixed(4)}`;
    const tc = this.textCache;
    if (tc && tc.key === key) { c.save(); c.setTransform(1, 0, 0, 1, 0, 0); c.drawImage(tc.canvas, 0, 0); c.restore(); return; }
    const layer = tc?.canvas || document.createElement('canvas');
    layer.width = this.canvas.width; layer.height = this.canvas.height;
    const lc = layer.getContext('2d');
    lc.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    lc.clearRect(0, 0, g.width, g.height);
    this._drawSheetText(lc, g, p, onDrum);
    this.textCache = { key, canvas: layer };
    c.save(); c.setTransform(1, 0, 0, 1, 0, 0); c.drawImage(layer, 0, 0); c.restore();
  }

  _drawSheetText(c, g, p, onDrum) {
    const vis = this.shown;
    const glyphs = vis.glyphs;
    if (!glyphs.length) return;
    const layout = this._layout(vis);
    const rtl = rtlParagraphs(glyphs);
    const titleLen = Title.length(glyphs);
    const seed = this.seed;
    const ink = p.ink;
    const wobble = g.cell * 0.05;
    const typingRowFlat = g.baseline + this.reviewAnim.value;
    const row = this.row.value;
    const rows = new Map();
    const rowOf = (r) => {
      if (rows.has(r)) return rows.get(r);
      const flat = typingRowFlat + (r - row) * g.lineHeight;
      let res;
      if (flat > g.height + g.lineHeight) res = null;
      else if (onDrum >= 1) res = g.project(flat);
      else {
        const pr = g.project(flat);
        res = pr == null ? { baseline: flat, scaleX: 1, scaleY: 1, alpha: 1 - onDrum }
          : { baseline: flat + (pr.baseline - flat) * onDrum, scaleX: 1 + (pr.scaleX - 1) * onDrum, scaleY: 1 + (pr.scaleY - 1) * onDrum, alpha: 1 + (pr.alpha - 1) * onDrum };
      }
      rows.set(r, res);
      return res;
    };
    const cx = g.paperCenterX;
    const rtlRows = new Map();
    c.font = this._font(); c.textBaseline = 'alphabetic'; c.direction = 'ltr';
    for (let i = 0; i < glyphs.length; i++) {
      const gl = glyphs[i];
      if (gl.c === '\n') continue;
      const r = layout.rows[i];
      const pr = rowOf(r);
      if (!pr) continue;
      if (rtl[i]) { if (!rtlRows.has(r)) rtlRows.set(r, []); rtlRows.get(r).push(gl); continue; }
      if (gl.c === ' ' && !gl.s) continue;
      const base = pr.baseline;
      const cellX = g.textLeft + layout.cols[i] * g.cell;
      const cellW = layout.widths[i] * g.cell;
      const title = i < titleLen;
      const a = (title ? 1 : Jitter.alpha(i, seed)) * pr.alpha;
      c.save();
      c.translate(cx, base); c.scale(pr.scaleX, pr.scaleY); c.translate(-cx, -base);
      if (gl.s) this._drawOverstrike(c, gl, cellX, cellW, base, g, ink, a, i, seed);
      else {
        const rot = Jitter.rotation(i, seed);
        const dy = Jitter.offset(i, seed) * wobble;
        this._drawGlyph(c, g, gl.c, cellX, cellW, base, ink, a, rot, dy, i, seed, wobble);
        if (title) this._drawGlyph(c, g, gl.c, cellX + g.cell * 0.075, cellW, base, ink, a, rot, dy, i, seed, 0);
      }
      c.restore();
    }
    for (const [r, list] of rtlRows) {
      const pr = rowOf(r);
      if (!pr) continue;
      const s = list.map((x) => (x.s && x.o != null ? x.o : x.c)).join('');
      c.save();
      c.translate(cx, pr.baseline); c.scale(pr.scaleX, pr.scaleY); c.translate(-cx, -pr.baseline);
      c.direction = 'rtl'; c.textAlign = 'right';
      c.fillStyle = rgba(ink, 0.9 * pr.alpha);
      c.fillText(s, g.textRight, pr.baseline);
      c.restore();
    }
  }

  _drawGlyph(c, g, text, cellX, cellW, baseline, ink, alpha, rotation, dy, index, seed, wobble) {
    const w = this.measure(text);
    const sx = w > cellW * 1.04 ? (cellW * 1.04) / w : 1;
    const left = cellX + (cellW - w * sx) / 2;
    const firstBaseline = g.fontPx * ASCENT;
    const top = baseline - firstBaseline + dy;
    const t = Jitter.ghostDx(index, seed), b = Jitter.ghostDy(index, seed);
    const topA = Math.min(1, Math.max(0.35, alpha * (0.86 + 0.14 * t)));
    const botA = Math.min(1, Math.max(0.35, alpha * (0.86 - 0.14 * b)));
    const h = Math.ceil(g.fontPx);
    const gr = c.createLinearGradient(0, 0, 0, h);
    gr.addColorStop(0, rgba(ink, topA)); gr.addColorStop(1, rgba(ink, botA));
    c.save();
    const px = cellX + cellW / 2, py = baseline - firstBaseline * 0.35;
    c.translate(px, py); c.rotate((rotation * Math.PI) / 180); c.translate(-px, -py);
    c.translate(left, top); c.scale(sx, 1);
    c.fillStyle = gr;
    c.fillText(text, 0, firstBaseline);
    const ghost = Jitter.ghostAlpha(index, seed);
    if (ghost > 0) {
      c.globalAlpha = ghost;
      c.fillText(text, Jitter.ghostDx(index, seed) * wobble * 1.4, firstBaseline + Jitter.ghostDy(index, seed) * wobble * 0.8);
    }
    c.restore();
  }

  /** Two different type faces hammered into one cell: a dense, smudged block of ink. */
  _drawOverstrike(c, gl, cellX, cellW, baseline, g, ink, alpha, index, seed) {
    const capTop = baseline - g.fontPx * 0.68, h = g.fontPx * 0.80;
    const j = Jitter.strikeTilt(index, seed) * g.cell * 0.05;
    c.fillStyle = rgba(ink, 0.55 * alpha); c.fillRect(cellX + g.cell * 0.06 + j, capTop + j, cellW - g.cell * 0.12, h);
    c.fillStyle = rgba(ink, 0.30 * alpha); c.fillRect(cellX + g.cell * 0.12 - j, capTop - g.fontPx * 0.03, cellW - g.cell * 0.2, h * 1.02);
    const faces = [gl.c, gl.o].filter((f) => f != null);
    faces.forEach((f, k) => {
      if (!f.trim()) return;
      const w = this.measure(f);
      const sx = w > cellW * 1.04 ? (cellW * 1.04) / w : 1;
      const left = cellX + (cellW - w * sx) / 2 + (k === 0 ? -0.4 : 0.4) * g.unit;
      c.save(); c.translate(left, baseline); c.scale(sx, 1);
      c.fillStyle = rgba(ink, 0.95); c.fillText(f, 0, 0);
      c.restore();
    });
  }

  // ---------------------------------------------------------------------------- night lamp
  _drawLamp(main, g, leverX, near) {
    const lamp = this.lamp, L = this.lampCanvas, c = L.getContext('2d');
    const W = g.width, H = g.height;
    c.setTransform(1, 0, 0, 1, 0, 0); c.globalCompositeOperation = 'source-over'; c.clearRect(0, 0, L.width, L.height);
    c.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    const ox = leverX, oy = g.leverTop;
    c.fillStyle = `rgba(11,9,6,${0.74 + 0.14 * lamp.intensity})`; c.fillRect(0, 0, W, H);
    const spread = (34 + 30 * lamp.beam) * (Math.PI / 180);
    const reach = H * (0.50 + 0.22 * lamp.intensity);
    const far = H * 2;
    const warm = lerpColor('#FFE7C2', '#FFB45A', lamp.warmth);
    c.globalCompositeOperation = 'destination-out';
    for (let k = 0; k < 16; k++) {
      const t = k / 15;
      const half = spread * (0.45 + 0.75 * t);
      const rad = reach * (0.75 + 0.35 * (1 - t));
      const gr = c.createRadialGradient(ox, oy, 0, ox, oy, rad);
      gr.addColorStop(0, 'rgba(0,0,0,0.085)'); gr.addColorStop(0.35, 'rgba(0,0,0,0.07)'); gr.addColorStop(0.75, 'rgba(0,0,0,0.025)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
      c.beginPath();
      c.moveTo(ox, oy + 2 * g.unit);
      c.lineTo(ox - far * Math.sin(half), oy - far * Math.cos(half));
      c.lineTo(ox + far * Math.sin(half), oy - far * Math.cos(half));
      c.closePath();
      c.fillStyle = gr; c.fill();
    }
    const pool = H * (0.16 + 0.06 * lamp.intensity);
    const pcx = ox, pcy = oy - pool * 0.25;
    let gr = c.createRadialGradient(pcx, pcy, 0, pcx, pcy, pool);
    gr.addColorStop(0, 'rgba(0,0,0,0.55)'); gr.addColorStop(0.5, 'rgba(0,0,0,0.3)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    c.fillStyle = gr; c.beginPath(); c.arc(pcx, pcy, pool, 0, Math.PI * 2); c.fill();
    c.globalCompositeOperation = 'source-atop';
    const amber = lerpColor('#8A7A62', '#B8742C', lamp.warmth);
    gr = c.createRadialGradient(ox, oy, 0, ox, oy, reach * 0.85);
    gr.addColorStop(0, rgba(amber, 0.8)); gr.addColorStop(0.5, rgba(amber, 0.35)); gr.addColorStop(1, rgba(amber, 0));
    c.fillStyle = gr; c.fillRect(0, 0, W, H);
    c.globalCompositeOperation = 'source-over';
    const br = g.carriageWidth * 0.12;
    gr = c.createRadialGradient(ox, oy, 0, ox, oy, br);
    gr.addColorStop(0, rgba(warm, 0.6)); gr.addColorStop(0.5, rgba(warm, 0.08)); gr.addColorStop(1, rgba(warm, 0));
    c.fillStyle = gr; c.beginPath(); c.arc(ox, oy, br, 0, Math.PI * 2); c.fill();
    c.fillStyle = rgba(warm, 0.9); c.fillRect(ox - 11 * g.unit, oy + 7.6 * g.unit, 22 * g.unit, 1.4 * g.unit);
    main.save(); main.setTransform(1, 0, 0, 1, 0, 0); main.globalAlpha = this.lampFade.value; main.drawImage(L, 0, 0); main.restore();
    main.save(); main.globalAlpha = this.lampFade.value; this._drawLeds(main, g, leverX, near, 1.6); main.restore();
  }
}
