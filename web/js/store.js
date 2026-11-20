// Local persistence (the web stand-in for Room + DataStore): notes and settings in localStorage.
import { encode, decode } from './engine/glyph.js';
import { canCreate } from './engine/limits.js';

const NOTES = 'platen.notes', SETTINGS = 'platen.settings', NEXT = 'platen.nextId';

export const DEFAULT_SETTINGS = Object.freeze({
  sound: true, playInSilentMode: false, haptics: true,
  correction: 'DELETE', // "Backspace erases" is on by default since 3.2
  autocorrect: false, theme: 'CREAM', studio: false,
  lampOn: false, lampWarmth: 0.6, lampIntensity: 0.7, lampBeam: 0.5, charms: true,
});

function read(key, fallback) {
  try { const v = localStorage.getItem(key); return v == null ? fallback : JSON.parse(v); } catch { return fallback; }
}
function write(key, v) { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* quota / private mode */ } }

export class Store {
  constructor() {
    this.listeners = new Set();
    this.settings = { ...DEFAULT_SETTINGS, ...read(SETTINGS, {}) };
    this.notes = (read(NOTES, []) || []).map((n) => ({ id: n.id, glyphs: decode(n.g), createdAt: n.createdAt, updatedAt: n.updatedAt }));
    this.nextId = read(NEXT, 0) || this.notes.reduce((m, n) => Math.max(m, n.id), 0) + 1;
  }
  subscribe(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  _emit() { for (const fn of this.listeners) fn(); }
  _persistNotes() {
    write(NOTES, this.notes.map((n) => ({ id: n.id, g: encode(n.glyphs), createdAt: n.createdAt, updatedAt: n.updatedAt })));
    write(NEXT, this.nextId);
  }
  updateSettings(transform) {
    this.settings = { ...this.settings, ...transform(this.settings) };
    write(SETTINGS, this.settings);
    this._emit();
  }
  /** Newest first, like NoteDao.observeAll(). */
  list() { return [...this.notes].sort((a, b) => b.updatedAt - a.updatedAt || b.id - a.id); }
  get(id) { return this.notes.find((n) => n.id === id) || null; }
  /** Creates a blank note if the active-note rule allows it: { id } or { limit: true }. */
  create(initial = []) {
    if (!canCreate(this.notes.length, this.settings.studio)) return { limit: true };
    const now = Date.now();
    const id = this.nextId++;
    this.notes.push({ id, glyphs: initial, createdAt: now, updatedAt: now });
    this._persistNotes(); this._emit();
    return { id };
  }
  save(id, glyphs) {
    const n = this.get(id);
    if (!n) return;
    n.glyphs = glyphs; n.updatedAt = Date.now();
    this._persistNotes(); this._emit();
  }
  delete(id) {
    this.notes = this.notes.filter((n) => n.id !== id);
    this._persistNotes(); this._emit();
  }
}
