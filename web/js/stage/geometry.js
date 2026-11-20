// Port of ui/stage/StageGeometry.kt — proportions of the machine stage, measured on the reference
// layouts and expressed as fractions so they scale to any window. All values in CSS px (density = 1).
const MAX_THETA = (Math.PI / 2) * 0.96;
const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);

export function stageGeometry(widthPx, heightPx, density = 1, fontScale = 1, portrait = heightPx > widthPx) {
  const paperLeft = portrait ? 0 : widthPx * 0.153;
  const paperWidth = portrait ? widthPx : widthPx * 0.695;
  const carW = clamp(paperWidth * (portrait ? 0.18 : 0.184), 100 * density, 176 * density);
  const carH = (carW * 70) / 164;
  const unit = carW / 164;
  const toolbarH = portrait ? 42 * density : 0;
  const toolbarTop = portrait ? heightPx - 8 * density - toolbarH : heightPx;
  const rodCenter = portrait ? toolbarTop - 18 * density - carH / 2 : heightPx * 0.838;
  const cellShare = portrait ? 0.0177 : 0.0155;
  const fontPx = clamp((paperWidth * cellShare) / 0.6, 12.5 * density * fontScale, 24 * density * fontScale);
  const cell = fontPx * 0.6;
  const margin = paperWidth * (portrait ? 0.155 : 0.158);
  const columns = Math.max(8, Math.floor((paperWidth - 2 * margin) / cell));
  const textLeft = paperLeft + margin;
  const baseline = rodCenter - 1.29 * carH;
  const lh = cell * 1.75;
  const drumWanted = portrait ? Math.max(heightPx * 0.36, 118 * density) : heightPx * 0.326;
  const drumTop = Math.max(0, Math.min(drumWanted, baseline - 4 * lh));
  const g = {
    width: widthPx, height: heightPx, portrait, paperLeft, paperWidth,
    cornerRx: paperWidth * 0.146, cornerRy: paperWidth * 0.37, drumTop,
    rodCenter, rodThickness: carH * 0.49, carriageWidth: carW, carriageHeight: carH, unit,
    cell, fontPx, lineHeight: lh, textLeft, columns, baseline,
    stopWidth: carH * 0.36, stopHeight: carH * 0.62, toolbarTop, toolbarHeight: toolbarH,
  };
  g.paperRight = paperLeft + paperWidth;
  g.paperCenterX = paperLeft + paperWidth / 2;
  g.textRight = textLeft + columns * cell;
  g.carriageTop = rodCenter - carH / 2;
  g.carriageBottom = rodCenter + carH / 2;
  g.leverTop = g.carriageTop - 39 * unit;
  g.cursorY = baseline + cell * 0.45;
  g.cellCenterX = (col, w = 1) => textLeft + (col + w / 2) * cell;
  g.strikeRise = Math.max(g.leverTop - (baseline - fontPx * 0.62), 6 * unit);
  g.drumRadius = (baseline - drumTop) * 0.62;
  g.bendStart = drumTop + g.drumRadius;
  g.project = (flat) => {
    if (flat >= g.bendStart) return { baseline: flat, scaleX: 1, scaleY: 1, alpha: 1 };
    const r = g.drumRadius;
    const theta = (g.bendStart - flat) / r;
    if (theta >= MAX_THETA) return null;
    const c = Math.cos(theta);
    return { baseline: g.bendStart - r * Math.sin(theta), scaleX: 1 - 0.17 * Math.sin(theta), scaleY: Math.max(c, 0.08), alpha: 0.62 + 0.38 * c };
  };
  return g;
}
