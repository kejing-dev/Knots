#!/usr/bin/env bash
# Build, unit-test, install on the line emulator and restart the app: DEV=<assigned emulator> tools/redeploy.sh
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1' PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk'
DEV=${DEV:?set DEV to the assigned emulator}
ADB="${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe"
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon -q 2>&1 | grep -E "^e:|FAIL|failed" && exit 1 || true
"$ADB" -s $DEV install -r app/build/outputs/apk/debug/app-debug.apk | tail -1
MSYS_NO_PATHCONV=1 "$ADB" -s $DEV shell am force-stop io.embodit.knots; sleep 2
MSYS_NO_PATHCONV=1 "$ADB" -s $DEV shell am start -n io.embodit.knots/.platform.LaunchActivity >/dev/null
DEV=$DEV AVD=${AVD:-PICO_6_1_Factory5} PKG=io.embodit.knots TAG=Knots bash tools/harness/emu.sh fast on >/dev/null
for i in $(seq 1 40); do "$ADB" -s $DEV logcat -d -s Knots:V | grep -qE "STATE S(0[1-9]|1[0-9])" && break; sleep 2; done
MSYS_NO_PATHCONV=1 "$ADB" -s $DEV shell "am broadcast -a io.embodit.knots.DEBUG -p io.embodit.knots --es cmd 'lang zh'" >/dev/null
echo "redeployed"
