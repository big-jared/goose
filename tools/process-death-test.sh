#!/usr/bin/env bash
# Genuine kill-and-relaunch process-death test for the m1 sample, driven host-side over adb
# (instrumentation cannot test this: killing the target process kills the test process too).
#
# What it proves: after a REAL process kill (new pid), the app restores the pushed back stack
# (Profile on top of Home) and Mavericks @PersistState fields (the notes), and back still walks
# down to Home.
#
# Usage: ./tools/process-death-test.sh [serial]   (default emulator-5554; app must be installed:
#        ./gradlew :samples:m1:app:installDebug)
set -euo pipefail
SERIAL="${1:-emulator-5554}"
PKG="dev.goose.sample.m1"
A() { adb -s "$SERIAL" "$@"; }

dump() { A exec-out uiautomator dump /dev/tty 2>/dev/null || true; }

wait_for_text() {
  local text="$1" tries="${2:-15}"
  for _ in $(seq 1 "$tries"); do
    if dump | grep -qF "text=\"$text"; then return 0; fi
    sleep 1
  done
  echo "FAIL: never saw '$text'"; exit 1
}

tap_text() {
  local coords
  coords=$(dump | python3 -c "
import sys, re
x = sys.stdin.read()
m = re.search(r'text=\"$1\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', x)
l,t,r,b = map(int, m.groups()); print((l+r)//2, (t+b)//2)")
  A shell input tap $coords
  sleep 2
}

echo "== clean start (force-stop clears the system's persisted task state too)"
A shell am force-stop "$PKG"
A shell pm clear "$PKG" > /dev/null
A shell am start -n "$PKG/.MainActivity" > /dev/null
wait_for_text "Team"

echo "== navigate Home -> Profile, write @PersistState notes"
tap_text "grace"
wait_for_text "Profile: grace"
tap_text "Add a goose to notes"
tap_text "Add a goose to notes"
wait_for_text "Notes (persisted): &#129727;&#129727;"

echo "== background, then genuinely kill the process"
A shell input keyevent 3
sleep 2
PID_BEFORE=$(A shell pidof "$PKG" | tr -d '\r')
A shell am kill "$PKG"
sleep 2
if [ -n "$(A shell pidof "$PKG" | tr -d '\r')" ]; then echo "FAIL: process survived am kill"; exit 1; fi

echo "== relaunch: a NEW process must restore the stack and the notes"
A shell am start -n "$PKG/.MainActivity" > /dev/null
sleep 3
PID_AFTER=$(A shell pidof "$PKG" | tr -d '\r')
if [ -z "$PID_AFTER" ] || [ "$PID_AFTER" = "$PID_BEFORE" ]; then echo "FAIL: no fresh process"; exit 1; fi
wait_for_text "Profile: grace"
wait_for_text "Notes (persisted): &#129727;&#129727;"

echo "== back walks down to Home"
A shell input keyevent 4
wait_for_text "Team"

echo "PASS: process death ($PID_BEFORE -> $PID_AFTER) restored the stack and @PersistState"
