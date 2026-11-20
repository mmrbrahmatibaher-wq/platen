// Port of domain/Morse.kt — codec + the <morsecode> command state machine.
const TABLE = new Map(Object.entries({
  A: '.-', B: '-...', C: '-.-.', D: '-..', E: '.', F: '..-.', G: '--.', H: '....', I: '..', J: '.---',
  K: '-.-', L: '.-..', M: '--', N: '-.', O: '---', P: '.--.', Q: '--.-', R: '.-.', S: '...', T: '-',
  U: '..-', V: '...-', W: '.--', X: '-..-', Y: '-.--', Z: '--..',
  1: '.----', 2: '..---', 3: '...--', 4: '....-', 5: '.....', 6: '-....', 7: '--...', 8: '---..', 9: '----.', 0: '-----',
  '.': '.-.-.-', ',': '--..--', '?': '..--..', '!': '-.-.--', "'": '.----.', ':': '---...', ';': '-.-.-.', '=': '-...-',
  '+': '.-.-.', '-': '-....-', '@': '.--.-.', '/': '-..-.', '(': '-.--.', ')': '-.--.-', '&': '.-...', '"': '.-..-.',
}));
// Object.entries puts integer-like keys first; rebuild in the Kotlin order for the panel.
const REVERSE = new Map([...TABLE].map(([k, v]) => [v, k]));
export const LETTERS = Object.freeze('ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((c) => [c, TABLE.get(c)]));
export const DIGITS = Object.freeze('1234567890'.split('').map((c) => [c, TABLE.get(c)]));
export const codeFor = (c) => TABLE.get(c.toUpperCase()) ?? null;

export function toMorse(text) {
  return text.trim().toUpperCase().split(/\s+/)
    .map((w) => [...w].map((c) => TABLE.get(c)).filter(Boolean).join(' '))
    .filter((w) => w.length > 0).join(' / ');
}
export function toEnglish(morse) {
  return normalize(morse).trim().split(/\s*\/\s*|\s{3,}/)
    .map((w) => w.trim().split(/\s+/).filter((x) => x).map((code) => REVERSE.get(code) ?? '?').join(''))
    .filter((w) => w.length > 0).join(' ');
}
export function normalize(s) {
  let out = '';
  for (const c of s) {
    if (c === '\u2026') out += '...';
    else if ('\u2022\u00B7\u2219\u22C5\u25CF\u30FB\u2027'.includes(c)) out += '.';
    else if ('\u2013\u2014\u2212\u2010\u2011\u2012\u2015_\u02D7\uFE63\uFF0D'.includes(c)) out += '-';
    else if ('\u00A0\u2009\u202F'.includes(c)) out += ' ';
    else out += c;
  }
  return out;
}

export const MORSE_TRIGGER = '<morsecode>';
export const MorseDir = Object.freeze({ MORSE_TO_ENGLISH: 'MORSE_TO_ENGLISH', ENGLISH_TO_MORSE: 'ENGLISH_TO_MORSE' });
export const PREFIX = '=> ';
export const MENU = '1. Morse to English\n2. English to Morse\n';
export const NO_SPACE = '=> (no space)\n';
export function hintFor(dir) {
  return dir === MorseDir.MORSE_TO_ENGLISH
    ? 'Type . and - ; use / between words.\nEnter = translate. Empty line = exit.\n'
    : 'Type English text.\nEnter = translate. Empty line = exit.\n';
}

/** state: { kind: 'Off' } | { kind: 'Menu' } | { kind: 'On', dir } */
export class MorseController {
  constructor() { this.state = { kind: 'Off' }; }
  onEnter(line, room) {
    const s = this.state;
    if (s.kind === 'Off') {
      if (line.trimEnd() !== MORSE_TRIGGER) return null;
      if (MENU.length <= room) { this.state = { kind: 'Menu' }; return MENU; }
      return noSpace(room);
    }
    if (s.kind === 'Menu') {
      const t = line.trim();
      let dir;
      if (t === '1') dir = MorseDir.MORSE_TO_ENGLISH;
      else if (t === '2') dir = MorseDir.ENGLISH_TO_MORSE;
      else { this.state = { kind: 'Off' }; return null; }
      const hint = hintFor(dir);
      if (hint.length <= room) { this.state = { kind: 'On', dir }; return hint; }
      this.state = { kind: 'Off' }; return noSpace(room);
    }
    if (line.trim() === '') { this.state = { kind: 'Off' }; return null; }
    const result = (s.dir === MorseDir.MORSE_TO_ENGLISH ? toEnglish(line) : toMorse(line)) || '?';
    const out = `${PREFIX}${result}\n`;
    return out.length <= room ? out : noSpace(room);
  }
  reset() { this.state = { kind: 'Off' }; }
  get isOn() { return this.state.kind === 'On'; }
}
const noSpace = (room) => (NO_SPACE.length <= room ? NO_SPACE : null);
