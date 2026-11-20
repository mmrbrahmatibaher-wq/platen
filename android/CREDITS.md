# Platen — third-party assets and licences

## Sounds — real recordings, CC0 1.0 (public domain)

All sounds in `app/src/main/res/raw/` are real recordings released under **CC0 1.0** (no rights
reserved; commercial use allowed, no attribution required — credit is given anyway).
No sound was taken from any other typewriter app or from reference videos.

**BigSoundBank.com — recordings by Joseph SARDIN** (a Hermes Precisa 305), CC0:
licence page https://bigsoundbank.com/licenses.html. v3 uses the site's full-quality
**FLAC 48 kHz / 24-bit** originals (not the MP3 previews used in v2), downloaded from
`https://bigsoundbank.com/UPLOAD/flac/<id>.flac`.

| App file | Source recording (BigSoundBank id) | Offset in source |
|---|---|---|
| key_01, key_02 | "Typewriter #4, very slow" (2837) | 1.32 s, 2.28 s |
| key_03, key_10 | "Typewriter #3" (2836) | 0.26 s, 0.49 s |
| key_04, key_07, key_09 | "Typewriter #2" (2835) | 21.13 s, 39.71 s, 81.12 s |
| key_05 | "Typewriter #7" (2840) | 24.98 s |
| key_06, key_08 | "Typewriter #8" (2841) | 6.55 s, 27.67 s |
| backspace | "Typewriter #8" (2841) | 22.22 s |
| space | "Typewriter, space" (2843) | 0.11 s |
| bell | "Typewriter, Bell #1" (2844) | 0.07 s |
| bell_2 | "Typewriter, bell #2" (2845) | 0.13 s |
| carriage_return, carriage_return_2 | "Typewriter #5" (2838) — carriage slide + slam | 62.82 s, 85.50 s |
| jam | derived from key_05 (low-passed, damped) | — |

**Freesound #406243 by _stubb** ("ding"), CC0 1.0, https://freesound.org/s/406243/
(24-bit / 48 kHz original obtained via Wikimedia Commons) → `bell_3`.

Processing (`scripts/process_sounds_v3.py`): decode FLAC/WAV → explicit L/R average to mono →
onset-aligned cut (≈3 ms pre-roll) → short fade-in, exponential fade-out at the natural decay →
light loudness match (no compression, no EQ) → 16-bit PCM WAV, 48 kHz mono. Measured spectral
centroid of the ten key strikes (first 100 ms): 5.4–6.7 kHz; decay to −30 dB: 21–61 ms.
The app adds ±4 % pitch and ±10 % volume per strike at play time.
A manifest of the sources is kept in `sound_src/` (the large FLAC originals are not in git; re-download them from the URLs above).

## Fonts

* **Special Elite** — © Astigmatic (AOETI), Apache License 2.0. `licenses/SpecialElite-LICENSE.txt`.
  Used for the typed sheet, titles, wordmark and exports.
* **Courier Prime** — © Alan Dague-Greene / Quote-Unquote Apps, SIL Open Font License 1.1.
  `OFL-CourierPrime.txt`. Used for small UI text.
* Persian/Arabic text falls back to the device's system font (Special Elite is Latin-only).

## Artwork

Paper textures, carriage bodies, the T-lever, the rubber rod stops, charms, wordmark and launcher
icon are rendered procedurally for this app by `scripts/render_assets.py` and
`scripts/render_assets_v3.py` (numpy + Pillow). The rod, shadows, LEDs and night-lamp light are
drawn live in Compose. Toolbar icons are Material Symbols (Apache License 2.0, via
`androidx.compose.material:material-icons-extended`). No third-party or reference images are used.
