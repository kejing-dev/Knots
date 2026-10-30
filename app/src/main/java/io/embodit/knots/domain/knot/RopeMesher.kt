package io.embodit.knots.domain.knot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Plain mesh arrays in metres (positions xyz, normals xyz, uv0, triangle indices); the scene turns them into an SDK MeshModel. */
class MeshData(val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray) {
    val vertexCount: Int
        get() = positions.size / 3
}

/** Growable mesh arrays. */
class MeshArrays(cap: Int = 1024) {
    private var pos = FloatArray(cap * 3)
    private var nor = FloatArray(cap * 3)
    private var uv = FloatArray(cap * 2)
    private var idx = IntArray(cap * 6)
    var vertices = 0
        private set
    private var indexCount = 0

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float = 0.5f): Int {
        if ((vertices + 1) * 3 > pos.size) {
            pos = pos.copyOf(pos.size * 2)
            nor = nor.copyOf(nor.size * 2)
            uv = uv.copyOf(uv.size * 2)
        }
        val i = vertices
        pos[i * 3] = x
        pos[i * 3 + 1] = y
        pos[i * 3 + 2] = z
        nor[i * 3] = nx
        nor[i * 3 + 1] = ny
        nor[i * 3 + 2] = nz
        uv[i * 2] = u
        uv[i * 2 + 1] = v
        vertices++
        return i
    }

    fun tri(a: Int, b: Int, c: Int) {
        if (indexCount + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[indexCount++] = a
        idx[indexCount++] = b
        idx[indexCount++] = c
    }

    val isEmpty: Boolean
        get() = indexCount == 0

    fun build(): MeshData = MeshData(pos.copyOf(vertices * 3), nor.copyOf(vertices * 3), uv.copyOf(vertices * 2), idx.copyOf(indexCount))
}

/** The rope's colours as columns of an 8 × 1 palette texture (design `spatialLayout.rope.colors`), each with a darker stripe column beside it. */
enum class RopeColor(val column: Int) {
    HEMP(0),
    ORANGE(2),
    BLUE(4),
    WHIP(6),
    RED(8);

    /** u of the plain colour / of its stripe, at the texel centre. */
    fun u(stripe: Boolean): Float = (column + (if (stripe) 1 else 0) + 0.5f) / PALETTE

    companion object {
        const val PALETTE = 10
    }
}

/**
 * The demo rope as one tube mesh (design `entities3d.rope`): 8 sides, a ring every ≥ 4 mm (fewer on long ropes so it
 * stays ≤ ~2.4k vertices), colour by arc length (hemp standing part, the last 150 mm orange, the square knot's first
 * 150 mm blue, 0.56 d dark whipping at both ends) and a three-strand spiral stripe.
 */
object RopeMesher {
    const val SIDES = 8
    private const val MAX_RINGS = 300
    const val TIP_MM = 150f

    /**
     * [line] in mm (rope frame), [dMm] the diameter; [from] skips the first part (the standing part outside the
     * work area); [mirror] negates x (left hand). [blueEnd]: the s = 0 end is blue (square knot). [solid]: the whole
     * rope in one colour (the two ropes of a bend: blue and red).
     */
    fun mesh(line: Centerline, dMm: Float, from: Float = 0f, mirror: Boolean = false, blueEnd: Boolean = false, out: MeshArrays = MeshArrays(2600), solid: RopeColor? = null): MeshArrays {
        val len = line.length
        if (len - from < 1f) return out
        val step = max(4f, (len - from) / MAX_RINGS)
        val n = max(2, ((len - from) / step).toInt() + 1)
        val r = dMm / 2000f
        val sx = if (mirror) -1f else 1f
        val pitch = 3.2f * dMm
        val whip = 0.56f * dMm
        // points and tangents along the rope
        val px = FloatArray(n)
        val py = FloatArray(n)
        val pz = FloatArray(n)
        val ss = FloatArray(n)
        for (i in 0 until n) {
            val s = min(len, from + (len - from) * i / (n - 1))
            val p = line.at(s)
            px[i] = p[0] * sx / 1000f
            py[i] = p[1] / 1000f
            pz[i] = p[2] / 1000f
            ss[i] = s
        }
        // parallel-transported frame
        var nx = 0f
        var ny = 0f
        var nz = 0f
        val base = out.vertices
        for (i in 0 until n) {
            val a = max(0, i - 1)
            val b = min(n - 1, i + 1)
            var tx = px[b] - px[a]
            var ty = py[b] - py[a]
            var tz = pz[b] - pz[a]
            val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-9f)
            tx /= tl
            ty /= tl
            tz /= tl
            if (i == 0) {
                // any perpendicular
                val hx = if (abs(ty) < 0.9f) 0f else 1f
                val hy = if (abs(ty) < 0.9f) 1f else 0f
                nx = ty * 0f - tz * hy
                ny = tz * hx - tx * 0f
                nz = tx * hy - ty * hx
            }
            val d = nx * tx + ny * ty + nz * tz
            nx -= tx * d
            ny -= ty * d
            nz -= tz * d
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            if (nl < 1e-6f) {
                nx = if (abs(tx) < 0.9f) 1f else 0f
                ny = if (abs(tx) < 0.9f) 0f else 1f
                nz = 0f
            } else {
                nx /= nl
                ny /= nl
                nz /= nl
            }
            val bx = ty * nz - tz * ny
            val by = tz * nx - tx * nz
            val bz = tx * ny - ty * nx
            val s = ss[i]
            val color =
                when {
                    s - from < whip && from < 1f -> RopeColor.WHIP
                    len - s < whip -> RopeColor.WHIP
                    solid != null -> solid
                    len - s <= TIP_MM -> RopeColor.ORANGE
                    blueEnd && s <= TIP_MM -> RopeColor.BLUE
                    else -> RopeColor.HEMP
                }
            for (k in 0 until SIDES) {
                val ang = 2f * PI.toFloat() * k / SIDES
                val c = cos(ang)
                val si = sin(ang)
                val dx = nx * c + bx * si
                val dy = ny * c + by * si
                val dz = nz * c + bz * si
                // three strands: a stripe where the twist phase is in the first sixth of each third
                val phase = k.toFloat() / SIDES * 3f + s / pitch
                val stripe = color != RopeColor.WHIP && phase - floor(phase) < 0.34f
                out.vertex(px[i] + dx * r, py[i] + dy * r, pz[i] + dz * r, dx, dy, dz, color.u(stripe))
            }
        }
        for (i in 0 until n - 1) for (k in 0 until SIDES) {
            val a = base + i * SIDES + k
            val b = base + i * SIDES + (k + 1) % SIDES
            out.tri(a, a + SIDES, b)
            out.tri(b, a + SIDES, b + SIDES)
        }
        // end caps (dark whipping)
        cap(out, base, px[0], py[0], pz[0], RopeColor.WHIP.u(false))
        cap(out, base + (n - 1) * SIDES, px[n - 1], py[n - 1], pz[n - 1], RopeColor.WHIP.u(false))
        return out
    }

    private fun cap(out: MeshArrays, ring: Int, cx: Float, cy: Float, cz: Float, u: Float) {
        val c = out.vertex(cx, cy, cz, 0f, 1f, 0f, u)
        for (k in 0 until SIDES) out.tri(c, ring + k, ring + (k + 1) % SIDES)
    }

    /** Dashes along [line] between arc lengths [s0] and [s1] (the route still to go, design `motion` / `yourTurn`). */
    fun dashes(line: Centerline, s0: Float, s1: Float, dMm: Float, mirror: Boolean, out: MeshArrays = MeshArrays(1024)): MeshArrays {
        val dash = 10f
        val gap = 7f
        var s = s0
        // keep the split: a bend's second rope starts with no gap in arc length (else the route shifts along it)
        val sub = Centerline(line.xyz, line.arcs, line.split)
        while (s < s1 - 2f) {
            val e = min(s1, s + dash)
            val seg = KnotGeometry.cut(sub, e)
            val piece = trimStart(seg, s)
            mesh(piece, dMm * 0.45f, 0f, mirror, false, out).let { }
            s = e + gap
        }
        return out
    }

    private fun trimStart(line: Centerline, s: Float): Centerline {
        var i = 0
        while (i < line.size - 1 && line.arcs[i + 1] <= s) i++
        val n = line.size - i
        if (n < 2) return line
        val xyz = FloatArray(n * 3)
        val p = line.at(s)
        xyz[0] = p[0]
        xyz[1] = p[1]
        xyz[2] = p[2]
        System.arraycopy(line.xyz, (i + 1) * 3, xyz, 3, (n - 1) * 3)
        return Centerline(xyz, KnotGeometry.arcsOf(xyz))
    }
}
