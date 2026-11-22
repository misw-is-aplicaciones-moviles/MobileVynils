#!/bin/bash
# Funciones comunes para las pasadas de perfilamiento en el emulador API 21.
export MSYS_NO_PATHCONV=1
ADB=/c/Users/yoima/AppData/Local/Android/Sdk/platform-tools/adb.exe
PKG=co.edu.uniandes.vinilos
PAUSE=${PAUSE:-2}      # pausa igual después de cada toque o desplazamiento (s)
KEYPAUSE=${KEYPAUSE:-0.3}  # pausa entre caracteres (s)
REST=${REST:-10}       # reposo final de cada ventana (s)
ACTIONS=0

sh_() { "$ADB" shell "$@" | tr -d '\r'; }
uptime_s() { sh_ cat /proc/uptime | cut -d' ' -f1; }

# Acciones del cuerpo de la ventana (coordenadas fijas, pausas iguales).
tap() { [ -z "${2:-}" ] && { echo "COORDENADAS VACÍAS" >&2; exit 1; }; "$ADB" shell input tap "$1" "$2"; ACTIONS=$((ACTIONS+1)); sleep "$PAUSE"; }
swipe() { "$ADB" shell input swipe "$1" "$2" "$3" "$4" 400; ACTIONS=$((ACTIONS+1)); sleep "$PAUSE"; }
back() { "$ADB" shell input keyevent 4; ACTIONS=$((ACTIONS+1)); sleep "$PAUSE"; }
type_() { # escribe carácter por carácter; espacio como %s
  local s="$1" i c
  for ((i=0; i<${#s}; i++)); do c="${s:i:1}"; [ "$c" = " " ] && c="%s"
    "$ADB" shell input text "$c"; ACTIONS=$((ACTIONS+1)); sleep "$KEYPAUSE"; done
  sleep "$PAUSE"
}

# Preparación por jerarquía (fuera de las ventanas).
dump() { sh_ uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; sh_ cat /data/local/tmp/ui.xml | tr '>' '\n'; }
# center PATRÓN [n]: centro del n-ésimo nodo (1 por defecto) cuyo texto coincide con PATRÓN (regex extendida)
center() {
  local n=${2:-1}
  dump | grep -E "$1" | sed -n "${n}p" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' |
    sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/echo $(((\1+\3)\/2)) $(((\2+\4)\/2))/' | bash
}
# wait_for PATRÓN [segundos]: espera a que aparezca un nodo
wait_for() { local t=${2:-30} i; for ((i=0; i<t; i+=2)); do dump | grep -qE "$1" && return 0; sleep 2; done; echo "NO APARECIÓ: $1" >&2; exit 1; }
ptap() { local xy; xy=$(center "$1" "${2:-1}"); [ -z "$xy" ] && { echo "NO ENCONTRADO: $1" >&2; exit 1; }; "$ADB" shell input tap $xy; sleep 2.5; }
pback() { "$ADB" shell input keyevent 4; sleep 2.5; }
