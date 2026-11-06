package io.embodit.knots.ui.knot

import io.embodit.knots.domain.knot.Category
import io.embodit.knots.domain.knot.Knot
import io.embodit.knots.domain.knot.KnotLibrary
import io.embodit.knots.domain.knot.KnotStep
import io.embodit.knots.domain.knot.StepType
import io.embodit.knots.domain.model.Hand
import io.embodit.knots.domain.model.LanguagePref
import io.embodit.knots.domain.model.Progress
import io.embodit.knots.domain.model.RopeHeight
import io.embodit.knots.domain.model.Settings
import io.embodit.knots.domain.model.Vec3

/** Base states of the design package (S08 turned, S09 controller hint, S10 menu, S11 settings, S12 confirm, S14 out of sight are overlays). */
enum class Mode {
    LOADING, // S00
    WELCOME, // S01
    LIBRARY, // S02
    PREVIEW, // S03
    STEP, // S04 demonstrating / S05 holding / S06 scrubbing (by [Phase])
    YOUR_TURN, // S16
    DONE, // S07
    RESUME, // S13
    DATA_ERROR, // S15
}

/** Within a step: the demo loops (S04), stops at the step's end (S05), or the user scrubs / paused (S06). */
enum class Phase { DEMO, HOLD, SCRUB }

/**
 * The rope frame (design `spatialLayout.rope.where`): origin in Stage metres and its yaw; x = the user's right hand,
 * y up, z towards the user. World fixed after placement.
 */
data class RopePose(val center: Vec3, val yaw: Float)

data class KnotUiState(
    val mode: Mode = Mode.LOADING,
    val phase: Phase = Phase.DEMO,
    val knotId: String = "figure8",
    val category: Category = Category.ALL,
    val step: Int = 0,
    /** Whole-knot progress 0..1 while scrubbing / paused (S06). */
    val scrub: Float = 0f,
    val rope: RopePose? = null,
    /** Bumped on every step entry, replay and preview restart: the scene restarts the demo, the dwell count resets. */
    val stepSerial: Int = 0,
    /** The demo has played this step once (gates look-to-advance). */
    val playedOnce: Boolean = false,
    val menuOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val confirmClear: Boolean = false,
    /** S18: settings' "quit app" asks once more in the same panel (factory D26). */
    val confirmExit: Boolean = false,
    val settings: Settings = Settings(),
    val progress: Progress = Progress(),
    /** A controller delivers poses: S09 hint on the step panel. */
    val controllerActive: Boolean = false,
    /** The demo rope is turned away from the user's view (S08): the replay key becomes "back to my view". */
    val turned: Boolean = false,
    /** S14: the rope is out of sight ("behind" / "left" / "right"); the panel lazy-follows with "bring it here". */
    val outOfSight: String? = null,
    /** The floor marker (Stage metres, on the floor) the rope goes to with "move to marker" (factory rule D1). */
    val marker: Vec3? = null,
    /** S17: placing the marker (controller ray / gaze + pinch on the floor), then "move to marker". */
    val placing: Boolean = false,
    /** ⋯ "hide the demo rope" (T26). */
    val ropeHidden: Boolean = false,
    /** Eye tracking is not available: look-to-advance uses the head's direction (panel hint). */
    val gazeFromHead: Boolean = true,
    val sceneReady: Boolean = false,
    /** Debug: freeze the demo at this share of the step (0..1). */
    val ghost: Float? = null,
    /** Debug: freeze "your turn" at this share. */
    val yourTurnGhost: Float? = null,
    /** Debug: `state Sxx hold` keeps a timed state from moving on. */
    val hold: Boolean = false,
) {
    val knot: Knot
        get() = KnotLibrary.knot(knotId) ?: KnotLibrary.knots.first()

    val currentStep: KnotStep
        get() = knot.steps[step.coerceIn(0, knot.steps.size - 1)]

    val lastStep: Boolean
        get() = step >= knot.steps.size - 1

    val tightenStep: Boolean
        get() = currentStep.type == StepType.TIGHTEN

    /** The rope is shown and the user is learning a knot (S03 - S07, S16). */
    val inKnot: Boolean
        get() = mode == Mode.PREVIEW || mode == Mode.STEP || mode == Mode.YOUR_TURN || mode == Mode.DONE

    /** Pinch-drag on the demo rope turns it (T10): with a rope only on S03 / S07 (hands hold the real rope otherwise). */
    val pinchTurnAllowed: Boolean
        get() = inKnot && (!settings.hasRope || mode == Mode.PREVIEW || mode == Mode.DONE)

    /** The designed state id the screen is in (for logs and QA). */
    val stateId: String
        get() =
            when {
                settingsOpen && confirmClear -> "S12"
                settingsOpen && confirmExit -> "S18"
                settingsOpen -> "S11"
                menuOpen -> "S10"
                placing && inKnot -> "S17"
                outOfSight != null && inKnot -> "S14"
                else ->
                    when (mode) {
                        Mode.LOADING -> "S00"
                        Mode.WELCOME -> "S01"
                        Mode.LIBRARY -> "S02"
                        Mode.PREVIEW -> "S03"
                        Mode.STEP ->
                            when (phase) {
                                Phase.DEMO -> "S04"
                                Phase.HOLD -> "S05"
                                Phase.SCRUB -> "S06"
                            }
                        Mode.YOUR_TURN -> "S16"
                        Mode.DONE -> "S07"
                        Mode.RESUME -> "S13"
                        Mode.DATA_ERROR -> "S15"
                    }
            }

    /** Look-to-advance applies (design `input.dwell`): "Next" only, not "Done", once the demo has played, not while scrubbing or in "your turn". */
    val dwellArmed: Boolean
        get() = settings.dwell && mode == Mode.STEP && phase != Phase.SCRUB && !lastStep && playedOnce && !menuOpen && !settingsOpen && outOfSight == null && !placing

    val language: LanguagePref
        get() = settings.language

    val hand: Hand
        get() = settings.hand

    val height: RopeHeight
        get() = settings.height
}

/** Every input of the ViewModel: scene callbacks, panel controls, lifecycle and the debug hooks. */
sealed class KnotEvent {
    // scene
    data object SceneReady : KnotEvent()

    /** The rope frame was put over the marker ("move to marker") or moved with the handle (T25): the session's anchor. */
    data class RopePlaced(val pose: RopePose) : KnotEvent()

    data class DemoPlayed(val serial: Int) : KnotEvent()

    /** The step's demo looped three times (S04 -> S05 / S16), or the preview played once. */
    data class DemoFinished(val serial: Int) : KnotEvent()

    /** "Your turn" reached 95 %: S16 -> S05. */
    data class YourTurnDone(val serial: Int) : KnotEvent()

    data class ControllerChanged(val active: Boolean) : KnotEvent()

    data class DwellFired(val serial: Int) : KnotEvent()

    data class TurnedChanged(val turned: Boolean) : KnotEvent()

    data class OutOfSight(val where: String?) : KnotEvent()

    data class GazeSource(val fromHead: Boolean) : KnotEvent()

    /** A pinch / trigger on the floor while placing: the marker goes there. */
    data class MarkerPlaced(val point: Vec3) : KnotEvent()

    // panels
    data class HasRopePicked(val has: Boolean) : KnotEvent()

    data class RopeSizePicked(val mm: Int) : KnotEvent()

    data object WelcomeDone : KnotEvent()

    data class CategoryPicked(val category: Category) : KnotEvent()

    data class KnotPicked(val id: String) : KnotEvent()

    data object FollowClicked : KnotEvent()

    data object PreviewAgainClicked : KnotEvent()

    data object SettingsClicked : KnotEvent()

    data object BackClicked : KnotEvent()

    data object NextClicked : KnotEvent()

    data object PrevClicked : KnotEvent()

    data object ReplayClicked : KnotEvent()

    data object MyViewClicked : KnotEvent()

    data object Turn90Clicked : KnotEvent()

    data object PlayPauseClicked : KnotEvent()

    data class Scrubbed(val share: Float) : KnotEvent()

    data object SpeedClicked : KnotEvent()

    data object MoreClicked : KnotEvent()

    data object MenuBringHere : KnotEvent()

    /** "Put a new marker" (menu, S14, first knot). */
    data object PlaceMarkerClicked : KnotEvent()

    /** "Move to marker". */
    data object MoveToMarkerClicked : KnotEvent()

    data object PlacingCancelled : KnotEvent()

    data object MenuHideRope : KnotEvent()

    data object MenuRestart : KnotEvent()

    data object MenuSettings : KnotEvent()

    data object MenuHome : KnotEvent()

    data object AgainClicked : KnotEvent()

    data object ResumeClicked : KnotEvent()

    data object RetryClicked : KnotEvent()

    data object BringHereClicked : KnotEvent()

    data class LanguageSelected(val language: LanguagePref) : KnotEvent()

    data class HandSelected(val hand: Hand) : KnotEvent()

    data class HeightSelected(val height: RopeHeight) : KnotEvent()

    data class DwellSelected(val on: Boolean) : KnotEvent()

    data object ClearClicked : KnotEvent()

    data object ClearConfirmed : KnotEvent()

    data object ClearCancelled : KnotEvent()

    data object SettingsDismissed : KnotEvent()

    /** v0.1.4: the rope's volume window went away (closed, recalled): no rope until it reports again. */
    data object RopeGone : KnotEvent()

    /** v0.1.4: the user closed the rope's volume window with the system bar's ×: back to the library. */
    data object RopeWindowClosed : KnotEvent()

    data object ExitClicked : KnotEvent()

    data object ExitConfirmed : KnotEvent()

    data object ExitCancelled : KnotEvent()

    // lifecycle
    data object WentBackground : KnotEvent()

    data object CameForeground : KnotEvent()

    // debug hooks
    data class DebugState(val spec: String) : KnotEvent()

    data class DebugKnot(val id: String) : KnotEvent()

    data class DebugStep(val step: Int) : KnotEvent()

    data class DebugGhost(val share: Float?) : KnotEvent()

    data class DebugYourTurn(val share: Float?) : KnotEvent()

    data class DebugDwell(val on: Boolean) : KnotEvent()

    data class DebugDataError(val on: Boolean) : KnotEvent()

    /** Debug: a marker at this floor point and the rope moved there (fixed Stage point, never the head). */
    data class DebugMarker(val point: Vec3, val move: Boolean) : KnotEvent()

    data object DebugClear : KnotEvent()
}

/** One-shot effects the scene carries out. */
sealed class KnotUiEffect {
    /** Put the rope frame over the marker (yaw turned to face the viewer, kept ≥ 0.6 m from the head). */
    data class MoveToMarker(val marker: Vec3) : KnotUiEffect()

    /** Turn the demo rope by this many degrees (the title-bar "turn 90°", T11b). */
    data class Turn(val degrees: Float) : KnotUiEffect()

    /** Back to the user's view (0°). */
    data object ResetTurn : KnotUiEffect()

    /** Ask for the eye-tracking permission (once). */
    data object AskEyePermission : KnotUiEffect()

    /** v0.1.4: bring the rope's volume window back in front of the user (the system places it). */
    data object RecallRope : KnotUiEffect()

    /** Quit (D26): release the scene, remove the task, end the process. */
    data object Quit : KnotUiEffect()
}
