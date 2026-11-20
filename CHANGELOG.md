# Changelog

All notable changes to Platen. Android `versionName` / `versionCode` in brackets.

## Web 3.2.0 — 2026-11-20
- First web version: a plain HTML/CSS/JS port of Android 3.2 (no build step, no dependencies,
  no CDN). Same stage, strike choreography, sounds, Morse mode, night lamp, themes, charms,
  prompts, Box, Studio, PNG and PDF export; notes live in the browser's `localStorage`.
- `node --test` suite mirroring the Kotlin domain tests (engine, graphemes, input diff, layout,
  Morse, titles, strike timeline, limits, jitter, prompts, pagination, geometry, PDF writer).
- GitHub Pages workflow (`.github/workflows/pages.yml`).
- Android build: release signing now reads environment variables or a git-ignored
  `keystore.properties`, and falls back to the debug key, so a fresh clone builds. App code is unchanged.

## 3.2 [5]
- **Backspace erases** is on by default and erases across line breaks; turn it off to get the
  classic overstrike (■) correction.
- New strike choreography: the T-lever rises and strikes, the letter inks at impact, the
  carriage steps after the hit, and the LEDs flash. Return has a bounce.
- Works in portrait and landscape with the drum; vertical toolbar on the right in landscape,
  and a cream pill under the rod in portrait.
- The first line of a note is its **title**: typed bold on the sheet and used in the Box and file names.
- The arched sheet slides down as soon as you type the first key; the caret blinks only when idle.

## 3.1 [4]
- **Morse mode**: type `<morsecode>` and press Enter, then choose 1 (Morse → English) or
  2 (English → Morse). Enter translates the line; an empty line exits.
- Morse reference chart (expandable panel or compact strip) with tap-to-type tiles.

## 3.0 [3]
- New stage: arched paper over a drum, a steel rod with rubber stops, a sliding carriage with
  three LEDs, and per-glyph ink variation (Special Elite).
- Sounds re-cut from full-quality CC0 FLAC recordings (48 kHz) with ±4 % pitch and ±10 % volume
  randomisation per strike, synced to the moment of impact; margin bell.
- Night lamp redrawn with warmth, intensity and beam controls.

## 2.0 [2]
- First set of real CC0 typewriter recordings (BigSoundBank, Freesound).
- Themes (Cream, Mint, Charcoal, Garden), enamel charms, night lamp.
- Export as an image (4:5), Story (9:16) and PDF (A4, US Letter, A5), with Studio unlocking PDF and unlimited notes.
- 120 writing prompts ("Need a first line?").

## 1.0 [1]
- First release: one-way typewriter notes (no editing, no going back), a 3,000-character sheet,
  the Box of notes, and three free notes.
