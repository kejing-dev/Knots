package io.embodit.knots.domain.knot

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design `acceptance.geometryTests`, for every knot at every rope diameter (4 / 8 / 11 mm). */
class KnotEngineTest {

    private fun each(block: (Knot, Int, KnotGeometry) -> Unit) {
        for (k in KnotLibrary.knots) for (d in KnotGeometry.DIAMETERS) block(k, d, KnotGeometry(k, d.toFloat()))
    }

    @Test
    fun looseAndTightKeepTheirLength() =
        each { k, d, g ->
            val loose = g.looseLength
            for (t in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                val l = g.frame(g.total, t).length
                assertTrue("${k.id} d$d t$t: tight $l vs loose $loose", abs(l - loose) <= loose * 0.01f)
            }
        }

    @Test
    fun everyStepEndKeepsStrandsApart() =
        each { k, d, g ->
            for (i in g.steps.indices) {
                if (g.steps[i].tighten) continue
                val gap = KnotGeometry.minGap(g.stepEnd(i), d.toFloat())
                assertTrue("${k.id} d$d step ${i + 1}: gap ${"%.2f".format(gap)} < 0.9 d", gap >= 0.9f * d)
            }
        }

    @Test
    fun tighteningKeepsStrandsApart() =
        each { k, d, g ->
            for (t in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                val gap = KnotGeometry.minGap(g.frame(g.total, t), d.toFloat())
                assertTrue("${k.id} d$d tight $t: gap ${"%.2f".format(gap)} < 0.9 d", gap >= 0.9f * d)
            }
        }

    @Test
    fun cloveStaysOffThePost() {
        val k = KnotLibrary.knot("clove")!!
        for (d in KnotGeometry.DIAMETERS) {
            val g = KnotGeometry(k, d.toFloat())
            val frames = g.steps.indices.map { g.stepEnd(it) } + listOf(0.5f, 1f).map { g.frame(g.total, it) }
            for (line in frames) for (i in 0 until line.size) {
                if (abs(line.y(i)) > KnotLibrary.POST_HEIGHT_MM / 2f) continue
                val surface = hypot(line.x(i), line.z(i)) - KnotLibrary.POST_R
                assertTrue("clove d$d: centreline ${"%.2f".format(surface)} mm from the post", surface >= 0.45f * d - 1e-3f)
            }
        }
    }

    /**
     * Square knot: both ends leave on the same side (up), the two half knots turn the orange end around the blue end in
     * opposite directions (same direction = granny), and the projected crossings follow the designed square-knot order
     * (orange over, under, over; then again over, under, over).
     */
    @Test
    fun squareKnotIsNotAGranny() {
        val k = KnotLibrary.knot("reef")!!
        for (d in KnotGeometry.DIAMETERS) {
            val g = KnotGeometry(k, d.toFloat())
            val line = g.stepEnd(g.steps.size - 2)
            val knotY = (14..40).map { line.at(g.ctrlArcs[it])[1] }.average().toFloat()
            assertTrue("reef d$d: blue end above the knot", line.y(0) > knotY)
            assertTrue("reef d$d: orange end above the knot", line.y(line.size - 1) > knotY)
            fun sweep(from: Int, to: Int): Float {
                var total = 0f
                var prev: Float? = null
                for (i in 0 until line.size) {
                    if (line.arcs[i] < g.ctrlArcs[from] || line.arcs[i] > g.ctrlArcs[to]) continue
                    val a = kotlin.math.atan2(line.x(i), line.z(i))
                    prev?.let { p ->
                        var da = a - p
                        while (da > Math.PI) da -= (2 * Math.PI).toFloat()
                        while (da < -Math.PI) da += (2 * Math.PI).toFloat()
                        total += da
                    }
                    prev = a
                }
                return total
            }
            val first = sweep(14, 26)
            val second = sweep(28, 40)
            assertTrue("reef d$d: half knots turn opposite ways ($first, $second)", first * second < 0f)
            assertTrue("reef d$d: each half knot wraps at least 1.25 turns ($first, $second)", abs(first) > 2.5f * Math.PI.toFloat() && abs(second) > 2.5f * Math.PI.toFloat())
            val blueEnd = g.ctrlArcs[7]
            val orangeStart = g.ctrlArcs[13]
            val seq = KnotGeometry.crossings(line).filter { it.first < blueEnd && it.second > orangeStart }.sortedBy { it.second }.map { !it.third }
            assertEquals("reef d$d: orange over (true) / under along its path", listOf(true, false, true, true, false, true), seq)
        }
    }

    @Test
    fun stepsRunForwardFromTheStartShape() =
        each { k, d, g ->
            var prev = g.startArc
            for (r in g.steps) {
                if (r.tighten) {
                    assertEquals(g.total, r.s0, 0.01f)
                    continue
                }
                // a bend's second rope starts from its laid part, not from where the first rope ended
                val from = if (k.twoRopes && r.s0 > prev) r.s0 else prev
                assertTrue("${k.id} d$d: step from $prev", r.s0 == from && r.s1 > r.s0)
                prev = r.s1
            }
            assertEquals("${k.id}: last lay step ends at the rope's end", g.total, prev, 0.01f)
        }

    @Test
    fun ropeIsAboutTheDesignedLength() =
        each { k, d, g -> assertTrue("${k.id} d$d length ${g.total}", g.total in 300f..(if (k.id.endsWith("follow") || k.id.endsWith("loop") || k.twoRopes) 3800f else 2000f)) }

    /** v0.1.3 bends: two ropes, the second's standing part laid from the start, each rope keeps its own length and the ropes keep 0.9 d apart (minGap compares them). */
    @Test
    fun bendsDrawTwoRopes() {
        val bends = KnotLibrary.knots.filter { it.twoRopes }
        assertEquals(listOf("sheetbend", "fishermans"), bends.map { it.id }.sorted().reversed())
        for (k in bends) for (d in KnotGeometry.DIAMETERS) {
            val g = KnotGeometry(k, d.toFloat())
            val start = g.frame(g.startArc)
            assertEquals("${k.id} d$d: two ropes at the start", 2, start.ropes)
            val laid = start.part(1).length
            assertTrue("${k.id} d$d: second rope laid from the start ($laid mm)", laid > 100f)
            val loose = g.frame(g.total)
            val tight = g.frame(g.total, 1f)
            for (r in 0..1) {
                assertEquals("${k.id} d$d rope $r keeps its length", loose.part(r).length, tight.part(r).length, loose.part(r).length * 0.01f)
            }
            for (i in g.steps.indices) {
                val line = g.stepEnd(i)
                assertEquals("${k.id} d$d step ${i + 1}: two ropes", 2, line.ropes)
                val r = g.steps[i]
                if (r.tighten) continue
                // the moving end is at the step's end, on the rope being tied
                val tip = line.at(line.tipArc)
                val want = g.frame(g.total).at(r.s1)
                val off = hypot(hypot(tip[0] - want[0], tip[1] - want[1]), tip[2] - want[2])
                assertTrue("${k.id} d$d step ${i + 1}: tip ${"%.1f".format(off)} mm off", off < 1f)
            }
            // the gap between the ropes alone (not only along each rope)
            val a = tight.part(0)
            val b = tight.part(1)
            var best = Float.MAX_VALUE
            for (i in 0 until a.size step 2) for (j in 0 until b.size step 2) best = minOf(best, hypot(hypot(a.x(i) - b.x(j), a.y(i) - b.y(j)), a.z(i) - b.z(j)))
            assertTrue("${k.id} d$d: ropes ${"%.2f".format(best / d)} d apart", best >= 0.9f * d)
        }
    }

    /**
     * The knot type of each rope (its ends joined above everything), from a slightly turned projection: the knot
     * determinant |Δ(−1)| (Fox colouring matrix). Overhand 3, figure eight 5, double overhand (5_1) 5: v0.1.2's double
     * overhand wrapped against its loop and was only an overhand (3); the double fisherman's is a double overhand in each rope.
     */
    @Test
    fun knotTypes() {
        val want = mapOf("overhand" to listOf(3), "figure8" to listOf(5), "doubleoverhand" to listOf(5), "fishermans" to listOf(5, 5))
        for ((id, dets) in want) {
            val g = KnotGeometry(KnotLibrary.knot(id)!!, 8f)
            val line = g.frame(g.total)
            val got = (0 until line.ropes).map { determinant(line.part(it)) }
            assertEquals("$id: knot determinant per rope", dets, got)
        }
    }

    private fun determinant(line: Centerline): Int {
        // turn the view a little (0.21 rad about x, 0.13 about y) and join the ends by a chord high above everything
        val ca = kotlin.math.cos(0.21)
        val sa = kotlin.math.sin(0.21)
        val cb = kotlin.math.cos(0.13)
        val sb = kotlin.math.sin(0.13)
        val pts = ArrayList<DoubleArray>()
        for (i in 0 until line.size) {
            val x = line.x(i).toDouble()
            val y0 = line.y(i).toDouble()
            val z0 = line.z(i).toDouble()
            val y = y0 * ca - z0 * sa
            val z1 = y0 * sa + z0 * ca
            pts += doubleArrayOf(x * cb + z1 * sb, y, -x * sb + z1 * cb)
        }
        val a = pts.first()
        val b = pts.last()
        pts += doubleArrayOf(b[0], b[1], 1e5)
        pts += doubleArrayOf(a[0], a[1], 1e5)
        pts += doubleArrayOf(a[0] + 1e-3, a[1] + 1e-3, a[2])
        // crossings: (position along the rope of the over strand, of the under strand)
        val cr = ArrayList<Pair<Double, Double>>()
        for (i in 0 until pts.size - 1) for (j in i + 2 until pts.size - 1) {
            val p = pts[i]
            val q = pts[i + 1]
            val r = pts[j]
            val s = pts[j + 1]
            val den = (q[0] - p[0]) * (s[1] - r[1]) - (q[1] - p[1]) * (s[0] - r[0])
            if (abs(den) < 1e-12) continue
            val t = ((r[0] - p[0]) * (s[1] - r[1]) - (r[1] - p[1]) * (s[0] - r[0])) / den
            val u = ((r[0] - p[0]) * (q[1] - p[1]) - (r[1] - p[1]) * (q[0] - p[0])) / den
            if (t < 0 || t >= 1 || u < 0 || u >= 1) continue
            val zi = p[2] + (q[2] - p[2]) * t
            val zj = r[2] + (s[2] - r[2]) * u
            cr += if (zi > zj) (i + t) to (j + u) else (j + u) to (i + t)
        }
        if (cr.isEmpty()) return 1
        val unders = cr.map { it.second }.sorted()
        val n = unders.size
        fun arcOf(s: Double): Int = unders.indexOfFirst { s < it }.let { if (it < 0) 0 else it }
        val m = Array(n) { DoubleArray(n) }
        cr.forEachIndexed { c, (over, under) ->
            val k = unders.indexOf(under)
            m[c][arcOf(over)] += 2.0
            m[c][k] -= 1.0
            m[c][(k + 1) % n] -= 1.0
        }
        // any (n-1) minor
        val a2 = Array(n - 1) { r -> DoubleArray(n - 1) { c -> m[r + 1][c + 1] } }
        var det = 1.0
        for (c in 0 until n - 1) {
            val piv = (c until n - 1).maxByOrNull { abs(a2[it][c]) }!!
            if (abs(a2[piv][c]) < 1e-9) return 0
            if (piv != c) {
                val tmp = a2[piv]
                a2[piv] = a2[c]
                a2[c] = tmp
                det = -det
            }
            det *= a2[c][c]
            for (r in c + 1 until n - 1) {
                val f = a2[r][c] / a2[c][c]
                for (k in c until n - 1) a2[r][k] -= f * a2[c][k]
            }
        }
        return kotlin.math.round(abs(det)).toInt()
    }

    @Test
    fun nearestFindsAPointOnThePath() {
        val g = KnotGeometry(KnotLibrary.knot("figure8")!!, 8f)
        val r = g.steps[0]
        val line = g.frame(r.s1)
        val mid = (r.s0 + r.s1) / 2f
        val p = line.at(mid)
        val (s, dist) = KnotGeometry.nearest(line, p, r.s0, r.s1)
        assertTrue("dist $dist", dist < 0.5f)
        assertEquals(mid, s, 2f)
    }

    @Test
    fun growingRopeEndsAtTheTip() {
        val g = KnotGeometry(KnotLibrary.knot("bowline")!!, 8f)
        val r = g.steps[1]
        val s = (r.s0 + r.s1) / 2f
        assertEquals(s, g.frame(s).length, 1.5f)
    }
}
