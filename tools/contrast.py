"""Measured contrast of a text region in a screenshot: python -I tools/contrast.py shot.png name x0 y0 x1 y1 [name x0 y0 x1 y1 ...]
Background = the region's median colour, text = its 0.25th-percentile darkest (or brightest, whichever is farther) pixel.
WCAG relative luminance; prints ratio per region."""
import sys

from PIL import Image


def lum(c):
    def ch(v):
        v = v / 255.0
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = c[:3]
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


im = Image.open(sys.argv[1]).convert("RGB")
a = sys.argv[2:]
for i in range(0, len(a), 5):
    name = a[i]
    box = tuple(int(v) for v in a[i + 1:i + 5])
    px = sorted(im.crop(box).getdata(), key=lum)
    bg = px[len(px) // 2]
    dark = px[len(px) // 400]
    light = px[-1 - len(px) // 400]
    lb = lum(bg)
    fg = dark if abs(lum(dark) - lb) > abs(lum(light) - lb) else light
    lf = lum(fg)
    ratio = (max(lb, lf) + 0.05) / (min(lb, lf) + 0.05)
    print("%-14s bg=%s fg=%s  %.1f:1" % (name, "#%02x%02x%02x" % bg, "#%02x%02x%02x" % fg, ratio))
