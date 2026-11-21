#!/bin/bash
# Usage: gfx.sh <file.gfx>. Prints frames, janky frames and p90 in ms; on API 21 computes them from "Profile data".
f=$1
if grep -q "Total frames rendered" $f; then
  tr -d '\r' < $f | awk '/Total frames rendered/{t=$4} /^Janky frames:/{j=$3} /^90th percentile/{p=$3; sub(/ms/,"",p)} END{printf "frames=%s janky=%s p90_ms=%s\n", t, j, p}'
else
  tr -d '\r' < $f | awk '/Profile data in ms/{on=1; next} on && /^\t?[0-9.]+\t[0-9.]+\t[0-9.]+/{n++; s=$1+$2+$3; v[n]=s; if (s>16) j++} on && /View hierarchy|^$/{if(n) on=0}
    END{ for(i=1;i<=n;i++) for(k=i+1;k<=n;k++) if(v[k]<v[i]){x=v[i];v[i]=v[k];v[k]=x}; p=(n? v[int(n*0.9+0.5)>0?int(n*0.9+0.5):1]:0); printf "frames=%d janky=%d p90_ms=%.1f\n", n, j, p }'
fi
