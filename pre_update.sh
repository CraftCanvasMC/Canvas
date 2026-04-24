#!/bin/bash
set -euo pipefail

echo "=== Applying all patches ==="
./gradlew applyAllPatches --quiet

echo "=== Enabling git file patches ==="
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

echo "=== Rebuilding single file patches ==="
./gradlew rebuildFoliaSingleFilePatches --quiet

echo "=== Rebuilding file patches as Git patches ==="
./gradlew rebuildAllServerFilePatches --quiet
./gradlew rebuildPaperApiFilePatches --quiet

echo "=== Moving Minecraft source patches to sources_unapplied ==="
dirs=(
  "canvas-server/minecraft-patches/sources canvas-server/minecraft-patches/sources_unapplied"
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
echo "=== Run the following tasks during update to apply patches and move failed ones ==="
echo "  ./gradlew applyOrMovePaperServerFilePatches"
echo "  ./gradlew applyOrMovePaperApiFilePatches"
