#!/usr/bin/env python3
"""
Platen v3 sound set. Sources are full-quality originals (48 kHz / 24-bit FLAC from BigSoundBank,
CC0; 24-bit WAV of freesound #406243 by _stubb via Wikimedia Commons, CC0) kept in sound_src/v3.

Processing is deliberately light (v2's spectral de-noise smeared the transient and killed the
body): mono mix (average), 30 Hz high-pass, onset trim with 2 ms pre-roll, cosine fade-out,
loudness match on the loudest 20 ms, peak cap at -1 dBFS. Output: 16-bit 48 kHz mono WAV.
"""
import numpy as np, subprocess, wave, os, json
import os as _os
ROOT = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))  # android/

SR = 48000
SRC = ROOT + "/sound_src/v3"
OUT = ROOT + "/app/src/main/res/raw"

# (file, onset seconds) picked by scripts/analyse_sounds_v3.py: bright strikes (centroid 5-7 kHz),
# isolated (no other strike within 170 ms); the last four carry a low "thunk" (12-18 % < 300 Hz).
KEYS = [
    ("2837", 1.32225), ("2837", 2.27671), ("2836", 0.25894), ("2835", 21.13413), ("2840", 24.98488),
    ("2841", 6.55058), ("2835", 39.70758), ("2841", 27.67458), ("2835", 81.12002), ("2836", 0.49094),
]
BACKSPACE = ("2841", 22.21735)


def load(name):
    p = name if name.startswith("/") else f"{SRC}/{name}.flac"
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", p, "-af", "pan=mono|c0=0.5*c0+0.5*c1" if stereo(p) else "anull",
                          "-ar", str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64)


def stereo(p):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=channels", "-of", "csv=p=0", p],
                         capture_output=True, text=True).stdout.strip()
    return out == "2"


def highpass(x, fc=30.0):
    a = np.exp(-2 * np.pi * fc / SR); y = np.zeros_like(x); prev_x = 0.0; prev_y = 0.0
    for i, v in enumerate(x):
        prev_y = a * (prev_y + v - prev_x); prev_x = v; y[i] = prev_y
    return y


def env(x, ms=2):
    k = max(1, int(SR * ms / 1000)); return np.convolve(np.abs(x), np.ones(k) / k, "same")


def cut(x, t, length, fade_ms, pre_ms=2):
    s = max(0, int(t * SR))
    # refine: first sample within 10 ms whose envelope exceeds 10 % of the local peak
    w = x[s: s + int(0.02 * SR)]; e = env(w, 1)
    on = int(np.argmax(e > e.max() * 0.1)) if len(e) else 0
    s = max(0, s + on - int(pre_ms * SR / 1000))
    seg = highpass(x[s: s + int(length * SR)].copy())
    n = int(fade_ms * SR / 1000)
    seg[-n:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, n)))
    seg[: int(0.001 * SR)] *= np.linspace(0, 1, int(0.001 * SR))
    return seg


def loud(x):
    k = int(0.02 * SR)
    return 10 * np.log10(max(np.mean(x[i:i + k] ** 2) for i in range(0, max(1, len(x) - k), k // 4)) + 1e-12)


def finish(x, target_db):
    x = x * 10 ** ((target_db - loud(x)) / 20)
    pk = np.max(np.abs(x))
    if pk > 0.89: x *= 0.89 / pk
    return x


def write(name, x):
    path = f"{OUT}/{name}.wav"
    w = wave.open(path, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes()); w.close()
    return path


def main():
    for f in os.listdir(OUT):
        if f.endswith(".ogg") or f.endswith(".wav"):
            os.remove(os.path.join(OUT, f))
    cache = {}
    def src(n):
        if n not in cache: cache[n] = load(n)
        return cache[n]
    manifest = []
    for i, (f, t) in enumerate(KEYS):
        manifest.append((write(f"key_{i + 1:02d}", finish(cut(src(f), t, 0.20, 70), -16.0 + (i % 3) * -0.8)), f, t))
    f, t = BACKSPACE
    manifest.append((write("backspace", finish(cut(src(f), t, 0.16, 60), -20.0)), f, t))
    # Space bar ("Typewriter, space"): wooden bar + escapement, naturally duller than a typebar.
    spc = src("2843"); p = int(np.argmax(env(spc))) / SR - 0.004
    manifest.append((write("space", finish(cut(spc, p, 0.30, 90), -17.0)), "2843", p))
    # Margin bells: two Hermes bells + _stubb's ding (24-bit WAV).
    for name, n in [("bell", "2844"), ("bell_2", "2845")]:
        b = src(n); p = int(np.argmax(env(b))) / SR - 0.004
        manifest.append((write(name, finish(cut(b, p, 1.1, 450), -12.0)), n, p))
    d = load(f"{SRC}/freesound_406243_stubb_ding.wav"); p = int(np.argmax(env(d))) / SR - 0.004
    manifest.append((write("bell_3", finish(cut(d, p, 0.9, 400), -12.0)), "freesound 406243", p))
    # Carriage return: lever + slide + slam ("Typewriter #5").
    five = src("2838")
    for name, (a, b) in {"carriage_return": (62.82, 63.95), "carriage_return_2": (85.50, 86.80)}.items():
        manifest.append((write(name, finish(cut(five, a, b - a, 120, pre_ms=0), -14.0)), "2838", a))
    # Limit "jam": a strike low-passed and damped (typebar blocked).
    k = cut(src("2840"), 24.98488, 0.22, 60)
    y = np.zeros_like(k); a = np.exp(-2 * np.pi * 900 / SR)
    for i in range(1, len(k)): y[i] = (1 - a) * k[i] + a * y[i - 1]
    y *= np.exp(-np.arange(len(y)) / SR / 0.06)
    manifest.append((write("jam", finish(y, -20.0)), "2840 (derived)", 24.98488))
    json.dump([(os.path.basename(p), f, round(t, 4)) for p, f, t in manifest], open(ROOT + "/sound_src/v3/manifest.json", "w"), indent=1)


if __name__ == "__main__":
    main()
