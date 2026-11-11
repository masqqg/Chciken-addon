"""Generate the painted textures used by the Chicken Client cosmetics.

Everything is drawn at 3x and downsampled, so the results have soft edges
instead of the aliased 1px look of the earlier flat-quad art.
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "chicken", "textures")
SS = 3


def save(img, *parts):
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(path, img.size)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def feather():
    w, h = 96, 380
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    W, H = w * SS, h * SS

    def profile(t):
        # Leaf silhouette: narrow at the quill, widest at 62%, softly pointed tip.
        return (math.sin(math.pi * min(1.0, t ** 0.78)) ** 0.85) * 0.5

    def bend(t):
        return math.sin(t * 1.7) * 0.10

    rows = H
    for y in range(rows):
        t = y / (rows - 1)
        half = profile(t) * W * 0.94
        if half < 0.6:
            continue
        cx = W * 0.5 + bend(t) * W
        x0, x1 = cx - half, cx + half

        for x in range(int(x0), int(x1) + 1):
            u = (x - x0) / max(1.0, (x1 - x0))
            edge = min(u, 1.0 - u)
            fade = min(1.0, edge * 6.0)          # soft feathered edge
            length = t
            base = (58, 8, 18)
            mid = (168, 22, 46)
            tip = (255, 108, 126)
            if length < 0.45:
                col = lerp(base, mid, length / 0.45)
            else:
                col = lerp(mid, tip, (length - 0.45) / 0.55)
            # baked directional shading across the vane
            shade = 0.55 + 0.45 * (1.0 - abs(u - 0.32) * 1.5) if u < 0.32 else 0.55 + 0.45 * (1.0 - (u - 0.32) * 1.1)
            shade = max(0.42, min(1.0, shade))
            col = tuple(int(c * shade) for c in col)
            alpha = int(255 * fade * (0.55 + 0.45 * (1.0 - length)))
            d.point((x, y), fill=col + (alpha,))

    # Quill and vane veins on top.
    for y in range(rows):
        t = y / (rows - 1)
        cx = W * 0.5 + bend(t) * W
        d.line([(cx, y), (cx, y + 1)], fill=(246, 232, 214, 235))
    for i in range(1, 26):
        t = i / 26.0
        y = int(t * H)
        cx = W * 0.5 + bend(t) * W
        half = profile(t) * W * 0.9
        for side in (-1, 1):
            for k in range(6):
                x = cx + side * half * (k / 6.0)
                d.point((x, y - k * 2.0), fill=(255, 190, 195, 70))

    img = img.resize((w, h), Image.LANCZOS)
    img = img.filter(ImageFilter.GaussianBlur(0.4))
    save(img, "wings", "feather.png")


def glow():
    size = 128
    img = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(90, 0, -1):
        t = i / 90.0
        r = int((size * 2) * 0.5 * t)
        alpha = int(150 * (1.0 - t) ** 2.2)
        cx, cy = size, size
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 214, 140, alpha))
    img = img.resize((size, size), Image.LANCZOS)
    img = img.filter(ImageFilter.GaussianBlur(3))
    save(img, "common", "glow.png")


def blade():
    w, h = 200, 420
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    W, H = w * SS, h * SS

    # Hawkbill curve: spine and edge as two parametric curves.
    def spine(t):
        x = W * (0.52 - 0.30 * math.sin(t * 1.25) ** 1.5)
        y = H * (0.94 - 0.90 * t)
        return x, y

    def edge(t):
        x = W * (0.72 - 0.10 * t - 0.30 * math.sin(t * 1.25) ** 1.5)
        y = H * (0.96 - 0.88 * t)
        return x, y

    steps = 260
    for i in range(steps):
        t = i / (steps - 1)
        sx, sy = spine(t)
        ex, ey = edge(t)
        width = max(1.0, (ex - sx))
        for x in range(int(sx), int(ex) + 1):
            u = (x - sx) / max(1.0, width)
            # Metallic gradient: dark spine -> steel -> bright bevel near the edge.
            if u < 0.18:
                col = lerp((26, 28, 34), (92, 98, 110), u / 0.18)
            elif u < 0.82:
                col = lerp((92, 98, 110), (196, 204, 216), (u - 0.18) / 0.64)
            else:
                col = lerp((214, 222, 234), (255, 255, 255), (u - 0.82) / 0.18)
            # Brushed streaks along the blade.
            streak = math.sin(t * 90.0 + u * 12.0) * 7.0
            col = tuple(max(0, min(255, int(c + streak))) for c in col)
            d.point((x, int(sy)), fill=col + (255,))

    # Bright cutting edge.
    for i in range(steps):
        t = i / (steps - 1)
        ex, ey = edge(t)
        d.ellipse([ex - 2 * SS, ey - 2 * SS, ex + 2 * SS, ey + 2 * SS], fill=(255, 250, 235, 255))

    # Dark spine shadow.
    for i in range(steps):
        t = i / (steps - 1)
        sx, sy = spine(t)
        d.ellipse([sx - 3 * SS, sy - 3 * SS, sx + 3 * SS, sy + 3 * SS], fill=(14, 16, 20, 255))

    img = img.resize((w, h), Image.LANCZOS)
    img = img.filter(ImageFilter.GaussianBlur(0.5))
    save(img, "karambit", "blade.png")


def grip():
    w, h = 96, 220
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    W, H = w * SS, h * SS

    d.rounded_rectangle([W * 0.18, 0, W * 0.82, H], radius=W * 0.22, fill=(28, 30, 38, 255))
    for i in range(26):
        y = H * i / 26.0
        d.line([(W * 0.18, y), (W * 0.82, y + H * 0.02)], fill=(12, 13, 17, 255), width=int(SS * 2.2))
        d.line([(W * 0.18, y + SS * 1.2), (W * 0.82, y + H * 0.02 + SS * 1.2)], fill=(58, 62, 74, 210), width=int(SS * 1.2))
    d.rounded_rectangle([W * 0.14, 0, W * 0.86, H * 0.06], radius=W * 0.05, fill=(198, 158, 74, 255))
    d.rounded_rectangle([W * 0.14, H * 0.94, W * 0.86, H], radius=W * 0.05, fill=(198, 158, 74, 255))
    img = img.resize((w, h), Image.LANCZOS)
    save(img, "karambit", "grip.png")


def cloth():
    """Soft fabric/plume texture for the wing membrane between feathers."""
    w, h = 64, 256
    img = Image.new("RGBA", (w, h), (96, 12, 26, 255))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        for x in range(w):
            u = x / (w - 1)
            base = lerp((72, 8, 20), (196, 26, 54), t)
            shade = 0.75 + 0.25 * math.sin(u * math.pi)
            px[x, y] = tuple(int(c * shade) for c in base) + (255,)
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    save(img, "wings", "membrane.png")


feather()
glow()
blade()
grip()
cloth()
