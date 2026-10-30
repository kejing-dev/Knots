package io.embodit.knots.ui.knot

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.embodit.knots.domain.knot.KnotLibrary
import io.embodit.knots.domain.model.CurrentKnot
import io.embodit.knots.domain.model.KnotRecord
import io.embodit.knots.domain.model.Progress
import io.embodit.knots.domain.model.RopeHeight
import io.embodit.knots.domain.model.SavedPose
import io.embodit.knots.domain.model.Settings
import io.embodit.knots.domain.model.Vec3
import io.embodit.knots.platform.KnotGraph
import io.embodit.knots.platform.KnotLog
import io.embodit.knots.ui.knot.components.DemoClock
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The whole state machine of the app (design `states` S00 - S16). Events in, [KnotUiState] out; the scene renders
 * the state (and drives the demo motion per frame), the panels read it. Progress and settings are written to the
 * repository on every change (design: all offline, local JSON).
 */
class KnotViewModel(private val appContext: Context) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = KnotViewModel(context.applicationContext) as T
    }

    private val repository = KnotGraph.repository(appContext)
    private val debuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<KnotUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<KnotUiEffect>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: SharedFlow<KnotUiEffect> = _effects

    private var loadJob: Job? = null
    private var lastStateId = ""

    /** Debug: knots.json "fails to load" (S15). */
    private var dataBroken = false

    private fun initialState(): KnotUiState {
        val settings = repository.loadSettings(defaultDwell = !debuggable)
        val progress = repository.loadProgress()
        // the anchor of the last session comes back with the app (the user moves it only by putting a new marker)
        val rope = progress.ropePose?.let { RopePose(Vec3(it.x, it.y, it.z), it.yaw) }
        val marker = rope?.let { Vec3(it.center.x, 0f, it.center.z) }
        return KnotUiState(settings = settings, progress = progress, rope = rope, marker = marker)
    }

    init {
        KnotLog.i("STATE S00 (init) settings=${s.settings} current=${s.progress.current}")
        scheduleLeaveLoading(LOADING_MIN_MS)
    }

    private val s: KnotUiState
        get() = _state.value

    private fun update(block: KnotUiState.() -> KnotUiState) {
        _state.value = _state.value.block()
        val id = s.stateId
        if (id != lastStateId) {
            lastStateId = id
            KnotLog.i("STATE $id knot=${s.knot.id} step=${s.step + 1}/${s.knot.steps.size} rope=${s.rope != null} hasRope=${s.settings.hasRope}")
        }
    }

    fun onEvent(event: KnotEvent) {
        when (event) {
            KnotEvent.SceneReady -> {
                update { copy(sceneReady = true) }
                if (s.mode == Mode.LOADING && !s.hold) scheduleLeaveLoading(LOADING_MIN_MS / 2)
            }
            is KnotEvent.RopePlaced -> {
                val restart = s.placing || s.rope == null
                update { copy(rope = event.pose, outOfSight = null, placing = false) }
                // the preview starts once the rope is at its anchor
                if (restart && s.mode == Mode.PREVIEW) update { copy(stepSerial = stepSerial + 1, playedOnce = false) }
                val c = event.pose.center
                saveProgress(s.progress.copy(ropePose = SavedPose(c.x, c.y, c.z, event.pose.yaw)))
            }
            is KnotEvent.DemoPlayed -> if (event.serial == s.stepSerial && !s.playedOnce) update { copy(playedOnce = true) }
            is KnotEvent.DemoFinished ->
                if (event.serial == s.stepSerial && s.mode == Mode.STEP && s.phase == Phase.DEMO && !s.hold) {
                    if (s.settings.hasRope) update { copy(phase = Phase.HOLD, playedOnce = true) }
                    else {
                        KnotLog.i("YOURTURN enter ${s.knot.id} ${s.step + 1}")
                        update { copy(mode = Mode.YOUR_TURN, playedOnce = true, stepSerial = stepSerial + 1) }
                    }
                }
            is KnotEvent.YourTurnDone ->
                if (event.serial == s.stepSerial && s.mode == Mode.YOUR_TURN) {
                    KnotLog.i("YOURTURN done ${s.knot.id} ${s.step + 1}")
                    update { copy(mode = Mode.STEP, phase = Phase.HOLD, playedOnce = true, stepSerial = stepSerial + 1, yourTurnGhost = null) }
                }
            is KnotEvent.ControllerChanged -> if (event.active != s.controllerActive) update { copy(controllerActive = event.active) }
            is KnotEvent.DwellFired ->
                if (event.serial == s.stepSerial && s.dwellArmed) {
                    KnotLog.i("DWELL advance serial=${event.serial}")
                    next()
                }
            is KnotEvent.TurnedChanged -> if (event.turned != s.turned) update { copy(turned = event.turned) }
            is KnotEvent.OutOfSight -> if (event.where != s.outOfSight) update { copy(outOfSight = event.where) }
            is KnotEvent.GazeSource -> if (event.fromHead != s.gazeFromHead) update { copy(gazeFromHead = event.fromHead) }
            is KnotEvent.MarkerPlaced ->
                if (s.placing) {
                    KnotLog.i("MARKER placed at ${KnotLog.pos(event.point)}")
                    update { copy(marker = event.point) }
                }
            KnotEvent.PlaceMarkerClicked -> update { copy(placing = true, menuOpen = false, settingsOpen = false, outOfSight = null) }
            KnotEvent.MoveToMarkerClicked -> s.marker?.let { m ->
                update { copy(menuOpen = false) }
                _effects.tryEmit(KnotUiEffect.MoveToMarker(m))
            }
            KnotEvent.PlacingCancelled -> if (s.rope != null) update { copy(placing = false) } else goLibrary()
            is KnotEvent.HasRopePicked -> saveSettings(s.settings.copy(hasRope = event.has))
            is KnotEvent.RopeSizePicked -> saveSettings(s.settings.copy(ropeMm = event.mm, hasRope = true))
            KnotEvent.WelcomeDone -> {
                val first = !s.settings.welcomeSeen
                saveSettings(s.settings.copy(welcomeSeen = true))
                update { copy(mode = Mode.LIBRARY) }
                if (first) _effects.tryEmit(KnotUiEffect.AskEyePermission)
            }
            is KnotEvent.CategoryPicked -> update { copy(category = event.category) }
            is KnotEvent.KnotPicked -> pickKnot(event.id)
            KnotEvent.FollowClicked -> enterStep(0)
            KnotEvent.PreviewAgainClicked -> update { copy(mode = Mode.PREVIEW, stepSerial = stepSerial + 1, playedOnce = false, ghost = null) }
            KnotEvent.SettingsClicked -> update { copy(settingsOpen = !settingsOpen, confirmClear = false, menuOpen = false) }
            KnotEvent.BackClicked -> goLibrary()
            KnotEvent.NextClicked -> next()
            KnotEvent.PrevClicked -> if ((s.mode == Mode.STEP || s.mode == Mode.YOUR_TURN) && s.step > 0) enterStep(s.step - 1)
            KnotEvent.ReplayClicked -> if (s.mode == Mode.STEP || s.mode == Mode.YOUR_TURN) enterStep(s.step)
            KnotEvent.MyViewClicked -> _effects.tryEmit(KnotUiEffect.ResetTurn)
            KnotEvent.Turn90Clicked -> _effects.tryEmit(KnotUiEffect.Turn(90f))
            KnotEvent.PlayPauseClicked -> playPause()
            is KnotEvent.Scrubbed -> scrubTo(event.share)
            KnotEvent.SpeedClicked -> {
                val next = when (s.settings.speed) { 0.5f -> 1f; 1f -> 1.5f; else -> 0.5f }
                saveSettings(s.settings.copy(speed = next))
                KnotLog.i("SPEED $next")
            }
            KnotEvent.MoreClicked -> update { copy(menuOpen = !menuOpen, settingsOpen = false, confirmClear = false) }
            KnotEvent.MenuBringHere -> {
                update { copy(menuOpen = false) }
                s.marker?.let { _effects.tryEmit(KnotUiEffect.MoveToMarker(it)) } ?: update { copy(placing = true) }
            }
            KnotEvent.MenuHideRope -> update { copy(menuOpen = false, ropeHidden = !ropeHidden) }
            KnotEvent.MenuRestart -> enterStep(0)
            KnotEvent.MenuSettings -> update { copy(menuOpen = false, settingsOpen = true, confirmClear = false) }
            KnotEvent.MenuHome -> goLibrary()
            KnotEvent.AgainClicked -> enterStep(0)
            KnotEvent.ResumeClicked -> {
                val cur = s.progress.current
                val knot = cur?.let { KnotLibrary.knot(it.knotId) }
                if (cur == null || knot == null) return goLibrary()
                update { copy(knotId = knot.id, ropeHidden = false, placing = rope == null) }
                enterStep(cur.step.coerceIn(0, knot.steps.size - 1))
                // design S13: back to that step, held at its end
                update { copy(phase = Phase.HOLD, playedOnce = true) }
            }
            KnotEvent.RetryClicked -> {
                update { copy(mode = Mode.LOADING) }
                scheduleLeaveLoading(LOADING_MIN_MS)
            }
            KnotEvent.BringHereClicked -> update { copy(outOfSight = null, placing = true) }
            is KnotEvent.LanguageSelected -> saveSettings(s.settings.copy(language = event.language))
            is KnotEvent.HandSelected -> saveSettings(s.settings.copy(hand = event.hand))
            is KnotEvent.HeightSelected -> setHeight(event.height)
            is KnotEvent.DwellSelected -> saveSettings(s.settings.copy(dwell = event.on))
            KnotEvent.ClearClicked -> update { copy(confirmClear = true) }
            KnotEvent.ClearCancelled -> update { copy(confirmClear = false) }
            KnotEvent.ClearConfirmed -> {
                saveProgress(Progress(ropePose = s.progress.ropePose))
                update { copy(confirmClear = false) }
                KnotLog.i("CLEAR progress")
            }
            KnotEvent.SettingsDismissed -> if (s.settingsOpen) update { copy(settingsOpen = false, confirmClear = false) }
            KnotEvent.WentBackground -> Unit
            KnotEvent.CameForeground -> Unit
            is KnotEvent.DebugState -> debugState(event.spec)
            is KnotEvent.DebugKnot -> if (KnotLibrary.knot(event.id) != null) {
                update { copy(knotId = event.id, step = 0) }
                if (s.mode == Mode.STEP || s.mode == Mode.DONE || s.mode == Mode.YOUR_TURN) enterStep(0)
                else if (s.mode == Mode.PREVIEW) update { copy(stepSerial = stepSerial + 1) }
            }
            is KnotEvent.DebugStep -> {
                debugAnchor()
                enterStep((event.step - 1).coerceIn(0, s.knot.steps.size - 1))
            }
            is KnotEvent.DebugGhost -> update { copy(ghost = event.share) }
            is KnotEvent.DebugYourTurn -> update { copy(yourTurnGhost = event.share) }
            is KnotEvent.DebugDwell -> saveSettings(s.settings.copy(dwell = event.on))
            is KnotEvent.DebugDataError -> dataBroken = event.on
            is KnotEvent.DebugMarker -> {
                update { copy(placing = true, marker = event.point) }
                if (event.move) _effects.tryEmit(KnotUiEffect.MoveToMarker(event.point))
            }
            KnotEvent.DebugClear -> {
                saveProgress(Progress())
                saveSettings(Settings(dwell = !debuggable))
                update { copy(mode = Mode.WELCOME, rope = null, marker = null, placing = false, menuOpen = false, settingsOpen = false, confirmClear = false, phase = Phase.DEMO, hold = false) }
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    private fun scheduleLeaveLoading(afterMs: Long) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                delay(afterMs)
                if (s.mode == Mode.LOADING && !s.hold && s.sceneReady) leaveLoading()
            }
    }

    /** S00 -> S15 / S13 / S01 / S02. */
    private fun leaveLoading() {
        loadJob?.cancel()
        val ok = !dataBroken && KnotLibrary.knots.size == KnotLibrary.order.size
        val next =
            when {
                !ok -> Mode.DATA_ERROR
                s.progress.current?.let { KnotLibrary.knot(it.knotId) } != null -> Mode.RESUME
                !s.settings.welcomeSeen -> Mode.WELCOME
                else -> Mode.LIBRARY
            }
        update { copy(mode = next, hold = false) }
    }

    /**
     * T3: S02 -> S03. The rope stays at the session's anchor (factory rule D1: it moves only when the user puts a new
     * marker); with no anchor yet, S17 asks for a marker first.
     */
    private fun pickKnot(id: String) {
        val k = KnotLibrary.knot(id) ?: return
        KnotLog.i("PICK ${k.id} steps=${k.steps.size} anchor=${s.rope != null}")
        update { copy(knotId = k.id, step = 0, mode = Mode.PREVIEW, phase = Phase.DEMO, stepSerial = stepSerial + 1, playedOnce = false, settingsOpen = false, menuOpen = false, ropeHidden = false, ghost = null, turned = false, placing = rope == null) }
        _effects.tryEmit(KnotUiEffect.ResetTurn)
    }

    private fun enterStep(index: Int) {
        val n = index.coerceIn(0, s.knot.steps.size - 1)
        update { copy(mode = Mode.STEP, phase = Phase.DEMO, step = n, stepSerial = stepSerial + 1, playedOnce = false, menuOpen = false, settingsOpen = false, confirmClear = false, ghost = null, yourTurnGhost = null) }
        saveProgress(s.progress.copy(current = CurrentKnot(s.knot.id, n)))
        KnotLog.i("STEP ${s.knot.id} ${n + 1}/${s.knot.steps.size} type=${s.currentStep.type} serial=${s.stepSerial}")
    }

    private fun next() {
        if (s.mode != Mode.STEP && s.mode != Mode.YOUR_TURN) return
        if (s.lastStep) {
            val id = s.knot.id
            val old = s.progress.records[id] ?: KnotRecord()
            val rec = KnotRecord(learned = true, learnedAt = System.currentTimeMillis(), times = old.times + 1)
            saveProgress(s.progress.copy(records = s.progress.records + (id to rec), current = null))
            update { copy(mode = Mode.DONE, menuOpen = false, settingsOpen = false, stepSerial = stepSerial + 1) }
            KnotLog.i("DONE $id times=${rec.times}")
        } else {
            enterStep(s.step + 1)
        }
    }

    private fun playPause() {
        if (s.mode != Mode.STEP) return
        if (s.phase == Phase.SCRUB) {
            val step = stepOf(s.scrub)
            enterStep(step)
        } else {
            val share = DemoClock.share.floatValue
            update { copy(phase = Phase.SCRUB, scrub = share) }
            KnotLog.i("SCRUB pause at ${KnotLog.f(share, 3)}")
        }
    }

    private fun scrubTo(share: Float) {
        if (s.mode != Mode.STEP) return
        val v = share.coerceIn(0f, 1f)
        val step = stepOf(v)
        update { copy(phase = Phase.SCRUB, scrub = v, step = step) }
        if (s.progress.current?.step != step) saveProgress(s.progress.copy(current = CurrentKnot(s.knot.id, step)))
    }

    /** The step that whole-knot share [v] falls in (each step owns an equal share of the bar). */
    private fun stepOf(v: Float): Int {
        val n = s.knot.steps.size
        return (v * n).toInt().coerceIn(0, n - 1)
    }

    /** T22: only the rope frame's height moves; its place and yaw stay. */
    private fun setHeight(height: RopeHeight) {
        val old = s.settings.height
        saveSettings(s.settings.copy(height = height))
        val rope = s.rope ?: return
        val moved = RopePose(rope.center + Vec3(0f, height.aboveM - old.aboveM, 0f), rope.yaw)
        onEvent(KnotEvent.RopePlaced(moved))
        KnotLog.i("HEIGHT ${height.id} ropeY=${KnotLog.f(moved.center.y, 3)}")
    }

    private fun goLibrary() {
        update { copy(mode = Mode.LIBRARY, menuOpen = false, settingsOpen = false, confirmClear = false, phase = Phase.DEMO, ghost = null, outOfSight = null) }
    }

    private fun saveSettings(settings: Settings) {
        update { copy(settings = settings) }
        repository.saveSettings(settings)
    }

    private fun saveProgress(progress: Progress) {
        update { copy(progress = progress) }
        repository.saveProgress(progress)
    }

    // ------------------------------------------------------------------------------------------
    // debug

    /** Straight to a designed state: `S00 [hold]`, `S04 bowline 2`, `S16 [knot] [step]`, ... (see DebugCommands). */
    private fun debugState(spec: String) {
        val parts = spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val name = parts.firstOrNull()?.uppercase(Locale.US) ?: return
        val args = parts.drop(1).map { it.lowercase(Locale.US) }
        val hold = "hold" in args
        loadJob?.cancel()
        val knotArg = args.firstOrNull { KnotLibrary.knot(it) != null }
        val stepArg = args.firstNotNullOfOrNull { it.toIntOrNull() }
        if (knotArg != null) update { copy(knotId = knotArg) }
        if (stepArg != null) update { copy(step = (stepArg - 1).coerceIn(0, knot.steps.size - 1)) }
        fun needRope() = debugAnchor()
        update { copy(menuOpen = false, settingsOpen = false, confirmClear = false, hold = hold, ghost = null, yourTurnGhost = null, outOfSight = null, placing = false) }
        when (name) {
            "S00" -> update { copy(mode = Mode.LOADING) }
            "S01" -> update { copy(mode = Mode.WELCOME) }
            "S02" -> goLibrary()
            "S03" -> {
                needRope()
                update { copy(mode = Mode.PREVIEW, stepSerial = stepSerial + 1, playedOnce = false) }
            }
            "S04", "S05", "S06", "S08", "S09" -> {
                if (knotArg == null && stepArg == null) update { copy(knotId = "bowline", step = if (name == "S05") 3 else 1) }
                needRope()
                enterStep(s.step)
                when (name) {
                    "S05" -> update { copy(phase = Phase.HOLD, playedOnce = true) }
                    "S06" -> scrubTo(0.6f)
                    "S08" -> _effects.tryEmit(KnotUiEffect.Turn(180f))
                }
                if (hold) update { copy(hold = true) }
            }
            "S16" -> {
                if (knotArg == null && stepArg == null) update { copy(knotId = "bowline", step = 1) }
                needRope()
                enterStep(s.step)
                update { copy(mode = Mode.YOUR_TURN, playedOnce = true, stepSerial = stepSerial + 1, hold = hold) }
            }
            "S07" -> {
                if (knotArg == null) update { copy(knotId = "reef") }
                needRope()
                update { copy(step = knot.steps.size - 1, mode = Mode.STEP) }
                next()
            }
            "S10" -> {
                if (!s.inKnot) {
                    needRope()
                    enterStep(s.step)
                    update { copy(phase = Phase.HOLD, playedOnce = true) }
                }
                update { copy(menuOpen = true) }
            }
            "S11" -> {
                if (s.mode == Mode.LOADING || s.mode == Mode.WELCOME) update { copy(mode = Mode.LIBRARY) }
                update { copy(settingsOpen = true) }
            }
            "S12" -> {
                if (s.mode == Mode.LOADING || s.mode == Mode.WELCOME) update { copy(mode = Mode.LIBRARY) }
                update { copy(settingsOpen = true, confirmClear = true) }
            }
            "S13" -> {
                val k = knotArg ?: "bowline"
                val st = (stepArg ?: 3) - 1
                saveProgress(s.progress.copy(current = CurrentKnot(k, st)))
                update { copy(mode = Mode.RESUME, knotId = k, step = st) }
            }
            "S14" -> {
                if (!s.inKnot) {
                    needRope()
                    enterStep(s.step)
                }
                update { copy(outOfSight = "behind", hold = true) }
            }
            "S15" -> update { copy(mode = Mode.DATA_ERROR) }
            "S17" -> {
                if (knotArg == null) update { copy(knotId = "figure8") }
                update { copy(mode = Mode.PREVIEW, placing = true, stepSerial = stepSerial + 1, hold = true) }
            }
            else -> KnotLog.i("CMD state unknown $name")
        }
    }

    /** Debug: with no anchor yet, a marker at a fixed Stage point 1.3 m ahead of the Stage origin and the rope moved there. */
    private fun debugAnchor() {
        if (s.rope != null) return
        val m = Vec3(0f, 0f, -1.3f)
        update { copy(marker = m) }
        _effects.tryEmit(KnotUiEffect.MoveToMarker(m))
    }

    /** `DUMP {...}`: the state for QA. */
    fun dumpJson(): String {
        val st = s
        val o = JSONObject()
        o.put("state", st.stateId)
        o.put("mode", st.mode.name)
        o.put("phase", st.phase.name)
        o.put("knot", st.knot.id)
        o.put("step", st.step + 1)
        o.put("steps", st.knot.steps.size)
        o.put("type", st.currentStep.type.name)
        o.put("serial", st.stepSerial)
        o.put("playedOnce", st.playedOnce)
        o.put("scrub", st.scrub.toDouble())
        o.put("category", st.category.id)
        o.put("menu", st.menuOpen)
        o.put("settings", st.settingsOpen)
        o.put("confirm", st.confirmClear)
        o.put("controller", st.controllerActive)
        o.put("turned", st.turned)
        o.put("outOfSight", st.outOfSight ?: JSONObject.NULL)
        o.put("ropeHidden", st.ropeHidden)
        o.put("placing", st.placing)
        st.marker?.let { m -> o.put("marker", org.json.JSONArray().put(m.x).put(m.y).put(m.z)) }
        o.put("gazeFromHead", st.gazeFromHead)
        o.put("ghost", st.ghost ?: JSONObject.NULL)
        o.put("hasRope", st.settings.hasRope)
        o.put("ropeMm", st.settings.drawMm)
        o.put("height", st.settings.height.id)
        o.put("speed", st.settings.speed.toDouble())
        o.put("lang", st.settings.language.name)
        o.put("hand", st.settings.hand.name)
        o.put("dwell", st.settings.dwell)
        o.put("learned", KnotLibrary.order.filter { st.progress.learned(it) })
        st.progress.current?.let { c -> o.put("current", JSONObject().put("knot", c.knotId).put("step", c.step + 1)) }
        return o.toString()
    }

    private companion object {
        const val LOADING_MIN_MS = 1100L
    }
}

