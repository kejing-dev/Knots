#!/usr/bin/env bash
# Every designed state (AGENTS.md screenshot list) -> ../spatial-app-factory/apps/knots/build/shots/<name>.png
#   DEV=emulator-5568 AVD=PICO_6_1_Factory5 tools/shoot_all.sh [name-filter]
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export DEV=${DEV:-emulator-5568} AVD=${AVD:-PICO_6_1_Factory5} PKG=io.embodit.knots
export PATH="$PATH:${LOCALAPPDATA}/Android/Sdk/platform-tools"
OUT="$ROOT/../spatial-app-factory/apps/knots/build/shots"; mkdir -p "$OUT"
F="${1:-}"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; }
s() { # s NAME "STATE ARGS" [extra cmds]
  local name="$1" st="$2"; shift 2
  [ -n "$F" ] && [[ "$name" != *$F* ]] && return
  SETTLE=${SETTLE:-7} bash "$ROOT/tools/qa_shot.sh" "$st" "$name" "$@" >/dev/null 2>&1
  SETTLE=2 PRE=0.5 bash "$ROOT/tools/qa_shot.sh" - "$name" >/dev/null 2>&1
  cp -f "$ROOT/.scratch/shots/$name.png" "$OUT/$name.png" && echo "shot $name"
}
cmd "lang zh"; cmd "marker 0,-1.3 move"; cmd "hand right"; cmd "rope 8"; cmd "rope on"; cmd "height normal"; cmd "dwell off"
s S00-loading "S00 hold"
s S01-welcome "S01"
s S01-norope "S01" "rope off"
s S01-thick "S01" "rope on" "rope 11"
cmd "rope 8"
s S02-library "S02"
s S02-boating "S02" "category boating"
cmd "category all"
s S03-bowline-half "S03 bowline" "ghost 0.5"
s S03-bowline-done "S03 bowline" "ghost 1"
s S03-clove "S03 clove" "ghost 1"
s S04-bowline2 "S04 bowline 2" "ghost 0.6"
s S04-reef1 "S04 reef 1" "ghost 0.6"
s S04-fig8-tighten "S04 figure8 4" "ghost 0.5"
s S04-reef-tighten "S04 reef 3" "ghost 0.5"
s S04-clove-tighten "S04 clove 5" "ghost 0.5"
s S04-bowline-tighten "S04 bowline 5" "ghost 0.5"
s S05-bowline4 "S05 bowline 4"
s S06-scrub "S06 bowline 2"
s S06-speed "S06 bowline 2" "speed"
cmd "speed"; cmd "speed"
s S07-reef "S07 reef"
s S07-clove "S07 clove"
s S08-turn180 "S04 bowline 3" "ghost 1" "turn90" "turn90"
s S08-snapback "S05 bowline 3" "turn 8" "release"
s S10-menu "S10"
s S11-settings "S11"
s S12-confirm "S12"
s S13-resume "S13 bowline 3"
s S14-away "S14"
s S15-error "S15"
s S17-place "S17 bowline"
s S17-marker "S17 bowline" "marker 0.25,-1.3"
s S16-yourturn "S16 bowline 2" "rope off"
s S16-half "S16 bowline 2" "yourturn 0.5"
s S16-pull "S16 figure8 4"
cmd "rope on"
s S05-lefthand "S05 bowline 3" "hand left"
cmd "hand right"
s S05-thick11 "S05 bowline 3" "rope 11"
cmd "rope 8"
s S05-low "S05 bowline 3" "height low"
cmd "height normal"
s S05-english "S05 bowline 3" "lang en"
cmd "lang zh"
echo done
