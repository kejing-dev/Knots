#!/usr/bin/env bash
# "In and out 30 times" panel self-test: open / close the more menu and the settings popup 30 times each
# at 60 fps, then capture; the app must still draw its glass (taste rule A4) and not crash.
cd "$(dirname "$0")/.."
export DEV=${DEV:-emulator-5564} AVD=${AVD:-Pico_Emulator_6_1_b} PKG=io.embodit.foldguide TAG=FoldGuide
export PATH="$PATH:${LOCALAPPDATA}/Android/Sdk/platform-tools"
H="bash tools/harness/emu.sh"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; }
adb -s $DEV logcat -c
cmd "state S08 masu 3"; sleep 3
for i in $(seq 1 30); do
  [ $((i % 6)) -eq 1 ] && $H fast on >/dev/null 2>&1
  cmd "more"; sleep 0.45; cmd "more"; sleep 0.35
  cmd "ui gear" ; cmd "more"; sleep 0.2; cmd "ui menu_settings"; sleep 0.45; cmd "state S08 masu 3"; sleep 0.5
done
echo "cycles done; crashes: $(adb -s $DEV logcat -d AndroidRuntime:E *:S | grep -c FATAL)"
cmd "more"; sleep 1.5; $H fast on >/dev/null 2>&1; sleep 6
bash tools/qa_shot.sh - soak-menu
cmd "more"; cmd "ui menu_settings"; sleep 1.5
bash tools/qa_shot.sh - soak-settings
adb -s $DEV logcat -d -s FoldGuide:V | grep -c "STATE "
