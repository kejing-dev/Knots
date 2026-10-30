package io.embodit.knots.ui.knot.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import io.embodit.knots.domain.knot.Knot
import io.embodit.knots.domain.knot.KnotGeometry
import io.embodit.knots.domain.knot.RopeMesher
import io.embodit.knots.ui.theme.KnotColors

/**
 * A knot's small line drawing (design `spatialUiMap.ropeThumb`): the tightened centreline projected on the xy plane,
 * the strands drawn back to front (an outline under each so crossings read), the working end orange.
 */
@Composable
internal fun KnotThumb(knot: Knot, size: Dp = 44.dp) {
    val line = remember(knot.id) { KnotGeometry(knot, 8f).let { g -> g.frame(g.total, 1f) } }
    val outline = Color(0xFF1B1209).copy(alpha = 0.55f) // design-style: fixed-figma-color thumbOutline: rope edge in the line drawing
    Canvas(modifier = Modifier.size(size)) {
        // the part inside the knot's own box (the long standing part is cut at the top)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val first = (0 until line.size).firstOrNull { line.y(it) < 90f && kotlin.math.abs(line.x(it)) < 160f } ?: 0
        for (i in first until line.size) {
            minX = minOf(minX, line.x(i))
            maxX = maxOf(maxX, line.x(i))
            minY = minOf(minY, line.y(i))
            maxY = maxOf(maxY, line.y(i))
        }
        val span = maxOf(maxX - minX, maxY - minY).coerceAtLeast(1f)
        val k = this.size.minDimension * 0.86f / span
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        fun p(i: Int) = Offset(cx + (line.x(i) - (minX + maxX) / 2f) * k, cy - (line.y(i) - (minY + maxY) / 2f) * k)
        val w = 8f * k
        // segments sorted by depth so the strand on top is drawn last
        // (a bend: no segment across the undrawn step from the first rope to the second)
        val segs = (first until line.size - 1).filter { it + 1 != line.split }.sortedBy { line.z(it) + line.z(it + 1) }
        for (i in segs) drawLine(outline, p(i), p(i + 1), strokeWidth = w + 3f, cap = StrokeCap.Round)
        for (i in segs) {
            val color =
                if (knot.twoRopes) {
                    if (i < line.split) KnotColors.SecondBlue else KnotColors.SecondRed
                } else {
                    val tip = line.length - line.arcs[i] <= RopeMesher.TIP_MM
                    val blue = knot.twoColor && line.arcs[i] <= RopeMesher.TIP_MM
                    if (tip) KnotColors.WorkingOrange else if (blue) KnotColors.SecondBlue else KnotColors.Hemp
                }
            drawLine(color, p(i), p(i + 1), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

/** A rope cross-section picture for the thickness choice: a disc of the rope's diameter (1:1 feel, design S01). */
@Composable
internal fun RopeDot(mm: Int, size: Dp = 28.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val r = this.size.minDimension / 2f * (mm / 12f)
        drawCircle(KnotColors.Hemp, radius = r)
        drawCircle(KnotColors.Whipping, radius = r, style = Stroke(width = 1.5f))
    }
}

/** The done mark: a green ring with a check. */
@Composable
internal fun DoneMark(size: Dp = 48.dp) {
    val c = PicoTheme.colorScheme.passable
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        drawCircle(c, radius = w * 0.44f, style = Stroke(width = w * 0.07f))
        val p = Path()
        p.moveTo(w * 0.3f, w * 0.52f)
        p.lineTo(w * 0.44f, w * 0.66f)
        p.lineTo(w * 0.72f, w * 0.36f)
        drawPath(p, c, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Read-only step dots (sui-page-control semantics): the current step filled, the others faint. */
@Composable
internal fun StepDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    val on = PicoTheme.colorScheme.labelPrimary
    val off = PicoTheme.colorScheme.labelTertiary
    Canvas(modifier = modifier.size(width = (count * 16).dp, height = 14.dp)) {
        val step = 16.dp.toPx()
        val r = 3.5.dp.toPx()
        for (i in 0 until count) drawCircle(if (i == index) on else off, radius = if (i == index) r * 1.3f else r, center = Offset(step * i + step / 2f, size.height / 2f))
    }
}

/** Step ticks under the scrubber: one mark at every step boundary, the current step's span highlighted. */
@Composable
internal fun StepTicks(count: Int, index: Int, modifier: Modifier) {
    val on = PicoTheme.colorScheme.interaction
    val off = PicoTheme.colorScheme.labelTertiary
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        val w = size.width
        drawLine(on, Offset(w * index / count, y), Offset(w * (index + 1) / count, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        for (i in 0..count) drawCircle(off, radius = 2.5.dp.toPx(), center = Offset(w * i / count, y))
    }
}

/**
 * Look-to-advance (design `input.dwell`): wraps "Next". The button's bounds go to [DwellTarget]; the scene's frame
 * system counts while the gaze rests on them (after entering, once the demo has played) and fires at 1.2 s. This only
 * draws the orange ring from the progress the scene writes.
 */
@Composable
internal fun DwellRing(enabledSetting: Boolean, content: @Composable () -> Unit) {
    val progress = DwellTarget.progress.floatValue
    val track = PicoTheme.colorScheme.labelTertiary
    val fill = PicoTheme.colorScheme.interaction
    DisposableEffect(Unit) { onDispose { DwellTarget.bounds = null } }
    Box(
        modifier =
            Modifier.onGloballyPositioned { c ->
                if (c.isAttached) {
                    val root = c.findRootCoordinates()
                    val tl = root.localPositionOf(c, Offset.Zero)
                    val br = root.localPositionOf(c, Offset(c.size.width.toFloat(), c.size.height.toFloat()))
                    DwellTarget.bounds = DwellTarget.Bounds((tl.x + br.x) / 2f, (tl.y + br.y) / 2f, br.x - tl.x, br.y - tl.y, root.size.width, root.size.height)
                }
            }
    ) {
        Box(modifier = Modifier.padding(4.dp)) { content() }
        if (enabledSetting) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val r = arcSize.height / 2f
                // a capsule outline: the progress runs along it
                drawRoundRectOutline(track, inset, arcSize, r, stroke, 1f)
                if (progress > 0f) drawRoundRectOutline(fill, inset, arcSize, r, stroke, progress)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectOutline(color: Color, inset: Float, s: Size, r: Float, stroke: Float, share: Float) {
    // perimeter = 2 straight runs + 2 half circles, walked from the top centre clockwise
    val straight = (s.width - 2f * r).coerceAtLeast(0f)
    val arc = Math.PI.toFloat() * r
    val total = 2f * straight + 2f * arc
    var left = total * share.coerceIn(0f, 1f)
    val path = Path()
    val x0 = inset + s.width / 2f
    val top = inset
    val bottom = inset + s.height
    val leftX = inset + r
    val rightX = inset + s.width - r
    path.moveTo(x0, top)
    fun run(len: Float, block: (Float) -> Unit) {
        val l = minOf(len, left)
        if (l > 0f) block(l / len)
        left -= l
    }
    run(straight / 2f) { f -> path.lineTo(x0 + (rightX - x0) * f, top) }
    run(arc) { f -> path.arcTo(androidx.compose.ui.geometry.Rect(rightX - r, top, rightX + r, bottom), -90f, 180f * f, false) }
    run(straight) { f -> path.lineTo(rightX - (rightX - leftX) * f, bottom) }
    run(arc) { f -> path.arcTo(androidx.compose.ui.geometry.Rect(leftX - r, top, leftX + r, bottom), 90f, 180f * f, false) }
    run(straight / 2f) { f -> path.lineTo(leftX + (x0 - leftX) * f, top) }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}
