#!/bin/bash
# Usage: summary.sh <device>. Median and range of each metric for both builds.
S=$(cd "$(dirname "$0")" && pwd)  # results/ and apk/ live next to the scripts
D=$S/results/$1
{
  bash $S/analyze.sh $D
  for g in $D/*.gfx; do b=$(basename $g .gfx); build=${b%%_r*}; rest=${b#*_r}; rep=${rest%%_*}; w=${rest#*_}
    echo "$w $build r$rep $(bash $S/gfx.sh $g)"; done
  for m in $D/*.mem; do b=$(basename $m .mem); build=${b%%_r*}; rest=${b#*_r}; rep=${rest%%_*}; w=${rest#*_}
    tr -d '\r' < $m | awk -v w=$w -v b=$build -v r=$rep '/^ *Java Heap:/{jh=$3} /^ *TOTAL PSS:/{tp=$3} /^ *TOTAL:/{if(!tp) tp=$2} /^ +TOTAL +[0-9]/{if(!tp) tp=$2} /^ *Dalvik Heap /{if(!dh) dh=$3}
      END{printf "%s %s r%s java_heap_kb=%s total_pss_kb=%s\n", w, b, r, (jh?jh:dh), tp}'; done
} | awk '{ key=$1" "$2; for(i=4;i<=NF;i++){split($i,kv,"="); m=key" "kv[1]; if(!(m in seen)){seen[m]=1; order[++n]=m}; vals[m]=vals[m]" "kv[2]} }
  END{ for(i=1;i<=n;i++){ m=order[i]; c=split(substr(vals[m],2),a," "); for(x=1;x<=c;x++) for(y=x+1;y<=c;y++) if(a[y]+0<a[x]+0){t=a[x];a[x]=a[y];a[y]=t}
       med=(c%2)?a[(c+1)/2]:(a[c/2]+a[c/2+1])/2; printf "%s median=%s range=%s-%s n=%d\n", m, med, a[1], a[c], c } }' | sort
