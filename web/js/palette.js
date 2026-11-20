// Port of ui/theme/Palette.kt — the four paper themes.
const common = { stageInk: '#CFC8BC', stageAccent: '#A9D79C' };
export const THEMES = ['CREAM', 'MINT', 'CHARCOAL', 'GARDEN'];
export const THEME_LABEL = { CREAM: 'Cream', MINT: 'Mint', CHARCOAL: 'Charcoal', GARDEN: 'Garden' };
const P = {
  CREAM: {
    background: '#E8DFCB', paper: '#F8F2E4', paperEdge: '#E2D6BC', ink: '#2A2420', body: '#F1E6CC', bodyShade: '#D5C4A1',
    bodyHighlight: '#FFF8E8', roller: '#1D1B1A', capsule: '#F5ECD8', indicator: '#D0432C', led: '#41C463', onBackground: '#2A2420',
    muted: '#7C7162', card: '#F6EFDF', isDark: false, paperTex: 'paper_cream', carriageTex: 'carriage_cream',
    stageSide: '#1D1A15', stageVoid: '#050304', charms: ['charm_coffee', 'charm_star'],
  },
  MINT: {
    background: '#D6E8DD', paper: '#F5F8EF', paperEdge: '#D9E3D3', ink: '#1F2E28', body: '#BCE0CB', bodyShade: '#94C4AB',
    bodyHighlight: '#E4F5EA', roller: '#1C2421', capsule: '#EAF5EC', indicator: '#D24E36', led: '#3FCB6A', onBackground: '#1F2E28',
    muted: '#5F7369', card: '#EFF6EE', isDark: false, paperTex: 'paper_mint', carriageTex: 'carriage_mint',
    stageSide: '#141C17', stageVoid: '#030504', charms: ['charm_heart', 'charm_flower'],
  },
  CHARCOAL: {
    background: '#161617', paper: '#2B2A28', paperEdge: '#3A3936', ink: '#EDE6D6', body: '#3B3B3D', bodyShade: '#262628',
    bodyHighlight: '#55555A', roller: '#0C0C0D', capsule: '#4D4C4F', indicator: '#E6654D', led: '#58E27D', onBackground: '#EDE6D6',
    muted: '#9C968B', card: '#242425', isDark: true, paperTex: 'paper_charcoal', carriageTex: 'carriage_charcoal',
    stageSide: '#121213', stageVoid: '#030303', charms: ['charm_moon', 'charm_star'],
  },
  GARDEN: {
    background: '#D3DDC2', paper: '#F6F1E2', paperEdge: '#DCD3B8', ink: '#29271F', body: '#A9BC94', bodyShade: '#86996F',
    bodyHighlight: '#D2E0C0', roller: '#1A1C17', capsule: '#EFE8D4', indicator: '#D24A30', led: '#3FCB6A', onBackground: '#29271F',
    muted: '#656B55', card: '#F1EEDF', isDark: false, paperTex: 'paper_garden', carriageTex: 'carriage_garden',
    stageSide: '#191C13', stageVoid: '#040503', charms: ['charm_flower', 'charm_heart', 'charm_flower'],
  },
};
export function paletteFor(theme) { return { ...common, ...(P[theme] || P.CREAM), theme: P[theme] ? theme : 'CREAM' }; }

// ---- colour helpers
export function hexToRgb(h) { const n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
export function rgba(h, a = 1) { const [r, g, b] = hexToRgb(h); return `rgba(${r},${g},${b},${a})`; }
export function lerpColor(a, b, t) {
  const A = hexToRgb(a), B = hexToRgb(b);
  const c = A.map((v, i) => Math.round(v + (B[i] - v) * t));
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('');
}
export function luminance(h) {
  const lin = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  const [r, g, b] = hexToRgb(h);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}
export const PORTRAIT_VOID = '#1B1B1B';
