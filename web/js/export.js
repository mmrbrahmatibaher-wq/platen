// Port of export/NoteExporter.kt: a shareable PNG (4:5 or 9:16 story) and a print-ready PDF letter
// (A4 / US Letter / A5) with the date, paginated by whole lines. Everything is drawn on a canvas;
// the PDF is assembled by a tiny hand-written writer (no libraries, nothing fetched at runtime).
import * as Title from './engine/title.js';
import * as Jitter from './engine/jitter.js';
import { pages as paginate, fitScale } from './engine/limits.js';
import { rgba } from './palette.js';
import { GLYPH_FONT } from './stage/stage.js';

const ASCENT = 1440 / 2048, DESCENT = 608 / 2048; // Special Elite hhea metrics
const RTL_RE = /[\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFF]/;

/**
 * StaticLayout stand-in: word-wrapped lines (line spacing 1.3), each glyph with its own ink
 * density; the title line bold and 1.08x; overstruck cells print as the key typed over them on a
 * dark ink block. Returns { lines: [{ top, bottom, baseline, rtl, items: [{ text, x, w, alpha, title, struck }] }], height }.
 */
export function layoutText(ctx, glyphs, seed, fontPx, width) {
  const titleLen = Title.length(glyphs);
  const fontOf = (title) => `${title ? 'bold ' : ''}${title ? fontPx * 1.08 : fontPx}px ${GLYPH_FONT}`;
  const widthCache = new Map();
  const measure = (t, title) => {
    const k = (title ? 'T' : 'N') + t;
    let w = widthCache.get(k);
    if (w === undefined) { ctx.font = fontOf(title); w = ctx.measureText(t).width; widthCache.set(k, w); }
    return w;
  };
  const paragraphs = [[]];
  glyphs.forEach((g, i) => {
    if (g.c === '\n') { paragraphs.push([]); return; }
    paragraphs[paragraphs.length - 1].push({ text: g.s && g.o != null ? g.o : g.c, alpha: i < titleLen ? 1 : Jitter.alpha(i, seed), title: i < titleLen, struck: !!g.s, space: g.c === ' ' && !g.s });
  });
  const lines = [];
  let y = 0;
  const pushLine = (items, rtl) => {
    const big = items.some((it) => it.title);
    const size = big ? fontPx * 1.08 : fontPx;
    const h = size * (ASCENT + DESCENT) * 1.3;
    lines.push({ top: y, bottom: y + h, baseline: y + size * ASCENT, rtl, items });
    y += h;
  };
  for (const para of paragraphs) {
    const rtl = para.some((it) => RTL_RE.test(it.text));
    if (!para.length) { pushLine([], false); continue; }
    // words with their trailing spaces (spaces hang past the margin, like StaticLayout)
    const words = [];
    let cur = [];
    for (const it of para) {
      if (it.space) { cur.push(it); words.push(cur); cur = []; } else {
        if (cur.length && cur[cur.length - 1].space) { words.push(cur); cur = []; }
        cur.push(it);
      }
    }
    if (cur.length) words.push(cur);
    let line = [], x = 0;
    const flush = () => { pushLine(line, rtl); line = []; x = 0; };
    for (const word of words) {
      const solid = word.filter((it) => !it.space);
      const ww = solid.reduce((s, it) => s + measure(it.text, it.title), 0);
      if (x > 0 && x + ww > width) flush();
      for (const it of word) {
        const w = measure(it.text, it.title);
        if (!it.space && x > 0 && x + w > width && ww > width) flush();
        line.push({ ...it, x, w });
        x += w;
      }
    }
    flush();
  }
  return { lines, height: y };
}

function drawLines(ctx, lay, ink, fontPx, width, from = 0, to = lay.lines.length - 1) {
  ctx.textBaseline = 'alphabetic';
  for (let li = from; li <= to; li++) {
    const line = lay.lines[li];
    if (line.rtl) {
      const s = line.items.map((it) => it.text).join('');
      ctx.font = `${fontPx}px ${GLYPH_FONT}`; ctx.direction = 'rtl'; ctx.textAlign = 'right';
      ctx.fillStyle = rgba(ink, 0.92); ctx.fillText(s.trimEnd(), width, line.baseline);
      ctx.direction = 'ltr'; ctx.textAlign = 'left';
      continue;
    }
    for (const it of line.items) {
      if (it.space) continue;
      const size = it.title ? fontPx * 1.08 : fontPx;
      if (it.struck) { ctx.fillStyle = rgba(ink, 0.62); ctx.fillRect(it.x, line.baseline - size * ASCENT, it.w, size * (ASCENT + DESCENT)); }
      ctx.font = `${it.title ? 'bold ' : ''}${size}px ${GLYPH_FONT}`;
      ctx.fillStyle = rgba(ink, it.alpha);
      ctx.fillText(it.text, it.x, line.baseline);
    }
  }
}

/** renderImage(): a canvas of format.widthPx x format.heightPx. */
export function renderImage(glyphs, seed, format, palette, paperImg) {
  const w = format.widthPx, h = format.heightPx;
  const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
  const c = cv.getContext('2d');
  const story = format.key === 'STORY';
  const sheet = story ? { l: 72, t: 150, r: w - 72, b: h - 210 } : { l: 0, t: 0, r: w, b: h };
  const sw = sheet.r - sheet.l, sh = sheet.b - sheet.t;
  const pat = c.createPattern(paperImg, 'repeat');
  if (story) {
    c.fillStyle = palette.background; c.fillRect(0, 0, w, h);
    c.fillStyle = 'rgba(0,0,0,0.235)'; c.beginPath(); c.roundRect(sheet.l + 6, sheet.t + 14, sw, sh, 28); c.fill();
    c.fillStyle = pat; c.beginPath(); c.roundRect(sheet.l, sheet.t, sw, sh, 24); c.fill();
  } else { c.fillStyle = pat; c.fillRect(0, 0, w, h); }
  const margin = 84, footer = 110;
  const contentW = sw - 2 * margin, contentH = sh - margin - footer;
  let size = 42;
  let lay = layoutText(c, glyphs, seed, size, contentW);
  let guard = 0;
  while (lay.height > contentH && size > 14 && guard++ < 12) {
    size = Math.max(14, size * fitScale(lay.height, contentH, 0.5) * 0.98);
    lay = layoutText(c, glyphs, seed, size, contentW);
  }
  c.save();
  c.beginPath(); c.rect(sheet.l + margin, sheet.t + margin * 0.5, contentW, contentH + margin * 0.5); c.clip();
  c.translate(sheet.l + margin, sheet.t + margin);
  drawLines(c, lay, palette.ink, size, contentW);
  c.restore();
  c.font = 'bold 30px "Courier Prime", monospace';
  if ('letterSpacing' in c) c.letterSpacing = '4.5px';
  c.textAlign = 'center'; c.fillStyle = palette.muted;
  c.fillText('platen', w / 2, story ? h - 110 : sheet.b - 52);
  if ('letterSpacing' in c) c.letterSpacing = '0px';
  const vg = c.createRadialGradient(sheet.l + sw / 2, sheet.t + sh / 2, 0, sheet.l + sw / 2, sheet.t + sh / 2, Math.max(sw, sh) * 0.75);
  vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(0.6, 'rgba(0,0,0,0)'); vg.addColorStop(1, `rgba(0,0,0,${(palette.isDark ? 60 : 26) / 255})`);
  c.fillStyle = vg;
  if (story) { c.beginPath(); c.roundRect(sheet.l, sheet.t, sw, sh, 24); c.fill(); } else c.fillRect(0, 0, w, h);
  return cv;
}

/** writePdf(): page canvases (white, date on page 1, page numbers) -> PDF bytes. */
export async function renderPdf(glyphs, seed, paper, { date = new Date(), dpi = 200 } = {}) {
  const S = dpi / 72;
  const ink = '#1C1816';
  const measureCanvas = document.createElement('canvas').getContext('2d');
  const contentWidth = paper.widthPt - 2 * paper.marginPt, contentHeight = paper.heightPt - 2 * paper.marginPt;
  const lay = layoutText(measureCanvas, glyphs, seed, paper.fontPt, contentWidth);
  const headerGap = paper.fontPt * 3;
  const available = contentHeight - headerGap;
  const ranges = paginate(lay.lines.map((l) => l.top), lay.lines.map((l) => l.bottom), available);
  const dateText = new Intl.DateTimeFormat('en-US', { dateStyle: 'long' }).format(date);
  const jpegs = [];
  for (let index = 0; index < ranges.length; index++) {
    const [first, last] = ranges[index];
    const cv = document.createElement('canvas');
    cv.width = Math.round(paper.widthPt * S); cv.height = Math.round(paper.heightPt * S);
    const c = cv.getContext('2d');
    c.fillStyle = '#FFFFFF'; c.fillRect(0, 0, cv.width, cv.height);
    c.scale(S, S);
    const m = paper.marginPt;
    c.font = `${paper.fontPt}px ${GLYPH_FONT}`; c.fillStyle = 'rgb(90,84,78)';
    if (index === 0) { c.textAlign = 'right'; c.fillText(dateText, paper.widthPt - m, m); c.textAlign = 'left'; }
    if (last >= first) {
      const top = lay.lines[first].top;
      c.save(); c.translate(m, m + headerGap - top);
      drawLines(c, lay, ink, paper.fontPt, contentWidth, first, last);
      c.restore();
    }
    if (ranges.length > 1) {
      c.font = `${paper.fontPt * 0.8}px ${GLYPH_FONT}`; c.fillStyle = 'rgb(90,84,78)'; c.textAlign = 'center';
      c.fillText(`\u2014 ${index + 1} \u2014`, paper.widthPt / 2, paper.heightPt - m / 2);
    }
    const blob = await new Promise((r) => cv.toBlob(r, 'image/jpeg', 0.92));
    jpegs.push({ bytes: new Uint8Array(await blob.arrayBuffer()), w: cv.width, h: cv.height });
  }
  return { bytes: buildPdf(jpegs, paper.widthPt, paper.heightPt, Title.display(glyphs), date), pages: ranges.length };
}

function pdfString(s) {
  if (/^[\x20-\x7E]*$/.test(s)) return '(' + s.replace(/[\\()]/g, (m) => '\\' + m) + ')';
  let hex = 'FEFF';
  for (let i = 0; i < s.length; i++) hex += s.charCodeAt(i).toString(16).padStart(4, '0').toUpperCase();
  return '<' + hex + '>';
}

/** Minimal PDF 1.4 writer: one full-page JPEG (DCTDecode) per page. */
export function buildPdf(pages, wPt, hPt, title = 'Platen note', date = new Date()) {
  const enc = new TextEncoder();
  const chunks = []; let length = 0; const offsets = [];
  const add = (x) => { const b = typeof x === 'string' ? enc.encode(x) : x; chunks.push(b); length += b.length; };
  add('%PDF-1.4\n'); add(new Uint8Array([0x25, 0xE2, 0xE3, 0xCF, 0xD3, 0x0A]));
  const n = pages.length;
  // object numbers: 1 catalog, 2 pages, 3 info, then per page: page, content, image
  const pageObj = (i) => 4 + i * 3;
  const obj = (num, body) => { offsets[num] = length; add(`${num} 0 obj\n`); body(); add('\nendobj\n'); };
  obj(1, () => add('<< /Type /Catalog /Pages 2 0 R >>'));
  obj(2, () => add(`<< /Type /Pages /Count ${n} /Kids [${pages.map((_, i) => `${pageObj(i)} 0 R`).join(' ')}] >>`));
  const d = date;
  const pad = (v) => String(v).padStart(2, '0');
  const pdfDate = `D:${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())}Z`;
  obj(3, () => add(`<< /Title ${pdfString(title)} /Producer (Platen web) /Creator (Platen) /CreationDate (${pdfDate}) >>`));
  pages.forEach((p, i) => {
    const po = pageObj(i), co = po + 1, io = po + 2;
    obj(po, () => add(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${wPt} ${hPt}] /Resources << /XObject << /Im0 ${io} 0 R >> /ProcSet [/PDF /ImageC] >> /Contents ${co} 0 R >>`));
    const content = `q ${wPt} 0 0 ${hPt} 0 0 cm /Im0 Do Q`;
    obj(co, () => add(`<< /Length ${content.length} >>\nstream\n${content}\nendstream`));
    obj(io, () => {
      add(`<< /Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.bytes.length} >>\nstream\n`);
      add(p.bytes); add('\nendstream');
    });
  });
  const total = 4 + n * 3;
  const xref = length;
  let x = `xref\n0 ${total}\n0000000000 65535 f \n`;
  for (let i = 1; i < total; i++) x += String(offsets[i]).padStart(10, '0') + ' 00000 n \n';
  add(x);
  add(`trailer\n<< /Size ${total} /Root 1 0 R /Info 3 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
  const out = new Uint8Array(length); let o = 0;
  for (const c of chunks) { out.set(c, o); o += c.length; }
  return out;
}

/** Hands a file to the user: the share sheet on phones (when supported), a download otherwise. */
export async function deliver(blob, name, title) {
  const file = new File([blob], name, { type: blob.type });
  const coarse = window.matchMedia?.('(pointer: coarse)').matches;
  if (coarse && navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title }); return 'shared'; } catch (e) { if (e?.name === 'AbortError') return 'cancelled'; }
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
  return 'downloaded';
}
