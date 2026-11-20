#!/usr/bin/env python3
"""
Renders Platen's skeuomorphic bitmap assets (xxhdpi, 3 px per dp) with a tiny software
"lighting" pipeline: signed-distance shapes -> height maps -> normals -> diffuse + specular +
ambient occlusion + grain, plus soft multi-layer drop shadows. Output: WebP in drawable-nodpi
and launcher icon layers in mipmap-*dpi.
"""
import numpy as np
from PIL import Image, ImageFilter, ImageDraw, ImageFont
import os
import os as _os
ROOT = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))  # android/

RES = ROOT + "/app/src/main/res"
OUT = f"{RES}/drawable-nodpi"
FONT = f"{RES}/font/special_elite.ttf"
DP = 3          # output px per dp (xxhdpi)
SS = 2          # supersampling factor
rng = np.random.default_rng(1905)

LIGHT = np.array([-0.35, -0.75, 0.56]); LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0]); HALF /= np.linalg.norm(HALF)


def hexc(h):
    h = h.lstrip('#'); return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


# ---------------------------------------------------------------- primitives
def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x + 0.5, y + 0.5


def sdf_rrect(X, Y, x0, y0, x1, y1, r):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hw, hh = (x1 - x0) / 2, (y1 - y0) / 2
    r = min(r, hw, hh)
    qx = np.abs(X - cx) - (hw - r); qy = np.abs(Y - cy) - (hh - r)
    out = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    ins = np.minimum(np.maximum(qx, qy), 0)
    return out + ins - r


def cover(sdf, aa=1.0):
    return np.clip(0.5 - sdf / aa, 0, 1)


def pillow(sdf, bevel, height):
    """Rounded-edge height field: flat top, quarter-circle falloff over `bevel` px."""
    t = np.clip(-sdf / bevel, 0, 1)
    return height * np.sqrt(1 - (1 - t) ** 2)


def normals(h):
    gy, gx = np.gradient(h)
    n = np.dstack([-gx, -gy, np.ones_like(h)])
    return n / np.linalg.norm(n, axis=2, keepdims=True)


def blur(a, radius):
    im = Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(radius))).astype(np.float32) / 255


def fbm_tile(w, h, beta=1.6, seed=0):
    """Periodic (seamlessly tiling) 1/f^beta noise, zero-mean, unit std."""
    r = np.random.default_rng(seed)
    fx = np.fft.fftfreq(w)[None, :]; fy = np.fft.fftfreq(h)[:, None]
    f = np.sqrt(fx ** 2 + fy ** 2); f[0, 0] = 1
    spec = (r.standard_normal((h, w)) + 1j * r.standard_normal((h, w))) / f ** (beta / 2)
    spec[0, 0] = 0
    n = np.real(np.fft.ifft2(spec))
    return (n - n.mean()) / (n.std() + 1e-9)


def grain(w, h, amount, seed):
    return 1 + amount * (0.6 * fbm_tile(w, h, 0.4, seed) + 0.4 * fbm_tile(w, h, 1.4, seed + 1))


def shade(base, n, ao=None, amb=0.62, dif=0.48, spec=0.25, power=40, spec_col=(1, 1, 1)):
    ndl = np.clip((n * LIGHT).sum(2), 0, 1)
    ndh = np.clip((n * HALF).sum(2), 0, 1)
    lum = amb + dif * ndl
    if ao is not None:
        lum = lum * ao
    col = base[None, None, :] * lum[..., None] + spec * (ndh ** power)[..., None] * np.array(spec_col)[None, None, :]
    return col


def to_img(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 1)
    if alpha is None:
        return Image.fromarray((rgb * 255 + 0.5).astype(np.uint8), "RGB")
    a = np.clip(alpha, 0, 1)
    return Image.fromarray((np.dstack([rgb, a[..., None]]) * 255 + 0.5).astype(np.uint8), "RGBA")


def down(img):
    return img.resize((img.width // SS, img.height // SS), Image.LANCZOS)


def over(dst_rgb, dst_a, src_rgb, src_a):
    """Porter-Duff source-over on straight-alpha float arrays."""
    out_a = src_a + dst_a * (1 - src_a)
    out = (src_rgb * src_a[..., None] + dst_rgb * (dst_a * (1 - src_a))[..., None]) / np.maximum(out_a, 1e-6)[..., None]
    return out, out_a


def soft_shadow(mask, layers, w, h):
    """Multi-layer drop shadow: [(dx, dy, blur, opacity)] in px."""
    acc = np.zeros((h, w), np.float32)
    for dx, dy, b, op in layers:
        m = np.roll(np.roll(mask, int(dy), 0), int(dx), 1)
        acc = 1 - (1 - acc) * (1 - blur(m, b) * op)
    return acc


def save(img, name, q=92):
    img.save(f"{OUT}/{name}.webp", "WEBP", quality=q, method=6)
    print(f"{name:24s} {img.size}  {os.path.getsize(f'{OUT}/{name}.webp') // 1024} KB")


THEMES = {
    #           paper       body       capsule
    "cream":    ("#F6EFDF", "#EAE0CB", "#F3ECDD"),
    "mint":     ("#F4F7EE", "#BFDDCB", "#E8F3EC"),
    "charcoal": ("#2D2C2A", "#3B3B3E", "#4C4B4F"),
    "garden":   ("#F5F0E1", "#A9BC94", "#EFE8D4"),
}


# ---------------------------------------------------------------- paper
def paper(name, hexcol, dark=False):
    S = 512
    base = hexc(hexcol)
    mott = fbm_tile(S, S, 2.4, 11)
    fine = fbm_tile(S, S, 0.2, 12)
    mid = fbm_tile(S, S, 1.2, 13)
    lum = 1 + 0.018 * mott + 0.012 * mid + 0.022 * fine
    img = np.ones((S, S)) * 0
    # fibres: short curved strokes, wrapped so the tile stays seamless
    fib = Image.new("L", (S * 3, S * 3), 0); d = ImageDraw.Draw(fib)
    for _ in range(420):
        x, y = rng.uniform(S, 2 * S), rng.uniform(S, 2 * S)
        a = rng.uniform(0, np.pi * 2); L = rng.uniform(8, 38); pts = []
        for k in range(10):
            pts.append((x, y)); a += rng.normal(0, 0.18); x += np.cos(a) * L / 10; y += np.sin(a) * L / 10
        d.line(pts, fill=int(rng.uniform(40, 140)), width=1)
    fib = np.asarray(fib.filter(ImageFilter.GaussianBlur(0.6))).astype(np.float32) / 255
    f = np.zeros((S, S))
    for i in range(3):
        for j in range(3):
            f += fib[i * S:(i + 1) * S, j * S:(j + 1) * S]
    sign = np.sign(fbm_tile(S, S, 2.0, 14))
    lum += 0.05 * f * np.where(sign > 0, 1, -0.7)
    # occasional specks
    for _ in range(60):
        x, y = rng.integers(0, S, 2); r = rng.uniform(0.4, 1.3)
        Y, X = np.ogrid[-3:4, -3:4]
        spot = np.exp(-(X ** 2 + Y ** 2) / (2 * r * r)) * rng.uniform(0.03, 0.09)
        for yy in range(7):
            for xx in range(7):
                lum[(y + yy - 3) % S, (x + xx - 3) % S] -= spot[yy, xx]
    if dark:
        lum = 1 + (lum - 1) * 1.6
    rgb = base[None, None, :] * lum[..., None]
    save(to_img(rgb), f"paper_{name}", q=90)


# ---------------------------------------------------------------- machine body
def body(name, hexcol):
    W, H = 1440 * SS // 1, 312 * SS          # 480 x 104 dp
    X, Y = grid(W, H)
    r = 30 * DP * SS
    sdf = sdf_rrect(X, Y, 0, 0, W, H + r * 2, r)
    m = cover(sdf, SS)
    h = pillow(sdf, 16 * DP * SS, 10 * DP * SS)
    n = normals(h)
    base = hexc(hexcol)
    # gentle top-to-bottom falloff, like a curved casting catching the lamp
    vert = 1.04 - 0.10 * (Y / H) ** 1.2
    g = grain(W, H, 0.012, 21)
    col = shade(base, n, amb=0.66, dif=0.40, spec=0.10, power=24) * (vert * g)[..., None]
    # thin bright lip on the top edge + inner shadow below it
    lip = np.exp(-((sdf + 2.2 * DP * SS) / (1.0 * DP * SS)) ** 2) * 0.10
    col += lip[..., None]
    save(down(to_img(col, m)), f"body_{name}")


# ---------------------------------------------------------------- roller (rod)
def roller():
    W, H = 1024 * SS, 40 * DP * SS          # rod 24 dp + shadow room
    X, Y = grid(W, H)
    rod_r = 12 * DP * SS; cy = 13 * DP * SS
    yy = (Y - cy) / rod_r
    inside = np.abs(yy) < 1
    m = np.clip((1 - np.abs(yy)) * rod_r / SS, 0, 1)
    nz = np.sqrt(np.clip(1 - yy ** 2, 0, 1)); n = np.dstack([np.zeros_like(yy), yy, nz])
    base = hexc("#121213")
    col = shade(base, n, amb=0.9, dif=0.55, spec=0.0)
    ndh = np.clip((n * HALF).sum(2), 0, 1)
    # rubber-gloss: tight specular + broad sheen + rim light from the paper below
    col += (0.85 * ndh ** 160 + 0.16 * ndh ** 12)[..., None]
    rim = np.clip(yy - 0.55, 0, 1) ** 2 * 0.35
    col += rim[..., None] * hexc("#C9BFA9")[None, None, :]
    col *= (1 + 0.025 * fbm_tile(W, H, 0.6, 31))[..., None]
    # soft shadow on the body below the rod
    sh = np.zeros((H, W), np.float32); sh[inside] = 1
    sha = soft_shadow(sh, [(0, 3 * DP * SS, 2 * DP * SS, 0.35), (0, 7 * DP * SS, 6 * DP * SS, 0.25)], W, H)
    rgb, a = over(np.zeros((H, W, 3)), sha * 0.9, col, m)
    save(down(to_img(rgb, a)), "roller")


# ---------------------------------------------------------------- carriage capsule
def capsule(name, hexcol):
    m_dp = 18
    cw, ch = 210, 76
    W, H = (cw + 2 * m_dp) * DP * SS, (ch + 2 * m_dp) * DP * SS
    X, Y = grid(W, H)
    x0, y0 = m_dp * DP * SS, m_dp * DP * SS; x1, y1 = x0 + cw * DP * SS, y0 + ch * DP * SS
    r = 34 * DP * SS
    sdf = sdf_rrect(X, Y, x0, y0, x1, y1, r)
    mask = cover(sdf, SS)
    h = pillow(sdf, 26 * DP * SS, 22 * DP * SS)
    # indicator slot: a recessed groove
    gx0, gx1 = x0 + 56 * DP * SS, x0 + 150 * DP * SS
    gy = y0 + 44 * DP * SS
    gsdf = sdf_rrect(X, Y, gx0, gy - 4 * DP * SS, gx1, gy + 4 * DP * SS, 4 * DP * SS)
    h -= pillow(gsdf, 3 * DP * SS, 3 * DP * SS)
    # LED housing: a small raised bezel
    lx, ly = x1 - 24 * DP * SS, y0 + 44 * DP * SS
    lsdf = np.hypot(X - lx, Y - ly) - 9 * DP * SS
    h += pillow(lsdf, 3 * DP * SS, 2.5 * DP * SS) * 0.6
    n = normals(h)
    ao = 1 - 0.35 * blur(cover(gsdf + 2 * DP * SS, SS), 2 * DP * SS)
    base = hexc(hexcol)
    g = grain(W, H, 0.010, 41)
    col = shade(base, n, ao=ao, amb=0.70, dif=0.38, spec=0.30, power=30) * g[..., None]
    # satin sheen band across the top
    band = np.exp(-(((Y - (y0 + 16 * DP * SS)) / (9 * DP * SS)) ** 2)) * 0.06 * mask
    col += band[..., None]
    # red indicator line: emissive core + glow
    core = cover(sdf_rrect(X, Y, gx0 + 4 * DP * SS, gy - 1.6 * DP * SS, gx1 - 4 * DP * SS, gy + 1.6 * DP * SS, 1.6 * DP * SS), SS)
    red = hexc("#E5432B")
    glow = blur(core, 3 * DP * SS) * 0.9
    col = col * (1 - core[..., None]) + core[..., None] * (red * 1.05 + np.array([0.25, 0.08, 0.04]) * (1 - np.abs(Y - gy) / (1.6 * DP * SS)).clip(0, 1)[..., None])
    col = col + glow[..., None] * np.array([0.55, 0.12, 0.05]) * (1 - core[..., None])
    # green LED dome
    led_r = 6 * DP * SS
    lsd = np.hypot(X - lx, Y - ly) - led_r
    lm = cover(lsd, SS)
    lh = np.sqrt(np.clip(1 - ((X - lx) ** 2 + (Y - ly) ** 2) / led_r ** 2, 0, 1)) * led_r
    ln = normals(lh)
    green = hexc("#3FD066")
    lcol = shade(green, ln, amb=0.85, dif=0.35, spec=0.9, power=60)
    lcol += (np.exp(-((X - lx) ** 2 + (Y - ly) ** 2) / (2 * (2.5 * DP * SS) ** 2)) * 0.35)[..., None] * np.array([0.6, 1, 0.6])
    col = col * (1 - lm[..., None]) + lcol * lm[..., None]
    halo = blur(lm, 4 * DP * SS) * 0.55
    col = col + (halo * (1 - lm))[..., None] * np.array([0.15, 0.55, 0.2])
    # where the rod enters on the left: dark collar
    collar = cover(sdf_rrect(X, Y, x0 - 2, y0 + (ch / 2 - 14) * DP * SS, x0 + 14 * DP * SS, y0 + (ch / 2 + 14) * DP * SS, 12 * DP * SS), SS)
    col *= (1 - 0.18 * blur(collar, 4 * DP * SS) * mask)[..., None]
    # shadows: tight contact + wide ambient
    sha = soft_shadow(mask, [(0, 2 * DP * SS, 1.5 * DP * SS, 0.45), (1 * DP * SS, 6 * DP * SS, 5 * DP * SS, 0.28),
                             (2 * DP * SS, 12 * DP * SS, 10 * DP * SS, 0.18)], W, H)
    rgb, a = over(np.zeros((H, W, 3)), sha, col, mask)
    save(down(to_img(rgb, a)), f"capsule_{name}")


# ---------------------------------------------------------------- chrome T lever
def chrome_env(n):
    """Studio environment: reflections of soft boxes give chrome its bands."""
    rz = 2 * n[..., 2] * n[..., 2] - 1
    ry = 2 * n[..., 2] * n[..., 1]
    rx = 2 * n[..., 2] * n[..., 0]
    t = ry * 0.85 + rx * 0.35
    env = (0.55 + 0.45 * np.exp(-((t + 0.55) / 0.18) ** 2)   # bright softbox above
           - 0.42 * np.exp(-((t - 0.15) / 0.22) ** 2)        # dark horizon band
           + 0.25 * np.exp(-((t - 0.75) / 0.2) ** 2))        # floor bounce
    return np.clip(env, 0.05, 1.2)


def lever():
    Wd, Hd = 44, 70
    W, H = Wd * DP * SS, Hd * DP * SS
    X, Y = grid(W, H)
    cx = W / 2
    s = DP * SS
    bar = sdf_rrect(X, Y, cx - 16 * s, 6 * s, cx + 16 * s, 17 * s, 5.5 * s)
    stem = sdf_rrect(X, Y, cx - 2.8 * s, 14 * s, cx + 2.8 * s, 56 * s, 2.8 * s)
    collar = sdf_rrect(X, Y, cx - 7 * s, 52 * s, cx + 7 * s, 60 * s, 3 * s)
    # cylinders: bar is horizontal, stem vertical, collar a short drum
    hb = np.sqrt(np.clip(1 - ((Y - 11.5 * s) / (5.5 * s)) ** 2, 0, 1)) * 5.5 * s * cover(bar, SS)
    endcap = np.clip(1 - np.clip((np.abs(X - cx) - 12 * s) / (4 * s), 0, 1) ** 2, 0, 1)
    hb *= np.sqrt(endcap)
    hs = np.sqrt(np.clip(1 - ((X - cx) / (2.8 * s)) ** 2, 0, 1)) * 2.8 * s * cover(stem, SS)
    hc = np.sqrt(np.clip(1 - ((X - cx) / (7 * s)) ** 2, 0, 1)) * 4 * s * cover(collar, SS)
    h = np.maximum(np.maximum(hb, hs), hc)
    mask = np.maximum(np.maximum(cover(bar, SS), cover(stem, SS)), cover(collar, SS))
    n = normals(blur(h / h.max(), 0.8 * SS) * h.max())
    env = chrome_env(n)
    # knurling on the bar
    knurl = 1 - 0.12 * (np.sin(X / (1.2 * s) * np.pi) > 0.3) * cover(bar + 1.5 * s, SS) * (np.abs(X - cx) < 11 * s)
    col = env[..., None] * np.array([0.93, 0.95, 1.0]) * knurl[..., None]
    ndh = np.clip((n * HALF).sum(2), 0, 1)
    col += (ndh ** 90 * 0.9)[..., None]
    sha = soft_shadow(mask, [(2 * s, 3 * s, 1.5 * s, 0.35), (4 * s, 7 * s, 4 * s, 0.2)], W, H)
    rgb, a = over(np.zeros((H, W, 3)), sha, col, mask)
    save(down(to_img(rgb, a)), "lever")


# ---------------------------------------------------------------- LED glow (pulsing layer)
def led_glow():
    S = 48 * DP
    X, Y = grid(S, S)
    d = np.hypot(X - S / 2, Y - S / 2) / (S / 2)
    a = np.clip(np.exp(-(d / 0.42) ** 2) * 0.9, 0, 1)
    rgb = np.ones((S, S, 3)) * hexc("#5CF08A")
    save(to_img(rgb, a), "led_glow")


# ---------------------------------------------------------------- inked wordmark
def inked_text(text, px, ink="#221E1B", seed=3, pad=None, track=0.0):
    """Render text like a worn ribbon: uneven pressure, speckle gaps, bleed into fibres."""
    f = ImageFont.truetype(FONT, px * SS)
    l, t, r, b = f.getbbox(text)
    pad = pad or px // 3
    W, H = int((r - l) + track * px * SS * (len(text) - 1)) + 2 * pad * SS, (b - t) + 2 * pad * SS
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    # draw each letter with its own pressure / tiny offset
    x = pad * SS - l
    r_ = np.random.default_rng(seed)
    for ch in text:
        adv = f.getlength(ch)
        layer = Image.new("L", (W, H), 0); dl = ImageDraw.Draw(layer)
        dx, dy = r_.normal(0, 0.012 * px * SS), r_.normal(0, 0.02 * px * SS)
        dl.text((x + dx, pad * SS - t + dy), ch, font=f, fill=int(255 * r_.uniform(0.82, 1.0)))
        im = Image.fromarray(np.maximum(np.asarray(im), np.asarray(layer)))
        x += adv + track * px * SS
    a = np.asarray(im).astype(np.float32) / 255
    bleed = blur(a, 0.9 * SS * px / 60)
    a = np.clip(np.maximum(a * 0.92, bleed * 1.25 - 0.12), 0, 1)
    speck = fbm_tile(W, H, 0.3, seed + 7)
    a *= np.clip(1.15 - 0.55 * (speck > 1.4) - 0.08 * speck, 0, 1)
    rgb = np.ones((H, W, 3)) * hexc(ink)
    return down(to_img(rgb, a))


def wordmark():
    save(inked_text("platen", 30 * DP, track=0.08), "wordmark", q=95)


# ---------------------------------------------------------------- charms (enamel pins)
def charm(name, shape_fn, color, accent=None):
    S = 40 * DP * SS
    X, Y = grid(S, S)
    sdf = shape_fn(X, Y, S)
    mask = cover(sdf, SS)
    h = pillow(sdf, 4 * DP * SS, 3 * DP * SS)
    rim = cover(np.abs(sdf + 1.2 * DP * SS) - 1.0 * DP * SS, SS)  # gold metal rim
    n = normals(h)
    col = shade(hexc(color), n, amb=0.72, dif=0.35, spec=0.8, power=70)
    gold = shade(hexc("#D4AF63"), n, amb=0.8, dif=0.5, spec=1.0, power=40)
    col = col * (1 - rim[..., None]) + gold * rim[..., None]
    if accent is not None:
        col = accent(X, Y, S, col)
    sha = soft_shadow(mask, [(0, 1.5 * DP * SS, 1.2 * DP * SS, 0.4), (1 * DP * SS, 4 * DP * SS, 3 * DP * SS, 0.22)], S, S)
    rgb, a = over(np.zeros((S, S, 3)), sha, col, mask)
    save(down(to_img(rgb, a)), f"charm_{name}")


def poly_sdf(X, Y, pts):
    """Exact signed distance to a closed polygon (negative inside)."""
    pts = np.asarray(pts, np.float64)
    d = np.full(X.shape, 1e9); inside = np.zeros(X.shape, bool)
    for i in range(len(pts)):
        ax, ay = pts[i]; bx, by = pts[(i + 1) % len(pts)]
        ex, ey = bx - ax, by - ay
        t = np.clip(((X - ax) * ex + (Y - ay) * ey) / (ex * ex + ey * ey), 0, 1)
        d = np.minimum(d, np.hypot(X - ax - t * ex, Y - ay - t * ey))
        cond = ((ay > Y) != (by > Y)) & (X < (bx - ax) * (Y - ay) / (by - ay + 1e-12) + ax)
        inside ^= cond
    return np.where(inside, -d, d)


def star_sdf(X, Y, S):
    cx, cy = S / 2, S * 0.53
    pts = []
    for k in range(10):
        a = -np.pi / 2 + k * np.pi / 5
        rr = S * (0.42 if k % 2 == 0 else 0.19)
        pts.append((cx + rr * np.cos(a), cy + rr * np.sin(a)))
    return poly_sdf(X, Y, pts) - S * 0.02


def heart_sdf(X, Y, S):
    t = np.linspace(0, 2 * np.pi, 120, endpoint=False)
    hx = 16 * np.sin(t) ** 3
    hy = -(13 * np.cos(t) - 5 * np.cos(2 * t) - 2 * np.cos(3 * t) - np.cos(4 * t))
    k = S * 0.40 / 17
    return poly_sdf(X, Y, list(zip(S / 2 + hx * k, S * 0.50 + hy * k))) - S * 0.01


def circle_sdf(X, Y, S):
    return np.hypot(X - S / 2, Y - S / 2) - S * 0.38


def moon_sdf(X, Y, S):
    a = np.hypot(X - S / 2, Y - S / 2) - S * 0.38
    b = np.hypot(X - S * 0.64, Y - S * 0.40) - S * 0.30
    return np.maximum(a, -b)


def flower_accent(X, Y, S, col):
    c = cover(np.hypot(X - S / 2, Y - S / 2) - S * 0.12, SS)
    return col * (1 - c[..., None]) + c[..., None] * hexc("#F2C14E")


def flower_sdf(X, Y, S):
    ang = np.arctan2(Y - S / 2, X - S / 2)
    return np.hypot(X - S / 2, Y - S / 2) - S * (0.30 + 0.10 * np.cos(5 * ang))


def coffee_sdf(X, Y, S):
    cup = sdf_rrect(X, Y, S * 0.22, S * 0.30, S * 0.68, S * 0.80, S * 0.10)
    handle = np.abs(np.hypot(X - S * 0.70, Y - S * 0.52) - S * 0.11) - S * 0.045
    return np.minimum(cup, handle)


# ---------------------------------------------------------------- launcher icon
def icon():
    """Adaptive icon layers rendered at 432 px (xxxhdpi, 108 dp)."""
    S = 432 * SS
    u = S / 108.0                         # px per icon dp
    X, Y = grid(S, S)
    # ---- background: cream casting with soft dome shading and paper grain
    base = hexc("#EAE2D3")
    d = np.hypot(X - S * 0.42, Y - S * 0.30) / S
    lum = 1.05 - 0.22 * d ** 1.6
    bg = base[None, None, :] * (lum * grain(S, S, 0.02, 77))[..., None]
    fib = fbm_tile(S, S, 0.9, 78)
    bg *= (1 + 0.012 * fib)[..., None]
    Image.fromarray((np.clip(bg, 0, 1) * 255).astype(np.uint8)).resize((432, 432), Image.LANCZOS) \
        .save("/tmp/icon_bg.png")
    # ---- foreground composition (reference layout mapped into the 66 dp safe circle)
    fg = np.zeros((S, S, 3)); fa = np.zeros((S, S))
    def map_(px, py):           # reference 400 px space -> the 72 dp visible viewport
        return 18 * u + px * 0.18 * u, 18 * u + py * 0.18 * u
    # rod: from the far left edge into the capsule
    cy = map_(0, 294)[1]; rr = 5.2 * u
    yy = (Y - cy) / rr
    rodm = cover((np.abs(Y - cy) - rr) * 1.0, SS) * (X < map_(160, 0)[0])
    nz = np.sqrt(np.clip(1 - yy ** 2, 0, 1)); n = np.dstack([np.zeros_like(yy), np.clip(yy, -1, 1), nz])
    ndh = np.clip((n * HALF).sum(2), 0, 1)
    rodc = hexc("#111112")[None, None, :] * (0.9 + 0.55 * np.clip((n * LIGHT).sum(2), 0, 1))[..., None] + (0.9 * ndh ** 140 + 0.15 * ndh ** 10)[..., None]
    rodc += (np.clip(yy - 0.55, 0, 1) ** 2 * 0.3)[..., None] * hexc("#C9BFA9")
    rodsh = soft_shadow(rodm, [(0, 1.2 * u, 1.0 * u, 0.35), (0, 3 * u, 2.5 * u, 0.25)], S, S)
    fg, fa = over(fg, fa, np.zeros((S, S, 3)), rodsh)
    fg, fa = over(fg, fa, rodc, rodm)
    # capsule (bleeds off the right edge like the reference)
    x0, y0 = map_(108, 222); x1, y1 = map_(430, 346)
    sdf = sdf_rrect(X, Y, x0, y0, x1, y1, 11 * u)
    cm = cover(sdf, SS)
    h = pillow(sdf, 8 * u, 7 * u)
    gx0, gy = map_(146, 290); gx1, _ = map_(262, 290)
    gsdf = sdf_rrect(X, Y, gx0, gy - 1.1 * u, gx1, gy + 1.1 * u, 1.1 * u)
    h -= pillow(gsdf, 0.8 * u, 0.8 * u)
    # lever slot plate on the top of the capsule
    lcx = map_(318, 0)[0]
    psdf = sdf_rrect(X, Y, lcx - 3.2 * u, y0 + 0.2 * u, lcx + 3.2 * u, y0 + 1.6 * u, 0.7 * u)
    n = normals(h)
    capc = shade(hexc("#F1EBDF"), n, amb=0.70, dif=0.36, spec=0.3, power=30) * grain(S, S, 0.008, 79)[..., None]
    capc *= (1 - 0.30 * blur(cover(gsdf + 0.5 * u, SS), 0.6 * u))[..., None]
    core = cover(sdf_rrect(X, Y, gx0 + 0.8 * u, gy - 0.45 * u, gx1 - 0.8 * u, gy + 0.45 * u, 0.45 * u), SS)
    capc = capc * (1 - core[..., None]) + core[..., None] * hexc("#E8452C")
    capc += (blur(core, 1.2 * u) * (1 - core))[..., None] * np.array([0.5, 0.1, 0.04])
    pm = cover(psdf, SS)
    capc = capc * (1 - pm[..., None]) + pm[..., None] * hexc("#3A3633") * (1 + 0.4 * (Y < y0 + 0.8 * u))[..., None]
    lx, ly = map_(352, 292)
    lr = 2.0 * u
    lm = cover(np.hypot(X - lx, Y - ly) - lr, SS)
    lh = np.sqrt(np.clip(1 - ((X - lx) ** 2 + (Y - ly) ** 2) / lr ** 2, 0, 1)) * lr
    lcol = shade(hexc("#40D267"), normals(lh), amb=0.85, dif=0.35, spec=0.9, power=50)
    bez = cover(np.hypot(X - lx, Y - ly) - lr * 1.45, SS) * (1 - lm)
    capc *= (1 - 0.18 * bez)[..., None]
    capc = capc * (1 - lm[..., None]) + lcol * lm[..., None]
    capc += (blur(lm, 1.5 * u) * (1 - lm) * 0.8)[..., None] * np.array([0.15, 0.6, 0.2])
    capsh = soft_shadow(cm, [(0, 0.8 * u, 0.6 * u, 0.4), (0.4 * u, 2.4 * u, 2 * u, 0.28), (0.8 * u, 5 * u, 4 * u, 0.18)], S, S)
    fg, fa = over(fg, fa, np.zeros((S, S, 3)), capsh)
    fg, fa = over(fg, fa, capc, cm)
    # chrome T lever, standing in the slot
    bt = map_(0, 146)[1]
    bar = sdf_rrect(X, Y, lcx - 4.8 * u, bt, lcx + 4.8 * u, bt + 3.0 * u, 1.5 * u)
    stem = sdf_rrect(X, Y, lcx - 0.75 * u, bt + 2 * u, lcx + 0.75 * u, y0 + 1.0 * u, 0.75 * u)
    hb = np.sqrt(np.clip(1 - ((Y - (bt + 1.5 * u)) / (1.5 * u)) ** 2, 0, 1)) * 1.5 * u * cover(bar, SS)
    hs = np.sqrt(np.clip(1 - ((X - lcx) / (0.75 * u)) ** 2, 0, 1)) * 0.75 * u * cover(stem, SS)
    hl = np.maximum(hb, hs); lmask = np.maximum(cover(bar, SS), cover(stem, SS))
    nl = normals(blur(hl / max(hl.max(), 1e-6), 0.5 * SS) * hl.max())
    chrome = chrome_env(nl)[..., None] * np.array([0.93, 0.95, 1.0]) + (np.clip((nl * HALF).sum(2), 0, 1) ** 80 * 0.8)[..., None]
    lsh = soft_shadow(lmask, [(0.6 * u, 0.9 * u, 0.5 * u, 0.35), (1.2 * u, 2 * u, 1.2 * u, 0.2)], S, S)
    fg, fa = over(fg, fa, np.zeros((S, S, 3)), lsh)
    fg, fa = over(fg, fa, chrome, lmask)
    # wordmark, ~51 dp wide like the reference
    f = ImageFont.truetype(FONT, 100); wpx = f.getlength("platen") + 5 * 0.10 * 100
    wm = inked_text("platen", int(100 * 51 * u / SS / wpx), seed=5, track=0.10)
    wmb = wm.resize((wm.width * SS, wm.height * SS), Image.LANCZOS)
    wx = int(map_(192, 0)[0] - wmb.width / 2); wy = int(map_(0, 124)[1] - wmb.height / 2)
    arr = np.asarray(wmb).astype(np.float32) / 255
    tw = np.zeros((S, S, 4)); tw[wy:wy + wmb.height, wx:wx + wmb.width] = arr
    fg, fa = over(fg, fa, tw[..., :3], tw[..., 3])
    fgi = down(to_img(fg, fa))
    bgi = Image.open("/tmp/icon_bg.png")
    for q, sz in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
        dd = f"{RES}/mipmap-{q}"; os.makedirs(dd, exist_ok=True)
        fgi.resize((sz, sz), Image.LANCZOS).save(f"{dd}/ic_launcher_foreground.webp", "WEBP", quality=95, method=6)
        bgi.resize((sz, sz), Image.LANCZOS).save(f"{dd}/ic_launcher_background.webp", "WEBP", quality=92, method=6)
    # preview with a squircle-ish mask, like a launcher would show it
    full = bgi.convert("RGBA"); full.alpha_composite(fgi)
    m = Image.new("L", (432, 432), 0); ImageDraw.Draw(m).rounded_rectangle((54, 54, 378, 378), 80, fill=255)
    prev = full.crop((54, 54, 378, 378)); prev.putalpha(m.crop((54, 54, 378, 378)))
    prev.save(ROOT + "/screenshots/00b_app_icon_rendered.png")
    full.save("/tmp/icon_full.png")
    print("icon layers written")


if __name__ == "__main__":
    import sys
    os.makedirs(ROOT + "/screenshots", exist_ok=True)
    what = sys.argv[1:] or ["all"]
    if "all" in what or "paper" in what:
        for k, (p, b, c) in THEMES.items(): paper(k, p, dark=(k == "charcoal"))
    if "all" in what or "body" in what:
        for k, (p, b, c) in THEMES.items(): body(k, b)
    if "all" in what or "capsule" in what:
        for k, (p, b, c) in THEMES.items(): capsule(k, c)
    if "all" in what or "roller" in what: roller()
    if "all" in what or "lever" in what: lever()
    if "all" in what or "led" in what: led_glow()
    if "all" in what or "wordmark" in what: wordmark()
    if "all" in what or "charms" in what:
        charm("star", star_sdf, "#F2C14E")
        charm("heart", heart_sdf, "#D9473B")
        charm("flower", flower_sdf, "#F7F3EA", flower_accent)
        charm("moon", moon_sdf, "#3E5C8A")
        charm("coffee", coffee_sdf, "#7A4E33")
    if "all" in what or "icon" in what: icon()
