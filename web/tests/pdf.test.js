import test from 'node:test';
import assert from 'node:assert/strict';
import { buildPdf } from '../js/export.js';

test('buildPdf writes a well-formed PDF with a correct xref table', () => {
  const fake = (n) => ({ w: 4, h: 6, bytes: new Uint8Array(n).fill(0xff) });
  const pdf = buildPdf([fake(10), fake(25)], 595.28, 841.89, 'Note — تست', new Date(Date.UTC(2026, 10, 20, 12, 0, 0)));
  const text = Buffer.from(pdf).toString('latin1');
  assert.ok(text.startsWith('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n'));
  assert.ok(text.trimEnd().endsWith('%%EOF'));
  assert.match(text, /\/Count 2 /);
  assert.match(text, /\/CreationDate \(D:20261120120000Z\)/);
  const startxref = Number(/startxref\n(\d+)/.exec(text)[1]);
  assert.equal(text.slice(startxref, startxref + 4), 'xref');
  const rows = text.slice(startxref).split('\n').slice(3).filter((l) => / 00000 n $/.test(l));
  assert.equal(rows.length, 3 + 2 * 3);
  rows.forEach((row, i) => {
    const off = Number(row.slice(0, 10));
    assert.equal(text.slice(off, off + `${i + 1} 0 obj`.length), `${i + 1} 0 obj`);
  });
});
