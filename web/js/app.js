// Platen web — app shell: screens (Box / editor / settings / Studio), hash routing with a back
// stack (like PlatenApp.kt's route stack), asset preloading and the audio unlock.
import { Store } from './store.js';
import { SoundPlayer } from './audio.js';
import { Editor } from './ui/editor.js';
import { paletteFor, rgba, THEMES, THEME_LABEL } from './palette.js';
import * as Title from './engine/title.js';
import { MAX_ACTIVE } from './engine/engine.js';
import { FREE_ACTIVE_NOTES } from './engine/limits.js';
import { h, icon, switchEl, slider, dialog } from './ui/dom.js';

const IMAGES = ['paper_cream', 'paper_mint', 'paper_charcoal', 'paper_garden', 'carriage_cream', 'carriage_mint', 'carriage_charcoal',
  'carriage_garden', 'lever_t', 'rod_stop', 'charm_coffee', 'charm_star', 'charm_heart', 'charm_flower', 'charm_moon', 'wordmark'];

export const CREDITS =
  'Typewriter sounds: real recordings of a Hermes Precisa 305 by Joseph Sardin, BigSoundBank.com ' +
  '(CC0 1.0 public domain), plus a bell by _stubb on Freesound (CC0), trimmed and loudness-matched for Platen.\n' +
  'Typeface: Special Elite by Astigmatic (Apache License 2.0).\n' +
  'UI typeface: Courier Prime by Alan Dague-Greene (SIL Open Font License 1.1).\n' +
  'Icons: Material Symbols / Material Icons by Google (Apache License 2.0).\n' +
  'Machine, paper and icon artwork rendered for this app.';

export function applyThemeVars(p) {
  const st = document.body.style;
  st.background = p.background;
  st.setProperty('--dialog-bg', p.card); st.setProperty('--dialog-ink', p.onBackground); st.setProperty('--dialog-muted', p.muted);
}

class App {
  constructor(root) {
    this.root = root;
    this.store = new Store();
    this.sound = new SoundPlayer();
    this.images = {};
    this.stack = ['list'];
    this.screen = null;
  }

  async start() {
    await Promise.all([
      ...IMAGES.map((n) => new Promise((res) => { const img = new Image(); img.onload = img.onerror = () => res(); img.src = `assets/img/${n}.webp`; this.images[n] = img; })),
      document.fonts ? Promise.all(['16px "Special Elite"', '16px "Courier Prime"', 'bold 16px "Courier Prime"'].map((f) => document.fonts.load(f).catch(() => {}))) : Promise.resolve(),
    ]);
    this.store.subscribe(() => this.onStoreChange());
    window.addEventListener('popstate', () => this.fromHash());
    window.addEventListener('hashchange', () => this.fromHash());
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        const scrim = document.querySelector('.scrim');
        if (scrim) { scrim.remove(); e.preventDefault(); return; }
        if (this.screen instanceof Editor && this.screen.panel) { this.screen.panel = null; this.screen.renderChrome(); return; }
        if (this.stack.length > 1 && !(this.screen instanceof Editor)) this.back();
      }
    });
    window.addEventListener('pagehide', () => { if (this.screen instanceof Editor) this.screen.saveNow(); });
    document.addEventListener('visibilitychange', () => { if (document.hidden && this.screen instanceof Editor) this.screen.saveNow(); });
    this.fromHash(true);
    document.documentElement.classList.add('ready');
  }

  // ------------------------------------------------------------------------- routing
  fromHash(first = false) {
    const route = (location.hash || '#/').slice(2) || 'list';
    if (route === this.stack[this.stack.length - 1] && !first) return;
    const idx = this.stack.lastIndexOf(route);
    if (idx >= 0) this.stack = this.stack.slice(0, idx + 1);
    else this.stack.push(route);
    if (first && route !== 'list') this.stack = ['list', route];
    this.render();
  }
  _setHash(route) { const want = route === 'list' ? '#/' : `#/${route}`; if (location.hash !== want) history.replaceState(history.state, '', want); }
  go(route) {
    this.stack.push(route);
    history.pushState({ platen: true }, '', route === 'list' ? '#/' : `#/${route}`);
    this.render();
  }
  replace(route) { this.stack[this.stack.length - 1] = route; this._setHash(route); this.render(); }
  back() {
    // Pushed by go(): let the browser pop it (so the system back button and ours agree).
    if (this.stack.length > 1 && history.state?.platen) { history.back(); return; }
    this.stack = this.stack.length > 1 ? this.stack.slice(0, -1) : ['list'];
    this._setHash(this.route);
    this.render();
  }
  get route() { return this.stack[this.stack.length - 1]; }

  render() {
    if (this.screen?.unmount) this.screen.unmount();
    document.querySelectorAll('.scrim').forEach((s) => s.remove());
    this.root.replaceChildren();
    const p = paletteFor(this.store.settings.theme);
    applyThemeVars(p);
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', p.stageSide);
    const r = this.route;
    const m = /^note\/(\d+)$/.exec(r);
    if (m) {
      const ed = new Editor(this, Number(m[1]));
      if (!ed.load()) { this.stack = ['list']; this._setHash('list'); return this.render(); }
      this.screen = ed;
      ed.mount(this.root);
      document.title = `${Title.display(ed.engine.glyphs)} · Platen`;
      return;
    }
    document.title = 'Platen';
    if (r === 'settings') this.screen = this.renderSettings();
    else if (r === 'studio') this.screen = this.renderStudio();
    else this.screen = this.renderList();
  }

  onStoreChange() {
    if (this.screen instanceof Editor) return; // the editor listens itself
    const y = this.root.querySelector('.scroll')?.scrollTop || 0;
    this.render();
    const sc = this.root.querySelector('.scroll'); if (sc) sc.scrollTop = y;
  }

  toast(msg) {
    const t = h('div', { class: 'toast', text: msg });
    document.body.append(t);
    setTimeout(() => t.remove(), 3500);
  }

  showLimitDialog() {
    const d = dialog({
      title: 'Three sheets on the desk', tag: 'limitDialog', onDismiss: () => d.remove(),
      body: `The free version keeps ${FREE_ACTIVE_NOTES} active notes. Finish or remove one, or open Studio for more.`,
      buttons: [{ text: 'OK', onClick: () => d.remove() }, { text: 'See Studio', onClick: () => { d.remove(); this.go('studio'); } }],
    });
    document.body.append(d);
  }

  // ------------------------------------------------------------------------- notes list (Box)
  renderList() {
    const p = paletteFor(this.store.settings.theme);
    const studio = this.store.settings.studio;
    const notes = this.store.list();
    const wordmark = p.isDark
      ? h('div', { class: 'wordmark tinted', role: 'img', 'aria-label': 'platen', style: { background: p.onBackground } })
      : h('img', { class: 'wordmark', src: 'assets/img/wordmark.webp', alt: 'platen' });
    const screen = h('div', { class: 'screen list', style: { background: p.background, color: p.onBackground } },
      h('div', { class: 'list-head' },
        h('div', { class: 'grow' }, wordmark),
        h('button', { class: 'chip-button', 'data-tag': 'studioChip', onClick: () => this.go('studio'), style: { background: studio ? p.indicator : p.card, color: studio ? p.paper : p.onBackground }, text: studio ? 'Studio' : `Free \u00B7 ${notes.length}/${FREE_ACTIVE_NOTES}` }),
        h('button', { class: 'icon-button', 'data-tag': 'settings', 'aria-label': 'Settings', onClick: () => this.go('settings'), style: { color: p.onBackground } }, icon('settings', 24))),
      h('div', { class: 'subtitle', style: { color: p.muted }, text: 'Short notes. Real keys. No going back.' }),
      h('div', { style: { height: '10px' } }));
    if (!notes.length) {
      screen.append(h('div', { class: 'empty' },
        h('div', { class: 'empty-title', 'data-tag': 'emptyState', text: 'A blank sheet is waiting.' }),
        h('div', { class: 'empty-sub', style: { color: p.muted }, text: `Letters, lists, small thoughts. Up to ${MAX_ACTIVE} characters each.` }),
        h('button', { class: 'text-button', style: { color: p.indicator }, text: 'Start typing', onClick: () => this.newNote() })));
    } else {
      const list = h('div', { class: 'scroll notes', 'data-tag': 'notesList' });
      for (const n of notes) list.append(this.noteCard(n, p));
      screen.append(list);
    }
    screen.append(h('button', { class: 'fab', 'data-tag': 'newNote', onClick: () => this.newNote(), style: { background: p.roller, color: p.capsule } }, icon('add', 24), h('span', { text: 'Insert paper' })));
    this.root.append(screen);
    return { unmount() {} };
  }

  newNote() {
    const r = this.store.create();
    if (r.limit) { this.showLimitDialog(); return; }
    this.go(`note/${r.id}`);
  }

  noteCard(note, p) {
    const title = note.glyphs.length ? Title.display(note.glyphs) : '(blank sheet)';
    const preview = h('div', { class: 'card-preview', dir: 'auto', style: { color: p.ink } });
    for (const g of Title.body(note.glyphs).slice(0, 400)) {
      if (g.s) preview.append(h('span', { style: { background: rgba(p.ink, 0.55) }, text: g.o ?? g.c }));
      else preview.append(g.c);
    }
    const date = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(note.updatedAt));
    const card = h('div', { class: 'note-card' + (p.isDark ? ' dark' : ''), 'data-tag': `note_${note.id}`, style: { backgroundImage: `url(assets/img/${p.paperTex}.webp)` } },
      h('div', { class: 'card-title', dir: 'auto', 'data-tag': `noteTitle_${note.id}`, style: { color: p.ink }, text: title }),
      Title.body(note.glyphs).length ? preview : null,
      h('div', { class: 'card-foot' },
        h('div', { class: 'card-meta', style: { color: p.muted }, text: `${date}  \u00B7  ${note.glyphs.length}/${MAX_ACTIVE}` }),
        h('button', { class: 'icon-button', 'data-tag': `delete_${note.id}`, 'aria-label': 'Delete note', style: { color: p.muted }, onClick: (e) => { e.stopPropagation(); this.confirmDelete(note.id, p); } }, icon('delete', 24))));
    card.addEventListener('click', () => this.go(`note/${note.id}`));
    return card;
  }

  confirmDelete(id, p) {
    const d = dialog({
      title: 'Throw this sheet away?', onDismiss: () => d.remove(),
      body: "The note will be deleted. There's no undo on a typewriter.",
      buttons: [{ text: 'Keep', onClick: () => d.remove() }, { text: 'Delete', tag: 'confirmDelete', color: p.indicator, onClick: () => { d.remove(); this.store.delete(id); } }],
    });
    document.body.append(d);
  }

  // ------------------------------------------------------------------------- settings
  renderSettings() {
    const s = this.store.settings, p = paletteFor(s.theme);
    const upd = (t) => this.store.updateSettings(t);
    const section = (title, ...content) => [h('div', { class: 'section-title', style: { color: p.muted }, text: title.toUpperCase() }), h('div', { class: 'section', style: { background: p.card } }, ...content)];
    const toggle = (title, sub, checked, tag, onChange, enabled = true) => h('div', { class: 'toggle-row' },
      h('div', { class: 'grow' }, h('div', { class: 'row-title', style: { color: enabled ? p.onBackground : p.muted }, text: title }), sub ? h('div', { class: 'row-sub', style: { color: p.muted }, text: sub }) : null),
      switchEl(checked, onChange, { tag, enabled, label: title }));
    const sliderRow = (label, key, tag) => h('div', { class: 'slider-row' }, h('div', { class: 'row-label', style: { color: p.onBackground }, text: label }), slider(s[key], (v) => upd(() => ({ [key]: v })), { tag, label }));
    const swatch = (t) => {
      const tp = paletteFor(t), sel = s.theme === t;
      return h('div', { class: 'swatch-col' },
        h('button', { class: 'swatch', 'data-tag': `theme_${t}`, role: 'radio', 'aria-checked': String(sel), 'aria-label': `${THEME_LABEL[t]} theme`, onClick: () => upd(() => ({ theme: t })), style: { background: tp.body, border: sel ? `3px solid ${p.indicator}` : `1px solid ${tp.bodyShade}` } },
          h('span', { class: 'sw-paper', style: { background: tp.paper } }), h('span', { class: 'sw-roller', style: { background: tp.roller } }), h('span', { class: 'sw-led', style: { background: tp.led } })),
        h('div', { class: 'swatch-label', style: { color: p.onBackground }, text: THEME_LABEL[t] }));
    };
    const screen = h('div', { class: 'screen settings', style: { background: p.background, color: p.onBackground, '--switch-on': p.led, '--switch-thumb': p.paper } },
      h('div', { class: 'bar' }, h('button', { class: 'icon-button', 'data-tag': 'back', 'aria-label': 'Back', onClick: () => this.back(), style: { color: p.onBackground } }, icon('arrow_back', 24)), h('div', { class: 'bar-title', text: 'Settings' })),
      h('div', { class: 'scroll settings-body' },
        ...section('Sound & feel',
          toggle('Key sounds', 'Strikes, space bar, carriage return and the margin bell', s.sound, 'sw_sound', (v) => upd(() => ({ sound: v }))),
          toggle('Play in silent mode', 'Keep the clack even when the ringer is off (Android only; browsers follow the system volume)', s.playInSilentMode, 'sw_silent', (v) => upd(() => ({ playInSilentMode: v })), s.sound),
          toggle('Haptics', 'A small tick under every key (where the browser supports vibration)', s.haptics, 'sw_haptics', (v) => upd(() => ({ haptics: v })))),
        ...section('Typing',
          toggle('Backspace erases', s.correction === 'DELETE' ? 'Backspace removes the last letter, across lines too. Hold it to keep erasing.' : 'Off: backspace steps the carriage back and typing over a letter overstrikes it with a dark block, like a real machine.', s.correction === 'DELETE', 'sw_erase', (v) => upd(() => ({ correction: v ? 'DELETE' : 'STRIKE' }))),
          h('div', { style: { height: '8px' } }),
          toggle('Autocorrect', 'Off: every keystroke lands exactly as typed', s.autocorrect, 'sw_autocorrect', (v) => upd(() => ({ autocorrect: v })))),
        ...section('Paper & machine',
          h('div', { class: 'swatches' }, THEMES.map(swatch)),
          h('div', { style: { height: '8px' } }),
          toggle('Charms', 'Little enamel pins on the machine', s.charms, 'sw_charms', (v) => upd(() => ({ charms: v })))),
        ...section('Night lamp',
          h('div', { class: 'row-sub', style: { color: p.muted }, text: 'Tip: tap the lamp in the toolbar (hold it for these sliders), or long-press the carriage.' }),
          toggle('Lamp on', null, s.lampOn, 'sw_lamp', (v) => upd(() => ({ lampOn: v }))),
          sliderRow('Warmth', 'lampWarmth', 'set_warmth'), sliderRow('Intensity', 'lampIntensity', 'set_intensity'), sliderRow('Beam', 'lampBeam', 'set_beam')),
        ...section('Studio',
          h('button', { class: 'studio-row', 'data-tag': 'openStudio', onClick: () => this.go('studio') },
            h('div', { class: 'grow' }, h('div', { class: 'row-title', style: { color: p.onBackground }, text: s.studio ? 'Studio unlocked (demo)' : 'Platen Studio' }), h('div', { class: 'row-sub', style: { color: p.muted }, text: 'More notes and print-ready PDF letters' })),
            h('span', { style: { color: p.muted } }, icon('chevron_right', 24)))),
        ...section('Credits', h('div', { class: 'row-sub credits', 'data-tag': 'credits', style: { color: p.muted }, text: CREDITS })),
        h('div', { class: 'row-sub', style: { color: p.muted, padding: '16px 8px 0' } }, 'Platen web \u00B7 v3.2 \u00B7 notes stay in this browser (localStorage).')));
    this.root.append(screen);
    return { unmount() {} };
  }

  // ------------------------------------------------------------------------- Studio (demo unlock)
  renderStudio() {
    const s = this.store.settings, p = paletteFor(s.theme);
    const feature = (title, body) => h('div', { class: 'feature' }, h('span', { style: { color: p.led } }, icon('check', 24)), h('div', {}, h('div', { class: 'row-title', style: { color: p.onBackground }, text: title }), h('div', { class: 'row-sub', style: { color: p.muted }, text: body })));
    const screen = h('div', { class: 'screen studio', style: { background: p.background, color: p.onBackground } },
      h('button', { class: 'icon-button', 'data-tag': 'back', 'aria-label': 'Back', onClick: () => this.back(), style: { color: p.onBackground, margin: '4px' } }, icon('arrow_back', 24)),
      h('div', { class: 'scroll studio-body' },
        h('div', { class: 'studio-kicker', style: { color: p.muted }, text: 'platen' }),
        h('div', { class: 'studio-title', text: 'Studio' }),
        h('div', { style: { height: '18px' } }),
        h('div', { class: 'features', style: { background: p.card } },
          feature('More sheets on the desk', `Free keeps ${FREE_ACTIVE_NOTES} active notes. Studio removes the cap.`),
          feature('Print-ready letters', 'PDF export in A4, US Letter and A5 with a typed-letter layout.'),
          feature('Same honest page', `Every note still stops at ${MAX_ACTIVE} characters. That's the point.`)),
        h('div', { style: { height: '24px' } }),
        s.studio
          ? h('div', {}, h('div', { class: 'row-title', 'data-tag': 'studioState', style: { color: p.onBackground }, text: 'Studio is unlocked on this device (demo).' }), h('div', { style: { height: '10px' } }),
            h('button', { class: 'outlined-button', 'data-tag': 'studioToggle', style: { color: p.ink, borderColor: p.muted }, text: 'Turn off demo unlock', onClick: () => this.store.updateSettings(() => ({ studio: false })) }))
          : h('button', { class: 'filled-button', 'data-tag': 'studioToggle', style: { background: p.roller, color: p.capsule }, text: 'Unlock Studio \u00B7 demo', onClick: () => this.store.updateSettings(() => ({ studio: true })) }),
        h('div', { style: { height: '10px' } }),
        h('div', { class: 'row-sub', style: { color: p.muted }, text: "In-app purchase isn't connected in this build, so unlocking is free and stays on this device." })));
    this.root.append(screen);
    return { unmount() {} };
  }
}

const app = new App(document.getElementById('app'));
window.platen = app; // handy for debugging and for the headless screenshot script
app.start();
