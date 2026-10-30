#!/usr/bin/env bash
# v0.1.3 review shots for the user: library, new icons, each new knot finished + a middle step -> ../spatial-app-factory/apps/knots/build/v013/
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export DEV=${DEV:?set DEV to the assigned emulator} AVD=${AVD:-} PKG=io.embodit.knots
export PATH="$PATH:${LOCALAPPDATA}/Android/Sdk/platform-tools"
OUT="$ROOT/../spatial-app-factory/apps/knots/build/v013"; mkdir -p "$OUT"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; }
s() { local name="$1" st="$2"; shift 2
  SETTLE=${SETTLE:-8} bash "$ROOT/tools/qa_shot.sh" "$st" "$name" "$@" >/dev/null 2>&1
  SETTLE=2 PRE=0.5 bash "$ROOT/tools/qa_shot.sh" - "$name" >/dev/null 2>&1
  cp -f "$ROOT/.scratch/shots/$name.png" "$OUT/$name.png" && echo "shot $name"; }
cmd "lang zh"; cmd "hand right"; cmd "rope on"; cmd "rope 8"; cmd "height normal"; cmd "marker 0,-1.3 move"
declare -A MID=([fishermans]=3)
s library "S02"
s library-scrolled "S02" "category all"
for k in tautline sheetbend fishermans doubleoverhand; do
  s "$k-mid" "S04 $k ${MID[$k]:-2}" "ghost 0.6"
  s "$k-done" "S03 $k" "ghost 1"
done
echo done
