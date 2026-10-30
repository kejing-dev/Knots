#!/usr/bin/env bash
# Rework 1 check on emulator-5560: install, step 1 of the puppy face, turn the head onto "Next", 1 s -> step 2.
cd "$(dirname "$0")/.."
export DEV=${DEV:-emulator-5560} AVD=${AVD:-Pico_Emulator_6_1_fresh} PKG=io.embodit.foldguide TAG=FoldGuide
export PATH="$PATH:${LOCALAPPDATA}/Android/Sdk/platform-tools"
H="bash tools/harness/emu.sh"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; }
px() { python -c "import sys; print(int(round(float(sys.argv[1]) / 0.0537)))" "$1"; }
turn() { # right-drag: dx px right = turn right, dy px up = look up
  $H fast on >/dev/null 2>&1; sleep 1.5
  $H drag $((1080 - $1/2)) $((1079 + $2/2)) $((1080 + $1/2)) $((1079 - $2/2)) -Button right 2>&1 | tail -1 | cut -c1-70; sleep 1.5; }
adb -s $DEV logcat -c
cmd "state S08 dog 1"; sleep 4
cmd "dwell on"; sleep 0.5
cmd "aim next"; sleep 1
line=$(adb -s $DEV logcat -d -s $TAG:V | grep "AIM next" | tail -1 | sed 's/.*AIM/AIM/'); echo "$line"
r=$(echo "$line" | sed -n 's/.*turnRight=\([-0-9.]*\).*/\1/p'); u=$(echo "$line" | sed -n 's/.*turnUp=\([-0-9.]*\).*/\1/p')
[ -z "$r" ] && { echo "no AIM line"; exit 1; }
turn "$(px "$r")" "$(px "$u")"
cmd "aim next"; sleep 1; adb -s $DEV logcat -d -s $TAG:V | grep "AIM next" | tail -1 | sed 's/.*AIM/AIM/'
$H fast on >/dev/null 2>&1
sleep 4
adb -s $DEV logcat -d -s $TAG:V | grep -E "DWELL|STEP |STATE S" | sed 's/.*FoldGuide: //' | tail -8
