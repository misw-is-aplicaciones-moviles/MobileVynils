# Helpers to drive the app with adb and uiautomator. Sourced by measure.sh.
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
PKG=co.edu.uniandes.vinilos
sh_() { MSYS_NO_PATHCONV=1 "$ADB" shell "$@" | tr -d '\r'; }
dump() { MSYS_NO_PATHCONV=1 timeout 15 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; MSYS_NO_PATHCONV=1 timeout 10 "$ADB" shell cat /data/local/tmp/ui.xml | sed 's/[[:space:]]*$//'; }
# center of the first node matching an attribute regex, e.g. 'resource-id="[^"]*:id/coverInput"'
center() { echo "$1" | grep -o "<node [^>]*$2[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }
tap_on() { # tap_on <attr regex> [tries]
  local tries=${2:-15} xy
  for _ in $(seq 1 $tries); do
    xy=$(center "$(dump)" "$1")
    if [ -n "$xy" ]; then sh_ input tap $xy; return 0; fi
    sleep 1
  done
  echo "not found: $1" >&2; return 1
}
id() { echo "resource-id=\"$PKG:id/$1\""; }
text() { echo "text=\"$1\""; }
mark() { sh_ log -t B1PERF "MARK $1"; }
type_slow() { # one adb call per character, like a person typing
  local s="$1" i
  for ((i = 0; i < ${#s}; i++)); do sh_ input text "${s:$i:1}"; done
}
wait_for() { # wait_for <attr regex> [tries]
  local tries=${2:-20}
  for _ in $(seq 1 $tries); do [ -n "$(center "$(dump)" "$1")" ] && return 0; sleep 1; done
  echo "never appeared: $1" >&2; return 1
}
