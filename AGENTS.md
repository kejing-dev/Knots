# Knots (绳结)

Knot-tying coach for PICO OS 6. Kotlin, PICO Spatial SDK 6.1.9, one Mixed Stage (Full Space, passthrough), package
`io.embodit.knots`. A life-size demo rope (4 / 8 / 11 mm) hangs 0.60 m ahead and 0.25 m below the eyes and ties each
knot step by step from the user's own point of view; with a real rope you tie along, without one ("no rope", factory
rule D7) each step ends with "your turn": pinch (or ray + trigger) the orange tip and drag it along the dashes.
v1 knots: figure eight, square knot, clove hitch (virtual post), bowline.

Design package (binding): `../spatial-app-factory/apps/knots/design-package.json` (archetype D, STAGE_MIXED,
single_panel_with_popup, states S00 - S16). Mockup + engine: `../spatial-app-factory/apps/knots/sample.html`,
`knots.preview.js`. Built from `../fold-guide` (same skeleton: panel pool, look-to-advance, debug bus).

## Structure

```
app/src/main/java/io/embodit/knots/
  Main.kt                      DefaultStage { KnotsTheme { KnotScreen() } }
  platform/                    SpatialApplication, LaunchActivity, KnotGraph, KnotLog, EyePermissionActivity
    tracking/HeadTracker       HMD pose (placement, out-of-sight, gaze fallback)
    tracking/EyeTracker        EyeTrackingProvider gaze (look-to-advance), from Rehearsal Hall
    input/ControllerInput      controller presence + thumbstick (turning the rope)
  domain/knot/KnotLibrary      the 4 knots: control points (mm), steps, tighten params, copy (zh / en)
  domain/knot/KnotEngine       KnotGeometry: Catmull-Rom centreline, growing rope, crossings, tighten, length keeping
  domain/knot/RopeMesher       one 8-sided tube mesh, colour by arc length through an 8 x 1 palette texture
  domain/knot/Shapes           post, handle, arrows, tip ring, shadow disc
  data/KnotRepository          SharedPreferences JSON: settings, progress (records, current step, rope pose)
  ui/knot/KnotViewModel        state machine S00 - S16; KnotUiState / KnotEvent / KnotUiEffect
  ui/knot/scene/KnotScene      ECS side: placement, demo timeline, your-turn drag, turning, panels, dwell, S14
  ui/knot/components           GuidePanel (every mode), Popups (settings, menu), Diagrams, Common
app/src/test/                  KnotEngineTest: design acceptance.geometryTests for 4 knots x 3 diameters
app/src/debug/                 DebugCommandReceiver + DebugCommands (QA hooks)
tools/                         make_icon.py (adaptive icon XML + 512 PNG), subset_cjk_font.py
```

Per-frame work (demo motion, mesh updates at <= 30 Hz, turning, drags, gaze, out-of-sight) is in `KnotScene.onFrame`
only; Compose only renders the state and reads two quantised values (`DemoClock`, `DwellTarget.progress`).

## Placement (v0.1.1, factory rules D1 / D20)

The rope is never placed from the head pose. With no anchor yet, opening a knot shows S17: point the controller ray
(trigger) or look + pinch at the floor (an invisible 16 m tap box at Stage y = 0; a ghost ring follows the controller ray
or the eyes), then "Move to marker": the rope frame stands 1.25 / 1.10 / 0.95 m over the marker (height setting), turned
to face the viewer, ≥ 0.6 m from the head. That anchor holds for the session and is saved (next / previous / replay /
another knot / background never move it; ⋯ "Put a new marker" / S14 "New marker" do). The guide panel lazy-follows the
head (0.9 m, 10° down) and, while a knot is open, sits 28° to the hand side; if the rope enters the eye -> panel corners
cone it turns 30 % see-through (log `CONE`). Grep gate with every head-pose use explained:
`../spatial-app-factory/apps/knots/build/ui-audit.md`.

## Geometry (design `drawingModel`, engine changes by the build station)

- Rope grows from s = 0 to the tip; every step is an arc range; tighten = control points pulled together by the
  knot's `lambda` share for that diameter (largest share that keeps strands >= 0.9 d apart).
- The knot (not the straight standing part) scales with d / 8 so gaps stay >= 0.9 d at 4 and 11 mm; the bowline is
  drawn 1.3x (at 1x its loose gap was 0.83 d). Clove centreline is pushed off the post (>= R + d/2).
- Tightening keeps the rope's length: the slack goes to the off-screen standing part (figure eight, bowline, clove)
  or the tail (square knot). The standing part above 0.17 m / beyond 0.22 m sideways is not drawn.

## QA hooks (debug build)

```
adb -s <serial> shell "am broadcast -a io.embodit.knots.DEBUG -p io.embodit.knots --es cmd '<command>'"
```

| command | effect |
|---|---|
| `state <Sxx> [knot] [step] [hold]` | S00 .. S16; defaults: S04/S06/S08/S09 bowline 2, S05 bowline 4, S07 reef, S13 bowline 3, S16 bowline 2; S06 = paused at 60 %, S08 = turned 180° |
| `knot <figure8\|reef\|clove\|bowline>`, `step <n>` | pick / jump (no anchor yet: a debug marker at Stage (0, 0, -1.3)) |
| `marker x,z [move]`, `tomarker`, `state S17 [knot]` | floor marker at Stage (x, 0, z) (+ move the rope there) / "move to marker" / the place-marker panel |
| `next` `prev` `replay` `more` `back` `follow` `play` `speed` `scrub <0..1>` | panel controls |
| `ghost <0..1>` / `ghost off` | freeze the step demo (or the S03 preview) at that share |
| `yourturn <0..1>` / `off`, `tip <0..1>` | freeze S16 / drag the tip along the route (completes at 95 %) |
| `rope 4\|8\|11`, `rope on\|off`, `hand left\|right`, `height high\|normal\|low`, `lang system\|zh\|en`, `dwell on\|off`, `category <id>` | settings |
| `turn <deg>`, `turn90`, `myview`, `release`, `stick <x> [ms]` | turning; `release` snaps within ±12° |
| `drag dx,dy,dz` | handle drag (metres) then the 0.6 m clearance push |
| `head yaw <deg>` / `head off` | S14 without turning the camera |
| `gaze x,y,z` / `off`, `aim [control]` | look-to-advance tests |
| `dataerror on\|off` | S15 on the next load |
| `ui <control>`, `bounds`, `dump`, `pose`, `park`, `fps on\|off`, `clear` | probes |

`dump` -> `DUMP {...}`: state, knot, step, phase, diameter, rope pose, `headDistH` / `ahead` / `below` (design T3:
0.60 / 0.25), step timing (`stepMoveS`, `stepCycleS`), tip arc, guide panel pose and distance.

Screenshot list (design `states[].qaCapture`): S00 `state S00 hold`; S01 `clear` (then `rope off`, `rope 11`); S02
`state S02` (+ `category boating`); S03 `state S03 bowline` + `ghost 0.5`, finished, `state S03 clove`; S04
`state S04 bowline 2` + `ghost 0.6`, `state S04 reef 1`, each knot's tighten step + `ghost 0.5`; S05 `state S05`;
S06 `state S06` (+ `speed`); S07 `state S07 reef`, `state S07 clove`; S08 `state S04 bowline 3` + `turn90` x2,
`turn 8` + `release`; S09 controller mode on S04; S10 `state S10`; S11 `state S11` (+ `hand left`, `rope 11`,
`height low`, `lang en` on S05); S12 `state S12`; S13 `state S13`; S14 `state S14` or `head yaw 180`; S15 `state S15`;
S16 `state S16`, `yourturn 0.5`, `state S16 figure8 4`. Log tag `Knots`: `STATE`, `STEP`, `ROPE placed`, `DEMO`,
`YOURTURN`, `DWELL`, `TURN`, `DRAG`, `AWAY`, `GAZE`, `PANEL`, `SCENE`, `CMD`, `DUMP`.

## Build

```
export JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1' PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk'
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
python -I tools/make_icon.py --xml ; python -I tools/make_icon.py <out512.png>
python -I tools/subset_cjk_font.py      # after changing Chinese copy
```

Material gate: `grep -rEf ../spatial-app-factory/bin/material-grep.txt --include=*.kt --include=*.kts app/src` empty.

## Deviations (within tolerance)

- Route dashes show the whole step's route (built once per step) instead of a moving 15 cm window: one dynamic mesh
  upload per frame only (the emulator blocks ~1 s per upload, Air Sketch finding).
- Knot geometry scales with the diameter (bowline 1.3x); tighten amount per diameter (geometry tests).
- Guide panel 440 x 380 dp (design default 340, max 440) for roomy layout (profile B19).
- Turn 90° is a worded button ("转 90°") and replay / "my view" carry words (B19).
- Eye-tracking permission added (design said none) because look-to-advance prefers eye gaze (design `input.dwell`).

## Not verified yet

Emulator run (no emulator free at build time), the replaceWithMeshModel perf spike, UI occlusion audit, texture
orientation (none used for text: the palette texture is 8 x 1), headset install.
