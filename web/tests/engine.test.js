// Mirrors app/src/test/.../TypewriterEngineTest.kt, InputAndGraphemesTest.kt and V32DomainTest.kt (erase part).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TypewriterEngine, inputDiff, nearMargin, bellFor } from '../js/engine/engine.js';
import { CorrectionMode, TypeEvent as E, glyph, plainText, encode, decode } from '../js/engine/glyph.js';
import * as Graphemes from '../js/engine/graphemes.js';
import { sheetLayout } from '../js/engine/layout.js';

const texts = (e) => e.glyphs.map((g) => g.c);
const gl = (s) => [...s].map((c) => glyph(c));

test('types characters in order', () => {
  const e = new TypewriterEngine(); e.type('abc');
  assert.deepEqual(texts(e), ['a', 'b', 'c']); assert.equal(e.activeText(), 'abc'); assert.equal(e.activeCount, 3);
});
test('events for key, space and return', () => {
  assert.deepEqual(new TypewriterEngine().type('a b\n'), [E.KEY, E.SPACE, E.KEY, E.RETURN]);
});
test('empty input does nothing', () => { const e = new TypewriterEngine(); assert.deepEqual(e.type(''), []); assert.equal(e.size, 0); });
test('typewriter backspace only moves the carriage', () => {
  const e = new TypewriterEngine(); e.type('cat');
  assert.equal(e.backspace(), E.BACKSPACE); assert.equal(e.size, 3); assert.equal(e.cursor, 2);
  assert.ok(!e.glyphs.some((g) => g.s)); assert.equal(e.fieldText(), 'ca');
});
test('typing a different letter overstrikes the cell', () => {
  const e = new TypewriterEngine(); e.type('abc'); e.backspace(); e.backspace();
  assert.deepEqual(e.type('X'), [E.STRIKE]);
  assert.deepEqual(e.glyphs[1], { c: 'b', s: true, o: 'X' });
  assert.equal(e.fieldText(), 'aX'); assert.equal(plainText(e.glyphs), 'aXc'); assert.equal(plainText(e.glyphs, true), 'abc');
});
test('same letter re-inks and space just advances', () => {
  const e = new TypewriterEngine(); e.type('ab c'); for (let i = 0; i < 4; i++) e.backspace();
  assert.deepEqual(e.type('a '), [E.KEY, E.SPACE]); assert.ok(!e.glyphs.some((g) => g.s));
  assert.deepEqual(e.type('x'), [E.KEY]); assert.deepEqual(e.glyphs[2], { c: 'x' }); assert.equal(e.size, 4);
});
test('backspace stops at the left margin', () => {
  const e = new TypewriterEngine(); assert.equal(e.backspace(), null);
  e.type('ab\nc'); assert.equal(e.backspace(), E.BACKSPACE); assert.equal(e.backspace(), null); assert.equal(e.cursor, 3);
});
test('return always feeds at the end', () => {
  const e = new TypewriterEngine(); e.type('abc'); e.backspace(); e.backspace();
  assert.deepEqual(e.type('\n'), [E.RETURN]); assert.equal(plainText(e.glyphs), 'abc\n'); assert.equal(e.cursor, 4);
});
test('delete mode removes glyph and newline', () => {
  const e = new TypewriterEngine(); e.mode = CorrectionMode.DELETE; e.type('dog');
  assert.equal(e.backspace(), E.ERASE); assert.equal(e.activeText(), 'do');
  const f = new TypewriterEngine(); f.mode = CorrectionMode.DELETE; f.type('a\n'); f.backspace(); assert.deepEqual(f.glyphs, [{ c: 'a' }]);
});
test('legacy struck notes still load', () => {
  const e = new TypewriterEngine(); e.reset(decode('[{"c":"a"},{"c":"b","s":true},{"c":"c"}]'));
  assert.equal(e.size, 3); assert.ok(e.glyphs[1].s); assert.equal(e.glyphs[1].o, undefined); assert.equal(plainText(e.glyphs), 'ac');
});
test('carriage returns are normalised; tabs and control chars', () => {
  const e = new TypewriterEngine(); e.type('a\r\nb\rc'); assert.equal(e.activeText(), 'a\nb\nc'); assert.equal(e.size, 5);
  const f = new TypewriterEngine(); f.type('a\tb\u0007c\u0000'); assert.equal(f.activeText(), 'a bc');
});
test('hard limit on live characters', () => {
  const e = new TypewriterEngine(); const ev = e.type('x'.repeat(3005));
  assert.equal(e.size, 3000); assert.equal(ev[ev.length - 1], E.LIMIT); assert.ok(e.isFull); assert.deepEqual(e.type('y'), [E.LIMIT]);
});
test('limit counts newlines and spaces; overtyping never grows the sheet', () => {
  const e = new TypewriterEngine([], 4); e.type('a \nbc'); assert.equal(e.activeText(), 'a \nb');
  const f = new TypewriterEngine([], 3); f.type('abc'); assert.deepEqual(f.type('d'), [E.LIMIT]); f.backspace();
  assert.deepEqual(f.type('d'), [E.STRIKE]); for (let i = 0; i < 20; i++) { f.backspace(); f.type('q'); } assert.equal(f.size, 3);
});
test('surrogate pair emoji is one glyph', () => {
  const e = new TypewriterEngine(); e.type('a😀b'); assert.deepEqual(texts(e), ['a', '😀', 'b']);
  e.backspace(); e.backspace(); assert.equal(e.fieldText(), 'a'); e.type('z');
  assert.deepEqual(e.glyphs[1], { c: '😀', s: true, o: 'z' });
});
test('ZWJ family, flags and skin tones are single glyphs', () => {
  const e = new TypewriterEngine(); e.type('👨‍👩‍👧🇮🇷👍🏽'); assert.deepEqual(texts(e), ['👨‍👩‍👧', '🇮🇷', '👍🏽']);
});
test('lone surrogates are dropped', () => { const e = new TypewriterEngine(); e.type('a\uD83Db\uDE00c'); assert.equal(e.activeText(), 'abc'); });
test('combining mark and skin tone typed separately join the previous glyph', () => {
  const e = new TypewriterEngine(); e.type('e'); e.type('\u0301'); assert.deepEqual(texts(e), ['e\u0301']);
  const f = new TypewriterEngine(); f.type('👍'); f.type('🏽'); assert.deepEqual(texts(f), ['👍🏽']);
});
test('mark after newline or struck glyph does not join', () => {
  const e = new TypewriterEngine(); e.type('ab'); e.backspace(); e.type('x'); e.type('\u0301');
  assert.equal(e.size, 3); e.type('\n\u0301'); assert.equal(e.glyphs[3].c, '\n'); assert.equal(e.glyphs[4].c, '\u0301');
});
test('Persian text with ZWNJ and harakat', () => {
  const e = new TypewriterEngine();
  const text = '\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645 \u0633\u064E\u0644\u0627\u0645';
  e.type(text);
  assert.ok(e.glyphs.some((g) => g.c === '\u06CC\u200C')); assert.ok(e.glyphs.some((g) => g.c === '\u0633\u064E'));
  assert.equal(e.activeText(), text); assert.equal(e.activeCount, 12); e.backspace(); assert.equal(e.fieldText(), text.slice(0, -1));
});
test('column counts glyphs since last newline', () => {
  const e = new TypewriterEngine(); e.type('abc\nde'); assert.equal(e.column(), 2); e.backspace(); assert.equal(e.column(), 1); e.type('\n'); assert.equal(e.column(), 0);
});
test('margin bell rings at bell column; disabled by default', () => {
  const e = new TypewriterEngine(); e.bellColumn = 5; const ev = e.type('abcdefg');
  assert.equal(ev.filter((x) => x === E.BELL).length, 1); assert.equal(ev[5], E.BELL); e.type('\n'); assert.ok(e.type('abcde').includes(E.BELL));
  assert.ok(!new TypewriterEngine().type('x'.repeat(80)).includes(E.BELL));
  assert.equal(bellFor(40), 34); assert.equal(bellFor(11), 0); assert.ok(nearMargin(34, 40)); assert.ok(!nearMargin(33, 40));
});
test('reset restores state; glyphs is a snapshot', () => {
  const e = new TypewriterEngine(); e.reset([glyph('a'), glyph('b', true, 'x'), { c: '' }, glyph('c')]);
  assert.equal(e.size, 3); assert.equal(e.cursor, 3); assert.equal(e.fieldText(), 'axc');
  const f = new TypewriterEngine(); f.type('a'); const snap = f.glyphs; f.type('b'); assert.equal(snap.length, 1);
});
test('serialization round trip and compact tolerant codec', () => {
  const e = new TypewriterEngine(); e.type('Hi 😀\nسلام'); e.backspace(); e.type('é👍🏽');
  assert.ok(e.glyphs.some((g) => g.o === 'é')); assert.deepEqual(decode(encode(e.glyphs)), e.glyphs);
  assert.equal(encode([glyph('a'), glyph('b', true), glyph('c', true, 'd')]), '[{"c":"a"},{"c":"b","s":true},{"c":"c","s":true,"o":"d"}]');
  assert.deepEqual(decode('not json'), []); assert.deepEqual(decode(null), []);
  assert.deepEqual(decode('[{"c":"x","extra":1},{"c":""}]'), [{ c: 'x' }]);
});
test('plainText optionally includes struck', () => {
  const g = [glyph('a'), glyph('b', true), glyph('c'), glyph('d', true, 'e')];
  assert.equal(plainText(g), 'ace'); assert.equal(plainText(g, true), 'abcd');
});
test('erase removes back across line breaks (v3.2)', () => {
  const e = new TypewriterEngine(); e.mode = CorrectionMode.DELETE; e.type('Title\nab');
  assert.equal(e.backspace(), E.ERASE); assert.equal(e.backspace(), E.ERASE); assert.equal(e.fieldText(), 'Title\n');
  assert.equal(e.backspace(), E.ERASE); assert.equal(e.fieldText(), 'Title'); e.type('s'); assert.equal(e.fieldText(), 'Titles');
  for (let i = 0; i < 6; i++) e.backspace(); assert.equal(e.backspace(), null); assert.equal(e.activeCount, 0);
});
test('overstrike only without erase', () => {
  const d = new TypewriterEngine(); d.mode = CorrectionMode.DELETE; d.type('ab'); d.backspace();
  assert.deepEqual(d.type('c'), [E.KEY]); assert.deepEqual(d.glyphs, [{ c: 'a' }, { c: 'c' }]);
});
test('lastFinishedLine shows overstrikes as ■', () => {
  const e = new TypewriterEngine(); e.type('abc'); e.backspace(); e.type('x'); e.type('\n');
  assert.equal(e.lastFinishedLine(), 'ab\u25A0'); e.type('q'); assert.equal(e.lastFinishedLine(), ''); assert.equal(e.currentLine(), 'q');
});

// ---- graphemes + input diff
test('grapheme splitting', () => {
  assert.deepEqual(Graphemes.split('ab c'), ['a', 'b', ' ', 'c']); assert.deepEqual(Graphemes.split(''), []);
  assert.deepEqual(Graphemes.split('a\r\nb'), ['a', '\r\n', 'b']);
  assert.deepEqual(Graphemes.split('🇮🇷🇺🇸🇫'), ['🇮🇷', '🇺🇸', '🇫']);
  assert.deepEqual(Graphemes.split('1️⃣❤️'), ['1️⃣', '❤️']);
  assert.deepEqual(Graphemes.boundaries('a😀b'), [0, 1, 3, 4]);
});
const D = (d, i) => ({ deleteGlyphs: d, insert: i });
test('input diff', () => {
  assert.deepEqual(inputDiff('ab', 'abc'), D(0, 'c'));
  assert.deepEqual(inputDiff('he', 'hello '), D(0, 'llo '));
  assert.deepEqual(inputDiff('abc', 'ab'), D(1, ''));
  assert.deepEqual(inputDiff('a😀', 'a'), D(1, ''));
  assert.deepEqual(inputDiff('a😀', 'a\uD83D'), D(1, ''));
  assert.deepEqual(inputDiff('teh', 'the '), D(2, 'he '));
  assert.deepEqual(inputDiff('abc', ''), D(3, ''));
  assert.deepEqual(inputDiff('abc', 'abc'), D(0, ''));
  assert.deepEqual(inputDiff('e', 'e\u0301'), D(0, '\u0301'));
  assert.deepEqual(inputDiff('👍', '👍🏽'), D(0, '🏽'));
  assert.deepEqual(inputDiff('xa\u0301', 'xa\u0300'), D(1, 'a\u0300'));
  assert.deepEqual(inputDiff('سلام', 'سلا'), D(1, ''));
});

// ---- sheet layout
test('layout word-wraps, breaks long words, wide glyphs take two cells, spaces hang', () => {
  const l = sheetLayout(gl('hello world'), 8);
  assert.equal(l.rows[6], 1); assert.equal(l.cols[6], 0); assert.equal(l.rows[5], 0); // space hangs on row 0
  const long = sheetLayout(gl('abcdefghij'), 4); assert.equal(long.rows[4], 1); assert.equal(long.rowCount, 3);
  const wide = sheetLayout([glyph('😀'), glyph('a')], 10); assert.equal(wide.cols[1], 2);
  const full = sheetLayout(gl('abcd'), 4); assert.equal(full.cursorRow, 1); assert.equal(full.cursorCol, 0);
  const nl = sheetLayout(gl('ab\ncd'), 10); assert.equal(nl.rows[3], 1); assert.equal(nl.cursorCol, 2);
});
