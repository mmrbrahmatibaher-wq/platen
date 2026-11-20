#!/usr/bin/env python3
"""
Platen v3 bitmap layers, modelled on the measured proportions of the reference layout
(carriage pill 164 x 70 units, T-lever, rubber rod stops, finer paper with sparse fibre flecks).
Reuses the lighting primitives of render_assets.py. Everything is rendered here; nothing is
copied from the reference app.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from render_assets import (grid, sdf_rrect, cover, pillow, normals, blur, fbm_tile, grain, shade, to_img,
                           over, soft_shadow, chrome_env, hexc, HALF, OUT)

SS = 2
U = 4           # px per reference unit in the output (carriage = 164 x 70 units -> 656 x 280 px)
rng = np.random.default_rng(2026)


def save(img, name, q=92):
    img.save(f"{OUT}/{name}.webp", "WEBP", quality=q, method=6)
    print(f"{name:22s} {img.size} {os.path.getsize(f'{OUT}/{name}.webp') // 1024} KB")


def down(img):
    return img.resize((img.width // SS, img.height // SS), Image.LANCZOS)


PAPER = {"cream": ("#F0ECE1", False), "mint": ("#F1F5EC", False), "charcoal": ("#2E2D2B", True), "garden": ("#F1EDDD", False)}
CARRIAGE = {"cream": "#EAE3DB", "mint": "#E1EEE5", "charcoal": "#55545A", "garden": "#E9E3CF"}


def paper(name, hexcol, dark):
    """512 px seamless tile: fine even grain, very soft cloud, sparse brown / rust fibre flecks."""
    S = 512
    base = hexc(hexcol)
    lum = 1 + 0.011 * fbm_tile(S, S, 2.2, 5) + 0.005 * fbm_tile(S, S, 0.1, 6) + 0.004 * fbm_tile(S, S, 1.0, 7)
    rgb = base[None, None, :] * lum[..., None]
    # flecks: drawn on a 3x3 canvas and folded back so the tile stays seamless
    big = Image.new("RGBA", (S * 3, S * 3), (0, 0, 0, 0)); d = ImageDraw.Draw(big)
    cols = [(107, 91, 69), (96, 78, 60), (150, 92, 48), (181, 112, 47), (70, 60, 52)]
    for _ in range(300):
        x, y = rng.uniform(S, 2 * S), rng.uniform(S, 2 * S)
        c = cols[rng.integers(len(cols))]
        a = int(rng.uniform(35, 120) * (0.7 if dark else 1))
        if rng.random() < 0.7:
            r = rng.uniform(0.3, 0.75); d.ellipse([x - r, y - r, x + r, y + r], fill=c + (a,))
        else:
            ang = rng.uniform(0, np.pi); L = rng.uniform(1.5, 4.5)
            pts = [(x, y)]
            for k in range(4):
                ang += rng.normal(0, 0.35); x += np.cos(ang) * L / 4; y += np.sin(ang) * L / 4; pts.append((x, y))
            d.line(pts, fill=c + (a,), width=1)
    big = big.filter(ImageFilter.GaussianBlur(0.3))
    arr = np.asarray(big).astype(np.float32) / 255
    fold = np.zeros((S, S, 4), np.float32)
    for i in range(3):
        for j in range(3):
            t = arr[i * S:(i + 1) * S, j * S:(j + 1) * S]
            fold[..., :3] = fold[..., :3] * (1 - t[..., 3:]) + t[..., :3] * t[..., 3:]
            fold[..., 3] = 1 - (1 - fold[..., 3]) * (1 - t[..., 3])
    if dark:
        fold[..., :3] = 1 - fold[..., :3] * 0.4  # light flecks on charcoal paper
    a = np.clip(fold[..., 3:] * 1.6, 0, 1)
    col = np.where(fold[..., 3:] > 0, fold[..., :3] / np.maximum(fold[..., 3:], 1e-6), 0)
    col = np.clip(col, 0, 1)
    rgb = rgb * (1 - a * 0.85) + col * a * 0.85
    save(to_img(rgb), f"paper_{name}", q=90)


def carriage(name, hexcol):
    m = 22
    cw, ch = 164, 70
    s = U * SS
    W, H = (cw + 2 * m) * s, (ch + 2 * m) * s
    X, Y = grid(W, H)
    x0, y0 = m * s, m * s; x1, y1 = x0 + cw * s, y0 + ch * s
    sdf = sdf_rrect(X, Y, x0, y0, x1, y1, ch / 2 * s)
    mask = cover(sdf, SS)
    h = pillow(sdf, 24 * s, 16 * s)
    # lever slot: dark recess centred on the top surface
    slot = sdf_rrect(X, Y, x0 + cw / 2 * s - 15 * s, y0 + 1.2 * s, x0 + cw / 2 * s + 15 * s, y0 + 7.0 * s, 1.5 * s)
    h -= pillow(slot, 1.5 * s, 3 * s) * 1.0
    # three LED sockets (the LEDs themselves are drawn live on top)
    leds = [(x0 + (24 + 19 * i) * s, y0 + 36 * s) for i in range(3)]
    sock = np.zeros_like(X)
    for lx, ly in leds:
        sd = np.hypot(X - lx, Y - ly) - 6.2 * s
        h -= pillow(sd, 1.6 * s, 1.6 * s) * 0.8
        sock = np.maximum(sock, cover(sd, SS))
    n = normals(h)
    ao = 1 - 0.5 * blur(cover(slot - 1.5 * s, SS), 1.5 * s) - 0.25 * blur(sock, 1.2 * s)
    base = hexc(hexcol)
    g = grain(W, H, 0.008, 41)
    col = shade(base, n, ao=ao, amb=0.86, dif=0.22, spec=0.24, power=34) * g[..., None]
    # satin highlight band along the upper third, lower body slightly warmer/darker
    band = np.exp(-(((Y - (y0 + 13 * s)) / (6 * s)) ** 2)) * 0.07 * mask
    col += band[..., None]
    col *= (1 - 0.03 * np.clip((Y - y0) / (ch * s), 0, 1) ** 2)[..., None]
    # slot interior
    slot_m = cover(slot + 0.6 * s, SS)
    col = col * (1 - slot_m[..., None]) + slot_m[..., None] * np.array([0.23, 0.19, 0.17])
    # sockets: dark ring
    col *= (1 - 0.25 * sock)[..., None]
    sha = soft_shadow(mask, [(0, 1.5 * s, 1.2 * s, 0.50), (0, 5 * s, 4 * s, 0.30), (0, 11 * s, 9 * s, 0.22)], W, H)
    rgb, a = over(np.zeros((H, W, 3)), sha, col, mask)
    save(down(to_img(rgb, a)), f"carriage_{name}")


def lever_t():
    """T lever: gunmetal cross bar on a brushed steel stem. 34 x 40 units (+ shadow room)."""
    s = U * SS
    Wd, Hd = 40, 47
    W, H = Wd * s, Hd * s
    X, Y = grid(W, H)
    cx = W / 2
    bar = sdf_rrect(X, Y, cx - 14 * s, 2 * s, cx + 14 * s, 9 * s, 2 * s)
    stem = sdf_rrect(X, Y, cx - 2.6 * s, 8 * s, cx + 2.6 * s, 44 * s, 1 * s)
    hb = np.sqrt(np.clip(1 - ((Y - 5.5 * s) / (3.5 * s)) ** 2, 0, 1)) * 3.5 * s * cover(bar, SS)
    hs = np.sqrt(np.clip(1 - ((X - cx) / (2.6 * s)) ** 2, 0, 1)) * 2.6 * s * cover(stem, SS)
    mb, ms = cover(bar, SS), cover(stem, SS)
    n = normals(blur(np.maximum(hb, hs), 0.6 * SS))
    env = chrome_env(n)
    dark = (0.3 + 0.7 * env)[..., None] * np.array([0.50, 0.53, 0.56])  # gunmetal bar
    light = (0.55 + 0.45 * env)[..., None] * np.array([0.78, 0.80, 0.82])  # brushed steel stem
    ndh = np.clip((n * HALF).sum(2), 0, 1)
    dark += (ndh ** 60 * 0.55)[..., None]
    # thin bright upper lip on the bar
    lip = np.exp(-((Y - 2.9 * s) / (0.7 * s)) ** 2) * mb * 0.45
    col = light * (1 - mb[..., None]) + dark * mb[..., None] + lip[..., None]
    mask = np.maximum(mb, ms)
    sha = soft_shadow(mask, [(1 * s, 2 * s, 1.2 * s, 0.35), (2 * s, 4 * s, 3 * s, 0.2)], W, H)
    rgb, a = over(np.zeros((H, W, 3)), sha, col, mask)
    save(down(to_img(rgb, a)), "lever_t")


def stop():
    """Black rubber stop clamped on the rod: 26 x 44 units + shadow room."""
    s = U * SS
    m = 8
    W, H = (26 + 2 * m) * s, (44 + 2 * m) * s
    X, Y = grid(W, H)
    x0, y0 = m * s, m * s
    sdf = sdf_rrect(X, Y, x0, y0, x0 + 26 * s, y0 + 44 * s, 4 * s)
    mask = cover(sdf, SS)
    h = pillow(sdf, 5 * s, 4 * s)
    n = normals(h)
    col = shade(hexc("#2A2C2E"), n, amb=0.8, dif=0.4, spec=0.18, power=16) * grain(W, H, 0.03, 9)[..., None]
    sha = soft_shadow(mask, [(0, 1.5 * s, 1.5 * s, 0.5), (0, 4 * s, 4 * s, 0.3)], W, H)
    rgb, a = over(np.zeros((H, W, 3)), sha, col, mask)
    save(down(to_img(rgb, a)), "rod_stop")


if __name__ == "__main__":
    what = sys.argv[1:] or ["all"]
    if "all" in what or "paper" in what:
        for k, (c, dk) in PAPER.items(): paper(k, c, dk)
    if "all" in what or "carriage" in what:
        for k, c in CARRIAGE.items(): carriage(k, c)
    if "all" in what or "lever" in what: lever_t()
    if "all" in what or "stop" in what: stop()
