#!/bin/bash
# Una pasada de perfilamiento: 10 HU en el emulador API 21 con muestreo por adb.
# Uso: pass.sh ETAPA(antes|despues) REPETICION APK CARPETA_SALIDA [piloto]
# (sin set -u: los parámetros opcionales de lib.sh pueden venir vacíos)
STAGE=$1; REP=$2; APK=$3; OUT=$4; PILOT=${5:-}
RUN=$(dirname "$0"); source "$RUN/lib.sh"
mkdir -p "$OUT"; : > "$OUT/acciones.log"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/acciones.log"; }

log "1. Backend con semilla y datos API21"; bash "$RUN/fixtures.sh" > "$OUT/fixtures.json"
log "2. Instalación limpia"; "$ADB" install -r -d "$(cygpath -m "$APK")" >/dev/null
sh_ pm clear $PKG >/dev/null; sh_ ime disable com.android.inputmethod.latin/.LatinIME >/dev/null
sh_ input keyevent 3; sleep 2
"$ADB" logcat -v threadtime > "$OUT/logcat-todo.txt" 2>&1 & LOGPID=$!
"$ADB" logcat -b events -v threadtime > "$OUT/logcat-eventos.txt" 2>&1 & EVPID=$!
cleanup() { sh_ rm -f /data/local/tmp/muestreo; touch "$OUT/.parar-memoria"; kill $LOGPID $EVPID 2>/dev/null; }
trap cleanup EXIT
log "3. Arranque y entrada como coleccionista"
sh_ monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
wait_for 'collectorCard' 40 && ptap 'resource-id="[^"]*collectorCard"'
wait_for 'albumRow' 40
APPPID=$(sh_ ps | grep " $PKG\$" | awk '{print $2}')
log "PID $APPPID"
"$ADB" push "$(cygpath -m "$RUN/sampler.sh")" /data/local/tmp/sampler.sh >/dev/null
sh_ touch /data/local/tmp/muestreo
"$ADB" shell "sh /data/local/tmp/sampler.sh $APPPID" & SAMPLERPID=$!
rm -f "$OUT/.parar-memoria"
WOUT=$(echo "$OUT" | sed "s|^/c/|/mnt/c/|")
wsl.exe -d development -- bash -c "source ~/.nvm/nvm.sh >/dev/null; node /mnt/c/WSL/pf-api21/run/mem-sampler.mjs $APPPID $WOUT/memoria.csv $WOUT/.parar-memoria 500" & MEMPID=$!
log "Calentamiento 10 s"; sleep 10

API=http://localhost:3000
VENT="$OUT/ventanas.json"; echo '{' > "$VENT"; FIRST=1
window() { # window HU funcion_cuerpo
  local hu=$1 body=$2 s e
  ACTIONS=0; s=$(uptime_s); log "$hu inicio $s"
  $body
  sleep "$REST"; e=$(uptime_s); log "$hu fin $e ($ACTIONS acciones)"
  [ $FIRST = 1 ] && FIRST=0 || echo ',' >> "$VENT"
  printf '  "%s": {"start_s": %s, "end_s": %s, "body_actions": %s}' "$hu" "$s" "$e" "$ACTIONS" >> "$VENT"
  mkdir -p "$OUT/$hu"; "$ADB" exec-out screencap -p > "$OUT/$hu/emulador.png"
}
check() { if eval "$2"; then log "  verificación $1: OK"; echo "\"$1\": true" >> "$OUT/checks.txt"; else log "  verificación $1: FALLA"; echo "\"$1\": false" >> "$OUT/checks.txt"; fi; }
: > "$OUT/checks.txt"

# ---------- HU02: detalle de álbum ----------
hu02() { tap $X02; swipe 540 1700 540 600; swipe 540 600 540 1700; }
X02=$(center 'Buscando Am')
window HU02 hu02
check HU02 "dump | grep -q 'albumName'"

# ---------- HU04: detalle de artista ----------
pback; ptap 'performerListFragment'; wait_for 'performerRow'
X04a=$(center 'text="Rub.n Blades'); X04b=$(center 'text="Queen"')
hu04() { tap $X04a; swipe 540 1700 540 600; swipe 540 600 540 1700; back; tap $X04b; swipe 540 1700 540 600; swipe 540 600 540 1700; }
window HU04 hu04
check HU04 "dump | grep -q 'text=\"Queen\" resource-id=\"[^\"]*performerName'"

# ---------- HU05: listado de coleccionistas ----------
pback; ptap 'albumListFragment'; wait_for 'albumRow'
X05=$(center 'collectorListFragment')
hu05() { tap $X05; swipe 540 1500 540 600; swipe 540 600 540 1500; }
window HU05 hu05
check HU05 "dump | grep -q 'collectorRow'"

# ---------- HU07: crear álbum ----------
ptap 'albumListFragment'; wait_for 'albumRow'
X07=$(center 'primaryAction')
hu07() {
  tap $X07; tap 540 852; type_ "Zeta API21"
  tap 540 1116; type_ "https://example.com/zeta.jpg"
  tap 540 1380; tap 177 1268; tap 866 1746
  tap 540 1722; type_ "Album de perfilamiento"
  swipe 540 1700 540 500; tap 660 1388; tap 473 1631; tap 789 2032
}
window HU07 hu07
check HU07 "curl -s $API/albums | grep -q 'Zeta API21'"

# ---------- HU09: comentar álbum ----------
wait_for 'albumRow'; ptap 'Buscando Am'; wait_for 'albumName'
"$ADB" shell input swipe 540 1700 540 600 400; sleep 2.5
X09=$(center 'text="Comentar"')
hu09() { tap $X09; swipe 1040 1050 1040 500; tap 333 1039; tap 506 1409; tap 540 1735; type_ "123456789012345678901234567890"; tap 785 2010; }
window HU09 hu09
check HU09 "curl -s $API/albums/100/comments | grep -q '123456789012345678901234567890'"

# ---------- HU10: artista favorito ----------
ptap 'collectorListFragment'; wait_for 'collectorRow'; ptap 'text="API21 Perfilamiento"'; wait_for 'collectorPhone'
X10=$(center 'text="Agregar favorito"')
hu10() { tap $X10; tap 540 366; type_ "Queen"; tap 540 670; tap 540 2021; }
window HU10 hu10
check HU10 "curl -s $API/collectors/1/performers | grep -q 'Queen'"

# ---------- HU12: músico a banda ----------
ptap 'performerListFragment'; wait_for 'performerRow'; ptap 'text="API21 Perfilamiento"'; wait_for 'addMemberButton'
X12=$(center 'addMemberButton')
hu12() { tap $X12; tap 540 366; type_ "API21"; tap 540 648; tap 540 2021; }
window HU12 hu12
check HU12 "curl -s $API/bands/1/musicians | grep -q 'API21 Musico'"

# ---------- HU13: crear premio ----------
wait_for 'addPrizeButton'; ptap 'addPrizeButton'; wait_for 'createPrize'
X13=$(center 'resource-id="[^"]*createPrize"')
hu13() { tap $X13; tap 540 588; type_ "Zeta Premio"; tap 540 930; type_ "Premio de prueba"; tap 540 1271; type_ "Zeta Org"; tap 789 2032; }
window HU13 hu13
check HU13 "curl -s $API/prizes | grep -q 'Zeta Premio'"

# ---------- HU14: premio a artista ----------
ptap 'headerClose'; wait_for 'addPrizeButton'
X14=$(center 'addPrizeButton')
hu14() { tap $X14; tap 540 366; type_ "API21"; tap 540 648; tap 540 2021; tap 540 1801; tap 177 1268; tap 881 1746; tap 540 2010; }
window HU14 hu14
check HU14 "curl -s $API/bands/1 | grep -q 'performerPrizes\":\[{'"

# ---------- HU15: álbum a artista ----------
wait_for 'addArtistAlbumButton'
X15=$(center 'addArtistAlbumButton')
hu15() { tap $X15; tap 540 366; type_ "API21 A"; tap 540 670; tap 540 2021; }
window HU15 hu15
check HU15 "curl -s $API/bands/1/albums | grep -q 'API21 Album'"

echo '' >> "$VENT"; echo '}' >> "$VENT"
log "Fin de la pasada; detengo el muestreo"
sh_ rm -f /data/local/tmp/muestreo; touch "$OUT/.parar-memoria"; sleep 3; wait $SAMPLERPID $MEMPID 2>/dev/null
"$ADB" pull /data/local/tmp/cpu.txt "$(cygpath -m "$OUT/cpu-crudo.txt")" >/dev/null
kill $LOGPID $EVPID 2>/dev/null
curl -s $API/bands/1 > "$OUT/backend-final.json"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
cat > "$OUT/entorno.json" <<J
{"stage": "$STAGE", "repetition": $REP, "pilot": $([ -n "$PILOT" ] && echo true || echo false), "pid": $APPPID,
 "apk": "$(basename "$APK")", "apk_sha256": "$SHA",
 "device": "$(sh_ getprop ro.product.model) / Android $(sh_ getprop ro.build.version.release) (API $(sh_ getprop ro.build.version.sdk))",
 "avd": "Vinilos_API_21", "utc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
 "order": ["HU02","HU04","HU05","HU07","HU09","HU10","HU12","HU13","HU14","HU15"],
 "method": "ADB coordinate replay with equal pauses ($PAUSE s per action, $KEYPAUSE s per character, $REST s final rest); preparation by view hierarchy outside the windows; fresh app data and reseeded backend each pass; process kept between stories; no forced GC; device uptime clock; CPU App from /proc every 0.2 s; memory from /proc/PID/smaps read with run-as outside the app every ~0.5 s, private memory grouped like Android App Summary (Studio Total formula, not PSS); dumpsys meminfo not used because on API 21 it forces GCs in the app."}
J
log "Listo: $OUT"
