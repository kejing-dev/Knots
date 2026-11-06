package io.embodit.knots.ui.knot.scene

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.gesture.SpatialDragValue
import com.pico.spatial.ui.foundation.gesture.SpatialTapValue
import com.pico.spatial.ui.geometry.Offset3D
import io.embodit.knots.domain.knot.Centerline
import io.embodit.knots.domain.knot.Knot
import io.embodit.knots.domain.knot.KnotGeometry
import io.embodit.knots.domain.knot.MeshArrays
import io.embodit.knots.domain.knot.RopeColor
import io.embodit.knots.domain.knot.RopeMesher
import io.embodit.knots.domain.knot.Shapes
import io.embodit.knots.domain.model.Hand
import io.embodit.knots.domain.model.Quat4
import io.embodit.knots.domain.model.Vec3
import io.embodit.knots.domain.usecase.FacePanelUseCase
import io.embodit.knots.platform.KnotGraph
import io.embodit.knots.platform.KnotLog
import io.embodit.knots.platform.tracking.HeadPose
import io.embodit.knots.ui.knot.KnotEvent
import io.embodit.knots.ui.knot.KnotUiState
import io.embodit.knots.ui.knot.Mode
import io.embodit.knots.ui.knot.Phase
import io.embodit.knots.ui.knot.RopePose
import io.embodit.knots.ui.knot.components.DemoClock
import io.embodit.knots.ui.knot.components.DwellTarget
import io.embodit.knots.ui.knot.debug.KnotProbe
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject

/** The framework instantiates the system itself, so the running scene is reached through this holder. */
internal object ActiveScene {
    @Volatile var scene: KnotScene? = null
}

/** The one per-frame ECS system of the app. */
class KnotFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.scene?.onFrame(context)
    }
}

/**
 * The 3D side of the app: the demo rope (one dynamic tube mesh), its route dashes, the "pull here" arrows, the clove
 * hitch's post, the shadow, the "your turn" tip ring, and the placement of the pre-built panels. Compose
 * state comes in through [render] (idempotent); the demo motion, turning, dragging, look-to-advance and the
 * out-of-sight check happen in [onFrame] only and never go through Compose (high-rate data stays on the ECS side).
 * Everything started in [attach] is stopped in [detach]. Main thread only.
 */
class KnotScene(private val sink: (KnotEvent) -> Unit) : KnotProbe.SceneProbe {

    /** Panel id -> the panel is in view: a pooled panel composes its content only then. */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /** Bumped to make `SpatialView.update` run again while panel entities are still missing. */
    val bindTick = mutableIntStateOf(0)

    /** View pixels -> metres (set by the Stage composable from the physical length converter). */
    @Volatile var pxToM: (Float) -> Float = { it * 0.0008f }

    private val main = Handler(Looper.getMainLooper())
    private val headTracker = KnotGraph.head
    private val eyeTracker = KnotGraph.eyes
    private val controller = KnotGraph.controller
    private val facingRule = FacePanelUseCase()

    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var contentProbe: Entity? = null
    private var materials: SceneMaterials? = null
    private var shared: SharedResources? = null
    private var rig: PanelRig? = null
    private var attached = false

    // entities
    private var ropeRoot: Entity? = null
    private var turnNode: Entity? = null
    private var ropeEntity: Entity? = null
    private var ropeMesh: MeshResource? = null
    private var dashEntity: Entity? = null
    private var dashMesh: MeshResource? = null
    private var arrowEntity: Entity? = null
    private var arrowMesh: MeshResource? = null
    private var postEntity: Entity? = null
    private var shadowEntity: Entity? = null
    private var workTap: Entity? = null
    private var tipRing: Entity? = null

    private val follower = Follower("guide", LIBRARY_OFFSET, listOf(0.165f, 0.13f, 0.01f), nearM = 0.5f, farM = 1.4f, windowDeg = 35f)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var buildJob: Job? = null

    private var ui = KnotUiState()
    private var head = HeadPose(Vec3.ZERO, Quat4.IDENTITY)
    private var hasHead = false
    private var panelPressed = false
    private var lastPanelPressMs = NEVER
    private var controllerActive = false
    private var controllerChangedMs = NEVER
    private var lastFrameMs = 0L
    private var fpsOn = false
    private var frames = 0
    private var fpsStartMs = 0L

    // geometry
    private val geometries = HashMap<String, KnotGeometry>()
    private val clipCache = HashMap<String, Float>()
    private var builtStaticKey = ""
    private var lastMeshKey = ""
    private var lastMeshMs = 0L
    private var lastLine: Centerline? = null
    private var lastTipArc = 0f
    private var meshCount = 0
    private var lastModelMs = 0L

    // timeline
    private var timelineSerial = -1
    private var timelineStartMs = 0L
    private var playedSent = -1
    private var finishedSent = -1

    // turning (S08)
    private var spin = 0f
    private var spinVel = 0f
    private var spinAnimFrom = 0f
    private var spinAnimTo = 0f
    private var spinAnimStartMs = NEVER
    private var spinAnimMs = 0f
    private var lastStickMs = NEVER
    private var turnedSent = false
    private var doneSpin = 0f
    private var doneSpinStopped = false
    private var doneSerial = -1

    // dragging
    private enum class Drag { NONE, TURN, TIP, PULL }

    private var drag = Drag.NONE
    private var dragAcc = 0f
    private var dragTurnStarted = false
    private var dragSpinStart = 0f
    /** v0.1.4: the rope's pose in Stage coordinates, read each frame from its system volume window (null: no volume). */
    private var volumePose: RopePose? = null
    private var volumeContent: SpatialViewContent? = null
    private var volumeRoot: Entity? = null
    private var volumeProbe: Entity? = null
    private var sentPose: RopePose? = null
    private var firstPose: RopePose? = null
    private val seenScenes = HashMap<String, Int>()
    private var seenLogMs = 0L
    private var poseMovedSinceMs = NEVER
    private var dragInVolume = false
    private var pointerMm = floatArrayOf(0f, 0f, 0f)

    // "your turn" (S16)
    private var ytSerial = -1
    private var ytS = 0f
    private var ytTight = 0f
    private var ytTightStartMs = NEVER
    private var ytDoneMs = NEVER
    private var ytWritten = -1f

    // panel poses
    private var guidePose: Pair<Vec3, Quat4>? = null

    private var blocksSent = false

    /** Horizontal head -> rope distance when the rope went to its marker (S14 counts walking away from there). */
    private var placedDistH = 0.6f

    // the floor marker (D1 "move to marker")
    private var floorTap: Entity? = null
    private var markerRing: Entity? = null
    private var ghostRing: Entity? = null
    private var lastRay: Pair<Vec3, Vec3>? = null

    // out of sight (S14)
    private var awaySinceMs = NEVER
    private var awaySent: String? = null

    // look-to-advance
    /** Debug: a Stage point the gaze is taken to rest on instead of the eyes / head (emulator tests). */
    @Volatile var gazeOverride: Vec3? = null

    /** Debug `head yaw <deg>`: the out-of-sight check and placement see the head turned by this much. */
    @Volatile var headYawOverride: Float = 0f
    private var dwellMs = 0f
    private var dwellStartMs = NEVER
    private var dwellSerial = -1
    private var dwellLeftSinceStep = false
    private var dwellProgressWritten = 0f
    private var gazeFromHeadSent: Boolean? = null
    private var gazeChangedMs = NEVER

    fun onPanelPointer(pressed: Boolean) {
        panelPressed = pressed
        lastPanelPressMs = SystemClock.elapsedRealtime()
    }

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached || stageRoot != null) detach()
        val t0 = SystemClock.elapsedRealtime()
        this.content = content
        val root = Entity().named("stageRoot")
        content.addEntity(root)
        val probe = Entity().named("contentProbe")
        content.addEntity(probe)
        contentProbe = probe
        // An entity added to the SpatialView is not at the Stage origin: move the root once so its local space is Stage space.
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        KnotLog.i("STAGE root offset=${KnotLog.pos(offset.domain())}")
        val rig = PanelRig(root) { id, shown -> panelShown[id] = shown }
        this.rig = rig
        for (id in PanelSpec.pool) {
            val (w, h) = PanelSpec.size(id)
            rig.world(id, w, h).autoFace = false
        }
        headTracker.start()
        eyeTracker.start()
        controller.start()
        ActiveScene.scene = this
        bindPanels(attachments)
        follower.reset()
        buildJob =
            scope.launch {
                try {
                    build(root, t0)
                } catch (t: Throwable) {
                    if (t !is CancellationException) KnotLog.e("SCENE build failed: $t", t)
                    throw t
                }
            }
    }

    /** Materials and the rope's entity tree; the main thread is given back between meshes. */
    private suspend fun build(root: Entity, t0: Long) {
        val sh = SharedResources()
        shared = sh
        val m = SceneMaterials(sh)
        materials = m
        yield()
        val rr = Entity().named("ropeRoot")
        root.addChild(rr)
        rr.enabled = false
        ropeRoot = rr
        val tn = Entity().named("ropeTurn")
        rr.addChild(tn)
        turnNode = tn
        // the shadow does not turn with the rope (v0.1.4: no own bar under the rope; the system volume window has its bar)
        shadowEntity = staticPiece(rr, "shadow", m.shadow) { Shapes.disc(it, 0.1f) }?.also { it.setLocal(position = Vec3(0f, -SHADOW_BELOW_M, 0f)) }
        yield()
        postEntity = staticPiece(tn, "post", m.post) { Shapes.cylinder(it, 0f, -0.12f, 0f, 0f, 0.12f, 0f, 0.02f, 32) }?.also { it.enabled = false }
        yield()
        tipRing = staticPiece(tn, "tipRing", m.tipRing) { Shapes.ring(it, 0.022f, 0.03f) }?.also { it.enabled = false }
        yield()
        // the floor (Stage y = 0) as the marker's tap target, and the marker / ghost rings lying on it
        floorTap = Entity().named("floorTap").also {
            root.addChild(it)
            it.tapBox(Vec3(FLOOR_TAP_M, 0.02f, FLOOR_TAP_M), Vec3(0f, -0.01f, 0f))
            it.enabled = false
        }
        val flat = Quat4.axisAngle(Vec3.RIGHT, -PI.toFloat() / 2f)
        markerRing = staticPiece(root, "markerRing", m.marker) { Shapes.ring(it, 0.09f, 0.12f, 48); Shapes.ring(it, 0f, 0.015f, 16) }?.also { it.setLocal(rotation = flat); it.enabled = false }
        ghostRing = staticPiece(root, "ghostRing", m.markerGhost) { Shapes.ring(it, 0.09f, 0.12f, 48) }?.also { it.setLocal(rotation = flat); it.enabled = false }
        yield()
        val tap = Entity().named("ropeTap")
        tn.addChild(tap)
        tap.tapBox(Vec3(0.24f, 0.26f, 0.08f))
        workTap = tap
        attached = true
        KnotLog.i("SCENE attached build_ms=${SystemClock.elapsedRealtime() - t0}")
        render(ui, force = true)
        sink(KnotEvent.SceneReady)
    }

    private fun staticPiece(parent: Entity, name: String, material: Material, shape: (MeshArrays) -> Unit): Entity? =
        runCatching {
            val arrays = MeshArrays(256).also(shape)
            val (model, bounds) = Meshes.model(arrays.build())
            val mesh = MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
            ModelEntity(mesh, material).named(name).also { parent.addChild(it) }
        }.onFailure { KnotLog.e("SCENE piece $name failed: $it", it) }.getOrNull()

    /** Called from `SpatialView(update = ...)`: parents freshly composed panel entities under their holders. */
    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        val complete = rig.bind(attachments, PanelSpec.pool, SystemClock.elapsedRealtime())
        if (!complete) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    /** The rope's volume window composed its SpatialView: move the rope's entity tree into it (centre = volume centre). */
    fun attachVolume(content: SpatialViewContent) {
        val rr = ropeRoot
        if (rr == null) {
            KnotLog.i("VOLUME content before the scene was built: retrying")
            main.postDelayed({ attachVolume(content) }, 300L)
            return
        }
        val root = Entity().named("volumeRoot")
        content.addEntity(root)
        val probe = Entity().named("volumeProbe")
        content.addEntity(probe)
        root.addChild(rr)
        volumeRoot = root
        volumeProbe = probe
        volumeContent = content
        sentPose = null
        firstPose = null
        KnotLog.i("VOLUME rope moved into the volume window")
        render(ui, force = true)
    }

    /** The volume window is going away: take the rope back to the Stage (hidden) before its content is destroyed. */
    fun detachVolume() {
        val root = volumeRoot ?: return
        ropeRoot?.let { rr ->
            runCatching { stageRoot?.addChild(rr) }
            rr.enabled = false
        }
        runCatching { root.destroy(true) }
        runCatching { volumeProbe?.destroy(true) }
        volumeRoot = null
        volumeProbe = null
        volumeContent = null
        volumePose = null
        sentPose = null
        firstPose = null
        if (drag != Drag.NONE && dragInVolume) drag = Drag.NONE
        KnotLog.i("VOLUME rope back on the Stage (hidden)")
        sink(KnotEvent.RopeGone)
    }

    /** The rope frame's height inside the volume for the height setting (the volume itself is where the user put it). */
    private fun heightOffsetM(state: KnotUiState): Float = (state.height.aboveM - HEIGHT_NORMAL_M) * HEIGHT_IN_VOLUME

    /** Each Stage frame: where the system put the volume, in Stage coordinates; told to the ViewModel once it settles. */
    private fun volumeFrame(state: KnotUiState, now: Long) {
        val root = volumeRoot
        val stage = stageRoot
        if (root == null || stage == null) return
        val pose =
            runCatching {
                    val c = root.convertPositionTo(Vector3(0f, heightOffsetM(state), 0f), stage)
                    val r = root.convertRotationTo(Quat.identity(), stage)
                    val x = Quat4(r.x, r.y, r.z, r.w).rotate(Vec3.RIGHT)
                    RopePose(Vec3(c.x, c.y, c.z), atan2(-x.z, x.x))
                }
                .onFailure { KnotLog.w("VOLUME pose failed: $it") }
                .getOrNull() ?: return
        val sent = sentPose
        if (sent == null) {
            // the window's first seconds report a placeholder pose at the Stage origin (emulator: (0, 0, 0.2), ~4 s)
            // before the system places it: skip that, then the rope counts as placed once it has held still
            if (pose.center.length < PLACEHOLDER_M) return
            val last = firstPose
            if (last == null || last.center.distanceTo(pose.center) > POSE_SEND_M) {
                firstPose = pose
                poseMovedSinceMs = now
                return
            }
            if (now - poseMovedSinceMs < POSE_SETTLE_MS) return
        } else {
            volumePose = pose
            val moved = sent.center.distanceTo(pose.center) > POSE_SEND_M || abs(wrap(sent.yaw - pose.yaw)) > POSE_SEND_RAD
            if (!moved) {
                poseMovedSinceMs = NEVER
                return
            }
            if (poseMovedSinceMs == NEVER) poseMovedSinceMs = now
            if (now - poseMovedSinceMs < POSE_SETTLE_MS) return
        }
        volumePose = pose
        firstPose = null
        poseMovedSinceMs = NEVER
        sentPose = pose
        if (sent == null) placedDistH = horizontalDistance(pose.center)
        KnotLog.i("ROPE volume at center=${KnotLog.pos(pose.center)} yaw=${KnotLog.f(Math.toDegrees(pose.yaw.toDouble()).toFloat(), 1)} headDistH=${KnotLog.f(horizontalDistance(pose.center))}")
        sink(KnotEvent.RopePlaced(pose))
    }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        buildJob?.cancel()
        buildJob = null
        if (ActiveScene.scene === this) ActiveScene.scene = null
        RopeWindow.want(false, "stage gone")
        main.removeCallbacksAndMessages(null)
        headTracker.stop()
        eyeTracker.stop()
        controller.stop()
        panelShown.clear()
        runCatching { rig?.release() }
        runCatching { ropeRoot?.destroy(true) }
        runCatching { volumeRoot?.destroy(true) }
        runCatching { volumeProbe?.destroy(true) }
        volumeRoot = null
        volumeProbe = null
        volumeContent = null
        volumePose = null
        sentPose = null
        firstPose = null
        runCatching { stageRoot?.destroy(true) }
        runCatching { contentProbe?.destroy(true) }
        shared?.close()
        shared = null
        stageRoot = null
        contentProbe = null
        content = null
        materials = null
        rig = null
        ropeRoot = null
        turnNode = null
        ropeEntity = null
        ropeMesh = null
        dashEntity = null
        dashMesh = null
        arrowEntity = null
        arrowMesh = null
        postEntity = null
        shadowEntity = null
        workTap = null
        tipRing = null
        hasHead = false
        builtStaticKey = ""
        lastMeshKey = ""
        drag = Drag.NONE
        dwellMs = 0f
        dwellStartMs = NEVER
        dwellSerial = -1
        DwellTarget.progress.floatValue = 0f
        KnotLog.i("SCENE detached")
    }

    // =========================================================================================
    // state -> entities
    // =========================================================================================

    fun render(state: KnotUiState, force: Boolean = false) {
        ui = state
        if (!attached) return
        RopeWindow.want(state.inKnot, "knot ${state.knot.id}")
        val show = state.inKnot && volumeRoot != null
        ropeRoot?.enabled = show
        if (show) {
            // inside the volume: the volume's centre is the rope frame's place; the system turns the volume to the user
            ropeRoot?.setLocal(position = Vec3(0f, heightOffsetM(state) - state.knot.lowerMm / 1000f, 0f), rotation = Quat4.IDENTITY)
        }
        rig?.let { r ->
            if (!state.menuOpen) r[PanelSpec.MENU]?.hide()
            if (!state.settingsOpen) r[PanelSpec.SETTINGS]?.hide()
        }
        if (!show) return
        val g = geometry(state)
        val key = "${state.knot.id}|${state.settings.drawMm}|${state.step}|${state.mode}|${state.hand}|${state.stepSerial}"
        if (key != builtStaticKey || force) {
            builtStaticKey = key
            rebuildStatic(state, g)
        }
        ropeEntity?.enabled = !state.ropeHidden
        dashEntity?.enabled = !state.ropeHidden && showDashes(state)
        arrowEntity?.enabled = !state.ropeHidden && state.tightenStep && (state.mode == Mode.STEP || state.mode == Mode.YOUR_TURN)
        postEntity?.enabled = !state.ropeHidden && state.knot.post
        tipRing?.enabled = !state.ropeHidden && state.mode == Mode.YOUR_TURN && !state.tightenStep
    }

    private fun showDashes(state: KnotUiState): Boolean =
        !state.tightenStep && ((state.mode == Mode.STEP && state.phase == Phase.DEMO) || state.mode == Mode.YOUR_TURN)

    private fun geometry(state: KnotUiState): KnotGeometry {
        val d = state.settings.drawMm
        return geometries.getOrPut("${state.knot.id}|$d") { KnotGeometry(state.knot, d.toFloat()) }
    }

    /** Where drawing starts: the standing part outside the work area (above 0.17 m, beyond ±0.22 m) is left out. */
    private fun clipFrom(g: KnotGeometry): Float = clipCache.getOrPut("${g.knot.id}|${g.dMm}") { clipOf(g.frame(g.total)) }

    /** The same cut on a drawn line (the tightened rope takes its slack at the top, so the cut is found per line). */
    private fun clipOf(line: Centerline): Float {
        var i = 0
        while (i < line.size - 1 && (line.y(i) > CLIP_TOP_MM || abs(line.x(i)) > CLIP_SIDE_MM)) i++
        return line.arcs[i]
    }

    /** The rope mesh; a bend's two ropes as two tubes, blue and red, each with its standing part cut at the work area. */
    private fun meshRope(line: Centerline, dMm: Float, mirror: Boolean, knot: Knot): MeshArrays {
        if (line.ropes == 1) return RopeMesher.mesh(line, dMm, clipOf(line), mirror, knot.twoColor)
        val out = MeshArrays(3200)
        val a = line.part(0)
        val b = line.part(1)
        RopeMesher.mesh(a, dMm, clipOf(a), mirror, false, out, RopeColor.BLUE)
        RopeMesher.mesh(b, dMm, clipOf(b), mirror, false, out, RopeColor.RED)
        return out
    }

    /** Per step: the route dashes, the "pull here" arrows and the tap box sized to the knot. */
    private fun rebuildStatic(state: KnotUiState, g: KnotGeometry) {
        val m = materials ?: return
        val tn = turnNode ?: return
        val mirror = state.hand == Hand.LEFT
        val idx = state.step.coerceIn(0, g.steps.size - 1)
        val r = g.steps[idx]
        // dashes along this step's route
        val dash = MeshArrays(1024)
        if (!r.tighten && state.mode != Mode.PREVIEW && state.mode != Mode.DONE) RopeMesher.dashes(g.frame(r.s1), r.s0, r.s1, state.settings.drawMm.toFloat(), mirror, dash)
        dashEntity = replaceMesh(tn, dashEntity, dashMesh, dash, m.dash, "dash") { dashMesh = it }
        // arrows
        val arr = MeshArrays(512)
        if (r.tighten) {
            val sx = if (mirror) -1f else 1f
            for (p in state.knot.pulls) {
                val (x, y) = g.place(p.x, p.y)
                Shapes.arrow(arr, x / 1000f * sx, y / 1000f, 0f, p.dx * sx, p.dy)
            }
        }
        arrowEntity = replaceMesh(tn, arrowEntity, arrowMesh, arr, m.arrow, "arrows") { arrowMesh = it }
        // bounds of the knot -> tap box
        val full = g.frame(g.total)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (k in 0 until full.ropes) {
            val part = full.part(k)
            val from = clipOf(part)
            for (i in 0 until part.size) {
                if (part.arcs[i] < from) continue
                minX = min(minX, part.x(i))
                maxX = max(maxX, part.x(i))
                minY = min(minY, part.y(i))
                maxY = max(maxY, part.y(i))
            }
        }
        val w = ((maxX - minX) / 1000f + 0.04f).coerceIn(0.16f, 0.5f)
        val h = ((maxY - minY) / 1000f + 0.02f).coerceIn(0.2f, 0.6f)
        val cx = (maxX + minX) / 2000f * if (mirror) -1f else 1f
        val cy = (maxY + minY) / 2000f + 0.01f + (h - ((maxY - minY) / 1000f + 0.02f)) / 2f
        workTap?.tapBox(Vec3(w, h, 0.08f), Vec3(cx, cy, 0f))
        lastMeshKey = ""
        KnotLog.i("SCENE step built knot=${state.knot.id} step=${idx + 1} d=${state.settings.drawMm} box=${KnotLog.f(w, 3)}x${KnotLog.f(h, 3)}")
    }

    private fun replaceMesh(parent: Entity, entity: Entity?, mesh: MeshResource?, arrays: MeshArrays, material: Material, name: String, keep: (MeshResource?) -> Unit): Entity? {
        if (arrays.isEmpty) {
            entity?.enabled = false
            return entity
        }
        val tm = SystemClock.elapsedRealtime()
        val (model, bounds) = Meshes.model(arrays.build())
        lastModelMs = SystemClock.elapsedRealtime() - tm
        return runCatching {
            if (entity == null || mesh == null) {
                val created = MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
                keep(created)
                ModelEntity(created, material).named(name).also { parent.addChild(it) }
            } else {
                mesh.replaceWithMeshModel(model, bounds)
                entity.components[ModelComponent::class.java]?.mesh = mesh
                entity.enabled = true
                entity
            }
        }.onFailure { KnotLog.e("SCENE mesh $name failed: $it", it) }.getOrNull() ?: entity
    }

    // =========================================================================================
    // per frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        // v0.1.4: while the rope's volume window is open the Stage loses focus and its frames drop to ~1 fps, so the
        // volume's frames drive the scene then (one driver at a time, never both)
        val driver = volumeRoot?.scene ?: root.scene
        if (fpsOn) seenScenes["${java.lang.System.identityHashCode(context.scene)}"] = (seenScenes["${java.lang.System.identityHashCode(context.scene)}"] ?: 0) + 1
        if (fpsOn && SystemClock.elapsedRealtime() - seenLogMs > 1000L) {
            seenLogMs = SystemClock.elapsedRealtime()
            KnotLog.i("FRAMES scenes=$seenScenes driver=${java.lang.System.identityHashCode(driver)} stage=${java.lang.System.identityHashCode(root.scene)} volume=${volumeRoot?.let { java.lang.System.identityHashCode(it.scene) }}")
            seenScenes.clear()
        }
        if (driver == null || context.scene != driver) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs).coerceIn(0L, 100L)) / 1000f
        lastFrameMs = now
        headTracker.pose()?.let {
            head = if (headYawOverride != 0f) HeadPose(it.position, Quat4.axisAngle(Vec3.UP, Math.toRadians(headYawOverride.toDouble()).toFloat()) * it.rotation) else it
            hasHead = true
        }
        val rig = rig ?: return
        rig.onFrame(now)
        rig.stowBehind(head.view)
        val state = ui
        countFrame(now)
        if (!attached) return
        RopeWindow.stepGate(now, headPitchDeg())
        volumeFrame(state, now)
        sampleController(state, now, dt)
        turnFrame(state, now, dt)
        demoFrame(state, now)
        outOfSightFrame(state, now)
        val hold = panelPressed || (lastPanelPressMs != NEVER && now - lastPanelPressMs < PANEL_HOLD_MS) || state.menuOpen || state.settingsOpen
        if (hasHead && head.position.length > 1e-3f) follower.follow(head, emptyList(), 0, now, hold)
        placePanels(state, rig, now)
        markerFrame(state)
        dwellFrame(state, now)
    }

    /** Timeline of the demo (design `spatialLayout.rope.motion`), "your turn", scrubbing, done: one mesh update at ≤ 30 Hz. */
    private fun demoFrame(state: KnotUiState, now: Long) {
        val rope = volumePose
        if (!state.inKnot || rope == null) return
        val g = geometry(state)
        val n = g.steps.size
        val speed = state.settings.speed
        if (state.stepSerial != timelineSerial) {
            timelineSerial = state.stepSerial
            timelineStartMs = now
        }
        val t = (now - timelineStartMs) / 1000f * speed
        var stepIdx = state.step.coerceIn(0, n - 1)
        var f: Float
        when (state.mode) {
            Mode.PREVIEW -> {
                // the whole knot once, without stops, ending tightened
                val durs = g.steps.indices.map { moveSeconds(g, it) }
                var left = t - PREVIEW_START_S
                stepIdx = 0
                f = 0f
                if (left > 0f) {
                    var i = 0
                    while (i < n && left > durs[i]) {
                        left -= durs[i]
                        i++
                    }
                    if (i >= n) {
                        stepIdx = n - 1
                        f = 1f
                        if (finishedSent != state.stepSerial) {
                            finishedSent = state.stepSerial
                            sink(KnotEvent.DemoFinished(state.stepSerial))
                        }
                    } else {
                        stepIdx = i
                        f = Ease.inOutQuad((left / durs[i]).coerceIn(0f, 1f))
                    }
                }
                state.ghost?.let { gh ->
                    val share = gh.coerceIn(0f, 1f) * n
                    stepIdx = share.toInt().coerceIn(0, n - 1)
                    f = (share - stepIdx).coerceIn(0f, 1f).let { if (gh >= 1f) 1f else it }
                }
            }
            Mode.STEP ->
                f =
                    when (state.phase) {
                        Phase.HOLD -> 1f
                        Phase.SCRUB -> {
                            val share = state.scrub * n
                            stepIdx = share.toInt().coerceIn(0, n - 1)
                            (share - stepIdx).coerceIn(0f, 1f).let { if (state.scrub >= 1f) 1f else it }
                        }
                        Phase.DEMO -> {
                            val move = moveSeconds(g, stepIdx)
                            val cycle = PAUSE_S + move + HOLD_S
                            val ghost = state.ghost
                            when {
                                ghost != null -> ghost
                                t >= LOOPS * cycle -> {
                                    if (finishedSent != state.stepSerial) {
                                        finishedSent = state.stepSerial
                                        sink(KnotEvent.DemoFinished(state.stepSerial))
                                    }
                                    1f
                                }
                                else -> {
                                    if (t >= PAUSE_S + move && playedSent != state.stepSerial) {
                                        playedSent = state.stepSerial
                                        sink(KnotEvent.DemoPlayed(state.stepSerial))
                                    }
                                    val within = t % cycle
                                    when {
                                        within < PAUSE_S -> 0f
                                        within < PAUSE_S + move -> Ease.inOutCubic((within - PAUSE_S) / move)
                                        else -> 1f
                                    }
                                }
                            }
                        }
                    }
            Mode.YOUR_TURN -> f = yourTurnFrame(state, g, now)
            else -> {
                // DONE: tightened, one slow turn
                stepIdx = n - 1
                f = 1f
            }
        }
        val r = g.steps[stepIdx]
        val sTip = if (state.mode == Mode.YOUR_TURN && !r.tighten) ytS else if (r.tighten) g.total else r.s0 + (r.s1 - r.s0) * f
        val tight = if (r.tighten) f else 0f
        val share = ((stepIdx + (if (state.mode == Mode.YOUR_TURN) 0f else f)) / n).coerceIn(0f, 1f)
        val q = (share * 100f).toInt() / 100f
        if (q != DemoClock.share.floatValue) DemoClock.share.floatValue = q
        updateRope(state, g, sTip, tight, now)
    }

    /** Seconds of one step's move at 1× (design: 1.6 - 3.0 s by path length; tightening 2 s). */
    private fun moveSeconds(g: KnotGeometry, i: Int): Float {
        val r = g.steps[i]
        if (r.tighten) return TIGHTEN_S
        return (1.6f + (r.s1 - r.s0 - 60f) / 350f * 1.4f).coerceIn(1.6f, 3.0f)
    }

    private fun updateRope(state: KnotUiState, g: KnotGeometry, sTip: Float, tight: Float, now: Long) {
        val m = materials ?: return
        val tn = turnNode ?: return
        val key = "${g.knot.id}|${g.dMm}|${state.hand}|${(sTip * 2f).toInt()}|${(tight * 200f).toInt()}"
        if (key == lastMeshKey) return
        if (now - lastMeshMs < MESH_MIN_MS && lastMeshKey.isNotEmpty()) return
        lastMeshKey = key
        lastMeshMs = now
        val t0 = SystemClock.elapsedRealtime()
        val line = g.frame(sTip, tight)
        lastLine = line
        lastTipArc = sTip
        val arrays = meshRope(line, g.dMm, state.hand == Hand.LEFT, state.knot)
        val t1 = SystemClock.elapsedRealtime()
        ropeEntity = replaceMesh(tn, ropeEntity, ropeMesh, arrays, m.rope, "rope") { ropeMesh = it }
        val t2 = SystemClock.elapsedRealtime()
        meshCount++
        if (fpsOn || t2 - t0 > SLOW_MESH_MS) KnotLog.i("MESH rope verts=${arrays.vertices} build=${t1 - t0}ms upload=${t2 - t1}ms (model ${lastModelMs}ms) n=$meshCount")
        ropeEntity?.enabled = !state.ropeHidden
        // the tip ring sits on the tip during "your turn"
        if (state.mode == Mode.YOUR_TURN) {
            val p = line.at(line.tipArc)
            val sx = if (state.hand == Hand.LEFT) -1f else 1f
            tipRing?.setLocal(position = Vec3(p[0] / 1000f * sx, p[1] / 1000f, p[2] / 1000f + 0.006f))
        }
    }

    /** S16: the tip stays where the user's drag left it; returns the tighten share (tighten step) else 0. */
    private fun yourTurnFrame(state: KnotUiState, g: KnotGeometry, now: Long): Float {
        val r = g.steps[state.step.coerceIn(0, g.steps.size - 1)]
        if (state.stepSerial != ytSerial) {
            ytSerial = state.stepSerial
            ytS = r.s0
            ytTight = 0f
            ytTightStartMs = NEVER
            ytDoneMs = NEVER
            tipRing?.let { ring -> materials?.let { ring.setMaterial(it.tipRing) } }
            KnotLog.i("YOURTURN start s0=${KnotLog.f(r.s0, 1)} s1=${KnotLog.f(r.s1, 1)} tighten=${r.tighten}")
        }
        state.yourTurnGhost?.let { gh ->
            if (r.tighten) ytTight = gh else ytS = r.s0 + (r.s1 - r.s0) * gh
        }
        if (r.tighten && ytTightStartMs != NEVER) {
            ytTight = ((now - ytTightStartMs) / 1000f / TIGHTEN_S * state.settings.speed).coerceIn(0f, 1f)
            if (ytTight >= 1f && ytDoneMs == NEVER) ytDoneMs = now
        }
        val share = if (r.tighten) ytTight else ((ytS - r.s0) / (r.s1 - r.s0).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val q = (share * 100f).toInt() / 100f
        if (q != ytWritten) {
            ytWritten = q
            DemoClock.yourTurn.floatValue = q
        }
        if (ytDoneMs != NEVER && now - ytDoneMs >= YT_FLASH_MS && state.yourTurnGhost == null) {
            ytDoneMs = NEVER
            sink(KnotEvent.YourTurnDone(state.stepSerial))
        }
        return if (r.tighten) Ease.inOutCubic(ytTight) else 0f
    }

    private fun yourTurnComplete(now: Long) {
        if (ytDoneMs != NEVER) return
        ytDoneMs = now
        materials?.let { tipRing?.setMaterial(it.tipDone) }
        KnotLog.i("YOURTURN complete")
    }

    /** Spin animation, the done turn, snapping back to the user's view (±12°) and the "turned" flag (S08). */
    private fun turnFrame(state: KnotUiState, now: Long, dt: Float) {
        if (spinAnimStartMs != NEVER) {
            val q = ((now - spinAnimStartMs) / spinAnimMs).coerceIn(0f, 1f)
            spin = spinAnimFrom + (spinAnimTo - spinAnimFrom) * Ease.inOutCubic(q)
            if (q >= 1f) {
                spinAnimStartMs = NEVER
                spin = wrap(spinAnimTo)
            }
        }
        if (state.mode == Mode.DONE) {
            if (state.stepSerial != doneSerial) {
                doneSerial = state.stepSerial
                doneSpin = 0f
                doneSpinStopped = false
            }
            if (!doneSpinStopped && doneSpin < 2f * PI.toFloat()) doneSpin = min(2f * PI.toFloat(), doneSpin + dt * 2f * PI.toFloat() / DONE_TURN_S)
        } else {
            doneSpin = 0f
        }
        val turned = abs(wrap(spin)) > TURNED_RAD
        if (turned != turnedSent) {
            turnedSent = turned
            sink(KnotEvent.TurnedChanged(turned))
        }
        val total = spin + if (doneSpin >= 2f * PI.toFloat()) 0f else doneSpin
        turnNode?.setLocal(rotation = Quat4.axisAngle(Vec3.UP, total))
    }

    private fun stopDoneSpin() {
        if (ui.mode == Mode.DONE && !doneSpinStopped) {
            doneSpinStopped = true
            spin = wrap(spin + doneSpin)
            doneSpin = 0f
        }
    }

    private fun animateSpin(to: Float, ms: Float) {
        spinAnimFrom = spin
        spinAnimTo = to
        spinAnimMs = ms
        spinAnimStartMs = SystemClock.elapsedRealtime()
    }

    /** Released within ±12° of the user's view: back to 0 (T12). */
    private fun snapIfNear() {
        val w = wrap(spin)
        spin = w
        if (abs(w) <= SNAP_RAD && w != 0f) animateSpin(0f, SNAP_MS)
    }

    /** T11b: +90° in 0.3 s. */
    fun turnBy(degrees: Float) {
        stopDoneSpin()
        val base = if (spinAnimStartMs != NEVER) spinAnimTo else spin
        animateSpin(base + Math.toRadians(degrees.toDouble()).toFloat(), TURN90_MS)
        KnotLog.i("TURN by ${degrees.toInt()} to ${KnotLog.f(Math.toDegrees(wrap(base).toDouble()).toFloat() + degrees, 1)}")
    }

    /** Back to the user's view. */
    fun resetTurn() {
        stopDoneSpin()
        if (wrap(spin) != 0f) animateSpin(0f, SNAP_MS)
    }

    /** Controller: presence (S09 hint) and the thumbstick turning the rope (design T11: dead zone 0.2, ≤ 120°/s, smoothed). */
    private fun sampleController(state: KnotUiState, now: Long, dt: Float) {
        val sample = controller.sample()
        val active = sample != null
        // the marker's hover point: controller ray first, else the eyes (set in dwellFrame); never the head's direction
        lastRay = sample?.let { it.ray.origin to it.ray.direction }
        if (active != controllerActive) {
            if (controllerChangedMs == NEVER) controllerChangedMs = now
            if (now - controllerChangedMs >= CONTROLLER_DEBOUNCE_MS) {
                controllerActive = active
                controllerChangedMs = NEVER
                KnotLog.i("TRACK controller active=$active")
                sink(KnotEvent.ControllerChanged(active))
            }
        } else {
            controllerChangedMs = NEVER
        }
        val x = sample?.stickX ?: 0f
        val target = if (state.inKnot && abs(x) > STICK_DEAD_ZONE) -x.coerceIn(-1f, 1f) * ((abs(x) - STICK_DEAD_ZONE) / (1f - STICK_DEAD_ZONE)) * STICK_RATE_RAD_S else 0f
        spinVel += (target - spinVel) * min(1f, dt * STICK_SMOOTH)
        if (abs(spinVel) > 0.01f) {
            stopDoneSpin()
            spinAnimStartMs = NEVER
            spin += spinVel * dt
            lastStickMs = now
        } else if (lastStickMs != NEVER && now - lastStickMs > STICK_SETTLE_MS) {
            lastStickMs = NEVER
            spinVel = 0f
            snapIfNear()
        }
    }

    /** Debug `stick <x> <ms>`: the thumbstick held at x for a while (the emulator's stick does not reach a Stage app). */
    fun injectStick(x: Float, ms: Long) {
        val until = SystemClock.elapsedRealtime() + ms
        val r =
            object : Runnable {
                override fun run() {
                    val now = SystemClock.elapsedRealtime()
                    if (now >= until) return
                    val dt = 0.033f
                    spin -= x * ((abs(x) - STICK_DEAD_ZONE).coerceAtLeast(0f) / (1f - STICK_DEAD_ZONE)) * STICK_RATE_RAD_S * dt
                    lastStickMs = now
                    stopDoneSpin()
                    main.postDelayed(this, 33L)
                }
            }
        main.post(r)
    }

    /** S14: the user walked away (> 1.5 m) or turned away (> 90° for 2 s). */
    /** Head pitch (+ up) for the volume's level gate; null while the head pose is not valid yet (all-zero first frames). */
    private fun headPitchDeg(): Float? {
        if (!hasHead || head.position.length < 1e-3f) return null
        val f = head.forward
        return Math.toDegrees(kotlin.math.asin(f.y.coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    private fun outOfSightFrame(state: KnotUiState, now: Long) {
        val rope = volumePose
        if (!state.inKnot || rope == null || !hasHead) {
            awaySinceMs = NEVER
            awaySent = null
            return
        }
        // T27: the head inside the rope's work area, or (D20) the rope between the eyes and the panel: see-through
        val near = head.position.distanceTo(rope.center) < SEE_THROUGH_M
        val blocks = ropeBlocksPanel(rope, state.knot.lowerMm / 1000f)
        if (blocks != blocksSent) {
            blocksSent = blocks
            // q3-1: see-through is not enough; the rope's tap box must not take the panel's pinches
            workTap?.enabled = !blocks
            KnotLog.i("CONE rope ${if (blocks) "in front of the panel: see-through, hit boxes off" else "clear of the panel"}")
        }
        ropeRoot?.setOpacity(if (near || blocks) SEE_THROUGH_OPACITY else 1f)
        val to = Vec3(rope.center.x - head.position.x, 0f, rope.center.z - head.position.z)
        val dist = to.length
        val fwd = Vec3(head.forward.x, 0f, head.forward.z).let { if (it.length < 0.1f) Vec3.AHEAD else it.normalized() }
        val angle = if (dist < 1e-3f) 0f else Math.toDegrees(kotlin.math.acos(to.normalized().dot(fwd).coerceIn(-1f, 1f).toDouble())).toFloat()
        // "walked away" is measured from where the user stood when the rope went to the marker (a far marker is the user's choice)
        val awayDist = max(AWAY_DIST_M, placedDistH + AWAY_MORE_M)
        val away = dist > awayDist || angle > AWAY_ANGLE_DEG
        val back = dist <= awayDist && angle < BACK_ANGLE_DEG
        if (awaySent == null) {
            if (!away) {
                awaySinceMs = NEVER
                // shown by someone else (debug) and the rope is in sight: clear it, unless held for a screenshot
                if (state.outOfSight != null && back && !state.hold) sink(KnotEvent.OutOfSight(null))
                return
            }
            if (awaySinceMs == NEVER) awaySinceMs = now
            if (now - awaySinceMs >= AWAY_MS || dist > awayDist) {
                val side = fwd.cross(to).y // > 0: the rope is to the left
                val where = if (angle > 135f || dist > awayDist && angle > 90f) "behind" else if (side > 0f) "left" else "right"
                awaySent = where
                KnotLog.i("AWAY $where dist=${KnotLog.f(dist)} angle=${KnotLog.f(angle, 0)}")
                sink(KnotEvent.OutOfSight(where))
            }
        } else if (back || state.outOfSight == null) {
            awaySent = null
            awaySinceMs = NEVER
            if (state.outOfSight != null) {
                KnotLog.i("AWAY back")
                sink(KnotEvent.OutOfSight(null))
            }
        }
    }

    /**
     * The guide panel follows the head lazily (0.9 m, 10° below the view). While a knot is open it sits 28° to the hand
     * side, so the rope (where the user looks while tying) and the panel do not stand in each other's way (factory rule
     * D20); in the library, while placing the marker and in S14 it is straight ahead. Popups stand in front of it.
     */
    private fun placePanels(state: KnotUiState, rig: PanelRig, now: Long) {
        if (!hasHead) return
        val rope = volumePose
        val side = if (state.inKnot && rope != null && state.outOfSight == null && !state.placing) (if (state.hand == Hand.LEFT) -PANEL_SIDE_RAD else PANEL_SIDE_RAD) else 0f
        if (side != follower.sideRad) {
            follower.sideRad = side
            follower.park(head, emptyList(), now)
        }
        val pose = follower.pose.position to follower.rotation
        guidePose = pose
        val k = PanelSpec.SCALE
        rig[PanelSpec.GUIDE]?.place(pose.first, pose.second, k)
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE, k)
        if (state.settingsOpen) rig[PanelSpec.SETTINGS]?.place(pose.first + pose.second.rotate(Vec3(0f, 0f, POPUP_FORWARD_M)), pose.second, k)
        if (state.menuOpen) {
            val (mw, mh) = PanelSpec.halfM(PanelSpec.MENU, k)
            rig[PanelSpec.MENU]?.place(pose.first + pose.second.rotate(Vec3(hw - mw, hh - mh, POPUP_FORWARD_M)), pose.second, k)
        }
    }

    /**
     * D20: is the rope between the eyes and the guide panel? Points of the rope's work area (centre and its four sides)
     * are projected from the eye onto the panel's plane; one landing on the panel (grown by 3 cm) while lying in front of
     * it = the rope blocks the panel. Head position only (a viewing check, not a placement).
     */
    private fun ropeBlocksPanel(rope: RopePose, lowerM: Float): Boolean {
        val (pos, rot) = guidePose ?: return false
        val inv = rot.conjugate()
        val eye = inv.rotate(head.position - pos)
        if (eye.z <= 0.05f) return false
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        val turn = Quat4.axisAngle(Vec3.UP, rope.yaw + spin)
        val origin = rope.center + Vec3(0f, -lowerM, 0f)
        val line = lastLine
        val sx = if (ui.hand == Hand.LEFT) -1f else 1f
        val samples = ArrayList<Vec3>(CONE_SAMPLES)
        if (line != null) {
            val from = clipOf(line)
            var i = 0
            while (i < line.size) {
                if (line.arcs[i] >= from) samples += Vec3(line.x(i) / 1000f * sx, line.y(i) / 1000f, line.z(i) / 1000f)
                i += CONE_STRIDE
            }
        }
        for (o in samples) {
            val p = inv.rotate(origin + turn.rotate(o) - pos)
            if (p.z <= 0f || p.z >= eye.z) continue
            val f = eye.z / (eye.z - p.z)
            val x = eye.x + (p.x - eye.x) * f
            val y = eye.y + (p.y - eye.y) * f
            if (abs(x) <= hw + CONE_MARGIN_M && abs(y) <= hh + CONE_MARGIN_M) return true
        }
        return false
    }

    /**
     * Look-to-advance: the gaze ray (eyes when eye tracking delivers, else the head's forward) against the guide panel,
     * mapped to the panel's root pixels and tested against "Next" ([DwellTarget]). Counts on the wall clock once the
     * gaze has been off the button since the step began and the demo has played (`dwellArmed`); 1.2 s fires.
     */
    private fun dwellFrame(state: KnotUiState, now: Long) {
        if (state.stepSerial != dwellSerial) {
            dwellSerial = state.stepSerial
            dwellLeftSinceStep = false
            dwellMs = 0f
            dwellStartMs = NEVER
        }
        val eye = eyeTracker.pose(now)
        if (lastRay == null && eye != null) lastRay = eye.position to eye.forward
        val fromHead = eye == null
        if (fromHead != gazeFromHeadSent) {
            if (gazeChangedMs == NEVER) gazeChangedMs = now
            if (gazeFromHeadSent == null || now - gazeChangedMs > GAZE_DEBOUNCE_MS) {
                gazeFromHeadSent = fromHead
                gazeChangedMs = NEVER
                KnotLog.i("GAZE source=${if (fromHead) "head (${eyeTracker.unavailableReason(now)})" else "eyes"}")
                sink(KnotEvent.GazeSource(fromHead))
            }
        } else gazeChangedMs = NEVER
        val target = DwellTarget.bounds
        val onPanel = gazeOnGuidePanel(eye)
        val inside =
            target != null && onPanel != null && state.mode == Mode.STEP && drag == Drag.NONE &&
                abs(onPanel.first * target.rootW - target.cx) <= target.w / 2f && abs(onPanel.second * target.rootH - target.cy) <= target.h / 2f
        if (!inside) {
            dwellLeftSinceStep = true
            if (dwellMs > 0f) KnotLog.i("DWELL cleared at ${dwellMs.toInt()} ms")
            dwellMs = 0f
            dwellStartMs = NEVER
        } else if (state.dwellArmed && dwellLeftSinceStep) {
            if (dwellStartMs == NEVER) {
                dwellStartMs = now
                KnotLog.i("DWELL start serial=${state.stepSerial}")
            }
            dwellMs = (now - dwellStartMs).toFloat()
            if (dwellMs >= DWELL_MS) {
                KnotLog.i("DWELL fire serial=${state.stepSerial} after ${dwellMs.toInt()} ms")
                dwellMs = 0f
                dwellStartMs = NEVER
                dwellLeftSinceStep = false
                sink(KnotEvent.DwellFired(state.stepSerial))
            }
        }
        val q = ((dwellMs / DWELL_MS) * 20f).toInt() / 20f
        if (q != dwellProgressWritten) {
            dwellProgressWritten = q
            DwellTarget.progress.floatValue = q
        }
    }

    /** Where the gaze meets the guide panel, as shares of its width and height (0..1, top-left origin), or null. */
    private fun gazeOnGuidePanel(eye: HeadPose?): Pair<Float, Float>? {
        if (!hasHead) return null
        val (pos, rot) = guidePose ?: return null
        val origin = eye?.position ?: head.position
        val dir = gazeOverride?.let { (it - head.position).normalized() } ?: (eye?.forward ?: head.forward)
        val o = if (gazeOverride != null) head.position else origin
        val n = rot.rotate(Vec3(0f, 0f, 1f))
        val denom = dir.dot(n)
        if (abs(denom) < 1e-5f) return null
        val t = (pos - o).dot(n) / denom
        if (t <= 0f) return null
        val local = rot.conjugate().rotate(o + dir * t - pos)
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        if (abs(local.x) > hw || abs(local.y) > hh) return null
        return (local.x / (2f * hw) + 0.5f) to (0.5f - local.y / (2f * hh))
    }

    /** Debug `aim <control>`: the turn (right, up; degrees) from the current gaze to a guide-panel control's centre. */
    fun aimLine(name: String): String {
        val (pos, rot) = guidePose ?: return "AIM $name: no panel pose"
        val b = KnotProbe.controlsOf(PanelSpec.GUIDE)[name] ?: return "AIM $name: no such control (debug probe off or not composed)"
        val size = KnotProbe.sizeOf(PanelSpec.GUIDE) ?: return "AIM $name: panel size unknown"
        val (hw, hh) = PanelSpec.halfM(PanelSpec.GUIDE)
        val x = (b.cx / size.first - 0.5f) * 2f * hw
        val y = (0.5f - b.cy / size.second) * 2f * hh
        val point = pos + rot.rotate(Vec3(x, y, 0f))
        val to = point - head.position
        val dist = to.length
        val f = head.forward
        var dYaw = Math.toDegrees(atan2(-to.x.toDouble(), -to.z.toDouble())) - Math.toDegrees(atan2(-f.x.toDouble(), -f.z.toDouble()))
        while (dYaw > 180) dYaw -= 360
        while (dYaw < -180) dYaw += 360
        val pitchNow = Math.toDegrees(kotlin.math.asin(f.y.coerceIn(-1f, 1f).toDouble()))
        val pitchTo = Math.toDegrees(kotlin.math.asin((to.y / dist).coerceIn(-1f, 1f).toDouble()))
        return String.format(Locale.US, "AIM %s turnRight=%.1f turnUp=%.1f dist=%.2f point=%s", name, -dYaw, pitchTo - pitchNow, dist, KnotLog.pos(point))
    }

    private fun countFrame(now: Long) {
        if (!fpsOn) return
        frames++
        if (fpsStartMs == 0L) fpsStartMs = now
        if (now - fpsStartMs >= 1000L) {
            KnotLog.i("FPS app=${frames * 1000f / (now - fpsStartMs)} frames=$frames ms=${now - fpsStartMs}")
            frames = 0
            fpsStartMs = now
        }
    }

    // =========================================================================================
    // placement
    // =========================================================================================

    /**
     * "Move to marker" (factory rule D1): the rope frame stands [RopeHeight.aboveM] over the floor marker the user put
     * down with the controller ray or gaze + pinch, turned to face the viewer (x to the user's right, z towards the
     * user), and never nearer than 0.6 m to the head (D14). It stays there for the session: next / previous / replay /
     * another knot / back from the background never move it; only a new marker does.
     */
    /**
     * Debug `marker ... move` / `tomarker` (kept for old QA scripts): v0.1.4 the rope is in a system volume window the
     * app cannot move, so this brings the window back in front of the user (close, then open once the head is level).
     */
    fun moveToMarker(marker: Vec3) {
        KnotLog.i("ROPE marker ${KnotLog.pos(marker)} ignored: the rope is in a system volume; recall it instead")
        recallRope("marker")
    }

    /** "Bring it here": the volume window is closed and opened again in front of the user (the system places it). */
    fun recallRope(why: String) {
        resetTurn()
        RopeWindow.recall(why)
    }

    /** S17: the floor is a tap target while placing; the marker ring sits on the chosen point, a ghost ring under the controller ray / gaze. */
    private fun markerFrame(state: KnotUiState) {
        val placing = state.placing && state.inKnot
        floorTap?.enabled = placing
        val m = state.marker
        markerRing?.enabled = state.inKnot && m != null && placing
        if (m != null) markerRing?.setLocal(position = m + Vec3(0f, 0.004f, 0f))
        val hit = if (placing) lastRay?.let { (o, d) -> if (d.y < -0.05f) o + d * (-o.y / d.y) else null } else null
        ghostRing?.enabled = hit != null
        if (hit != null) ghostRing?.setLocal(position = Vec3(hit.x, 0.003f, hit.z))
    }

    private fun horizontalDistance(p: Vec3): Float = hypot(p.x - head.position.x, p.z - head.position.z)

    // =========================================================================================
    // system pointer (gaze + pinch / controller ray + trigger)
    // =========================================================================================

    fun onSpatialTap(value: SpatialTapValue) {
        val name = runCatching { value.targetEntity?.getName() }.getOrNull() ?: "none"
        KnotLog.i("TAP target=$name kind=${value.interactionKind}")
        val floor = floorTap ?: return
        if (value.targetEntity?.id != floor.id || !ui.placing) return
        val p = toStage(value.position.x, value.position.y, value.position.z) ?: return
        sink(KnotEvent.MarkerPlaced(Vec3(p.x, 0f, p.z)))
    }

    /**
     * Drags (v0.1.4: moving the whole rope is the system volume window bar's): on the rope near the orange tip during "your turn" the tip follows the
     * route (T24); a tighten step's "your turn" is any pull ≥ 4 cm; otherwise a sideways drag turns the rope (T10) where
     * allowed (with a real rope in the hands only on S03 / S07).
     */
    fun onSpatialDrag(phase: String, start: Offset3D?, value: SpatialDragValue, inVolume: Boolean = false) {
        when (phase) {
            "start" -> {
                KnotLog.i("DRAG raw start volume=$inVolume target=${runCatching { value.targetEntity?.getName() }.getOrNull()} start=${start?.let { "${it.x},${it.y},${it.z}" }}")
                val target = value.targetEntity ?: return
                val state = ui
                if (!state.inKnot || volumePose == null) return
                drag = Drag.NONE
                dragAcc = 0f
                dragTurnStarted = false
                dragInVolume = inVolume
                when {
                    workTap != null && target.id == workTap!!.id -> {
                        val g = geometry(state)
                        val r = g.steps[state.step.coerceIn(0, g.steps.size - 1)]
                        if (state.mode == Mode.YOUR_TURN && r.tighten) drag = Drag.PULL
                        else if (state.mode == Mode.YOUR_TURN && nearTip(start, state)) {
                            drag = Drag.TIP
                            val tip = lastLine?.let { it.at(it.tipArc) } ?: floatArrayOf(0f, 0f, 0f)
                            pointerMm = tip.copyOf()
                        } else if (state.pinchTurnAllowed) {
                            drag = Drag.TURN
                            dragSpinStart = spin
                        }
                    }
                }
                KnotLog.i("DRAG start kind=$drag target=${runCatching { target.getName() }.getOrNull()}")
            }
            "move" -> {
                if (drag == Drag.NONE || inVolume != dragInVolume) return
                val d = value.dragAmount
                // in the volume the view's pixels are not the Stage's: convert the drag through the volume's content
                val delta = (if (inVolume) toStageDelta(d.x, d.y, d.z) else null) ?: Vec3(pxToM(d.x), -pxToM(d.y), pxToM(d.z))
                if (fpsOn) KnotLog.i("DRAG move px=(${d.x}, ${d.y}, ${d.z}) m=${KnotLog.pos(delta)}")
                dragMove(delta)
            }
            "end", "cancel" -> {
                if (drag == Drag.NONE) return
                dragEnd()
            }
        }
    }

    /** The drag began within 4.5 cm of the orange tip (in the rope's plane; design: hit radius 3 cm, generous for the pointer). */
    private fun nearTip(start: Offset3D?, state: KnotUiState): Boolean {
        val tip = lastLine?.let { it.at(it.tipArc) } ?: return false
        if (start == null) return true
        val stage = toStage(start.x, start.y, start.z, dragInVolume) ?: return true
        val local = toRopeLocalMm(stage, state)
        val d = hypot(local[0] - tip[0], local[1] - tip[1])
        KnotLog.i("DRAG tip distance=${KnotLog.f(d, 1)} mm")
        return d <= TIP_GRAB_MM
    }

    /** A Stage point -> rope-frame millimetres (unturned, unmirrored geometry space). */
    private fun toRopeLocalMm(p: Vec3, state: KnotUiState): FloatArray {
        val rope = volumePose ?: return floatArrayOf(0f, 0f, 0f)
        val origin = rope.center + Vec3(0f, -state.knot.lowerMm / 1000f, 0f)
        val local = Quat4.axisAngle(Vec3.UP, -(rope.yaw + spin)).rotate(p - origin)
        val sx = if (state.hand == Hand.LEFT) -1f else 1f
        return floatArrayOf(local.x * 1000f * sx, local.y * 1000f, local.z * 1000f)
    }

    private fun dragMove(delta: Vec3) {
        val state = ui
        val rope = volumePose ?: return
        when (drag) {
            Drag.TURN -> {
                val local = Quat4.axisAngle(Vec3.UP, -(rope.yaw)).rotate(delta)
                dragAcc += local.x
                if (!dragTurnStarted && abs(dragAcc) >= TURN_START_M) {
                    dragTurnStarted = true
                    stopDoneSpin()
                    dragSpinStart = spin
                    dragAcc = 0f
                    KnotLog.i("DRAG turn begins")
                }
                if (dragTurnStarted) {
                    spinAnimStartMs = NEVER
                    spin = dragSpinStart + dragAcc / TURN_PER_90_M * (PI.toFloat() / 2f)
                }
            }
            Drag.TIP -> {
                val sx = if (state.hand == Hand.LEFT) -1f else 1f
                val local = Quat4.axisAngle(Vec3.UP, -(rope.yaw + spin)).rotate(delta)
                pointerMm[0] += local.x * 1000f * sx
                pointerMm[1] += local.y * 1000f
                pointerMm[2] += local.z * 1000f
                val g = geometry(state)
                val r = g.steps[state.step.coerceIn(0, g.steps.size - 1)]
                val route = g.frame(r.s1)
                val (s, dist) = KnotGeometry.nearest(route, pointerMm, ytS, r.s1)
                if (s > ytS && dist < ROUTE_REACH_MM) {
                    ytS = min(s, ytS + MAX_JUMP_MM)
                    if ((ytS - r.s0) / (r.s1 - r.s0) >= DONE_SHARE) {
                        ytS = r.s1
                        yourTurnComplete(SystemClock.elapsedRealtime())
                    }
                }
            }
            Drag.PULL -> {
                dragAcc += delta.length
                if (dragAcc >= PULL_M && ytTightStartMs == NEVER) {
                    ytTightStartMs = SystemClock.elapsedRealtime()
                    KnotLog.i("YOURTURN pull -> tighten")
                }
            }
            Drag.NONE -> Unit
        }
    }

    private fun dragEnd() {
        val state = ui
        when (drag) {
            Drag.TURN -> {
                if (dragTurnStarted) snapIfNear()
                KnotLog.i("DRAG turn end spin=${KnotLog.f(Math.toDegrees(wrap(spin).toDouble()).toFloat(), 1)}")
            }
            Drag.TIP -> KnotLog.i("DRAG tip end share=${KnotLog.f(DemoClock.yourTurn.floatValue)}")
            else -> Unit
        }
        drag = Drag.NONE
        render(state)
    }

    /** A drag amount in the volume view's pixels -> a Stage vector (metres): two points converted, subtracted. */
    private fun toStageDelta(x: Float, y: Float, z: Float): Vec3? {
        val a = toStage(0f, 0f, 0f, true) ?: return null
        val b = toStage(x, y, z, true) ?: return null
        return b - a
    }

    /** View-local pixels -> Stage metres, through the SpatialView content conversion (the Stage's or the volume's). */
    private fun toStage(x: Float, y: Float, z: Float, inVolume: Boolean = false): Vec3? {
        val c = (if (inVolume) volumeContent else content) ?: return null
        val probe = (if (inVolume) volumeProbe else contentProbe) ?: return null
        return runCatching {
                val local = c.convertPosition(Vector3(x, y, z), ViewCoordinateSpace.Local, c.localSpatialCoordinateSpace)
                probe.convertPositionTo(local, null).domain()
            }
            .onFailure { KnotLog.w("TAP convert failed: $it") }
            .getOrNull()
    }

    // =========================================================================================
    // debug (KnotProbe.SceneProbe)
    // =========================================================================================

    fun setSpinDegrees(degrees: Float) {
        stopDoneSpin()
        spinAnimStartMs = NEVER
        spin = Math.toRadians(degrees.toDouble()).toFloat()
        lastStickMs = SystemClock.elapsedRealtime()
    }

    /** Debug `release`: as if the stick / drag let go now (snap within ±12°). */
    fun release() = snapIfNear()

    /** Debug `tip <share>`: drags the tip along the route to that share (as the pointer would, in 3 cm jumps). */
    fun debugTipTo(share: Float) {
        val state = ui
        if (state.mode != Mode.YOUR_TURN) return
        val g = geometry(state)
        val r = g.steps[state.step.coerceIn(0, g.steps.size - 1)]
        if (r.tighten) {
            drag = Drag.PULL
            dragAcc = 0f
            dragMove(Vec3(PULL_M * 1.1f, 0f, 0f))
            drag = Drag.NONE
            return
        }
        val route = g.frame(r.s1)
        val goal = r.s0 + (r.s1 - r.s0) * share.coerceIn(0f, 1f)
        drag = Drag.TIP
        var guard = 0
        while (ytS < goal - 0.5f && guard++ < 500) {
            val p = route.at(min(goal, ytS + 20f))
            pointerMm = p
            val (s, dist) = KnotGeometry.nearest(route, pointerMm, ytS, r.s1)
            if (s <= ytS || dist > ROUTE_REACH_MM) break
            ytS = min(s, ytS + MAX_JUMP_MM)
            if ((ytS - r.s0) / (r.s1 - r.s0) >= DONE_SHARE) {
                ytS = r.s1
                yourTurnComplete(SystemClock.elapsedRealtime())
                break
            }
        }
        drag = Drag.NONE
        KnotLog.i("YOURTURN debug tip share=${KnotLog.f((ytS - r.s0) / (r.s1 - r.s0))}")
    }

    override fun poseLine(): String {
        val h = head
        return "POSE hmd pos=${KnotLog.pos5(h.position)} ${KnotLog.quat5(h.rotation)} fwd=${KnotLog.pos5(h.forward)} valid=$hasHead"
    }

    override fun panelLines(): List<String> =
        rig?.all?.map { s ->
            "PANEL id=${s.id} shown=${s.shown} pos=${KnotLog.pos5(s.enginePosition())} ${KnotLog.quat5(s.engineRotation())} scale=${KnotLog.f(s.scale, 3)} size_dp=${s.widthDp.toInt()}x${s.heightDp.toInt()}"
        } ?: emptyList()

    override fun parkPanel(): String {
        follower.park(head, emptyList(), SystemClock.elapsedRealtime())
        return "FOLLOW guide park"
    }

    override fun setFpsLine(on: Boolean): String {
        fpsOn = on
        frames = 0
        fpsStartMs = 0L
        return "FPS ${if (on) "on" else "off"}"
    }

    /** The rope in Stage coordinates: frame pose, distances, diameter, the drawn centreline's ends and the step's timing. */
    override fun sceneJson(): JSONObject {
        val o = JSONObject()
        val state = ui
        val rope = volumePose
        o.put("volume", RopeWindow.state)
        o.put("rope", rope?.let { JSONObject().put("x", it.center.x).put("y", it.center.y).put("z", it.center.z).put("yawDeg", Math.toDegrees(it.yaw.toDouble())) } ?: JSONObject.NULL)
        o.put("head", JSONObject().put("x", head.position.x).put("y", head.position.y).put("z", head.position.z))
        if (rope != null) {
            val flat = Vec3(head.forward.x, 0f, head.forward.z).let { if (it.length < 0.1f) Vec3.AHEAD else it.normalized() }
            val to = rope.center - head.position
            o.put("headDistH", horizontalDistance(rope.center).toDouble())
            o.put("ahead", Vec3(to.x, 0f, to.z).dot(flat).toDouble())
            o.put("below", (head.position.y - rope.center.y).toDouble())
        }
        o.put("spinDeg", Math.toDegrees(wrap(spin).toDouble()))
        o.put("doneSpinDeg", Math.toDegrees(doneSpin.toDouble()))
        o.put("diameterMm", state.settings.drawMm)
        o.put("speed", state.settings.speed.toDouble())
        if (state.inKnot) {
            val g = geometry(state)
            val idx = state.step.coerceIn(0, g.steps.size - 1)
            val r = g.steps[idx]
            o.put("total", g.total.toDouble())
            o.put("stepS0", r.s0.toDouble())
            o.put("stepS1", r.s1.toDouble())
            o.put("stepMoveS", (moveSeconds(g, idx) / state.settings.speed).toDouble())
            o.put("stepCycleS", ((PAUSE_S + moveSeconds(g, idx) + HOLD_S) / state.settings.speed).toDouble())
            o.put("tipArc", lastTipArc.toDouble())
            o.put("yourTurnS", ytS.toDouble())
            o.put("clipFrom", clipFrom(g).toDouble())
            lastLine?.let { line ->
                val mirror = state.hand == Hand.LEFT
                fun stage(p: FloatArray): JSONArray {
                    if (rope == null) return JSONArray()
                    val sx = if (mirror) -1f else 1f
                    val v = rope.center + Vec3(0f, -state.knot.lowerMm / 1000f, 0f) + Quat4.axisAngle(Vec3.UP, rope.yaw + spin).rotate(Vec3(p[0] / 1000f * sx, p[1] / 1000f, p[2] / 1000f))
                    return JSONArray().put(v.x).put(v.y).put(v.z)
                }
                o.put("lineFirst", stage(line.at(clipOf(line))))
                o.put("lineTip", stage(line.at(line.tipArc)))
                o.put("lineLength", line.length.toDouble())
            }
        }
        guidePose?.let { (p, q) -> o.put("guidePanel", JSONObject().put("x", p.x).put("y", p.y).put("z", p.z).put("qx", q.x).put("qy", q.y).put("qz", q.z).put("qw", q.w).put("dist", p.distanceTo(head.position).toDouble())) }
        return o
    }

    fun headPose(): HeadPose = head

    companion object {
        private const val NEVER = Long.MIN_VALUE
        private const val REBIND_DELAY_MS = 250L
        private const val PANEL_HOLD_MS = 600L
        private const val CONTROLLER_DEBOUNCE_MS = 800L
        private const val GAZE_DEBOUNCE_MS = 1500L

        /** Demo rhythm (design `motion`): pause 0.5 s, move, hold 0.8 s; three loops. */
        private const val PAUSE_S = 0.5f
        private const val HOLD_S = 0.8f
        private const val LOOPS = 3
        private const val TIGHTEN_S = 2.0f
        private const val PREVIEW_START_S = 0.6f
        private const val MESH_MIN_MS = 33L
        private const val SLOW_MESH_MS = 60L
        private const val DONE_TURN_S = 12f

        /** Rope placement (design `spatialLayout.rope.where`, D14). */

        /** v0.1.4 rope volume: the height setting moves the rope frame by half its old step inside the volume. */
        private const val HEIGHT_NORMAL_M = 1.10f
        private const val HEIGHT_IN_VOLUME = 0.5f
        private const val POSE_SEND_M = 0.02f
        private val POSE_SEND_RAD = Math.toRadians(3.0).toFloat()
        private const val POSE_SETTLE_MS = 500L
        private const val PLACEHOLDER_M = 0.5f
        private const val SHADOW_BELOW_M = 0.25f
        private const val SEE_THROUGH_M = 0.25f
        private const val SEE_THROUGH_OPACITY = 0.3f
        private const val CLIP_TOP_MM = 170f
        private const val CLIP_SIDE_MM = 220f

        /** Out of sight (design S14). */
        private const val AWAY_DIST_M = 1.5f
        private const val AWAY_ANGLE_DEG = 90f
        private const val BACK_ANGLE_DEG = 60f
        private const val AWAY_MS = 2000L
        private const val AWAY_MORE_M = 1.0f

        /** Turning (design `input.rotate`). */
        private const val TURN_START_M = 0.03f
        private const val TURN_PER_90_M = 0.10f
        private val SNAP_RAD = Math.toRadians(12.0).toFloat()
        private val TURNED_RAD = Math.toRadians(0.5).toFloat()
        private const val SNAP_MS = 250f
        private const val TURN90_MS = 300f
        private const val STICK_DEAD_ZONE = 0.2f
        private val STICK_RATE_RAD_S = Math.toRadians(120.0).toFloat()
        private const val STICK_SMOOTH = 10f
        private const val STICK_SETTLE_MS = 300L

        /** "Your turn" (design `spatialLayout.rope.yourTurn`). */
        private const val TIP_GRAB_MM = 45f
        private const val MAX_JUMP_MM = 30f
        private const val ROUTE_REACH_MM = 80f
        private const val DONE_SHARE = 0.95f
        private const val PULL_M = 0.04f
        private const val YT_FLASH_MS = 600L

        /** Look-to-advance: 1.2 s. */
        private const val DWELL_MS = 1200f

        private const val POPUP_FORWARD_M = 0.06f

        /** The guide panel 28° to the hand side while a knot is open (D20). */
        private val PANEL_SIDE_RAD = Math.toRadians(28.0).toFloat()
        private const val CONE_MARGIN_M = 0.03f
        private const val CONE_STRIDE = 6
        private val CONE_SAMPLES = listOf(Vec3(0f, 0f, 0f), Vec3(0.09f, 0f, 0f), Vec3(-0.09f, 0f, 0f), Vec3(0f, 0.1f, 0f), Vec3(0f, -0.12f, 0f))
        private const val FLOOR_TAP_M = 16f

        /** Library: 0.9 m from the eyes, 10° below the line of sight (design `spatialLayout.panel.library`). */
        private val LIBRARY_OFFSET = Vec3(0f, -0.1563f, -0.8863f)

        fun wrap(a: Float): Float {
            var x = a
            val tau = 2f * PI.toFloat()
            while (x > PI.toFloat()) x -= tau
            while (x <= -PI.toFloat()) x += tau
            return x
        }

        @Suppress("unused") private fun keep() = cos(0f) + sin(0f)
    }
}
