#!/usr/bin/env bash
# Genuine kill-and-relaunch process-death test for Gaggle, driven host-side over adb
# (instrumentation cannot test this: killing the target process kills the test process too).
#
# What it proves: after a REAL process kill (new pid), signing back in resumes EXACTLY where
# the user was — mid-checkout, at the shipping step, with the @PersistState address restored —
# because the persisted back stacks and Mavericks state outlive the process even though the
# in-memory session does not.
#
# Usage: ./tools/process-death-test.sh [serial]   (default emulator-5554; app must be installed:
#        ./gradlew :samples:gaggle:app:installDebug)
set -euo pipefail
SERIAL="${1:-emulator-5554}"
PKG="dev.goose.gaggle"
A() { adb -s "$SERIAL" "$@"; }

dump() { A exec-out uiautomator dump /dev/tty 2>/dev/null || true; }

wait_for_text() {
  local text="$1" tries="${2:-15}"
  for _ in $(seq 1 "$tries"); do
    if dump | grep -qF "$text"; then return 0; fi
    sleep 1
  done
  echo "FAIL: never saw '$text'"; exit 1
}

tap_text() {
  local coords
  coords=$(dump | python3 -c "
import sys, re
x = sys.stdin.read()
m = re.search(r'text=\"[^\"]*$1[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', x)
l,t,r,b = map(int, m.groups()); print((l+r)//2, (t+b)//2)")
  A shell input tap $coords
  sleep 2
}

echo "== clean start"
A shell am force-stop "$PKG"
A shell pm clear "$PKG" > /dev/null
A shell am start -n "$PKG/.MainActivity" > /dev/null
wait_for_text "Sign in as Goose Fan"

echo "== sign in, add to cart, get mid-checkout with an address chosen"
tap_text "Sign in as Goose Fan"
wait_for_text "Premium pond pellets"
tap_text "Premium pond pellets"
wait_for_text "Add to cart"
tap_text "Add to cart"
tap_text "Cart"
wait_for_text "Checkout (1)"
tap_text "Checkout (1)"
wait_for_text "Choose address"
tap_text "Choose address"
wait_for_text "1 Goose Way, Pondside"
tap_text "1 Goose Way"
wait_for_text "Ship to: 1 Goose Way, Pondside"

echo "== background, then genuinely kill the process"
A shell input keyevent 3
sleep 2
PID_BEFORE=$(A shell pidof "$PKG" | tr -d '\r')
A shell am kill "$PKG"
sleep 2
if [ -n "$(A shell pidof "$PKG" | tr -d '\r')" ]; then echo "FAIL: process survived am kill"; exit 1; fi

echo "== relaunch: a NEW process, sign back in, resume mid-checkout with the address restored"
A shell am start -n "$PKG/.MainActivity" > /dev/null
sleep 3
PID_AFTER=$(A shell pidof "$PKG" | tr -d '\r')
if [ -z "$PID_AFTER" ] || [ "$PID_AFTER" = "$PID_BEFORE" ]; then echo "FAIL: no fresh process"; exit 1; fi
wait_for_text "Sign in as Goose Fan"
tap_text "Sign in as Goose Fan"
wait_for_text "Ship to: 1 Goose Way, Pondside"

echo "PASS: process death ($PID_BEFORE -> $PID_AFTER); re-login resumed mid-checkout with @PersistState intact"
