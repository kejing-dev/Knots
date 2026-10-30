package io.embodit.knots.domain.knot

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One step's stretch of centreline: the tip moves from [s0] to [s1] (mm of arc length); a tighten step has s0 = s1 = total. */
data class StepRange(val s0: Float, val s1: Float, val tighten: Boolean)

/**
 * A drawn centreline: xyz in mm (rope frame), flat, plus the arc length at every sample. Two-rope knots (v0.1.3): the
 * second rope starts at sample [split] (the step from the first rope's end to it has no length and is not drawn);
 * [tipArc] is where the moving end is (the first rope's end while it is being tied, else the line's end).
 */
class Centerline(val xyz: FloatArray, val arcs: FloatArray, val split: Int = -1, tip: Float = -1f) {
    val size: Int
        get() = arcs.size

    val length: Float
        get() = if (arcs.isEmpty()) 0f else arcs[arcs.size - 1]

    val tipArc: Float = if (tip >= 0f) tip else if (arcs.isEmpty()) 0f else arcs[arcs.size - 1]

    /** Number of separate ropes in the line. */
    val ropes: Int
        get() = if (split > 0) 2 else 1

    /** Rope [k] (0 or 1) as a line of its own, arc lengths from its own start. */
    fun part(k: Int): Centerline {
        if (split <= 0) return this
        val from = if (k == 0) 0 else split
        val to = if (k == 0) split else size
        val xyz = xyz.copyOfRange(from * 3, to * 3)
        val a0 = arcs[from]
        return Centerline(xyz, FloatArray(to - from) { arcs[from + it] - a0 })
    }

    fun x(i: Int) = xyz[i * 3]

    fun y(i: Int) = xyz[i * 3 + 1]

    fun z(i: Int) = xyz[i * 3 + 2]

    /** The point at arc length [s] (clamped). */
    fun at(s: Float): FloatArray {
        if (size == 0) return floatArrayOf(0f, 0f, 0f)
        if (s <= 0f) return floatArrayOf(x(0), y(0), z(0))
        for (i in 1 until size) {
            if (arcs[i] >= s) {
                val span = arcs[i] - arcs[i - 1]
                val f = if (span < 1e-6f) 0f else (s - arcs[i - 1]) / span
                return floatArrayOf(x(i - 1) + (x(i) - x(i - 1)) * f, y(i - 1) + (y(i) - y(i - 1)) * f, z(i - 1) + (z(i) - z(i - 1)) * f)
            }
        }
        return floatArrayOf(x(size - 1), y(size - 1), z(size - 1))
    }
}

/**
 * The centreline engine (design `spatialLayout.rope.drawingModel`, port of `knots.preview.js` with the build station's
 * geometry fixes): control points -> Catmull-Rom (12 per span) -> the rope grows from s = 0 to the tip's arc length.
 * A crossing's z offset (±0.6 d) only rises once the other strand has arrived there. Tighten = the same points drawn
 * together (by the knot's lambda for this diameter), the tail lengthened or cut so the rope keeps its loose length.
 *
 * Build-station additions: the knot (not the straight standing part) scales with d / 8 so gaps stay ≥ 0.9 d at every
 * diameter; the clove hitch's centreline is kept off the post (≥ R + d/2); see `KnotEngineTest`.
 */
class KnotGeometry(val knot: Knot, val dMm: Float) {
    private val scale = if (knot.post) 1f else dMm / 8f * knot.spread
    private val pts: List<Ctl> = scaled()
    private val tightCx = if (knot.post) knot.tighten.cx else knot.tighten.cx * scale
    private val tightCy = if (knot.post) knot.tighten.cy else CENTER_Y + (knot.tighten.cy - CENTER_Y) * scale
    private val lambda = knot.tighten.lambda[dMm.toInt()] ?: knot.tighten.lambda.values.minOrNull() ?: 0f

    /** Two-rope knots: first control point of the second rope (-1: one rope). */
    private val split = knot.rope2

    /** Arc length of every control point (measured without crossing offsets, as the progress scale; the second rope follows the first). */
    val ctrlArcs: FloatArray =
        run {
            val line = sampleRopes(resolve({ 0f }, 0f))
            FloatArray(pts.size) { i -> line.arcs[sampleIndex(i)] }
        }

    val total: Float = ctrlArcs.last()

    /** Length of the first rope (two-rope knots; else the whole rope). */
    private val firstLength: Float = if (split > 0) ctrlArcs[split - 1] else total

    /** The second rope is always drawn up to here (its standing part, laid before the first rope is tied). */
    private val shownArc2: Float = if (split > 0) ctrlArcs[max(split, knot.rope2Shown)] else 0f

    /** When a control point is first drawn (for crossing lifts): the second rope's laid part is there from the start. */
    private val arrival = FloatArray(pts.size) { i -> if (split > 0 && i in split..knot.rope2Shown) 0f else ctrlArcs[i] }
    private val pairs: Map<String, Int> =
        buildMap { pts.forEachIndexed { i, p -> p.crossing?.let { val j = get(it); if (j == null || arrival[i] >= arrival[j]) put(it, i) } } }

    val steps: List<StepRange> =
        run {
            var prev = ctrlArcs[knot.start]
            knot.steps.map { st ->
                if (st.type == StepType.TIGHTEN) StepRange(total, total, true)
                else {
                    // a step on the second rope starts where its laid part ends
                    if (split > 0 && st.end >= split && prev < shownArc2) prev = shownArc2
                    StepRange(prev, ctrlArcs[st.end], false).also { prev = it.s1 }
                }
            }
        }

    /** The tip's arc length in the start shape. */
    val startArc: Float = ctrlArcs[knot.start]

    /** Length of the whole loose knot (crossings raised, post clearance applied): the tightened rope keeps it. */
    val looseLength: Float by lazy { full(0f).length }

    /** Two-rope knots: each rope's loose length. */
    private val looseParts: FloatArray by lazy { full(0f).let { floatArrayOf(it.part(0).length, it.part(1).length) } }

    /** A design point of the knot (mm, e.g. a "pull here" arrow) moved with the knot's scaling for this diameter. */
    fun place(x: Float, y: Float): Pair<Float, Float> = if (knot.post) x to y else (x * scale) to (CENTER_Y + (y - CENTER_Y) * scale)

    /** The drawn rope with its tip at [sTip] and the knot pulled together by [tightT] (0..1). */
    fun frame(sTip: Float, tightT: Float = 0f): Centerline {
        if (split > 0) return frameTwo(sTip, tightT)
        if (tightT > 0f) {
            val tight = full(tightT)
            // the slack goes to an end out of the way: the off-screen standing part, else the tail
            return if (knot.anchors > 0 || knot.post) conserveAtStart(tight, looseLength) else conserve(tight, looseLength)
        }
        val line = full(0f, sTip)
        return if (sTip >= total - 0.01f) line else cut(line, sTip)
    }

    /** Two ropes: the first is tied up to [sTip] (then drawn whole), the second shows its laid part, then is tied. */
    private fun frameTwo(sTip: Float, tightT: Float): Centerline {
        if (tightT > 0f) {
            val tight = full(tightT)
            // each rope keeps its length; the slack goes to its standing end, out of the way
            return join(conserveAtStart(tight.part(0), looseParts[0]), conserveAtStart(tight.part(1), looseParts[1]))
        }
        val line = full(0f, sTip)
        val a = line.part(0)
        val b = line.part(1)
        val sa = min(sTip, firstLength)
        val sb = max(sTip, shownArc2) - firstLength
        val ca = if (sa >= a.length - 0.01f) a else cut(a, sa)
        val cb = if (sb >= b.length - 0.01f) b else cut(b, sb)
        return join(ca, cb, if (sTip <= firstLength + 0.01f) ca.length else -1f)
    }

    /** End shape of step [index] (the lay steps' path ends, or the tightened knot). */
    fun stepEnd(index: Int): Centerline {
        val r = steps[index]
        return if (r.tighten) frame(total, 1f) else frame(r.s1)
    }

    private fun full(tightT: Float, sTip: Float = total): Centerline {
        val act = HashMap<String, Float>()
        for ((label, j) in pairs) act[label] = ((sTip - arrival[j] + 25f) / 35f).coerceIn(0f, 1f)
        val resolved = resolve({ i -> pts[i].crossing?.let { act[it] } ?: 1f }, tightT)
        return clampPost(sampleRopes(resolved))
    }

    /** Sample index of control point [i] (the second rope's samples follow the first rope's). */
    private fun sampleIndex(i: Int): Int = if (split <= 0 || i < split) i * SUB else (split - 1) * SUB + 1 + (i - split) * SUB

    /** Catmull-Rom per rope (the curve does not run on from one rope into the other). */
    private fun sampleRopes(c: FloatArray): Centerline =
        if (split <= 0) sample(c) else join(sample(c.copyOfRange(0, split * 3)), sample(c.copyOfRange(split * 3, c.size)))

    private fun scaled(): List<Ctl> {
        if (knot.post) return knot.pts
        val s = scale
        val out = knot.pts.mapIndexed { i, p ->
            if (i < knot.anchors) p
            else when (p) {
                is Ctl.Flat -> p.copy(x = p.x * s, y = CENTER_Y + (p.y - CENTER_Y) * s)
                is Ctl.Cyl -> p.copy(y = CENTER_Y + (p.y - CENTER_Y) * s)
                is Ctl.Front -> p.copy(x = p.x * s, y = CENTER_Y + (p.y - CENTER_Y) * s)
                is Ctl.Free -> p.copy(x = p.x * s, y = CENTER_Y + (p.y - CENTER_Y) * s)
            }
        }.toMutableList()
        // the straight standing part keeps its spacing above the first scaled point
        val a = knot.anchors
        if (a in 1 until out.size) {
            val refY = yOf(out[a])
            val origY = yOf(knot.pts[a])
            for (i in 0 until a) {
                out[i] =
                    when (val p = knot.pts[i]) {
                        is Ctl.Flat -> p.copy(y = refY + (p.y - origY))
                        is Ctl.Free -> p.copy(y = refY + (p.y - origY))
                        else -> p
                    }
            }
        }
        return out
    }

    private fun yOf(p: Ctl): Float =
        when (p) {
            is Ctl.Flat -> p.y
            is Ctl.Cyl -> p.y
            is Ctl.Front -> p.y
            is Ctl.Free -> p.y
        }

    /** Control points -> 3D (mm). [w] = crossing activation per control point; [tightT] 0..1. */
    private fun resolve(w: (Int) -> Float, tightT: Float): FloatArray {
        val d = dMm
        val rho = KnotLibrary.POST_R + d / 2f
        val t = knot.tighten
        val out = FloatArray(pts.size * 3)
        val kx = 1f + (t.k - 1f) * lambda * tightT
        val ky = 1f + (t.ky - 1f) * lambda * tightT
        pts.forEachIndexed { i, p ->
            val wi = w(i)
            var x: Float
            var y: Float
            val z: Float
            when (p) {
                is Ctl.Cyl -> {
                    val r = if (p.rk != null) p.rk * d else rho + if (p.lvl > 0) 1.1f * d * p.lvl * wi else 0f
                    val a = p.thetaDeg * PI.toFloat() / 180f
                    x = r * sin(a)
                    z = r * cos(a)
                    y = p.y + p.dy * d
                }
                is Ctl.Front -> {
                    x = p.x
                    y = p.y + p.dy * d
                    z = rho
                }
                is Ctl.Free -> {
                    x = p.x + p.dx * d
                    y = p.y + p.dy * d
                    z = p.z + p.zd * d
                }
                is Ctl.Flat -> {
                    x = p.x
                    y = p.y
                    z = 0.6f * d * p.lvl * wi
                }
            }
            if (tightT > 0f && t.ranges.any { i in it }) {
                if (p !is Ctl.Cyl) x = tightCx + (x - tightCx) * kx
                y = tightCy + (y - tightCy) * ky
            }
            out[i * 3] = x
            out[i * 3 + 1] = y
            out[i * 3 + 2] = z
        }
        return out
    }

    /** The post's surface pushes the centreline out to R + d/2 (Catmull-Rom chords between wraps cut inside it). */
    private fun clampPost(line: Centerline): Centerline {
        if (!knot.post) return line
        val rMin = KnotLibrary.POST_R + 0.5f * dMm
        val half = KnotLibrary.POST_HEIGHT_MM / 2f
        val xyz = line.xyz.copyOf()
        var changed = false
        for (i in 0 until line.size) {
            val y = xyz[i * 3 + 1]
            if (y < -half || y > half) continue
            val x = xyz[i * 3]
            val z = xyz[i * 3 + 2]
            val r = hypot(x, z)
            if (r >= rMin || r < 1e-4f) continue
            xyz[i * 3] = x * rMin / r
            xyz[i * 3 + 2] = z * rMin / r
            changed = true
        }
        return if (changed) Centerline(xyz, arcsOf(xyz, line.split), line.split, line.tipArc) else line
    }

    companion object {
        const val SUB = 12
        private const val CENTER_Y = 30f

        /** Rope diameters of the three settings (design `spatialLayout.rope.what`). */
        val DIAMETERS = listOf(4, 8, 11)

        fun sample(c: FloatArray): Centerline {
            val n = c.size / 3
            val out = FloatArray(((n - 1) * SUB + 1) * 3)
            var o = 0
            for (i in 0 until n - 1) {
                val i0 = max(0, i - 1)
                val i3 = min(n - 1, i + 2)
                for (s in 0 until SUB) {
                    val t = s.toFloat() / SUB
                    val t2 = t * t
                    val t3 = t2 * t
                    for (k in 0 until 3) {
                        val p0 = c[i0 * 3 + k]
                        val p1 = c[i * 3 + k]
                        val p2 = c[(i + 1) * 3 + k]
                        val p3 = c[i3 * 3 + k]
                        out[o++] = 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
                    }
                }
            }
            out[o++] = c[(n - 1) * 3]
            out[o++] = c[(n - 1) * 3 + 1]
            out[o] = c[(n - 1) * 3 + 2]
            return Centerline(out, arcsOf(out))
        }

        /** Two ropes as one line: the second rope's arc lengths follow the first's ([tip] < 0: the tip is the line's end). */
        fun join(a: Centerline, b: Centerline, tip: Float = -1f): Centerline {
            val xyz = a.xyz + b.xyz
            val off = a.length
            val arcs = FloatArray(a.size + b.size) { if (it < a.size) a.arcs[it] else b.arcs[it - a.size] + off }
            return Centerline(xyz, arcs, a.size, tip)
        }

        /** Arc lengths along [xyz]; the step into sample [split] (a second rope's start) has no length. */
        fun arcsOf(xyz: FloatArray, split: Int = -1): FloatArray {
            val n = xyz.size / 3
            val a = FloatArray(n)
            for (i in 1 until n) {
                if (i == split) {
                    a[i] = a[i - 1]
                    continue
                }
                val dx = xyz[i * 3] - xyz[i * 3 - 3]
                val dy = xyz[i * 3 + 1] - xyz[i * 3 - 2]
                val dz = xyz[i * 3 + 2] - xyz[i * 3 - 1]
                a[i] = a[i - 1] + sqrt(dx * dx + dy * dy + dz * dz)
            }
            return a
        }

        /** The line up to arc length [s] (the last sample interpolated). */
        fun cut(line: Centerline, s: Float): Centerline {
            if (s >= line.length) return line
            var n = 1
            while (n < line.size && line.arcs[n] < s) n++
            if (n >= line.size) return line
            val xyz = FloatArray((n + 1) * 3)
            System.arraycopy(line.xyz, 0, xyz, 0, n * 3)
            val p = line.at(s)
            xyz[n * 3] = p[0]
            xyz[n * 3 + 1] = p[1]
            xyz[n * 3 + 2] = p[2]
            val split = if (line.split in 1..n) line.split else -1
            return Centerline(xyz, arcsOf(xyz, split), split)
        }

        /** Keeps the rope's length: a shorter tightened rope gets its tail extended along its last direction, a longer one is cut. */
        fun conserve(line: Centerline, length: Float): Centerline {
            if (line.size < 2) return line
            if (line.length > length) return cut(line, length)
            val extra = length - line.length
            if (extra < 0.01f) return line
            val n = line.size
            val dx = line.x(n - 1) - line.x(n - 2)
            val dy = line.y(n - 1) - line.y(n - 2)
            val dz = line.z(n - 1) - line.z(n - 2)
            val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
            val pieces = max(1, (extra / 4f).toInt())
            val xyz = line.xyz.copyOf((n + pieces) * 3)
            for (k in 1..pieces) {
                val f = extra * k / pieces / l
                xyz[(n - 1 + k) * 3] = line.x(n - 1) + dx * f
                xyz[(n - 1 + k) * 3 + 1] = line.y(n - 1) + dy * f
                xyz[(n - 1 + k) * 3 + 2] = line.z(n - 1) + dz * f
            }
            return Centerline(xyz, arcsOf(xyz))
        }

        /** As [conserve], at the s = 0 end: a longer rope loses its first stretch, a shorter one grows straight back from its start. */
        fun conserveAtStart(line: Centerline, length: Float): Centerline {
            if (line.size < 2) return line
            val diff = line.length - length
            if (kotlin.math.abs(diff) < 0.01f) return line
            if (diff > 0f) {
                var i = 0
                while (i < line.size - 1 && line.arcs[i + 1] <= diff) i++
                val n = line.size - i
                val xyz = FloatArray(n * 3)
                val p = line.at(diff)
                xyz[0] = p[0]
                xyz[1] = p[1]
                xyz[2] = p[2]
                System.arraycopy(line.xyz, (i + 1) * 3, xyz, 3, (n - 1) * 3)
                return Centerline(xyz, arcsOf(xyz))
            }
            val extra = -diff
            val dx = line.x(0) - line.x(1)
            val dy = line.y(0) - line.y(1)
            val dz = line.z(0) - line.z(1)
            val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
            val pieces = max(1, (extra / 4f).toInt())
            val xyz = FloatArray((line.size + pieces) * 3)
            for (k in 0 until pieces) {
                val f = extra * (pieces - k) / pieces / l
                xyz[k * 3] = line.x(0) + dx * f
                xyz[k * 3 + 1] = line.y(0) + dy * f
                xyz[k * 3 + 2] = line.z(0) + dz * f
            }
            System.arraycopy(line.xyz, 0, xyz, pieces * 3, line.size * 3)
            return Centerline(xyz, arcsOf(xyz))
        }

        /** Smallest distance between samples more than 3 d apart along the rope, and between the two ropes (design `acceptance.geometryTests`). */
        fun minGap(line: Centerline, d: Float): Float {
            var best = Float.MAX_VALUE
            val n = line.size
            for (i in 0 until n) {
                val xi = line.x(i)
                val yi = line.y(i)
                val zi = line.z(i)
                for (j in i + 1 until n) {
                    val otherRope = line.split in (i + 1)..j
                    if (!otherRope && line.arcs[j] - line.arcs[i] <= 3f * d) continue
                    val dx = xi - line.x(j)
                    val dy = yi - line.y(j)
                    val dz = zi - line.z(j)
                    val g = dx * dx + dy * dy + dz * dz
                    if (g < best) best = g
                }
            }
            return sqrt(best)
        }

        /** Nearest point of [line] to [p] (mm) between arc lengths [from] and [to]: its arc length and distance. */
        fun nearest(line: Centerline, p: FloatArray, from: Float, to: Float): Pair<Float, Float> {
            var bestS = from
            var bestD = Float.MAX_VALUE
            for (i in 0 until line.size - 1) {
                val a0 = line.arcs[i]
                val a1 = line.arcs[i + 1]
                if (a1 < from || a0 > to) continue
                val ax = line.x(i)
                val ay = line.y(i)
                val az = line.z(i)
                val bx = line.x(i + 1) - ax
                val by = line.y(i + 1) - ay
                val bz = line.z(i + 1) - az
                val ll = bx * bx + by * by + bz * bz
                val f = if (ll < 1e-9f) 0f else (((p[0] - ax) * bx + (p[1] - ay) * by + (p[2] - az) * bz) / ll).coerceIn(0f, 1f)
                val s = (a0 + (a1 - a0) * f).coerceIn(from, to)
                val dx = ax + bx * f - p[0]
                val dy = ay + by * f - p[1]
                val dz = az + bz * f - p[2]
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d < bestD) {
                    bestD = d
                    bestS = s
                }
            }
            return bestS to bestD
        }

        /** Crossings in the xy projection: (arc length of the first strand, arc length of the second, which is on top: true = the first). */
        fun crossings(line: Centerline): List<Triple<Float, Float, Boolean>> {
            val res = ArrayList<Triple<Float, Float, Boolean>>()
            val n = line.size
            for (i in 0 until n - 1) for (j in i + 3 until n - 1) {
                if (i + 1 == line.split || j + 1 == line.split) continue // the undrawn step between the ropes
                val px = line.x(i)
                val py = line.y(i)
                val qx = line.x(i + 1)
                val qy = line.y(i + 1)
                val rx = line.x(j)
                val ry = line.y(j)
                val sx = line.x(j + 1)
                val sy = line.y(j + 1)
                val den = (qx - px) * (sy - ry) - (qy - py) * (sx - rx)
                if (kotlin.math.abs(den) < 1e-9f) continue
                val t = ((rx - px) * (sy - ry) - (ry - py) * (sx - rx)) / den
                val u = ((rx - px) * (qy - py) - (ry - py) * (qx - px)) / den
                if (t < 0f || t >= 1f || u < 0f || u >= 1f) continue
                val zi = line.z(i) + (line.z(i + 1) - line.z(i)) * t
                val zj = line.z(j) + (line.z(j + 1) - line.z(j)) * u
                res += Triple(line.arcs[i], line.arcs[j], zi > zj)
            }
            return res
        }
    }
}
