package io.embodit.knots.debug

import android.content.Context
import io.embodit.knots.domain.knot.Category
import io.embodit.knots.domain.model.Hand
import io.embodit.knots.domain.model.LanguagePref
import io.embodit.knots.domain.model.RopeHeight
import io.embodit.knots.domain.model.Vec3
import io.embodit.knots.platform.KnotLog
import io.embodit.knots.ui.knot.KnotEvent
import io.embodit.knots.ui.knot.debug.KnotProbe
import io.embodit.knots.ui.knot.scene.KnotScene
import java.io.File
import java.util.Locale

/**
 * QA hooks of the debug build (design `verification.debugHooks`, command names as in Fold Guide). One log line
 * (tag `Knots`) answers every command.
 *
 *   adb -s <serial> shell "am broadcast -a io.embodit.knots.DEBUG -p io.embodit.knots --es cmd '<command>'"
 *
 * - `state <Sxx> [knot] [step] [hold]`  straight to a designed state: S00 S01 S02 S03 S04 S05 S06 S07 S08 S09 S10 S11 S12
 *                                S13 S14 S15 S16 (`state S04 bowline 2`, `state S16 bowline 2`, `state S13 bowline 3`,
 *                                `state S00 hold`); S08 = S04 turned 180°, S06 = paused at 60 %
 * - `knot <figure8|reef|clove|bowline>`, `step <n>`   pick a knot / jump to step n (places the rope when needed)
 * - `marker x,z [move]`           puts the floor marker at Stage (x, 0, z) (S17), `move` = and "move to marker"
 * - `tomarker`                   "move to marker" with the current marker
 * - `next` / `prev` / `replay` / `more` / `back` / `follow` / `play` / `speed`   the panel's controls
 * - `scrub <0..1>`               the whole-knot scrubber (S06)
 * - `ghost <0..1>` / `ghost off` freezes the step's demo (or the preview) at that share, `off` plays again
 * - `yourturn <0..1>` / `yourturn off`   freezes "your turn" at that share (S16 screenshots)
 * - `tip <0..1>`                 drags the orange tip along the route to that share (as the pointer would; completes at 95 %)
 * - `dwell on|off`, `rope <4|8|11>`, `rope on|off` (has a rope), `hand left|right`, `height high|normal|low`,
 *   `lang system|zh|en`, `category <all|basic|camping|boating|climbing>`
 * - `turn <deg>`                 sets the rope's turn (as a drag would); `turn90`; `myview`; `release` (snap within ±12°)
 * - `stick <x> [ms]`             the thumbstick held at x for ms (default 1000)
 * - `drag dx,dy,dz`              a handle drag of that many metres, released (then the 0.6 m clearance)
 * - `head yaw <deg>` / `head off`   the out-of-sight check and placement see the head turned (S14 without turning the camera)
 * - `gaze x,y,z` / `gaze off`    the gaze rests on that Stage point (dwell tests); `aim [control]` how far to turn to a control
 * - `dataerror on|off`           knots.json "fails" on the next load (S15: `dataerror on` + `state S00` or Retry)
 * - `ui <control>`               presses a panel control by name (see `bounds` for the composed ones)
 * - `clear`                      records + settings, back to S01
 * - `dump`                       `DUMP {...}` (also files/dump.json): state, knot, step, rope pose and distances, diameter, step timing
 * - `pose`, `bounds`, `park`, `fps on|off`
 */
internal object DebugCommands {

    fun run(context: Context, raw: String, a: String?, s: String?) {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val name = tokens.first().lowercase(Locale.US)
        val rest = tokens.drop(1)
        val text = (s ?: rest.joinToString(" ")).trim()
        val numbers = (a ?: rest.joinToString(",")).split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        val host = KnotProbe.host
        if (host == null) {
            KnotLog.i("CMD $name ignored: the Stage is not running")
            return
        }
        try {
            dispatch(context, host, name, numbers, text)
        } catch (t: Throwable) {
            KnotLog.e("CMD $name failed: $t", t)
        }
    }

    private fun dispatch(context: Context, host: KnotProbe.Host, name: String, numbers: List<Float>, text: String) {
        val probe = host.scene() ?: return KnotLog.i("CMD $name ignored: no scene")
        val scene = host.sceneHandle() as? KnotScene
        val word = text.lowercase(Locale.US)
        when (name) {
            "state" -> host.dispatch(KnotEvent.DebugState(text))
            "knot" -> host.dispatch(KnotEvent.DebugKnot(word))
            "step" -> host.dispatch(KnotEvent.DebugStep(numbers.firstOrNull()?.toInt() ?: 1))
            "marker" -> if (numbers.size >= 2) host.dispatch(KnotEvent.DebugMarker(Vec3(numbers[0], 0f, numbers[1]), "move" in word)) else KnotLog.i("CMD marker needs x,z")
            "tomarker" -> host.dispatch(KnotEvent.MoveToMarkerClicked)
            "next" -> host.dispatch(KnotEvent.NextClicked)
            "prev" -> host.dispatch(KnotEvent.PrevClicked)
            "replay" -> host.dispatch(KnotEvent.ReplayClicked)
            "more" -> host.dispatch(KnotEvent.MoreClicked)
            "back" -> host.dispatch(KnotEvent.BackClicked)
            "follow" -> host.dispatch(KnotEvent.FollowClicked)
            "play", "pause" -> host.dispatch(KnotEvent.PlayPauseClicked)
            "speed" -> host.dispatch(KnotEvent.SpeedClicked)
            "scrub" -> host.dispatch(KnotEvent.Scrubbed(numbers.firstOrNull() ?: 0.5f))
            "ghost" -> host.dispatch(KnotEvent.DebugGhost(if (word == "off") null else (numbers.firstOrNull() ?: 0.5f).coerceIn(0f, 1f)))
            "yourturn" -> host.dispatch(KnotEvent.DebugYourTurn(if (word == "off") null else (numbers.firstOrNull() ?: 0.5f).coerceIn(0f, 1f)))
            "tip" -> scene?.debugTipTo(numbers.firstOrNull() ?: 1f)
            "dwell" -> host.dispatch(KnotEvent.DebugDwell(word != "off"))
            "rope" ->
                when (word) {
                    "on", "yes" -> host.dispatch(KnotEvent.HasRopePicked(true))
                    "off", "no" -> host.dispatch(KnotEvent.HasRopePicked(false))
                    else -> numbers.firstOrNull()?.toInt()?.takeIf { it in listOf(4, 8, 11) }?.let { host.dispatch(KnotEvent.RopeSizePicked(it)) } ?: KnotLog.i("CMD rope unknown $word")
                }
            "hand" -> host.dispatch(KnotEvent.HandSelected(if (word == "left") Hand.LEFT else Hand.RIGHT))
            "height" -> RopeHeight.byId(word)?.let { host.dispatch(KnotEvent.HeightSelected(it)) } ?: KnotLog.i("CMD height unknown $word")
            "lang" ->
                host.dispatch(
                    KnotEvent.LanguageSelected(
                        when (word) {
                            "zh" -> LanguagePref.ZH
                            "en" -> LanguagePref.EN
                            else -> LanguagePref.SYSTEM
                        }
                    )
                )
            "category" -> Category.byId(word)?.let { host.dispatch(KnotEvent.CategoryPicked(it)) } ?: KnotLog.i("CMD category unknown $word")
            "turn" -> scene?.setSpinDegrees(numbers.firstOrNull() ?: 0f)
            "turn90" -> host.dispatch(KnotEvent.Turn90Clicked)
            "myview" -> host.dispatch(KnotEvent.MyViewClicked)
            "release" -> scene?.release()
            "stick" -> scene?.injectStick(numbers.firstOrNull() ?: 0.8f, (numbers.getOrNull(1) ?: 1000f).toLong())
            "drag" -> if (numbers.size >= 3) scene?.debugHandleDrag(Vec3(numbers[0], numbers[1], numbers[2])) else KnotLog.i("CMD drag needs dx,dy,dz")
            "head" -> {
                scene?.headYawOverride = if (word == "off") 0f else numbers.firstOrNull() ?: 0f
                KnotLog.i("HEAD yaw override=${scene?.headYawOverride}")
            }
            "aim" -> KnotLog.i(scene?.aimLine(text.ifEmpty { "next" }) ?: "AIM: no scene")
            "gaze" -> {
                scene?.gazeOverride = if (word == "off" || numbers.size < 3) null else Vec3(numbers[0], numbers[1], numbers[2])
                KnotLog.i("GAZE override=${scene?.gazeOverride?.let { KnotLog.pos(it) } ?: "off"}")
            }
            "dataerror" -> host.dispatch(KnotEvent.DebugDataError(word != "off"))
            "clear" -> host.dispatch(KnotEvent.DebugClear)
            "ui" -> KnotLog.i(if (host.pressControl(text)) "UI $text" else "UI $text: no such control is composed")
            "dump" -> {
                val json = org.json.JSONObject(host.dumpJson())
                json.put("scene", probe.sceneJson())
                val line = json.toString()
                KnotLog.i("DUMP $line")
                runCatching { File(context.filesDir, "dump.json").writeText(line) }
            }
            "pose" -> KnotLog.i(probe.poseLine())
            "bounds" -> {
                probe.panelLines().forEach { KnotLog.i(it) }
                for (panel in listOf("guidePanel", "settingsPanel", "menuPanel")) {
                    for ((ctrl, b) in KnotProbe.controlsOf(panel)) KnotLog.i("CTRL panel=$panel name=$ctrl cx=${b.cx} cy=${b.cy} w=${b.w} h=${b.h}")
                }
            }
            "park" -> KnotLog.i(probe.parkPanel())
            "fps" -> KnotLog.i(probe.setFpsLine(word != "off"))
            else -> KnotLog.i("CMD unknown $name")
        }
    }
}
