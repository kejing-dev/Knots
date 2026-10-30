package io.embodit.knots.domain.knot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small static meshes of the scene (metres): post, handle bar, "pull here" arrows, the tip ring, the shadow disc. */
object Shapes {
    /** A closed cylinder from (ax, ay, az) to (bx, by, bz) of radius [r]. */
    fun cylinder(out: MeshArrays, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r: Float, sides: Int = 24, r2: Float = r) {
        var tx = bx - ax
        var ty = by - ay
        var tz = bz - az
        val l = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-9f)
        tx /= l
        ty /= l
        tz /= l
        // n = any perpendicular, b = t x n
        val hx = if (abs(ty) < 0.9f) 0f else 1f
        val hy = if (abs(ty) < 0.9f) 1f else 0f
        var nx = -tz * hy
        var ny = tz * hx
        var nz = tx * hy - ty * hx
        val nl = sqrt(nx * nx + ny * ny + nz * nz)
        nx /= nl
        ny /= nl
        nz /= nl
        val bxv = ty * nz - tz * ny
        val byv = tz * nx - tx * nz
        val bzv = tx * ny - ty * nx
        val base = out.vertices
        for (k in 0 until sides) {
            val a = 2f * PI.toFloat() * k / sides
            val dx = nx * cos(a) + bxv * sin(a)
            val dy = ny * cos(a) + byv * sin(a)
            val dz = nz * cos(a) + bzv * sin(a)
            out.vertex(ax + dx * r, ay + dy * r, az + dz * r, dx, dy, dz, 0.5f)
            out.vertex(bx + dx * r2, by + dy * r2, bz + dz * r2, dx, dy, dz, 0.5f)
        }
        for (k in 0 until sides) {
            val a = base + k * 2
            val b = base + ((k + 1) % sides) * 2
            out.tri(a, a + 1, b)
            out.tri(b, a + 1, b + 1)
        }
        val c0 = out.vertex(ax, ay, az, -tx, -ty, -tz, 0.5f)
        val c1 = out.vertex(bx, by, bz, tx, ty, tz, 0.5f)
        for (k in 0 until sides) {
            val a = base + k * 2
            val b = base + ((k + 1) % sides) * 2
            out.tri(c0, b, a)
            if (r2 > 1e-6f) out.tri(c1, a + 1, b + 1)
        }
    }

    /** An arrow from (x, y, z) along the unit (dx, dy) in the xy plane: shaft [len], cone head. */
    fun arrow(out: MeshArrays, x: Float, y: Float, z: Float, dx: Float, dy: Float, len: Float = 0.04f) {
        val sx = x + dx * len
        val sy = y + dy * len
        cylinder(out, x, y, z, sx, sy, z, 0.0025f, 12)
        cylinder(out, sx, sy, z, sx + dx * 0.016f, sy + dy * 0.016f, z, 0.008f, 16, r2 = 0f)
    }

    /** A flat ring facing +z (towards the user) at the origin. */
    fun ring(out: MeshArrays, inner: Float, outer: Float, segments: Int = 32) {
        for (i in 0 until segments) {
            val a0 = 2f * PI.toFloat() * i / segments
            val a1 = 2f * PI.toFloat() * (i + 1) / segments
            val p0 = out.vertex(cos(a0) * inner, sin(a0) * inner, 0f, 0f, 0f, 1f, 0.5f)
            val p1 = out.vertex(cos(a1) * inner, sin(a1) * inner, 0f, 0f, 0f, 1f, 0.5f)
            val p2 = out.vertex(cos(a1) * outer, sin(a1) * outer, 0f, 0f, 0f, 1f, 0.5f)
            val p3 = out.vertex(cos(a0) * outer, sin(a0) * outer, 0f, 0f, 0f, 1f, 0.5f)
            out.tri(p0, p2, p1)
            out.tri(p0, p3, p2)
        }
    }

    /** A flat disc in the xz plane facing up. */
    fun disc(out: MeshArrays, r: Float, segments: Int = 32) {
        val c = out.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0.5f)
        val first = out.vertices
        for (i in 0 until segments) {
            val a = 2f * PI.toFloat() * i / segments
            out.vertex(cos(a) * r, 0f, sin(a) * r, 0f, 1f, 0f, 0.5f)
        }
        for (i in 0 until segments) out.tri(c, first + (i + 1) % segments, first + i)
    }
}
