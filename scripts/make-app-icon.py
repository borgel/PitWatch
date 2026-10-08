"""Generates the PitWatch launcher + notification icon vectors (design 4d-2: slanted PW on a slanted
red/blue split, yellow underline). Letters come from Barlow Condensed ExtraBold, slanted 12 degrees, and
their exact outline bounds are centered on the canvas center."""
import math
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.misc.transform import Transform

FONT, RES = sys.argv[1], sys.argv[2]
SLANT = math.tan(math.radians(12))
WHITE, YELLOW, RED, BLUE = "#FFFFFFFF", "#FFFFD60A", "#FFC62828", "#FF1E5BD6"

font = TTFont(FONT)
cmap, glyphs, hmtx = font.getBestCmap(), font.getGlyphSet(), font["hmtx"]
upm = font["head"].unitsPerEm


def draw(pen, size, dx, dy):
    """Draws "PW" at font size `size` (canvas units), slanted, offset by (dx, dy)."""
    s = size / upm
    x = 0
    for ch in "PW":
        name = cmap[ord(ch)]
        # font (x, y-up) -> canvas (x, y-down), then skewX(-12deg) about the baseline.
        t = Transform(s, 0, SLANT * s, -s, dx + x * s, dy)
        glyphs[name].draw(TransformPen(pen, t))
        x += hmtx[name][0]


def letters(size, cx, cy):
    """Path data for the letters with their outline bounds centered on (cx, cy), plus those bounds."""
    b = BoundsPen(glyphs)
    draw(b, size, 0, 0)
    x0, y0, x1, y1 = b.bounds
    dx, dy = cx - (x0 + x1) / 2, cy - (y0 + y1) / 2
    p = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    draw(p, size, dx, dy)
    return p.getCommands(), (x0 + dx, y0 + dy, x1 + dx, y1 + dy)


def underline(bounds, cx, cy, gap, height, width):
    """A parallelogram under the letters on the same slanted axis through (cx, cy)."""
    top = bounds[3] + gap
    bottom = top + height
    mid = (top + bottom) / 2
    center = cx - SLANT * (mid - cy)

    def x_at(y, offset):
        return center + offset - SLANT * (y - mid)

    pts = [(x_at(top, -width / 2), top), (x_at(top, width / 2), top),
           (x_at(bottom, width / 2), bottom), (x_at(bottom, -width / 2), bottom)]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


def vector(size, viewport, paths, comment):
    body = "\n".join(
        f'    <path\n        android:fillColor="{color}"\n        android:pathData="{data}" />'
        for color, data in paths)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Generated from Barlow Condensed '
            f'ExtraBold by scripts/make-app-icon.py. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n'
            f'{body}\n</vector>\n')


def write(name, text):
    with open(f"{RES}/{name}", "w") as f:
        f.write(text)


# Launcher: 108-unit adaptive layers, centered on (54, 54).
glyph_path, b = letters(40, 54, 54)
line = underline(b, 54, 54, gap=3.5, height=5, width=36)
print("launcher letters bounds", [round(v, 2) for v in b], "center",
      round((b[0] + b[2]) / 2, 2), round((b[1] + b[3]) / 2, 2))

# The split line runs on the letters' slant through the center.
top_x, bottom_x = 54 + SLANT * 54, 54 - SLANT * 54
red = f"M0,0 L{top_x:.2f},0 L{bottom_x:.2f},108 L0,108 Z"
blue = f"M{top_x:.2f},0 L108,0 L108,108 L{bottom_x:.2f},108 Z"

write("drawable/ic_launcher_background.xml",
      vector(108, 108, [(RED, red), (BLUE, blue)], "Launcher background: red/blue split on the 12° slant."))
write("drawable/ic_launcher_foreground.xml",
      vector(108, 108, [(WHITE, glyph_path), (YELLOW, line)], "Launcher foreground: slanted PW, yellow underline."))
write("drawable/ic_launcher_monochrome.xml",
      vector(108, 108, [(WHITE, glyph_path + " " + line)], "Themed-icon layer: the foreground in one color."))

# Notification: 24 units, letters + underline as one white silhouette, the whole mark centered.
small_path, sb = letters(17.5, 12, 0)
sline = underline(sb, 12, 0, gap=1.6, height=2.4, width=16)
total_h = (sb[3] + 1.6 + 2.4) - sb[1]
shift = 12 - (sb[1] + total_h / 2)
small_path, sb = letters(17.5, 12, shift)
sline = underline(sb, 12, shift, gap=1.6, height=2.4, width=16)
print("status letters bounds", [round(v, 2) for v in sb])
write("drawable/ic_stat_pitwatch.xml",
      vector(24, 24, [(WHITE, small_path + " " + sline)], "Status-bar icon: slanted PW over an underline."))


def farthest(path_bounds_points):
    return max(math.hypot(x - 54, y - 54) for x, y in path_bounds_points)


# Everything must sit inside the 33-unit safe circle that every launcher mask keeps.
corners = [(b[0], b[3]), (b[2], b[1])] + [tuple(map(float, p.split(","))) for p in line[1:-2].split(" L")]
print("farthest point from center", round(farthest(corners), 2), "(safe zone 33)")
