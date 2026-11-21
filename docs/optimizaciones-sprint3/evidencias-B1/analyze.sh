#!/bin/bash
# Usage: analyze.sh <results dir of one device>. One line per build, window and repetition.
cd "$1"
for f in *_r[0-9].log; do
  build=${f%%_r*}; rep=${f#*_r}; rep=${rep%.log}
  # Some emulators keep older lines after logcat -c and add trailing spaces: read from the last start, trimmed.
  start=$(sed 's/[[:space:]]*$//' $f | grep -n "^MARK url_type_start$" | tail -1 | cut -d: -f1)
  sed 's/[[:space:]]*$//' $f | tail -n +$start | awk -v b=$build -v r=$rep '
    /^MARK .*_start$/ { w = $2; sub(/_start$/, "", w); inw = 1; first = ""; next }
    /^MARK .*_end$/ {
      printf "%s %s r%s cover_load=%d glide_started=%d create_render=%d create_ms=%.2f create_allocs=%d sheet_render=%d sheet_ms=%.2f sheet_allocs=%d stars_draw=%d header_bind=%d header_ms=%.2f header_allocs=%d detail_render=%d detail_ms=%.2f detail_allocs=%d global_allocs=%d global_kb=%d\n",
        w, b, r, n["cover_load"], glide, n["create_render"], ns["create_render"]/1e6, al["create_render"], n["sheet_render"], ns["sheet_render"]/1e6, al["sheet_render"], n["stars_draw"], n["header_bind"], ns["header_bind"]/1e6, al["header_bind"], n["detail_render"], ns["detail_render"]/1e6, al["detail_render"], ga1 - ga0, (gb1 - gb0) / 1024
      delete n; delete ns; delete al; glide = 0; inw = 0; next }
    !inw { next }
    /Started new load/ { glide++ ; next }
    /^[a-z_]+ ns=/ {
      name = $1; n[name]++
      for (i = 2; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
      ns[name] += v["ns"]; al[name] += v["allocs"]
      if (first == "") { ga0 = v["gallocs"]; gb0 = v["gbytes"]; first = 1 }
      ga1 = v["gallocs"]; gb1 = v["gbytes"]
    }'
done | sort -k1,1 -k2,2 -k3,3
