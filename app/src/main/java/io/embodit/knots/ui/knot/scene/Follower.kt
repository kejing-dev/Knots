package io.embodit.knots.ui.knot.scene

import io.embodit.knots.domain.model.PlaneSurface
import io.embodit.knots.domain.model.Quat4
import io.embodit.knots.domain.model.Vec3
import io.embodit.knots.domain.usecase.FacePanelUseCase
import io.embodit.knots.platform.KnotLog
import io.embodit.knots.platform.tracking.HeadPose
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos

/** A pose in Stage space: where a followed thing sits and its level turn (yaw about the vertical). */
data class FollowPose(val position: Vec3, val yaw: Float) {
    val rotation: Quat4
        get() = Quat4.axisAngle(Vec3.UP, yaw)
}

/**
 * Lazy follow (Spatial Ruler rule T11, taken over for the main panel and the extinguisher prop): the
 * thing rests where it settled; once its centre has been more than 28 degrees off the view direction,
 * or nearer than [nearM] or farther than [farM], for 0.5 s without a break, it slides to the designed
 * pose of that moment in 0.35 s (ease-in-out, time based). It holds still while [hold]. The designed
 * pose is [headOffset] in the head frame (x right, y up, -z ahead), level, turned to the head's heading.
 * The surface rule keeps it out of real surfaces ([halfExtents] along its own axes).
 */
internal class Follower(
    private val name: String,
    private val headOffset: Vec3,
    private val halfExtents: List<Float>,
    private val lean: Quat4 = Quat4.IDENTITY,
    private val nearM: Float = 0.35f,
    private val farM: Float = 0.9f,
    /** Degrees off the view direction before it slides back (design: library panel 35°). */
    private val windowDeg: Float = 28f,
) {
    private var started = false
    private var restYaw = 0f
    private var anchorPos = Vec3.ZERO
    private var applied = Vec3.ZERO
    private var outSinceMs = NEVER
    private var slideStartMs = NEVER
    private var slideFromPos = Vec3.ZERO
    private var slideFromYaw = 0f
    private var slideFromApplied = Vec3.ZERO
    private var slideToPos = Vec3.ZERO
    private var slideToYaw = 0f
    private var slideToApplied = Vec3.ZERO
    private var lastPlaneCount = -1

    /** The current pose (centre and level turn). */
    var pose: FollowPose = FollowPose(Vec3.ZERO, 0f)
        private set

    /** Turned to the head's heading and tilted to face the head from its elevation (plus the designed lean). */
    val rotation: Quat4
        get() = pose.rotation * Quat4.axisAngle(Vec3.RIGHT, pitch * FACE_SHARE) * lean

    val sliding: Boolean
        get() = slideStartMs != NEVER

    fun reset() {
        started = false
        outSinceMs = NEVER
        slideStartMs = NEVER
    }

    /** Horizontal angle of the designed place to the right of the view direction (radians; negative = left). */
    var sideRad = 0f

    /** Elevation of the panel below the horizontal, radians (negative = below); set by [designedFor]. */
    private var pitch = 0f

    /**
     * The designed place: [headOffset]'s distance and its own elevation added to the gaze elevation,
     * clamped to 0 - 25 degrees below the horizontal (design `panels[0]`: the panel follows the head's
     * turn only, it does not dive onto the desk when the user looks down at the sheet).
     */
    private fun designedFor(head: HeadPose): Pair<Vec3, Float> {
        val yaw = FacePanelUseCase.yawOf(head.forward, restYaw) - sideRad
        val gaze = kotlin.math.asin(head.forward.y.coerceIn(-1f, 1f))
        val own = kotlin.math.atan2(headOffset.y, -headOffset.z)
        pitch = (gaze + own).coerceIn(MIN_PITCH_RAD, MAX_PITCH_RAD)
        val range = headOffset.length
        val dir = Quat4.axisAngle(Vec3.UP, yaw).rotate(Vec3(0f, kotlin.math.sin(pitch), -kotlin.math.cos(pitch)))
        return (head.position + dir * range) to yaw
    }

    /** No room geometry in this app: nothing pushes the panel out of real surfaces. */
    @Suppress("UNUSED_PARAMETER")
    private fun correction(pos: Vec3, yaw: Float, headPos: Vec3, planes: Collection<PlaneSurface>): Vec3 = Vec3.ZERO

    /** Once per frame. Returns true when the pose changed this frame. */
    fun follow(head: HeadPose, planes: Collection<PlaneSurface>, planeCount: Int, nowMs: Long, hold: Boolean): Boolean {
        if (!started) {
            val (pos, yaw) = designedFor(head)
            started = true
            restYaw = yaw
            anchorPos = pos
            applied = correction(pos, yaw, head.position, planes)
            lastPlaneCount = planeCount
            write()
            KnotLog.i("FOLLOW $name park reason=start pos=${KnotLog.pos(pose.position)} yaw=${String.format(Locale.US, "%.1f", Math.toDegrees(yaw.toDouble()))}")
            return true
        }
        if (sliding) {
            slide(nowMs)
            return true
        }
        val to = pose.position - head.position
        val dist = to.length
        val away = if (dist < 1e-3f) 0f else Math.toDegrees(acos((to / dist).dot(head.forward).coerceIn(-1f, 1f)).toDouble()).toFloat()
        val reason =
            when {
                away > windowDeg -> "angle"
                dist < nearM -> "near"
                dist > farM -> "far"
                else -> null
            }
        if (reason == null || hold) {
            outSinceMs = NEVER
        } else if (outSinceMs == NEVER) {
            outSinceMs = nowMs
        } else if (nowMs - outSinceMs >= OUT_MS) {
            startSlide(head, planes, nowMs, reason)
            return true
        }
        if (planeCount != lastPlaneCount) {
            lastPlaneCount = planeCount
            val wanted = correction(anchorPos, restYaw, head.position, planes)
            if ((wanted - applied).length > DEAD_BAND_M) {
                applied = wanted
                write()
                return true
            }
        }
        return false
    }

    /** Slides to the designed pose now (debug `park`, and after a hidden panel comes back). */
    fun park(head: HeadPose, planes: Collection<PlaneSurface>, nowMs: Long) {
        if (!started) return
        startSlide(head, planes, nowMs, "park")
    }

    private fun startSlide(head: HeadPose, planes: Collection<PlaneSurface>, nowMs: Long, reason: String) {
        val (pos, yaw) = designedFor(head)
        slideFromPos = anchorPos
        slideFromYaw = restYaw
        slideFromApplied = applied
        slideToPos = pos
        slideToYaw = yaw
        slideToApplied = correction(pos, yaw, head.position, planes)
        slideStartMs = nowMs
        outSinceMs = NEVER
        KnotLog.i("FOLLOW $name slide reason=$reason to=${KnotLog.pos(pos + slideToApplied)}")
        slide(nowMs)
    }

    private fun slide(nowMs: Long) {
        val q = ((nowMs - slideStartMs) / SLIDE_MS).coerceIn(0f, 1f)
        val t = Ease.inOutCubic(q)
        var dYaw = slideToYaw - slideFromYaw
        while (dYaw > PI.toFloat()) dYaw -= 2f * PI.toFloat()
        while (dYaw < -PI.toFloat()) dYaw += 2f * PI.toFloat()
        anchorPos = slideFromPos.lerp(slideToPos, t)
        restYaw = slideFromYaw + dYaw * t
        applied = slideFromApplied.lerp(slideToApplied, t)
        write()
        if (q >= 1f) slideStartMs = NEVER
    }

    private fun write() {
        pose = FollowPose(anchorPos + applied, restYaw)
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE
        private const val OUT_MS = 500L
        private const val SLIDE_MS = 400f
        private const val DEAD_BAND_M = 0.01f
        private const val MIN_PITCH_RAD = -0.436332f
        private const val MAX_PITCH_RAD = 0f
        private const val FACE_SHARE = 0.8f

    }
}
