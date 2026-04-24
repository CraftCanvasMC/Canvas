#!/bin/bash
set -euo pipefail

echo "=== Applying all patches ==="
./gradlew applyAllPatches --quiet

echo "=== Enabling Git file patches ==="
BUILD_FILES=(
  "build.gradle.kts"
  "canvas-server/build.gradle.kts"
)

for file in "${BUILD_FILES[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: $file not found"
    exit 1
  fi

  sed -i 's/gitFilePatches *= *false/gitFilePatches = true/' "$file"
done

echo "=== Rebuilding single-file patches ==="
./gradlew rebuildFoliaSingleFilePatches --quiet

echo "=== Rebuilding file patches as Git patches ==="
./gradlew rebuildAllServerFilePatches --quiet
./gradlew rebuildPaperApiFilePatches --quiet

echo "=== Moving file patches to _unapplied ==="
dirs=(
  "canvas-server/minecraft-patches/sources canvas-server/minecraft-patches/sources_unapplied"
  "canvas-server/paper-patches/files canvas-server/paper-patches/files_unapplied"
  "canvas-server/folia-patches/files canvas-server/folia-patches/files_unapplied"
  "canvas-api/paper-patches/files canvas-api/paper-patches/files_unapplied"
  "canvas-api/folia-patches/files canvas-api/folia-patches/files_unapplied"
)

for dir in "${dirs[@]}"; do
  set -- $dir
  src=$1
  dest=$2

  if [[ -d "$src" ]]; then
    mkdir -p "$dest"
    mv "$src"/* "$dest"/ 2>/dev/null || true
  fi
done

echo "=== REMINDER: ==="
echo "After moving patches back to their directories during update, run the following tasks to apply them and move failed ones:"
echo "  ./gradlew applyOrMovePaperServerFilePatches"
echo "  ./gradlew applyOrMovePaperApiFilePatches"
