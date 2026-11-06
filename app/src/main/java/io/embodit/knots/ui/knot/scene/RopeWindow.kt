package io.embodit.knots.ui.knot.scene

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import io.embodit.knots.domain.LevelGate
import io.embodit.knots.platform.KnotLog

const val ROPE_VOLUME_ID = "RopeVolume"
const val ROPE_VOLUME_TAG = "rope"

/**
 * v0.1.4 (user 2026-11-05, factory D21): the demo rope lives in a system Volumetric window, so the system draws the
 * window bar under it and moving / closing it is the system's (the app's own 3D handle bar is gone). Process-level, so
 * the Stage (which wants the window while a knot is open) and the window (which reports its lifecycle) share it.
 *
 * Every open waits for a level head ([LevelGate], D9): the system opens a window square to the line of sight.
 */
object RopeWindow {
    /** The Stage's navigator while the Stage is composed. */
    var navigator: SpatialNavigator? = null

    /** Lifecycle of the window as it reports it: "never", then ON_CREATE … ON_DESTROY. */
    var state by mutableStateOf("never")
        private set

    /** "平视前方 / Look straight ahead": an open waits and the head is not level. */
    var levelHint by mutableStateOf(false)
        private set

    /** The system closed the window (×) while a knot was open and no recall was under way. */
    var onClosedByUser: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val gate = LevelGate()
    private var wanted = false
    private var recallPending = false
    private var lastStepMs = 0L

    val open: Boolean
        get() = state != "never" && state != "ON_DESTROY"

    /** The Stage wants the window (a knot is open) or not; called on every render, acts on changes only. */
    fun want(on: Boolean, why: String) {
        if (on == wanted) return
        wanted = on
        if (on) {
            if (!open) ask(why)
        } else {
            gate.cancel()
            levelHint = false
            recallPending = false
            if (open) close("unwanted/$why")
        }
    }

    /** "叫回面前": a window cannot be moved by the app, so close it and open it again once it reported ON_DESTROY. */
    fun recall(why: String) {
        if (!wanted) return
        if (!open) return ask(why)
        recallPending = true
        KnotLog.i("VOLUME recall why=$why: close, then open after ON_DESTROY")
        close("recall")
    }

    /** One Stage frame: [pitchDeg] = head pitch (+ up), null while the head pose is not valid yet. */
    fun stepGate(now: Long, pitchDeg: Float?) {
        lastStepMs = now
        if (!gate.waiting) {
            if (levelHint) levelHint = false
            return
        }
        val hint = gate.hintWanted(pitchDeg)
        if (hint != levelHint) {
            levelHint = hint
            KnotLog.i(String.format(java.util.Locale.US, "LEVEL hint=%s pitch=%.1f waited=%d ms", hint, pitchDeg ?: Float.NaN, gate.waitedMs(now)))
        }
        val why = gate.step(now, pitchDeg) ?: return
        levelHint = false
        KnotLog.i(String.format(java.util.Locale.US, "LEVEL ok pitch=%.1f -> open", pitchDeg ?: Float.NaN))
        openNow(why)
    }

    fun onLifecycle(event: String) {
        state = event
        KnotLog.i("VOLUME lifecycle $event")
        if (event != "ON_DESTROY") return
        when {
            recallPending -> {
                recallPending = false
                main.postDelayed({ if (wanted) ask("recall") }, 300)
            }
            wanted -> {
                wanted = false
                KnotLog.i("VOLUME closed by the system window bar")
                onClosedByUser?.invoke()
            }
        }
    }

    /** Quit: close the window first (it would otherwise outlive the Stage in the Shared Space). */
    fun closeForQuit() {
        wanted = false
        recallPending = false
        gate.cancel()
        levelHint = false
        if (open) close("quit")
    }

    private fun ask(why: String) {
        KnotLog.i("VOLUME ask why=$why state=$state (opens when the head is level)")
        gate.ask(why, SystemClock.elapsedRealtime())
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
    }

    /** No Stage frames for a while: do not hold the window back (headset cold start: frames start ~6 s late). */
    private val watchdog =
        object : Runnable {
            override fun run() {
                if (!gate.waiting) return
                val now = SystemClock.elapsedRealtime()
                val stalled = if (lastStepMs == 0L) gate.waitedMs(now) > FIRST_FRAME_MS else now - lastStepMs > WATCHDOG_MS
                if (stalled) {
                    val why = gate.pending ?: return
                    gate.cancel()
                    levelHint = false
                    openNow("$why/noframes")
                } else main.postDelayed(this, WATCHDOG_MS)
            }
        }

    private fun openNow(why: String) {
        val nav = navigator ?: return KnotLog.i("VOLUME open why=$why: no navigator")
        KnotLog.i("VOLUME open why=$why state=$state")
        runCatching { nav.openWindowContainer(ROPE_VOLUME_ID, ROPE_VOLUME_TAG) }.onFailure { KnotLog.i("VOLUME open failed: $it") }
    }

    private fun close(why: String) {
        KnotLog.i("VOLUME close why=$why")
        runCatching { navigator?.closeWindowContainer(ROPE_VOLUME_ID, ROPE_VOLUME_TAG) }.onFailure { KnotLog.i("VOLUME close failed: $it") }
    }

    private const val WATCHDOG_MS = 3000L
    private const val FIRST_FRAME_MS = 15000L
}

/**
 * The rope volume's content: one SpatialView the Stage's [KnotScene] moves the rope's entity tree into. Pinch-drags on
 * the rope (turning, "your turn") arrive here; moving the whole rope is the system window bar's job.
 */
@Composable
fun RopeVolume() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> RopeWindow.onLifecycle(e.name) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // this window's frames drive the scene while it is open (the Stage's slow down when it loses focus). Never
    // unregistered here: unregistering is per class for the whole app and would stop the Stage's frames too; the
    // window's scene goes away with the window
    DisposableEffect(Unit) {
        registerSystem<KnotFrameSystem>()
        onDispose { ActiveScene.scene?.detachVolume() }
    }
    SpatialView(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectSpatialDragGesture(
                        context = context,
                        targetedToEntity = TargetEntity.any(),
                        onDragStart = { start, value -> ActiveScene.scene?.onSpatialDrag("start", start, value, inVolume = true) },
                        onDragEnd = { value -> ActiveScene.scene?.onSpatialDrag("end", null, value, inVolume = true) },
                        onDragCancel = { value -> ActiveScene.scene?.onSpatialDrag("cancel", null, value, inVolume = true) },
                    ) { value ->
                        ActiveScene.scene?.onSpatialDrag("move", null, value, inVolume = true)
                    }
                },
        initial = { content, _ -> ActiveScene.scene?.attachVolume(content) ?: KnotLog.i("VOLUME content: no scene") },
    )
}
