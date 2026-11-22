#!/bin/bash
# Serie formal: 3 pasadas antes y 3 después, en ese orden. Se detiene si una pasada falla o una verificación no pasa.
R=/c/WSL/pf-api21; F=$R/formal; mkdir -p $F
for spec in "antes 1" "antes 2" "antes 3" "despues 1" "despues 2" "despues 3"; do
  set -- $spec; st=$1; n=$2
  apk=$R/apk/antes-6a8d5c1.apk; [ $st = despues ] && apk=$R/apk/despues-e69c12b.apk
  out=$F/$st/pasada-$n
  echo "[$(date +%H:%M:%S)] INICIO $st $n"
  bash $R/run/pass.sh $st $n $apk $out > $F/$st-pasada-$n.out 2>&1; rc=$?
  if [ $rc -ne 0 ] || grep -q false $out/checks.txt || [ "$(grep -c true $out/checks.txt)" != 10 ]; then
    echo "[$(date +%H:%M:%S)] FALLA $st $n (rc=$rc)"; exit 1; fi
  echo "[$(date +%H:%M:%S)] OK $st $n"
done
echo "[$(date +%H:%M:%S)] SERIE COMPLETA"
