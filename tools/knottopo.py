"""Knot type check for tools/knotlab.py shapes: the knot determinant |Delta(-1)| of each rope on its own (its ends joined
far outside the knot), from the projection's crossings with the Fox colouring matrix. 1 = unknot, 3 = trefoil
(overhand), 5 = figure eight, 7 = 5_2 (double overhand / strangle).

    python -I tools/knottopo.py <knot> [<knot> ...]
"""
import math
import os
import sys
from fractions import Fraction

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "knotlab.py"), encoding="utf-8").read().replace('if __name__ == "__main__":\n    main()', "")
ns = {"__name__": "knotlab"}
exec(compile(src, "knotlab.py", "exec"), ns)


def det(m):
    m = [[Fraction(v) for v in row] for row in m]
    n = len(m)
    d = Fraction(1)
    for c in range(n):
        p = next((r for r in range(c, n) if m[r][c] != 0), None)
        if p is None:
            return 0
        if p != c:
            m[c], m[p] = m[p], m[c]
            d = -d
        d *= m[c][c]
        for r in range(c + 1, n):
            f = m[r][c] / m[c][c]
            for k in range(c, n):
                m[r][k] -= f * m[c][k]
    return abs(d)


def rotated(line, ax, ay):
    """The line seen from a slightly different direction (a generic projection has no tangent or triple crossings)."""
    ca, sa, cb, sb = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay)
    out = []
    for (x, y, z) in line:
        y, z = y * ca - z * sa, y * sa + z * ca
        x, z = x * cb + z * sb, -x * sb + z * cb
        out.append((x, y, z))
    return out


def exact_crossings(line):
    """(s of the first strand, s of the second, first on top) with s = segment index + fraction (exact order along the rope)."""
    res = []
    n = len(line)
    for i in range(n - 1):
        p, q = line[i], line[i + 1]
        for j in range(i + 2, n - 1):
            r, s_ = line[j], line[j + 1]
            den = (q[0] - p[0]) * (s_[1] - r[1]) - (q[1] - p[1]) * (s_[0] - r[0])
            if abs(den) < 1e-12:
                continue
            t = ((r[0] - p[0]) * (s_[1] - r[1]) - (r[1] - p[1]) * (s_[0] - r[0])) / den
            u = ((r[0] - p[0]) * (q[1] - p[1]) - (r[1] - p[1]) * (q[0] - p[0])) / den
            if 0 <= t < 1 and 0 <= u < 1 and not (j == i + 1):
                zi = p[2] + (q[2] - p[2]) * t
                zj = r[2] + (s_[2] - r[2]) * u
                res.append((i + t, j + u, zi > zj, 0, 0))
    return res


def determinant(line):
    # join the ends by a chord high above everything (isotopic to joining them round the outside: the ends are long)
    a, b = line[0], line[-1]
    line = line + [(b[0], b[1], 1e5), (a[0], a[1], 1e5), (a[0] + 1e-3, a[1] + 1e-3, a[2])]
    cr = exact_crossings(line)
    if not cr:
        return 1, 0
    unders = sorted(s_j if over_i else s_i for (s_i, s_j, over_i, _, _) in cr)

    def arc_of(s):
        # arc k runs from unders[k-1] to unders[k] (arc 0 wraps round through the joined ends)
        for k, u in enumerate(unders):
            if s < u:
                return k
        return 0

    n = len(unders)
    rows = []
    for (s_i, s_j, over_i, _, _) in cr:
        s_over, s_under = (s_i, s_j) if over_i else (s_j, s_i)
        k = unders.index(s_under)
        row = [0] * n
        row[arc_of(s_over)] += 2
        row[k] -= 1  # the arc arriving at this under-crossing
        row[(k + 1) % n] -= 1  # the arc leaving it
        rows.append(row)
    minor = [r[1:] for r in rows[1:]]
    return det(minor), n


def main():
    for name in sys.argv[1:]:
        k = ns["SHAPES"][name]()
        line = ns["line_of"](k, 8.0)
        sp = k.line_split
        parts = [line] if sp < 0 else [line[:sp], line[sp:]]
        res = []
        for p in parts:
            dets = []
            # three slightly turned views (straight-on views have tangent crossings); all must agree
            for (ax, ay) in ((0.21, 0.13), (-0.17, 0.29), (0.33, -0.23)):
                d, n = determinant(rotated(p, ax, ay))
                dets.append("%s/%d" % (d, n))
            res.append("det/crossings by view: " + " ".join(dets))
        print(name, " | ".join(res))


if __name__ == "__main__":
    main()
