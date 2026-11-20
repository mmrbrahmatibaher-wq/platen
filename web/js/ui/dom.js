// Tiny DOM helpers (no framework).
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style' && typeof v === 'object') {
      for (const [sk, sv] of Object.entries(v)) { if (sv == null) continue; if (sk.startsWith('--')) el.style.setProperty(sk, sv); else el.style[sk] = sv; }
    }
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'text') el.textContent = v;
    else if (k === 'html') el.innerHTML = v;
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  for (const c of children.flat()) if (c != null && c !== false) el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  return el;
}
/** A Material outlined icon (SVG used as a CSS mask so it takes the current colour). */
export function icon(name, size = 24, extra = {}) {
  // absolute URL: a relative url() inside a custom property would resolve against css/
  const url = new URL(`assets/icons/${name}.svg`, document.baseURI).href;
  return h('span', { class: 'icon', 'aria-hidden': 'true', style: { width: size + 'px', height: size + 'px', '--icon': `url("${url}")`, ...extra } });
}
export function switchEl(checked, onChange, { tag, enabled = true, label } = {}) {
  const input = h('input', { type: 'checkbox', role: 'switch', 'data-tag': tag, 'aria-label': label });
  input.checked = checked; input.disabled = !enabled;
  input.addEventListener('change', () => onChange(input.checked));
  return h('label', { class: 'switch' + (enabled ? '' : ' disabled') }, input, h('span', { class: 'track' }, h('span', { class: 'thumb' })));
}
export function slider(value, onChange, { tag, label } = {}) {
  const input = h('input', { type: 'range', min: 0, max: 1, step: 0.01, 'data-tag': tag, 'aria-label': label, class: 'slider' });
  input.value = value;
  input.addEventListener('input', () => onChange(parseFloat(input.value)));
  return input;
}
/** Material AlertDialog look-alike. buttons: [{ text, onClick, tag, color }] (rightmost = confirm). */
export function dialog({ title, body, buttons = [], tag, onDismiss }) {
  const scrim = h('div', { class: 'scrim', 'data-tag': tag ? tag + 'Scrim' : null });
  const card = h('div', { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'data-tag': tag },
    title ? h('h2', { class: 'dialog-title', text: title }) : null,
    h('div', { class: 'dialog-body' }, body),
    h('div', { class: 'dialog-buttons' }, buttons.map((b) => h('button', { class: 'text-button', 'data-tag': b.tag, style: b.color ? { color: b.color } : null, onClick: b.onClick, text: b.text }))));
  scrim.append(card);
  scrim.addEventListener('pointerdown', (e) => { if (e.target === scrim) { onDismiss?.(); } });
  scrim.close = () => scrim.remove();
  return scrim;
}
export function grouped(n) { return new Intl.NumberFormat('en-US').format(n); }
