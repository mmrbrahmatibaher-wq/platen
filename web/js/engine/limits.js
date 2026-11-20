// Port of domain/NoteLimits.kt and export/ExportSpec.kt (pure rules).
export const FREE_ACTIVE_NOTES = 3;
export const maxNotes = (studio) => (studio ? Infinity : FREE_ACTIVE_NOTES);
export const canCreate = (existing, studio) => existing < maxNotes(studio);
export const remaining = (existing, studio) => (studio ? Infinity : Math.max(0, FREE_ACTIVE_NOTES - existing));

export const ImageFormat = Object.freeze({
  STANDARD: { key: 'STANDARD', label: 'Image', widthPx: 1080, heightPx: 1350 },
  STORY: { key: 'STORY', label: 'Story 9:16', widthPx: 1080, heightPx: 1920 },
});
export const PaperSize = Object.freeze({
  A4: { key: 'A4', label: 'A4', widthPt: 595, heightPt: 842, marginPt: 72, fontPt: 12 },
  LETTER: { key: 'LETTER', label: 'US Letter', widthPt: 612, heightPt: 792, marginPt: 72, fontPt: 12 },
  A5: { key: 'A5', label: 'A5', widthPt: 420, heightPt: 595, marginPt: 46, fontPt: 10 },
});
export const contentWidth = (p) => p.widthPt - 2 * p.marginPt;
export const contentHeight = (p) => p.heightPt - 2 * p.marginPt;

/** Pagination.pages: returns [start, end] inclusive ranges; [[0,-1]] for no lines. */
export function pages(lineTops, lineBottoms, pageHeight) {
  if (lineTops.length !== lineBottoms.length) throw new Error('size mismatch');
  if (!(pageHeight > 0)) throw new Error('pageHeight must be > 0');
  if (lineTops.length === 0) return [[0, -1]];
  const result = [];
  let start = 0;
  while (start < lineTops.length) {
    const origin = lineTops[start];
    let end = start;
    while (end + 1 < lineTops.length && lineBottoms[end + 1] - origin <= pageHeight) end++;
    result.push([start, end]);
    start = end + 1;
  }
  return result;
}
export function fitScale(contentH, available, minScale = 0.4) {
  if (contentH <= available || contentH <= 0) return 1;
  return Math.min(1, Math.max(minScale, available / contentH));
}
