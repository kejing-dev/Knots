"""Knot lab: author new knots as 3D control points (mm, rope frame: x right, y up, z towards the user), check them and
emit Kotlin for KnotLibrary.

    python -I tools/knotlab.py render <knot> [out.png]   front + top + side views, crossings marked (over strand drawn last)
    python -I tools/knotlab.py check  <knot>             min gap / d at every step end for d = 4 / 8 / 11 (Catmull-Rom as the app)
    python -I tools/knotlab.py kotlin <knot>             the Ctl list for KnotLibrary.kt

Control points are emitted as `Z(x, y, z, dy, zd)`: x, y in mm (the app scales them with d / 8), z in mm, dy / zd in
units of d (crossing lifts and wrap radii must scale with the rope's diameter). Post knots use the clove's post
(R = 20 mm at the origin) and are not scaled.
"""
import math
import sys

POST_R = 20.0
SUB = 12


class K:
    def __init__(self, id, pts, ends, post=False, anchors=0, split=-1, shown2=-1):
        self.id = id
        self.pts = pts  # list of (x, y, z, dy, zd)
        self.ends = ends  # control index where each step ends (lay steps)
        self.post = post
        self.anchors = anchors
        self.split = split  # two-rope knots: first control point of the second rope
        self.shown2 = shown2  # the second rope is laid up to here before the first is tied
        self.line_split = -1  # sample index of the second rope in the last line_of()


def resolve(k, d):
    s = 1.0 if k.post else d / 8.0
    out = []
    for i, (x, y, z, dx, dy, zd) in enumerate(k.pts):
        if not k.post and i >= k.anchors:
            x, y = x * s, 30 + (y - 30) * s
        out.append((x + dx * d, y + dy * d, z + zd * d))
    if not k.post and k.anchors > 0:
        # the straight standing part keeps its spacing above the first scaled point (as the app)
        a = k.anchors
        y_a = 30 + (k.pts[a][1] - 30) * s + k.pts[a][4] * d
        for i in range(a):
            out[i] = (out[i][0], y_a + (k.pts[i][1] - k.pts[a][1]), out[i][2])
    return out


def catmull(c):
    out = []
    n = len(c)
    for i in range(n - 1):
        p0, p1, p2, p3 = c[max(0, i - 1)], c[i], c[i + 1], c[min(n - 1, i + 2)]
        for sidx in range(SUB):
            t = sidx / SUB
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p1[q] + (-p0[q] + p2[q]) * t + (2 * p0[q] - 5 * p1[q] + 4 * p2[q] - p3[q]) * t2 + (-p0[q] + 3 * p1[q] - 3 * p2[q] + p3[q]) * t3) for q in range(3)))
    out.append(c[-1])
    return out


def clamp_post(k, line, d):
    if not k.post:
        return line
    rmin = POST_R + 0.5 * d
    res = []
    for (x, y, z) in line:
        r = math.hypot(x, z)
        if -120 <= y <= 120 and 1e-4 < r < rmin:
            x, z = x * rmin / r, z * rmin / r
        res.append((x, y, z))
    return res


def arcs(line, split=-1):
    a = [0.0]
    for i in range(1, len(line)):
        a.append(a[-1] + (0.0 if i == split else math.dist(line[i], line[i - 1])))
    return a


def min_gap(line, d, split=-1):
    a = arcs(line, split)
    best, at = 1e9, None
    n = len(line)
    for i in range(n):
        for j in range(i + 1, n):
            if not (i < split <= j) and a[j] - a[i] <= 3 * d:
                continue
            g = math.dist(line[i], line[j])
            if g < best:
                best, at = g, (i, j)
    return best, at


def crossings(line, split=-1):
    res = []
    a = arcs(line, split)
    for i in range(len(line) - 1):
        for j in range(i + 3, len(line) - 1):
            if i + 1 == split or j + 1 == split:
                continue
            p, q, r, s_ = line[i], line[i + 1], line[j], line[j + 1]
            den = (q[0] - p[0]) * (s_[1] - r[1]) - (q[1] - p[1]) * (s_[0] - r[0])
            if abs(den) < 1e-9:
                continue
            t = ((r[0] - p[0]) * (s_[1] - r[1]) - (r[1] - p[1]) * (s_[0] - r[0])) / den
            u = ((r[0] - p[0]) * (q[1] - p[1]) - (r[1] - p[1]) * (q[0] - p[0])) / den
            if 0 <= t < 1 and 0 <= u < 1:
                zi = p[2] + (q[2] - p[2]) * t
                zj = r[2] + (s_[2] - r[2]) * u
                res.append((a[i], a[j], zi > zj, p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t))
    return res


def line_of(k, d, upto=None):
    pts = resolve(k, d)
    if k.split > 0:
        a, b = pts[: k.split], pts[k.split:]
        if upto is not None:
            if upto < k.split:
                a, b = a[: upto + 1], b[: k.shown2 - k.split + 1]
            else:
                b = b[: max(upto, k.shown2) - k.split + 1]
        ca = catmull(a)
        k.line_split = len(ca)
        return clamp_post(k, ca + catmull(b), d)
    k.line_split = -1
    if upto is not None:
        pts = pts[: upto + 1]
    return clamp_post(k, catmull(pts), d)


# ------------------------------------------------------------------------------------------------
# shapes


def P(x, y, z=0.0, dx=0.0, dy=0.0, zd=0.0):
    return (float(x), float(y), float(z), float(dx), float(dy), float(zd))


def trefoil(R, t, zk=0.75):
    """Trefoil rotated so its cut lobe is at the top: (x, y, zd)."""
    x = -(math.sin(t) + 2 * math.sin(2 * t))
    y = -(math.cos(t) - 2 * math.cos(2 * t))
    return R * x, R * y, -zk * math.sin(3 * t)


def overhand():
    """Overhand knot: a trefoil opened at its top lobe. Standing part from above, the working end around the outside and down."""
    R = 12.0
    eps = 0.32
    n = 33
    tri = [trefoil(R, math.pi + eps + (2 * math.pi - 2 * eps) * i / n) for i in range(n + 1)]
    sx, sy, _ = tri[0]
    ex, ey, _ = tri[-1]
    pts = [P(sx, 400), P(sx, 250), P(sx, 150), P(sx, sy + 45)]
    pts += [P(x, y, 0, zd=z) for (x, y, z) in tri]
    # the working end: out to the right of the knot, then down past it
    pts += [P(ex + 22, ey + 6), P(4.6 * R, 0.5 * R), P(4.3 * R, -2.6 * R), P(3.2 * R, -5.2 * R), P(2.6 * R, -8.5 * R), P(2.4 * R, -11 * R)]
    k = K("overhand", pts, [], anchors=3)
    k.start = 3
    return k


SHAPES = {"overhand": overhand}


# ---------- the figure eight as drawn by the app (control points of KnotLibrary.figure8Pts, all crossings raised)
FIG8 = [(0, 400, 0), (0, 250, 0), (0, 150, 0), (0, 90, 0), (0, 66, 0), (0, 55, 1), (0, 43, 0), (0, 30, -1), (0, 15, 0), (0, 0, 0),
        (14, -16, 1), (30, -30, 0), (43, -12, 0), (40, 10, 0), (25, 17, -1), (12, 24, 0), (0, 30, 1), (-16, 34, 0), (-30, 38, 0),
        (-38, 50, 0), (-22, 58, 0), (0, 55, -1), (18, 61, 0), (34, 60, 0),
        (46, 47, 0), (38, 30, 0), (25, 17, 1), (20, 4, 0), (14, -16, -1), (10, -42, 0), (8, -80, 0), (7, -110, 0)]


def dense(points, per=SUB):
    """Catmull-Rom through (x, y, zd) points (zd kept in units of d)."""
    return catmull([tuple(p) for p in points])


def follow_through(base, base_start, knot_end, loop_r=26.0, gap_d=1.15, exit_y=95.0, step=7.0, grow=2.2):
    """A knot tied in the end, then the tail around a ring / harness and back along its own path, gap_d * d beside it.
    The knot is drawn `grow` times larger: a doubled knot really is about twice the size (each strand is a pair)."""
    base = [(x * grow, y if i < base_start else base[base_start][1] + (y - base[base_start][1]) * grow, zd) for i, (x, y, zd) in enumerate(base)]
    a = dense(base)
    # resample every `step` mm (in the d = 8 frame)
    res = [a[0]]
    acc = 0.0
    for i in range(1, len(a)):
        acc += math.dist(a[i][:2], a[i - 1][:2])
        if acc >= step:
            res.append(a[i])
            acc = 0.0
    if res[-1] != a[-1]:
        res.append(a[-1])
    # a doubled crossing is ~2.3 d wide: widen each crossing's lift along the rope (keep the strongest lift within ±2 samples)
    widened = []
    for i in range(len(res)):
        win = [res[j][2] for j in range(max(0, i - 2), min(len(res), i + 3))]
        widened.append((res[i][0], res[i][1], max(win, key=abs)))
    A = widened
    pts = [P(x, y, 0, 0, 0, zd * 0.72) for (x, y, zd) in A]
    n_first = len(pts)
    ex, ey, _ = A[-1]
    # the loop around the harness: a teardrop below the tail, leaving down-left and coming back up-right, beside the tail
    gm = gap_d * 8.0
    cxl, cyl = ex + gm / 2, ey - 20 - loop_r * 1.4
    loop = []
    for i in range(0, 13):
        th = math.radians(150 + (390 - 150) * i / 12)
        loop.append((cxl + loop_r * math.cos(th), cyl + loop_r * math.sin(th)))
    pts += [P(ex - 0.15 * gm, ey - 14), *[P(x, y) for (x, y) in loop], P(ex + 1.15 * gm, ey - 14)]
    loop_end = len(pts) - 1
    # retrace: A backwards, offset to the right of A's travel direction
    back = []
    for i in range(len(A) - 1, -1, -1):
        x, y, zd = A[i]
        j0, j1 = max(0, i - 1), min(len(A) - 1, i + 1)
        tx, ty = A[j1][0] - A[j0][0], A[j1][1] - A[j0][1]
        tl = math.hypot(tx, ty) or 1.0
        nx, ny = -ty / tl, tx / tl  # left of travel (the loop comes back on that side)
        back.append(P(x, y, 0, nx * gap_d, ny * gap_d, zd * 0.72))
        if y > exit_y and i < len(A) - 5:
            break
    pts += back
    return pts, n_first, loop_end


def fig8_follow():
    pts, n_first, loop_end = follow_through(FIG8, 3, 31, gap_d=1.05, grow=1.85, loop_r=22.0)
    k = K("fig8follow", pts, [n_first - 1, loop_end], anchors=3)
    k.start = 4
    return k


def overhand_follow():
    base = overhand()
    A = [(x, y, zd / 0.6) for (x, y, z, dx, dy, zd) in base.pts]
    pts, n_first, loop_end = follow_through(A, 3, len(A) - 1, gap_d=1.05, grow=1.9, loop_r=22.0)
    k = K("overhandloop", pts, [n_first - 1, loop_end], anchors=3)
    k.start = 3
    return k


# ---------- hitches on a post (R = 20 mm at the origin; standing part from the left along the post's front)
CLOVE = [("C", 0, 16, 0, -0.3), ("C", 45, 9, 0, 0), ("C", 90, 1, 0, 0), ("C", 135, -6, 0, 0), ("C", 180, -12, 0, 0), ("C", 225, -16, 0, 0), ("C", 270, -18, 0, 0),
         ("C", 315, -4, 0, 0), ("C", 340, 7, 1, 0), ("C", 360, 16, 1, 0), ("C", 380, 25, 1, 0), ("C", 400, 33, 1, 0), ("C", 420, 39, 1, 0), ("C", 445, 45, 0, 0), ("C", 470, 49, 0, 0),
         ("C", 510, 52, 0, 0), ("C", 550, 54, 0, 0), ("C", 590, 55, 0, 0), ("C", 630, 55, 0, 0),
         ("C", 675, 44, 0, 0), ("C", 705, 31, 0, 0.4), ("C", 735, 26, 0, 0.4), ("C", 765, 26, 0, 0.2)]
CLOVE_ENDS = [6, 14, 18, 22]  # indices in CLOVE where the clove's steps end (wrap, cross, wrap, tuck)


def post_wrap(turn_deg, y0, y1, n_per_90=2):
    """Around the post from the front, going right (theta 0 = front, 90 = right): (x, y, z, dx, dy, zd)."""
    pts = []
    steps = max(2, int(turn_deg / 90 * n_per_90))
    for i in range(1, steps + 1):
        th = math.radians(turn_deg * i / steps)
        y = y0 + (y1 - y0) * i / steps
        pts.append(P(20 * math.sin(th), y, 20 * math.cos(th), 0.5 * math.sin(th), 0, 0.5 * math.cos(th)))
    return pts


def clove_on_standing(x0):
    """A clove hitch tied around the standing part (axis: y = 0, z = 20 mm + d/2, along x) = two half hitches.
    Clove coordinates (theta, u) -> axial dx = (16 - u) / 8 d from x0, cross-section radius (1 + 1.1 lvl) d; theta 0 = front, 90 = up."""
    pts = []
    for (_, th, u, lvl, du) in CLOVE:
        r = 1.1 + 1.15 * lvl
        a = math.radians(th)
        pts.append(P(x0, 0, 20, (16 - u) / 8.0 - du, r * math.sin(a), 0.5 + r * math.cos(a)))
    return pts


def hitch(round_turn):
    standing = [P(x, 0, 20, 0, 0, 0.5) for x in (-420, -300, -200, -150)]
    # the standing part runs along the post's front up to the post
    pts = standing + [P(-60, 0, 20, 0, 0, 0.5), P(-24, 0, 20, 0, 0, 0.5)]
    start = len(pts) - 1
    turns = 300 + (360 if round_turn else 0)
    wrap = post_wrap(turns, 0, -28)
    pts += wrap
    wrap_end = len(pts) - 1
    # the working end comes back to the left below the standing part, then up to it at x0
    x0 = -100
    pts += [P(-45, -32, 20, 0, 0, 0.5), P(x0 + 20, -34, 20, 0, 0, 0.5), P(x0 + 4, -24, 20, 0, -0.0, 0.5), P(x0 + 1, -14, 20, 0, -0.4, 0.5)]
    ends = [wrap_end]
    c = clove_on_standing(x0)
    base = len(pts)
    pts += c
    ends += [base + CLOVE_ENDS[1], base + CLOVE_ENDS[3]]
    # the tail leaves above the standing part, towards the post
    pts += [P(x0, 0, 20, -0.6, 2.4, 1.9), P(x0 + 14, 0, 20, 0, 3.3, 1.2), P(x0 + 34, 0, 20, 0, 3.5, 0.6), P(x0 + 56, 0, 20, 0, 3.6, 0.5)]
    k = K("roundturn" if round_turn else "halfhitches", pts, ends, post=True)
    k.start = start
    return k


# ---------- double overhand (stopper): a loop, the end through it twice
def double_overhand():
    Rl = 22.0
    pts = [P(4, 400), P(4, 250), P(4, 150), P(4, 70), P(4, 40)]
    # the loop: from the top of the ring round anticlockwise (left, bottom, right) back to the top, over the standing part
    for i in range(1, 13):
        ph = math.pi / 2 + 2 * math.pi * i / 12 * 0.97
        pts.append(P(Rl * math.cos(ph), Rl * math.sin(ph), 0, 0, 0, 0.6 if i >= 11 else 0.0))
    loop_end = len(pts) - 1
    # down through the inside of the loop (front) to the helix
    pts += [P(-6, 8, 0, 0, 0, 1.0)]
    # two turns around the bottom strand of the ring (through the loop twice)
    n = 22
    for i in range(n + 1):
        f = i / n
        ph = math.radians(215 + 110 * f)
        psi = math.radians(90 + 720 * f)  # v0.1.3: was 90 - 720 f, which wraps against the loop and closes to a plain overhand (tools/knottopo.py)
        cx, cy = Rl * math.cos(ph), Rl * math.sin(ph)
        nx, ny = math.cos(ph), math.sin(ph)
        r = 1.1
        pts.append(P(cx, cy, 0, -math.cos(psi) * nx * r, -math.cos(psi) * ny * r, math.sin(psi) * r))
    helix_end = len(pts) - 1
    pts += [P(Rl * 1.05, -Rl * 0.75, 0, 1.4, -0.6, 1.4), P(Rl * 1.35, -Rl * 1.9, 0, 0, 0, 0.7), P(Rl * 1.3, -Rl * 3.6), P(Rl * 1.25, -Rl * 5.2)]
    k = K("doubleoverhand", pts, [loop_end, helix_end], anchors=3)
    k.start = 4
    return k


SHAPES.update({"fig8follow": fig8_follow, "overhandloop": overhand_follow, "halfhitches": lambda: hitch(False), "roundturn": lambda: hitch(True), "doubleoverhand": double_overhand})


# ---------- v0.1.3: taut-line hitch = round the stake, then a rolling hitch on the standing part (two turns inside the
# loop, towards the stake, then across them and one hitch outside: ABOK #1734 as taught in Scouting)
def rolling_on_standing(x0):
    """Like clove_on_standing, with one more full turn before the crossing (the turns run towards the post, +x)."""
    first = CLOVE[:7]  # theta 0..270, u 16 -> -18
    extra = []
    # one more turn: theta 315..630, u -18 -> -30 (pitch about 1.5 d)
    for i in range(1, 9):
        th = 270 + 45 * i
        extra.append(("C", th, -18 - 16 * i / 8, 0, 0))
    # across both turns (raised), back past the start: CLOVE's crossing shifted by a turn, starting lower
    cross = [("C", 315 + 360, -24, 1, 0), ("C", 340 + 360, 0, 1, 0), ("C", 360 + 360, 14, 1, 0), ("C", 380 + 360, 25, 1, 0), ("C", 400 + 360, 33, 1, 0), ("C", 420 + 360, 39, 1, 0)]
    rest = [(c, th + 360, u, lvl, du) for (c, th, u, lvl, du) in CLOVE[13:]]  # 445.. : the hitch outside
    pts = []
    for (_, th, u, lvl, du) in first + extra + cross + rest:
        r = 1.1 + 1.15 * lvl
        a = math.radians(th)
        pts.append(P(x0, 0, 20, (16 - u) / 8.0 - du, r * math.sin(a), 0.5 + r * math.cos(a)))
    return pts, len(first) + len(extra) - 1, len(first) + len(extra) + len(cross) - 1


def tautline():
    standing = [P(x, 0, 20, 0, 0, 0.5) for x in (-420, -300, -200, -150)]
    pts = standing + [P(-60, 0, 20, 0, 0, 0.5), P(-24, 0, 20, 0, 0, 0.5)]
    start = len(pts) - 1
    pts += post_wrap(300, 0, -28)
    wrap_end = len(pts) - 1
    x0 = -110
    pts += [P(-45, -32, 20, 0, 0, 0.5), P(x0 + 20, -34, 20, 0, 0, 0.5), P(x0 + 4, -24, 20, 0, -0.0, 0.5), P(x0 + 1, -14, 20, 0, -0.4, 0.5)]
    c, turns_end, cross_end = rolling_on_standing(x0)
    base = len(pts)
    pts += c
    ends = [wrap_end, base + turns_end, len(pts) - 1]
    pts += [P(x0, 0, 20, -0.6, 2.4, 1.9), P(x0 + 14, 0, 20, -1.2, 3.3, 1.2), P(x0 + 34, 0, 20, -1.2, 3.5, 0.6), P(x0 + 56, 0, 20, -1.2, 3.6, 0.5)]
    ends[-1] = len(pts) - 1
    k = K("tautline", pts, ends, post=True)
    k.start = start
    return k


# ---------- v0.1.3: sheet bend = the app's bowline with its eye cut (a sheet bend is a bowline whose loop is cut: the
# bight's legs are the bowline's tail and eye leg, the collar is its small loop). Drawn mirrored front-to-back so the
# red rope comes up through the bight from behind and tucks under itself, as in the usual instructions.
BOWLINE = [(0, 400, 0), (0, 250, 0), (0, 150, 0), (0, 100, 0), (0, 82, 0), (0, 67, 1), (0, 56, 0), (0, 46, -1), (0, 40, 0), (0, 36, 0),
           (-7, 29, -1), (-16, 20, 0), (-20, 6, 0), (-15, -3, 1), (-4, -9, 0), (6, -8, 1), (16, 0, 0), (19, 14, 0), (13, 30, -1), (8, 42, 0), (0, 46, 1), (-14, 52, -1),
           (-30, 42, 0), (-40, 12, 0), (-42, -40, 0), (-32, -95, 0), (-6, -118, 0), (20, -104, 0), (32, -60, 0), (30, -30, 0), (20, -16, 0),
           (6, -8, -1), (6, 8, 0), (13, 30, 1), (21, 46, 0),
           (18, 60, 0), (10, 67, 0), (0, 67, -1), (-10, 67, 0), (-15, 60, 0),
           (-14, 52, 1), (-7, 29, 1), (-7, 14, 0), (-15, -3, -1), (-16, -30, 0), (-17, -62, 0), (-18, -92, 0)]


def sheet_bend():
    sp = 1.3  # the bowline's spread

    def bp(i, x=None, y=None):
        bx, by, lvl = BOWLINE[i]
        bx = bx if x is None else x
        by = by if y is None else y
        return P(bx * sp, 30 + (by - 30) * sp, 0, 0, 0, -0.6 * lvl)  # z mirrored

    # blue: the bight (eye's right leg from below, up through the collar, round the red standing part, down: the tail)
    blue = [P(32 * sp, 30 + (-185 - 30) * sp), P(32 * sp, 30 + (-140 - 30) * sp), bp(28), bp(29), bp(30)] + [bp(i) for i in range(31, 47)]
    # red: standing part from above, through the bight, the collar round both legs, the tail down the eye's left leg
    red = [bp(i) for i in range(0, 25)] + [bp(25, x=-34, y=-95), P(-34 * sp, 30 + (-140 - 30) * sp)]
    split = len(blue)
    pts = blue + red
    shown2 = split + 3  # the red standing part down to y = 100
    # steps (red): through the bight (to the collar's start), round behind both legs, tuck under itself and out
    ends = [split + 9, split + 17, len(pts) - 1]
    k = K("sheetbend", pts, ends, split=split, shown2=shown2)
    k.start = shown2
    return k


# ---------- v0.1.3: double fisherman's = each end ties a double overhand (strangle) round the other rope's standing part
def strangle(x1, x0, sign, n=2):
    """The end along y = sign * -0.6 d arrives at x1, wraps n turns around both ropes back towards x0, then goes back
    through the turns (inside, at z = +1.0 d) and out past x1. Cross-section centre (y 0, z 0.33 d), radius 1.7 d.
    sign = 1: the blue knot (end travelling +x); -1: the red knot (the same knot turned 180 degrees about z)."""
    pts = []
    cz, R = 0.33, 1.85
    sweep = 360 * n + 270  # round n full times and on to the front, where the end goes into the bore (360 n - 90 is only an overhand: tools/knottopo.py)
    steps = int(sweep / 45)
    for i in range(0, steps + 1):
        f = i / steps
        ph = math.radians(180 + sweep * f)  # start below the pair (y = -R), round behind, over the top, down the front
        # the turn runs from x1 back to x0
        x = x1 + (x0 - x1) * f
        y, z = R * math.cos(ph), cz + R * math.sin(ph)
        pts.append((x, y, z))
    # dive into the bore beyond x0 and come back through it at (y 0, z 1.0)
    pts.append((x0 - 1.0 * 8 * sign, 0.0, 1.9))
    pts.append((x0 - 0.2 * 8 * sign, 0.0, 1.05))
    pts.append((x1 + 0.2 * 8 * sign, 0.0, 1.0))
    pts.append((x1 + 1.8 * 8 * sign, 0.2, 1.1))
    out = []
    for (x, y, z) in pts:
        # x in mm, y / z in units of d; turned 180 degrees about z for the red knot
        out.append(P(x, 30, 0, 0, sign * y, z))
    return out


def fishermans():
    g = 0.6  # each rope 0.6 d off the axis (centres 1.2 d apart)
    # blue: from the left along y = -0.6 d, its end ties round the red standing part on the right (x 18..38)
    blue = [P(x, 30, 0, 0, -g, 0) for x in (-420, -300, -200, -120, -60, 0)]
    blue += [P(10, 30, 0, 0, -g, 0)]
    bk = strangle(44, 14, 1)
    blue += [P(40, 30, 0, 0, -g, 0)] + bk + [P(90, 30, 0, 0, 0.3, 1.1), P(130, 30, 0, 0, 0.4, 1.0)]
    blue_lay = 7  # P(14, ...): the blue end laid alongside the red rope
    # red: from the right along y = +0.6 d, through the blue knot, its end ties round the blue standing part (x -18..-38)
    red = [P(x, 30, 0, 0, g, 0) for x in (420, 300, 200, 120, 60, 0)]
    red += [P(-10, 30, 0, 0, g, 0), P(-40, 30, 0, 0, g, 0)]
    rk = strangle(-44, -14, -1)
    red += rk + [P(-90, 30, 0, 0, -0.3, 1.1), P(-130, 30, 0, 0, -0.4, 1.0)]
    split = len(blue)
    pts = blue + red
    shown2 = split + 7  # the red end laid alongside the blue standing part
    nb = len(bk)
    ends = [blue_lay + 1 + nb - 4, split - 1, split + 7 + nb - 4, len(pts) - 1]
    k = K("fishermans", pts, ends, anchors=4, split=split, shown2=shown2)
    k.start = blue_lay
    return k


SHAPES.update({"tautline": tautline, "sheetbend": sheet_bend, "fishermans": fishermans})


# ------------------------------------------------------------------------------------------------
# render


ZOOM = 0  # render only the part within ZOOM mm of (0, 30) (bends: the long ropes run off the picture)


def render(k, out, d=8.0, upto=None):
    from PIL import Image, ImageDraw

    line = line_of(k, d, upto)
    W = 520
    im = Image.new("RGB", (W * 3, W), (40, 44, 48))
    dr = ImageDraw.Draw(im)
    core = [p for p in line if p[1] < 120] or line
    if ZOOM:
        core = [p for p in line if abs(p[0]) < ZOOM and abs(p[1] - 30) < ZOOM] or core
    xs = [p[0] for p in core]
    ys = [p[1] for p in core]
    zs = [p[2] for p in core]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    span = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs), 60) * 1.15
    sc = W * 0.8 / span
    sp = k.line_split
    a = arcs(line, sp)
    total = a[-1]
    for view in range(3):
        ox = view * W
        def pr(p):
            if view == 0:
                u, v, depth = p[0], p[1], p[2]
            elif view == 1:
                u, v, depth = p[0], -p[2], p[1]  # top: looking down, z towards viewer = down on screen
            else:
                u, v, depth = -p[2], p[1], p[0]  # side from the right
            return (ox + W / 2 + (u - cx if view != 2 else u) * sc, W / 2 - ((v - cy) if view != 1 else v) * sc), depth
        segs = []
        for i in range(len(line) - 1):
            if i + 1 == sp:
                continue
            (p1, d1), (p2, d2) = pr(line[i]), pr(line[i + 1])
            segs.append(((d1 + d2) / 2, p1, p2, a[i] if sp < 0 else (-1 if i < sp else -2)))
        segs.sort(key=lambda s: s[0])
        w = max(2, int(d * sc))
        for depth, p1, p2, s in segs:
            dr.line([p1, p2], fill=(20, 20, 20), width=w + 4)
            col = (47, 111, 179) if s == -1 else (215, 50, 46) if s == -2 else (242, 107, 29) if total - s <= 150 else (237, 227, 207)
            dr.line([p1, p2], fill=col, width=w)
        dr.text((ox + 8, 8), ["front", "top", "side (from right)"][view], fill=(255, 255, 0))
    im.save(out)


def main():
    cmd, name = sys.argv[1], sys.argv[2]
    k = SHAPES[name]()
    if cmd == "render":
        global ZOOM
        ZOOM = float(sys.argv[4]) if len(sys.argv) > 4 else 0
        render(k, sys.argv[3] if len(sys.argv) > 3 else name + ".png")
    elif cmd == "check":
        for d in (4.0, 8.0, 11.0):
            ends = k.ends + [len(k.pts) - 1]
            msg = []
            for e in ends:
                ln = line_of(k, d, e)
                g, at = min_gap(ln, d, k.line_split)
                msg.append("%.2f" % (g / d))
            line = line_of(k, d)
            cr = crossings(line, k.line_split)
            print("d=%g gap/d at step ends: %s  length=%.0f  crossings=%d %s" % (d, " ".join(msg), arcs(line)[-1], len(cr), "".join("o" if c[2] else "u" for c in sorted(cr, key=lambda c: c[1]))))
    elif cmd == "kotlin":
        print(", ".join("Z(%.1f, %.1f, %.1f, %.3f, %.3f, %.3f)" % p for p in k.pts))
        print("start=%d ends=%s anchors=%d post=%s n=%d split=%d shown2=%d" % (k.start, k.ends, k.anchors, k.post, len(k.pts), k.split, k.shown2))


if __name__ == "__main__":
    main()
