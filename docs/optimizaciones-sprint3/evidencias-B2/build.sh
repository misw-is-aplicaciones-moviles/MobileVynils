#!/usr/bin/env bash
# Builds temporary debug APKs with identical probes. Does not edit the delivery worktree.
set -euo pipefail
S=$(cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(git -C "$S" rev-parse --show-toplevel)
OUT=${1:?Usage: build.sh OUTPUT_DIRECTORY [common-commit]}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
COMMON=${2:-14e53d2}
BEFORE=6a8d5c1
WORK=$(mktemp -d /tmp/vinilos-b2-benchmark.XXXXXX)
rmdir "$WORK"
git -C "$ROOT" worktree add --detach "$WORK" "$COMMON"
trap 'git -C "$ROOT" worktree remove --force "$WORK"' EXIT
cp "$ROOT/local.properties" "$WORK/local.properties"
for variant in before after; do
  git -C "$WORK" restore --source="$COMMON" -- app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt app/src/main/java/co/edu/uniandes/vinilos/viewmodels/PerformerAlbumPickerUiState.kt
  rm -f "$WORK/app/src/main/java/co/edu/uniandes/vinilos/ui/artists/B2Probe.kt"
  if [ "$variant" = before ]; then
    for rel in ui/artists/PerformerDetailAdapter.kt viewmodels/PerformerAlbumPickerUiState.kt; do
      git -C "$ROOT" show "$BEFORE:app/src/main/java/co/edu/uniandes/vinilos/$rel" > "$WORK/app/src/main/java/co/edu/uniandes/vinilos/$rel"
    done
  fi
  python3 "$S/prepare.py" "$WORK"
  git -C "$WORK" diff -- app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt app/src/main/java/co/edu/uniandes/vinilos/viewmodels/PerformerAlbumPickerUiState.kt > "$OUT/$variant.patch"
  "$WORK/gradlew" -p "$WORK" assembleDebug assembleDebugAndroidTest > "$OUT/build-$variant.log" 2>&1
  cp "$WORK/app/build/outputs/apk/debug/app-debug.apk" "$OUT/$variant.apk"
  cp "$WORK/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$OUT/$variant-test.apk"
done
printf 'APKs built in %s\n' "$OUT"
