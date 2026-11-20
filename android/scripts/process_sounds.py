#!/usr/bin/env python3
"""
Cut, lightly de-noise, loudness-normalise and encode the real typewriter recordings used by
Platen. Sources: BigSoundBank (Joseph Sardin), CC0, Hermes Precisa 305 manual typewriter.
Download the MP3s into sound_src/ first (see CREDITS.md), then run this script.
"""
import numpy as np, wave, os, subprocess
import os as _os
ROOT = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))  # android/

SRC = ROOT + "/sound_src"
OUT = ROOT + "/app/src/main/res/raw"
SR = 48000


def load(n):
    p = f"{SRC}/{n}.wav"
    if not os.path.exists(p):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", f"{SRC}/{n}.mp3", "-ac", "1", "-ar", str(SR), p], check=True)
    w = wave.open(p)
    return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768


def stft(x, n=1024, hop=256):
    win = np.hanning(n)
    pad = np.concatenate([np.zeros(n), x, np.zeros(n)])
    frames = np.array([pad[i:i + n] * win for i in range(0, len(pad) - n, hop)])
    return np.fft.rfft(frames, axis=1), len(pad)


def istft(S, length, n=1024, hop=256):
    win = np.hanning(n)
    frames = np.fft.irfft(S, n, axis=1)
    out = np.zeros(length); norm = np.zeros(length)
    for k, f in enumerate(frames):
        i = k * hop
        out[i:i + n] += f * win; norm[i:i + n] += win ** 2
    out /= np.maximum(norm, 1e-6)
    return out[n:-n]


def noise_profile(x):
    """Magnitude spectrum of the quietest 250 ms of a recording."""
    hop = SR // 50
    rms = [np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, len(x) - hop, hop)]
    order = np.argsort(rms)[:12]
    chunk = np.concatenate([x[i * hop:(i + 1) * hop] for i in sorted(order)])
    S, _ = stft(chunk)
    return np.mean(np.abs(S), axis=0)


def denoise(x, prof, amount=1.6, floor=0.12):
    """Gentle spectral gate: pull bins near the room-noise level down, never to silence."""
    S, L = stft(x)
    mag = np.abs(S)
    gain = np.clip((mag - amount * prof) / np.maximum(mag, 1e-9), floor, 1.0)
    # smooth gains over time to avoid musical noise
    k = np.ones(3) / 3
    gain = np.apply_along_axis(lambda g: np.convolve(g, k, mode="same"), 0, gain)
    return istft(S * gain, L)[: len(x)]


def highpass(x, fc=45.0):
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.zeros_like(x); prev_x = 0.0; prev_y = 0.0
    for i, v in enumerate(x):
        prev_y = a * (prev_y + v - prev_x); prev_x = v; y[i] = prev_y
    return y


def onset_before(x, peak, floor_db):
    """Walk back from a peak to where the sound rises out of the noise floor."""
    hop = SR // 1000
    i = peak
    while i > hop:
        r = np.sqrt(np.mean(x[i - hop:i] ** 2))
        if 20 * np.log10(r + 1e-9) < floor_db + 6:
            break
        i -= hop
    # Keep at most 12 ms of pre-roll so the strike lands the moment the key is pressed.
    return max(0, i - int(0.004 * SR), peak - int(0.012 * SR))


def loudness(x):
    """Short-term loudness proxy: RMS (dB) of the loudest 120 ms, after a 100 Hz high-pass."""
    y = highpass(x, 100)
    win = int(0.12 * SR); best = 1e-9
    for i in range(0, max(1, len(y) - win), win // 4):
        best = max(best, np.sqrt(np.mean(y[i:i + win] ** 2)))
    return 20 * np.log10(best)


def finish(x, target_db, fade_ms=25):
    x = x - np.mean(x)
    fi = int(0.002 * SR); fo = int(fade_ms / 1000 * SR)
    x[:fi] *= np.linspace(0, 1, fi)
    x[-fo:] *= np.linspace(1, 0, fo) ** 2
    g = 10 ** ((target_db - loudness(x)) / 20)
    x = x * g
    pk = np.max(np.abs(x))
    if pk > 0.89:  # keep true-peak headroom without squashing everything to one peak
        x *= 0.89 / pk
    return x


def write(name, x):
    tmp = f"/tmp/{name}.wav"
    w = wave.open(tmp, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes()); w.close()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-ar", "44100", "-c:a", "libvorbis", "-q:a", "6",
                    f"{OUT}/{name}.ogg"], check=True)
    print(f"{name:20s} {len(x)/SR:5.2f}s  loud {loudness(x):6.1f} dB  peak {20*np.log10(np.max(np.abs(x))):5.1f} dBFS")


def peaks(x, min_gap=0.25, rel_db=-14):
    hop = SR // 200
    env = np.array([np.max(np.abs(x[i:i + hop])) for i in range(0, len(x) - hop, hop)])
    thr = env.max() * 10 ** (rel_db / 20)
    res = []; last = -1e9
    for i in range(1, len(env) - 1):
        if env[i] > thr and env[i] >= env[i - 1] and env[i] >= env[i + 1] and (i - last) * hop / SR > min_gap:
            seg = slice(i * hop, (i + 1) * hop)
            res.append(seg.start + int(np.argmax(np.abs(x[seg])))); last = i
    return res


def main():
    for f in os.listdir(OUT):
        if f.endswith(".wav") or f.endswith(".ogg"):
            os.remove(os.path.join(OUT, f))

    # --- Key strikes: "Typewriter #4" (very slow, isolated keys) + the single "Typewriter, Key".
    slow = load(2837); prof = noise_profile(slow)
    floor = 20 * np.log10(np.sqrt(np.mean(np.sort(slow ** 2)[: len(slow) // 5])) + 1e-9)
    cands = []
    for p in peaks(slow):
        s = onset_before(slow, p, floor)
        seg = slow[s: s + int(0.42 * SR)]
        if len(seg) < int(0.42 * SR):
            continue
        S = np.abs(np.fft.rfft(seg[: int(0.08 * SR)])); fr = np.fft.rfftfreq(int(0.08 * SR), 1 / SR)
        cands.append((s, (S * fr).sum() / S.sum(), seg))
    # pick 7 strikes spread across brightness, so they genuinely differ
    cands.sort(key=lambda c: c[1])
    idx = np.linspace(0, len(cands) - 1, 7).round().astype(int)
    keys = [cands[i][2] for i in sorted(set(idx))]
    single = load(2842); sp = noise_profile(single)
    p = int(np.argmax(np.abs(single))); s = onset_before(single, p, 20 * np.log10(np.mean(sp) + 1e-9) - 20)
    keys.append(denoise(single[s: s + int(0.42 * SR)], sp))
    for i, k in enumerate(keys):
        k = denoise(k, prof) if i < len(keys) - 1 else k
        write(f"key_{i + 1}", finish(k, -28.5, fade_ms=60))

    # --- Escapement / key-release ticks for the quiet mechanical tail layer ("Typewriter #6").
    six = load(2839); p6 = noise_profile(six)
    for name, (a, b) in {"tail_1": (1.735, 1.86), "tail_2": (1.875, 2.0)}.items():
        write(name, finish(denoise(six[int(a * SR): int(b * SR)], p6), -33.0, fade_ms=30))

    # --- Space bar.
    spc = load(2843); ps = noise_profile(spc)
    p = int(np.argmax(np.abs(spc))); s = onset_before(spc, p, 20 * np.log10(np.mean(ps) + 1e-9) - 20)
    write("space", finish(denoise(spc[s: s + int(0.5 * SR)], ps), -24.0, fade_ms=80))

    # --- Margin bell: short, metallic, inharmonic. Trim the long ring-out.
    for n, name in [(2844, "bell"), (2845, "bell_2")]:
        b = load(n); pb = noise_profile(b)
        p = int(np.argmax(np.abs(b))); s = onset_before(b, p, 20 * np.log10(np.mean(pb) + 1e-9) - 20)
        write(name, finish(denoise(b[s: s + int(1.15 * SR)], pb), -19.0, fade_ms=450))

    # --- Carriage return (lever, slide, slam) from "Typewriter #5".
    five = load(2838); p5 = noise_profile(five)
    for name, (a, b) in {"carriage_return": (62.82, 63.95), "carriage_return_2": (85.50, 86.80)}.items():
        write(name, finish(denoise(five[int(a * SR): int(b * SR)], p5), -21.0, fade_ms=120))

    # --- Jam / limit thunk: a struck key with the typebar blocked = low, dull, damped.
    k = keys[0].copy()
    y = np.zeros_like(k); a = np.exp(-2 * np.pi * 900 / SR)
    for i in range(1, len(k)):
        y[i] = (1 - a) * k[i] + a * y[i - 1]
    t = np.arange(len(y)) / SR
    y *= np.exp(-t / 0.06)
    write("jam", finish(y[: int(0.22 * SR)], -26.0, fade_ms=60))


if __name__ == "__main__":
    main()
