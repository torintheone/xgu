#!/usr/bin/env bash
# Photographs every screen of the app on a running emulator or a phone connected with adb.
# Used by .github/workflows/screenshots.yml; by hand: scripts/screenshots.sh petri.apk docs/screenshots
#
# The app opens the screens itself from launch extras (see PetriViewModel.handleLaunch), so no taps are needed.
set -euo pipefail

APK="$1"
OUT="$2"
PACKAGE=io.github.torintheone.petri
ACTIVITY="$PACKAGE/.MainActivity"

mkdir -p "$OUT"
adb install -r "$APK"
adb logcat -c

# A clean status bar: the same clock and full signal on every picture.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e level 4 -e datatype none
demo notifications -e visible false

# Brings the app forward with the given extras; FLAG_ACTIVITY_SINGLE_TOP delivers them to the open screen.
open_app() {
  adb shell am start -W -f 0x20000000 -n "$ACTIVITY" "$@" > /dev/null
}

shoot() {
  sleep "$2"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "saved $OUT/$1.png"
}

swipe_up() {
  local size width height
  size="$(adb shell wm size | sed -n 's/.*: \([0-9]*x[0-9]*\).*/\1/p' | tail -n 1)"
  width="${size%x*}"
  height="${size#*x}"
  adb shell input swipe $((width / 2)) $((height * 78 / 100)) $((width / 2)) $((height * 30 / 100)) 500
}

open_app
sleep 40 # the starting dish grows for 40 game hours

open_app --es petri.tab microscope
shoot 1-microscope 3

open_app --ez petri.inspect true
shoot 2-microscope-cell 4

open_app --es petri.tab genome --es petri.preset SMART_SWIMMER
shoot 3-genome 6
swipe_up
shoot 4-genome-parameters 2

open_app --es petri.tab environment
shoot 5-environment 2
swipe_up
shoot 6-environment-parameters 2

open_app --es petri.tab genbank
shoot 7-genbank 3

# Landscape: the tabs move to the side.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
open_app --es petri.tab microscope
shoot 8-microscope-landscape 4
adb shell settings put system user_rotation 0

demo exit

if adb logcat -d -b crash | grep -q "Process: $PACKAGE"; then
  echo "::error::The app crashed while taking screenshots:"
  adb logcat -d -b crash
  exit 1
fi
