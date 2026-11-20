// Mirrors MorseTest.kt, V32DomainTest.kt (title, strike) and DomainRulesTest.kt.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as M from '../js/engine/morse.js';
import * as Title from '../js/engine/title.js';
import * as Strike from '../js/engine/strike.js';
import * as J from '../js/engine/jitter.js';
import * as L from '../js/engine/limits.js';
import { PROMPTS, randomPrompt } from '../js/engine/prompts.js';
import { glyph } from '../js/engine/glyph.js';

const gl = (s) => [...s].map((c) => glyph(c));
const big = 3000;

test('morse to english', () => {
  assert.equal(M.toEnglish('.... . .-.. .-.. ---'), 'HELLO');
  assert.equal(M.toEnglish('.... .. / - .... . .-. .'), 'HI THERE');
  assert.equal(M.toEnglish('.... ..   - .... . .-. .'), 'HI THERE');
  assert.equal(M.toEnglish('  ... --- ...  '), 'SOS');
});
test('english to morse', () => {
  assert.equal(M.toMorse('SOS'), '... --- ...'); assert.equal(M.toMorse('sos'), '... --- ...');
  assert.equal(M.toMorse('Hi there'), '.... .. / - .... . .-. .'); assert.equal(M.toMorse('hi ~~~ '), '.... ..'); assert.equal(M.toMorse('~~ #'), '');
});
test('unknown codes become question marks', () => {
  assert.equal(M.toEnglish('.... ........ ..'), 'H?I'); assert.equal(M.toEnglish('abc'), '?'); assert.equal(M.toEnglish('\u25A0'), '?');
});
test('normalize fixes phone punctuation', () => {
  assert.equal(M.normalize('\u2013\u2014.'), '--.'); assert.equal(M.normalize('\u2212'), '-');
  assert.equal(M.normalize('\u2022\u00B7\u2219'), '...'); assert.equal(M.normalize('\u2026'), '...');
  assert.equal(M.toEnglish('\u2026 \u2014\u2014\u2014 \u2022\u2022\u2022'), 'SOS'); assert.equal(M.toEnglish('\u2014'), 'T');
});
test('letters for the panel are A to Z', () => {
  assert.equal(M.LETTERS.length, 26); assert.deepEqual(M.LETTERS[0], ['A', '.-']); assert.deepEqual(M.LETTERS[25], ['Z', '--..']);
  for (const [c, code] of M.LETTERS) assert.equal(M.toEnglish(code), c);
  assert.equal(M.DIGITS.length, 10);
});
test('trigger only on the exact line', () => {
  const m = new M.MorseController();
  for (const l of ['hello <morsecode>', '<morsecode', '<Morsecode>', '<morsecod\u25A0>']) assert.equal(m.onEnter(l, big), null);
  assert.equal(m.state.kind, 'Off'); assert.equal(m.onEnter(M.MORSE_TRIGGER, big), M.MENU); assert.equal(m.state.kind, 'Menu');
});
test('menu choices', () => {
  const m = new M.MorseController(); m.onEnter(M.MORSE_TRIGGER, big);
  assert.equal(m.onEnter('1', big), M.hintFor(M.MorseDir.MORSE_TO_ENGLISH)); assert.deepEqual(m.state, { kind: 'On', dir: M.MorseDir.MORSE_TO_ENGLISH });
  const m2 = new M.MorseController(); m2.onEnter(M.MORSE_TRIGGER, big);
  assert.equal(m2.onEnter('2', big), M.hintFor(M.MorseDir.ENGLISH_TO_MORSE));
  const m3 = new M.MorseController(); m3.onEnter(M.MORSE_TRIGGER, big); assert.equal(m3.onEnter('3', big), null); assert.equal(m3.state.kind, 'Off');
  m3.onEnter(M.MORSE_TRIGGER, big); assert.equal(m3.onEnter('a 1', big), null); assert.equal(m3.state.kind, 'Off');
});
test('translates each finished line and exits on empty enter', () => {
  const m = new M.MorseController(); m.onEnter(M.MORSE_TRIGGER, big); m.onEnter('1', big);
  assert.equal(m.onEnter('.... . .-.. .-.. ---', big), '=> HELLO\n'); assert.equal(m.onEnter('.... .. / - .... . .-. .', big), '=> HI THERE\n');
  assert.equal(m.onEnter('', big), null); assert.equal(m.state.kind, 'Off'); assert.equal(m.onEnter('.... ..', big), null);
  const e = new M.MorseController(); e.onEnter(M.MORSE_TRIGGER, big); e.onEnter('2', big);
  assert.equal(e.onEnter('SOS', big), '=> ... --- ...\n'); assert.equal(e.onEnter('~~', big), '=> ?\n'); assert.equal(e.onEnter('   ', big), null);
});
test('output that does not fit prints no space', () => {
  const m = new M.MorseController(); m.onEnter(M.MORSE_TRIGGER, big); m.onEnter('2', big);
  assert.equal(m.onEnter('SOS SOS', 20), M.NO_SPACE); assert.equal(m.onEnter('SOS SOS', 5), null);
  assert.equal(m.state.kind, 'On'); assert.equal(m.onEnter('S', 7), '=> ...\n');
  const menu = new M.MorseController(); assert.equal(menu.onEnter(M.MORSE_TRIGGER, 20), M.NO_SPACE); assert.equal(menu.state.kind, 'Off');
});

test('title is the first line', () => {
  const g = gl('My letter\nDear you,\nhello');
  assert.equal(Title.length(g), 9); assert.equal(Title.of(g), 'My letter'); assert.equal(Title.display(g), 'My letter');
  assert.equal(Title.body(g).map((x) => x.c).join(''), 'Dear you,\nhello');
  assert.ok(Title.isTitle(g, 0)); assert.ok(Title.isTitle(g, 8)); assert.ok(!Title.isTitle(g, 9));
});
test('title while typing the first line and old notes', () => {
  assert.equal(Title.of(gl('Half a tit')), 'Half a tit'); assert.equal(Title.display([]), Title.UNTITLED); assert.equal(Title.display(gl('\nbody')), Title.UNTITLED);
  const old = [glyph('T'), glyph('o', true, 'e'), glyph('a'), glyph('x', true), glyph('\n')];
  assert.equal(Title.of(old), 'Tea');
});
test('file stems are safe', () => {
  assert.equal(Title.fileStem(gl('My letter: to you!\nx'), 3), 'platen-my-letter-to-you');
  assert.equal(Title.fileStem(gl('   \nx'), 3), 'platen-note-3');
  assert.equal(Title.fileStem(gl('سلام دنیا'), 3), 'platen-سلام-دنیا');
  assert.ok(Title.fileStem(gl('a'.repeat(200)), 1).length <= 47);
});
test('long titles are shortened for display', () => {
  const t = Title.display(gl('x'.repeat(100)), 20); assert.equal(t.length, 20); assert.ok(t.endsWith('\u2026'));
});

test('struck cell is only a single key at the carriage', () => {
  const a = gl('Hel');
  assert.equal(Strike.struckCell(a, 3, gl('Hell'), 4), 3);
  assert.equal(Strike.struckCell(a, 3, gl('Hel '), 4), -1);
  assert.equal(Strike.struckCell(a, 3, gl('Hel\n'), 4), -1);
  assert.equal(Strike.struckCell(a, 3, gl('Hello'), 5), -1);
  assert.equal(Strike.struckCell(a, 3, gl('He'), 2), -1);
  assert.equal(Strike.struckCell(a, 3, a, 3), -1);
  assert.equal(Strike.struckCell(a, 3, gl('Xell'), 4), -1);
  assert.equal(Strike.struckCell(a, 1, [glyph('H'), glyph('e', true, 'a'), glyph('l')], 2), 1);
  assert.equal(Strike.struckCell(a, 1, a, 2), 1);
});
test('glyph is stamped at impact before the carriage steps', () => {
  const t = Object.fromEntries(Strike.timeline);
  assert.deepEqual(Strike.timeline.map((x) => x[0]), ['LEVER_UP', 'IMPACT_STAMP', 'LEVER_DOWN', 'CARRIAGE_STEP']);
  assert.equal(t.LEVER_UP + Strike.RISE_MS, t.IMPACT_STAMP); assert.ok(t.CARRIAGE_STEP > t.IMPACT_STAMP);
  assert.ok(Strike.RISE_MS + Strike.STEP_DELAY_MS + Strike.STEP_MS <= 250); assert.ok(Strike.RISE_MS >= 20 && Strike.RISE_MS <= 60);
});

test('free tier allows three notes; studio removes the cap', () => {
  assert.ok(L.canCreate(0, false)); assert.ok(L.canCreate(2, false)); assert.ok(!L.canCreate(3, false));
  assert.equal(L.remaining(2, false), 1); assert.equal(L.remaining(7, false), 0); assert.ok(L.canCreate(10000, true));
});
test('jitter is stable, bounded and varied', () => {
  for (let i = 0; i < 2000; i++) {
    const r = J.rotation(i, 42); assert.ok(Math.abs(r) <= 0.5); assert.equal(r, J.rotation(i, 42));
    const a = J.alpha(i, 42); assert.ok(a >= 0.74 && a <= 1); const o = J.offset(i, 42); assert.ok(o >= -1 && o <= 1);
  }
  const rs = new Set(Array.from({ length: 100 }, (_, i) => J.rotation(i, 1))); assert.ok(rs.size > 90);
  assert.notEqual(J.rotation(5, 1), J.rotation(5, 2));
  const many = Array.from({ length: 1000 }, (_, i) => J.rotation(i, 1)); assert.ok(many.some((r) => r < -0.4)); assert.ok(many.some((r) => r > 0.4));
});
test('there are 120 distinct prompts; random avoids repeat', () => {
  assert.equal(PROMPTS.length, 120); assert.equal(new Set(PROMPTS).size, 120); assert.ok(PROMPTS.every((p) => p.trim() && p.length < 120));
  let p = randomPrompt(); for (let i = 0; i < 200; i++) { const n = randomPrompt(p); assert.notEqual(n, p); p = n; }
});
test('export sizes and pagination', () => {
  assert.ok(Math.abs(L.ImageFormat.STORY.widthPx / L.ImageFormat.STORY.heightPx - 9 / 16) < 0.001);
  assert.equal(L.PaperSize.A4.widthPt, 595); assert.equal(L.PaperSize.A5.heightPt, L.PaperSize.A4.widthPt);
  assert.ok(Math.abs(L.PaperSize.A4.heightPt / L.PaperSize.A4.widthPt - Math.SQRT2) < 0.01);
  const tops = Array.from({ length: 10 }, (_, i) => i * 20), bottoms = tops.map((t) => t + 20);
  assert.deepEqual(L.pages(tops, bottoms, 85), [[0, 3], [4, 7], [8, 9]]); assert.deepEqual(L.pages(tops, bottoms, 200), [[0, 9]]);
  assert.equal(L.pages([], [], 100).length, 1); assert.deepEqual(L.pages([0, 300], [300, 310], 100), [[0, 0], [1, 1]]);
  assert.equal(L.fitScale(100, 200), 1); assert.ok(Math.abs(L.fitScale(400, 200) - 0.5) < 1e-4); assert.equal(L.fitScale(4000, 200), 0.4);
});

// ---- stage geometry (mirrors V32UiTest geometry tests)
import { stageGeometry } from '../js/stage/geometry.js';
test('portrait and landscape geometry', () => {
  const land = stageGeometry(1280, 889, 1, 1, false);
  assert.ok(Math.abs(land.paperLeft - 0.153 * 1280) < 0.5); assert.ok(Math.abs(land.paperWidth - 0.695 * 1280) < 0.5);
  assert.ok(Math.abs(land.drumTop - 0.326 * 889) < 0.5);
  const port = stageGeometry(889, 915, 1, 1, true);
  assert.equal(port.paperLeft, 0); assert.equal(port.paperWidth, 889);
  assert.ok(port.toolbarTop > port.carriageBottom); assert.ok(port.toolbarTop + port.toolbarHeight <= 915);
  assert.ok(port.drumTop >= 0.3 * 915 && port.drumTop <= 0.42 * 915);
});
test('rows roll over the drum', () => {
  const g = stageGeometry(1280, 889, 1, 1, false);
  const typing = g.project(g.baseline); assert.equal(typing.baseline, g.baseline); assert.equal(typing.scaleX, 1);
  let prev = typing, y = g.baseline, hidden = false;
  for (let i = 0; i < 60 && !hidden; i++) {
    y -= g.lineHeight; const p = g.project(y);
    if (!p) { hidden = true; break; }
    assert.ok(p.baseline <= prev.baseline); assert.ok(p.baseline >= g.drumTop - 0.5); assert.ok(p.scaleY <= prev.scaleY + 1e-4); prev = p;
  }
  assert.ok(hidden, 'old rows roll out of sight'); assert.ok(g.project(g.bendStart + 1));
});
