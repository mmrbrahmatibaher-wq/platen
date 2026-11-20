// Port of domain/Graphemes.kt: a small grapheme-cluster splitter (combining marks incl. Persian
// harakat, ZWNJ/ZWJ, variation selectors, skin tones, ZWJ emoji, flags, tags, CRLF).
const MARK = /\p{M}/u;

export function isLoneSurrogate(cp) { return cp >= 0xd800 && cp <= 0xdfff; }
export function isRegionalIndicator(cp) { return cp >= 0x1f1e6 && cp <= 0x1f1ff; }
export function isPictographic(cp) {
  return (cp >= 0x1f000 && cp <= 0x1faff) || (cp >= 0x2600 && cp <= 0x27bf) ||
    (cp >= 0x2300 && cp <= 0x23ff) || (cp >= 0x2b00 && cp <= 0x2bff);
}
export function isExtender(cp) {
  if (cp === 0x200c || cp === 0x200d) return true;
  if ((cp >= 0xfe00 && cp <= 0xfe0f) || (cp >= 0xe0100 && cp <= 0xe01ef)) return true;
  if (cp >= 0x1f3fb && cp <= 0x1f3ff) return true;
  if (cp >= 0xe0020 && cp <= 0xe007f) return true;
  if (isLoneSurrogate(cp)) return false;
  return MARK.test(String.fromCodePoint(cp));
}

/** Code points of s, keeping lone surrogates as their own (invalid) code point. */
function codePoints(s) {
  const out = [];
  for (let i = 0; i < s.length;) {
    const cp = s.codePointAt(i);
    out.push(cp);
    i += cp > 0xffff ? 2 : 1;
  }
  return out;
}

function joins(prev, cp, riRun, cluster) {
  if (prev === 13 && cp === 10) return true;
  if (cluster.length === 1 && (cluster === '\n' || cluster === '\r')) return false;
  if (isLoneSurrogate(prev) || isLoneSurrogate(cp)) return false;
  if (isExtender(cp)) return true;
  if (prev === 0x200d && isPictographic(cp)) return true;
  if (isRegionalIndicator(cp) && isRegionalIndicator(prev) && riRun % 2 === 1) return true;
  return false;
}

export function split(s) {
  if (!s) return [];
  const out = [];
  let sb = '';
  let prev = -1, riRun = 0;
  for (const cp of codePoints(s)) {
    const join = sb.length > 0 && joins(prev, cp, riRun, sb);
    if (!join && sb.length > 0) { out.push(sb); sb = ''; }
    if (!join) riRun = 0;
    sb += isLoneSurrogate(cp) ? String.fromCharCode(cp) : String.fromCodePoint(cp);
    riRun = isRegionalIndicator(cp) ? riRun + 1 : 0;
    prev = cp;
  }
  if (sb.length > 0) out.push(sb);
  return out;
}

export function boundaries(s) {
  const parts = split(s);
  const res = [];
  let acc = 0;
  for (const p of parts) { res.push(acc); acc += p.length; }
  res.push(acc);
  return res;
}

export function count(s) { return split(s).length; }
