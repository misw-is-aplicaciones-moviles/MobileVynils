#!/bin/bash
# Usage: measure.sh <device label> <before|after> [repetitions]. Needs apk/before.apk and apk/after.apk built with instrumentacion-temporal.patch.
S=$(cd "$(dirname "$0")" && pwd)  # results/ and apk/ live next to the scripts
source $S/lib.sh
DEV=$1; BUILD=$2; REPS=${3:-1 2 3}; OUT=$S/results/$DEV; mkdir -p $OUT
URL="https://i.pinimg.com/564x/aa/5f/ed/aa5fed7fac61cc8f41d1e79db917a7cd.jpg"
COMMENT="Excelentealbumdesalsaclasica84"
ALBUM='text="\(BUSCANDO AM.\{1,2\}RICA\|Buscando Am.\{1,2\}rica\)"'

"$ADB" reverse tcp:3000 tcp:3000 >/dev/null 2>&1
for m in $(sh_ ime list -s); do sh_ ime disable $m >/dev/null; done
sh_ setprop log.tag.Engine VERBOSE
sh_ setprop debug.hwui.profile true
sh_ logcat -G 4M >/dev/null 2>&1
for k in window_animation_scale transition_animation_scale animator_duration_scale; do sh_ settings put global $k 0; done
"$ADB" uninstall $PKG >/dev/null 2>&1
"$ADB" install $S/apk/$BUILD.apk | tail -1
read W H < <(sh_ wm size | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+)/\1 \2/')
{
  echo "device=$DEV build=$BUILD apk_sha256=$(sha256sum $S/apk/$BUILD.apk | cut -c1-16)"
  echo "model=$(sh_ getprop ro.product.model) sdk=$(sh_ getprop ro.build.version.sdk) screen=${W}x$H"
  sh_ cat /proc/meminfo | head -1; echo "cpus=$(sh_ cat /proc/cpuinfo | grep -c ^processor)"
} > $OUT/$BUILD.info

gfx_reset() { sh_ dumpsys gfxinfo $PKG reset >/dev/null; }
gfx_save() { sh_ dumpsys gfxinfo $PKG > $OUT/${BUILD}_r${REP}_$1.gfx; }
mem_save() { sh_ dumpsys meminfo $PKG > $OUT/${BUILD}_r${REP}_$1.mem; }
swipe_up() { sh_ input swipe $((W / 2)) $((H * 7 / 10)) $((W / 2)) $((H * 3 / 10)) 400; }
swipe_down() { sh_ input swipe $((W / 2)) $((H * 3 / 10)) $((W / 2)) $((H * 8 / 10)) 300; }

for REP in $REPS; do
  t0=$(date +%s)
  # Fresh seed per repetition, so every one sees the same albums and comments.
  (cd "${BACKVYNILS:-$S/../../../../BackVynils}" && docker compose down -v >/dev/null 2>&1 && docker compose up -d >/dev/null 2>&1)
  until curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:3000/albums | grep -q 200; do sleep 3; done
  sh_ am force-stop $PKG; sh_ pm clear $PKG >/dev/null
  sh_ am start -n $PKG/.MainActivity >/dev/null
  tap_on "$(id collectorCard)" 20 || exit 1
  wait_for "$ALBUM" || exit 1
  sh_ logcat -c

  # B1-01 a: type the cover URL, four characters per call (about 8 characters per second).
  tap_on "$(id primaryAction)" || exit 1; tap_on "$(id coverInput)" || exit 1; sleep 1
  mark url_type_start; ts=$(date +%s%N)
  for ((i = 0; i < ${#URL}; i += 4)); do sh_ input text "${URL:$i:4}"; done
  te=$(date +%s%N); sleep 3; mem_save url_type; mark url_type_end
  echo "url_type_ms_per_char $(( (te - ts) / 1000000 / ${#URL} ))" > $OUT/${BUILD}_r${REP}.pace
  tap_on "$(id headerClose)" || exit 1; sleep 2

  # B1-01 b: paste the whole URL in one call.
  tap_on "$(id primaryAction)" || exit 1; tap_on "$(id coverInput)" || exit 1; sleep 1
  mark url_paste_start; sh_ input text "$URL"; sleep 3; mem_save url_paste; mark url_paste_end
  tap_on "$(id headerClose)" || exit 1; sleep 2

  # B1-03: open the sheet, choose a collector and 4 stars, type 30 characters one by one.
  tap_on "$ALBUM" || exit 1; sleep 2
  for _ in 1 2 3 4 5 6 7 8; do xy=$(center "$(dump)" "$(id sectionAction)"); [ -n "$xy" ] && break; swipe_up; sleep 1; done
  sh_ input tap $xy; tap_on 'text="Manolo Bellon"' || exit 1; tap_on "$(id star4)" || exit 1
  tap_on "$(id commentField)" || exit 1; sleep 1
  gfx_reset; mark comment_type_start
  for ((i = 0; i < ${#COMMENT}; i++)); do sh_ input text "${COMMENT:$i:1}"; done
  sleep 1; gfx_save comment_type; mem_save comment_type; mark comment_type_end

  # B1-02: publish, then return to the top of the detail.
  gfx_reset; mark publish_start
  # On short screens the button can sit below the fold of the sheet: scroll the sheet once and retry.
  tap_on "$(id publishComment)" 6 || { swipe_up; sleep 1; tap_on "$(id publishComment)" || exit 1; }; sleep 4
  for _ in 1 2 3 4 5 6; do swipe_down; done; sleep 2
  gfx_save publish; mem_save publish; mark publish_end

  sh_ logcat -d -v raw -s B1PERF:D Engine:V > $OUT/${BUILD}_r${REP}.log
  echo "$DEV $BUILD rep $REP done in $(( $(date +%s) - t0 )) s, lines $(wc -l < $OUT/${BUILD}_r${REP}.log)"
done
sh_ am force-stop $PKG
